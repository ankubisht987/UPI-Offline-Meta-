# Dynamic Bridge Rotation Update

## Overview

This document describes the **Dynamic Bridge Rotation** update for the UPI Offline Mesh project. Previously, `bridge-node` was hardcoded as the only device with internet access. After this update, internet access can be **dynamically rotated to any of the 5 devices** (4 offline phones + 1 bridge) at runtime, while ensuring **exactly one device has internet at all times** and all in-flight packets are preserved.

---

## What Changed

### 1. `VirtualDevice.java`

- Changed `hasInternet` from `final` to mutable field.
- Added setter: `public void setHasInternet(boolean hasInternet)`.
- This allows internet access to be toggled on an existing device without recreating it or clearing its packet inbox.

### 2. `MeshSimulatorService.java`

Added:

```java
private String currentBridgeId;
```

**`resetMesh()`:**
- Creates all 5 devices (`phone-ankush`, `phone-bob`, `phone-carol`, `phone-dave`, `bridge-node`) if they do not already exist.
- Calls `rotateBridge()` immediately to assign internet to exactly one device.

**`rotateBridge()`:**
- Randomly selects one device ID from the existing map.
- Sets `hasInternet = true` for that device.
- Sets `hasInternet = false` for all other devices.
- Updates `currentBridgeId`.
- **Does NOT clear or recreate devices**, so all packets currently in each device's inbox are preserved.

### 3. `ApiController.java`

Added endpoint:

```
POST /api/mesh/rotate-bridge
```

- Calls `mesh.rotateBridge()` internally.
- Returns JSON containing `message`, `currentBridge`, and `mesh` snapshot showing updated `hasInternet` and `packetCount` for all devices.

---

## Why This Change

| Before | After |
| :-- | :-- |
| `bridge-node` was the only device that could ever have internet. | Any of the 5 devices can become the bridge at runtime. |
| To change the bridge, devices were recreated and all packets were lost. | Internet is toggled on existing devices; packets are preserved. |
| Static bridge simulation. | Dynamic, realistic simulation where signal moves between devices. |

This simulates real-world scenarios where different users step outside and gain internet access at different times.

---

## Security Impact

**None.** All security checks run on the server, independent of which device has internet:

| Security Control | Status | Reason |
| :-- | :-- | :-- |
| Hybrid encryption (RSA + AES-GCM) | Unchanged | Encryption/decryption logic does not depend on bridge identity. |
| Tamper detection | Unchanged | Tampered ciphertexts are still rejected during decryption. |
| Idempotency (`IdempotencyService`) | Unchanged | Uses ciphertext hash, not `bridgeNodeId`. |
| Settlement (`@Transactional` + `@Version`) | Unchanged | Atomic fund movement unaffected. |
| Freshness checks | Unchanged | Based on `signedAt`, not bridge identity. |
| Duplicate DB guard | Unchanged | Unique index on `packetHash` still applies. |

---

## Logic Impact

| Area | Impact | Notes |
| :-- | :-- | :-- |
| Mesh gossip | None | Packets still hop, TTL decrements, duplicates blocked. |
| `collectBridgeUploads()` | None | Filters by `hasInternet` on whatever device currently has it. |
| `MeshFlushService` | None | Ingests packets from the current bridge exactly as before. |
| Hop count approximation | None | Still based on `initialTtl - currentTtl`. |
| TTL expiry | **Can matter** | If you rotate the bridge after a packet's TTL has dropped too low, it may expire before reaching the new bridge. Rotate while TTL is still healthy. |
| Packets in-flight | **Preserved** | No `devices.clear()` occurs; inbox contents remain intact across rotations. |

---

## New Endpoint

### `POST /api/mesh/rotate-bridge`

Moves internet connectivity to a random device among the 5, ensuring exactly one device has internet.

**Request:**
```http
POST http://localhost:1010/api/mesh/rotate-bridge
```

**Response:**
```json
{
  "message": "Bridge moved",
  "currentBridge": "phone-bob",
  "devices": {
    "phone-ankush": {"hasInternet": false, "packetCount": 0},
    "phone-bob": {"hasInternet": true, "packetCount": 1},
    "phone-carol": {"hasInternet": false, "packetCount": 0},
    "phone-dave": {"hasInternet": false, "packetCount": 0},
    "bridge-node": {"hasInternet": false, "packetCount": 0}
  }
}
```

---

## How to Test

### Via PowerShell

```powershell
# Start app first: .\mvnw.cmd spring-boot:run

# Check current bridge state
Invoke-RestMethod "http://localhost:1010/api/mesh/state" | ConvertTo-Json -Depth 5

# Rotate bridge to a random phone
Invoke-RestMethod -Method Post "http://localhost:1010/api/mesh/rotate-bridge" | ConvertTo-Json -Depth 5

# Check again - exactly one device will have hasInternet: true
Invoke-RestMethod "http://localhost:1010/api/mesh/state" | ConvertTo-Json -Depth 5
```

### Full flow after rotation

1. `POST /api/demo/send?sender=ankush@bank&receiver=sahil@hdfc&amount=100&ttl=5`
2. `POST /api/mesh/gossip` (repeat until packet reaches current bridge)
3. `POST /api/mesh/rotate-bridge` (simulate signal moving)
4. `POST /api/mesh/gossip` (spread towards new bridge)
5. `POST /api/mesh/flush` (new bridge uploads → server settles)

### Verify only one device has internet

After every rotation, check `/api/mesh/state`. Exactly **one** device should show `"hasInternet": true`, all others `false`.

---

## Precautions

| Precaution | Reason |
| :-- | :-- |
| **Rotate only while TTL > 0** | If all packets have `ttl = 0`, they cannot move to the new bridge and will expire. |
| **Do not call `devices.clear()`** | The old implementation recreated devices and wiped all inboxes. Keep `hasInternet` mutable to preserve packets. |
| **Rotate before injection (optional)** | For a clean demo, rotate the bridge before injecting a packet, or ensure the packet still has TTL hops left after rotation. |

---

## Files Changed

| File | Change |
| :-- | :-- |
| `src/main/java/com/ankush/upi_offline_meta/services/VirtualDevice.java` | `hasInternet` now mutable; added `setHasInternet()`. |
| `src/main/java/com/ankush/upi_offline_meta/services/MeshSimulatorService.java` | Added `rotateBridge()`, `getCurrentBridgeId()`, `currentBridgeId` field, `Random` field. |
| `src/main/java/com/ankush/upi_offline_meta/controller/ApiController.java` | Added `POST /api/mesh/rotate-bridge` endpoint. |
| `src/main/resources/templates/dashboard.html` | Optional UI button to trigger rotation (see dashboard demo section below). |

---

## Dashboard Demo (Optional UI)

Add this button to `dashboard.html`:

```html
<button class="alt" onclick="rotateBridge()">Move bridge to random phone</button>
```

Add JavaScript function:

```js
async function rotateBridge() {
  try {
    const r = await postJson('/api/mesh/rotate-bridge');
    log('Bridge moved to: ' + r.currentBridge);
  } catch (e) { log('rotate-bridge failed: ' + e.message); }
  refresh();
}
```

Your mesh section will now show which device is online (`🌐 online`) vs offline (`📴 offline`) and update dynamically when you rotate.

---

## Summary

- **Security:** No impact. All controls remain server-side and bridge-agnostic.
- **Logic:** No impact. Gossip, flush, ingestion, and settlement work identically.
- **Packets:** Fully preserved across rotations.
- **Requirement met:** Exactly one device has internet at all times, selectable dynamically from the 5 available devices.