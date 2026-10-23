# UPI Offline Mesh

A proof-of-concept for **offline UPI-like payments** using a store-and-forward mesh network. Devices pass tamper-proof, encrypted payment packets over Bluetooth/Wi-Fi Direct without internet, and a bridge device uploads them to the bank server when connectivity is available.

---

## Table of Contents

- [Overview](#overview)
- [Problem Statement](#problem-statement)
- [Solution](#solution)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [End-to-End Payment Flow](#end-to-end-payment-flow)
- [Security Design](#security-design)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running the Application](#running-the-application)
- [Testing](#testing)
- [Dashboard Demo](#dashboard-demo)
- [Core Concepts Explained](#core-concepts-explained)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Author](#author)

---

## Overview

`UPI Offline Mesh` demonstrates how digital payments can work in low-connectivity or no-connectivity areas (rural zones, network outages, underground metros, etc.).

Instead of requiring both sender and receiver to be online, payments are encrypted on the sender's device and hop between nearby devices until they reach a device with internet access. That "bridge" device securely delivers the sealed packet to the server, which verifies, deduplicates and settles it atomically.

---

## Problem Statement

Traditional UPI requires both payer and payee (and PSPs) to have active internet connectivity to authorize and settle a transaction in real-time. This becomes a blocker in areas with poor network coverage.

---

## Solution

- **Offline Authorisation:** The sender signs and encrypts a `PaymentInstruction` on their device using hybrid cryptography.
- **Store-and-Forward Mesh:** Nearby phones gossip the encrypted packet peer-to-peer, decreasing TTL with each hop.
- **Bridge Upload:** The first device that gains internet connectivity acts as a bridge and uploads the sealed packet to the server.
- **Secure Settlement:** The server decrypts, validates freshness, prevents duplicates and atomically moves funds between accounts.

---

## Key Features

| Feature | Description |
| :-- | :-- |
| **Hybrid Cryptography** | RSA-OAEP (2048) to wrap a per-packet AES-256-GCM key. AES-GCM provides authenticated encryption and detects any tampering. |
| **Mesh Simulation** | Realistic gossip protocol with TTL, hop counting, duplicate suppression per device and a designated bridge node. |
| **End-to-End Idempotency** | Double-layer protection: in-memory `ConcurrentHashMap.putIfAbsent` + DB unique constraint on `packetHash`. |
| **Atomic Settlement** | `@Transactional` money movement with `@Version` optimistic locking on `Account` to prevent race conditions. |
| **Freshness Checks** | Rejects packets older than 24 hours or with timestamps > 5 minutes in the future to mitigate replay attacks. |
| **Tamper Detection** | Invalid/corrupted ciphertexts are rejected without touching account balances. |
| **Visual Dashboard** | Thymeleaf-based UI to simulate and visualize the entire flow in real-time. |
| **Concurrency Safe** | Proven correct under 25 simultaneous deliveries of the same packet. |

---

## Tech Stack

| Technology | Purpose |
| :-- | :-- |
| **Java 17** | Core language |
| **Spring Boot 3.5.14** | Application framework |
| **Spring Data JPA (Hibernate)** | ORM & persistence |
| **Spring Web** | REST APIs |
| **Spring Scheduling** | TTL eviction for idempotency cache |
| **Thymeleaf** | Server-side rendered dashboard |
| **MySQL 8.0** | Relational database |
| **JUnit 5** | Unit & integration testing |
| **Maven** | Build & dependency management |
| **Jackson** | JSON serialization/deserialization |

---

## Architecture

```text
[Sender Phone] --(encrypt)--> [PaymentInstruction sealed as MeshPacket]
        |                            |  (Bluetooth/Wi-Fi Direct gossip, TTL--)
      (no net)                    [Peer Phones]
                                     |
                                  [Bridge Phone] --(has internet)--> [REST API /api/bridge/ingest]
                                                                          |
                                                                    [BridgeIngestionService]
                                                                          |-- hashCiphertext -> IdempotencyService.claim
                                                                          |-- HybridCryptoService.decrypt
                                                                          |-- Freshness validation (age + skew)
                                                                          |-- SettlementService.settle (@Transactional + @Version)
                                                                          v
                                                                       [MySQL DB]
                                                                    Accounts | Transactions
```

### Core Components

| Component | Responsibility |
| :-- | :-- |
| `HybridCryptoService` | Hybrid RSA+AES-GCM encrypt/decrypt and SHA-256 fingerprinting of ciphertext. |
| `ServerKeyHolder` | Generates and exposes the server's RSA key pair on startup. |
| `DemoService` | Seeds demo accounts and creates valid `MeshPacket`s from `PaymentInstruction`s. |
| `VirtualDevice` | Simulated phone holding a deduplicated inbox of packets. |
| `MeshSimulatorService` | Orchestrates the mesh: `inject()`, `gossipOnce()`, `snapshot()`, `collectBridgeUploads()`, `clearBridge()`, `resetMesh()`. |
| `IdempotencyService` | In-memory claim-store with `putIfAbsent`, TTL and scheduled eviction. |
| `SettlementService` | Validates accounts/funds/amount, moves balances atomically and writes ledger entries. |
| `BridgeIngestionService` | Enforces the pipeline: `hash → claim → decrypt → freshness → settle`. |
| `MeshFlushService` | End-to-end flush: pulls from bridge → ingests → clears bridge → returns summary. |
| `ApiController` | REST endpoints for mesh, demo, ingestion, accounts and transactions. |
| `DashboardController` | Serves the Thymeleaf dashboard at `/`. |

---

## End-to-End Payment Flow

| Step | Action | Notes |
| :-- | :-- | :-- |
| **1. Setup** | App boots, `ServerKeyHolder` generates RSA keys, `DemoService` seeds 4 accounts if empty. | MySQL schema auto-updated via `ddl-auto=update`. |
| **2. Create Payment** | Sender taps "Pay". `DemoService.createPacket()` builds `PaymentInstruction` (sender, receiver, amount, `pinHash` = SHA-256(pin), nonce, `signedAt`). | Real PIN is never transmitted. |
| **3. Encrypt** | `HybridCryptoService.encrypt()` generates a random AES-256 key, encrypts JSON with AES-GCM, wraps AES key with server RSA public key. Output is Base64 of `[wrappedKey(256)][IV(12)][ciphertext+tag]`. | Tamper-evident by design. |
| **4. Inject into Mesh** | `POST /api/demo/send` injects the `MeshPacket` into a chosen device's inbox. | `ttl` defaults to 5. |
| **5. Gossip** | `POST /api/mesh/gossip` propagates packets one hop per call. Each copy gets `ttl-1`. Devices ignore duplicates and packets with `ttl <= 0`. | Snapshot-before-spread prevents infinite propagation. |
| **6. Reach Bridge** | The `bridge-node` (with `hasInternet=true`) receives a copy via gossip. | Multiple hops tracked via `hopCount` approximation (`initialTtl - currentTtl`). |
| **7. Flush/Upload** | `POST /api/mesh/flush` collects bridge packets, calls `BridgeIngestionService.ingest()` for each, then clears the bridge. | One call closes the entire loop. |
| **8. Verify & Deduplicate** | `hashCiphertext()` computed, `IdempotencyService.claim()` checks memory, then decrypt validates with server private key. | Tampered packets throw → `INVALID`. |
| **9. Freshness** | Reject if `signedAt < now - 24h` or `signedAt > now + 5min`. | Mitigates replay attacks. |
| **10. Settle Atomically** | `SettlementService.settle()` loads accounts, checks existence/funds/amount>0, debits sender, credits receiver, writes `Transaction` with `SETTLED` or `REJECTED`. Runs in `@Transactional`. | `@Version` on `Account` enables optimistic locking. DB unique index on `packetHash` is final guard. |
| **11. Report** | Returns `IngestResult(outcome, packetHash, transactionId, transactionStatus)`. Dashboard reflects updated balances and ledger. | `DUPLICATE_DROPPED` for any re-delivery. |

---

## Security Design

| Control | Rationale |
| :-- | :-- |
| **Hybrid Encryption** | Bypasses RSA size limits while retaining RSA's key-distribution model. |
| **AES-GCM Authenticated Encryption** | Any bit flip causes decryption failure; prevents ciphertext tampering/modification. |
| **Per-Packet AES Key** | Compromise of one key doesn't affect others. |
| **Ciphertext as Idempotency Key** | `packetId` can be spoofed, ciphertext cannot be forged for arbitrary payloads. |
| **PIN Hashing** | Only `SHA-256(pin)` is stored/transmitted in the instruction. |
| **Freshness Window** | 24h max age + 5min future skew prevents old/replayed packets. |
| **Atomic Settlement** | `@Transactional` ensures debit+credit+ledger are all-or-nothing. |
| **Optimistic Locking** | `@Version` prevents lost updates on concurrent balance changes. |
| **Double Deduplication** | In-memory + DB guarantees exactly-once settlement even across crashes/restarts. |
| **Non-Negative Amounts** | `amount.signum() <= 0` rejected to prevent money creation. |

---

## API Reference

Base URL: `http://localhost:1010`

### Public/Server

| Method | Endpoint | Description |
| :-- | :-- | :-- |
| `GET` | `/api/server-key` | Returns server RSA public key in Base64 + metadata. |

### Mesh Simulation

| Method | Endpoint | Description |
| :-- | :-- | :-- |
| `POST` | `/api/demo/send` | Create & inject a packet. Query: `fromDevice`, `sender`, `receiver`, `amount`, `pin`(default `1234`), `ttl`(default `5`). |
| `POST` | `/api/mesh/gossip` | Perform one gossip hop across all devices. |
| `GET` | `/api/mesh/state` | Snapshot of each device: `hasInternet`, `packetCount`. |
| `POST` | `/api/mesh/reset` | Clear mesh and recreate the 5 devices. |
| `POST` | `/api/mesh/flush` | Ingest all bridge packets. Query: `initialTtl`(default `5`). Returns `FlushResult`. |

### Ingestion

| Method | Endpoint | Description |
| :-- | :-- | :-- |
| `POST` | `/api/bridge/ingest` | Ingest a single `MeshPacket`. Query: `bridgeNodeId`(default `bridge-node`), `hopCount`(default `0`). Returns `IngestResult`. |

### Data

| Method | Endpoint | Description |
| :-- | :-- | :-- |
| `GET` | `/api/accounts` | List all accounts with balances. |
| `GET` | `/api/transactions` | List top 20 most recent transactions. |

### UI

| Method | Endpoint | Description |
| :-- | :-- | :-- |
| `GET` | `/` | Thymeleaf dashboard for full visual demo. |

### Sample `IngestResult`

```json
{
  "outcome": "SETTLED",
  "packetHash": "9f7a...e3b2",
  "transactionId": 12,
  "transactionStatus": "SETTLED"
}
```

Possible `outcome` values: `SETTLED`, `REJECTED`, `DUPLICATE_DROPPED`, `INVALID`.

---

## Project Structure

```text
UPI_Offline_Mesh/
└── src
    ├── main
    │   ├── java/com/ankush/upi_offline_meta
    │   │   ├── UpiOfflineMetaApplication.java  # Entry point + @EnableScheduling
    │   │   ├── controller/
    │   │   │   ├── ApiController.java        # REST endpoints
    │   │   │   └── DashboardController.java  # Serves dashboard
    │   │   ├── crypto/
    │   │   │   ├── HybridCryptoService.java  # RSA+AES-GCM
    │   │   │   └── ServerKeyHolder.java      # Key pair generator
    │   │   ├── model/
    │   │   │   ├── Account.java             # Entity with @Version
    │   │   │   ├── AccountRepository.java
    │   │   │   ├── MeshPacket.java         # Network packet
    │   │   │   ├── PaymentInstruction.java # Decrypted payload
    │   │   │   ├── Transaction.java        # Ledger entry
    │   │   │   └── TransactionRepository.java
    │   │   └── services/
    │   │       ├── BridgeIngestionService.java # Ingestion pipeline
    │   │       ├── DemoService.java            # Seeding + packet creation
    │   │       ├── FlushResult.java           # Flush summary DTO
    │   │       ├── IdempotencyService.java    # Duplicate guard + eviction
    │   │       ├── IngestResult.java          # Ingestion result DTO
    │   │       ├── MeshFlushService.java      # Orchestrates flush
    │   │       ├── MeshSimulatorService.java  # Mesh logic
    │   │       ├── SettlementService.java     # Money movement
    │   │       └── VirtualDevice.java         # Simulated phone
    │   └── resources
    │       ├── application.properties       # DB, server config
    │       └── templates/
    │           └── dashboard.html          # UI
    └── test
        └── java/com/ankush/upi_offline_meta
            ├── IdempotencyConcurrencyTest.java # Concurrency proof
            └── UpiOfflineMetaApplicationTests.java # Full test suite
```

---

## Getting Started

### Prerequisites

- [JDK 17+](https://adoptium.net/)
- [Maven 3.9+](https://maven.apache.org/download.cgi) (or use included `mvnw`)
- [MySQL 8.0+](https://dev.mysql.com/downloads/mysql/)
- [Git](https://git-scm.com/)

### Database Setup

Create the database in MySQL:

```sql
CREATE DATABASE upi_meta;
```

Update `src/main/resources/application.properties` if your credentials differ:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/upi_meta
spring.datasource.username=root
spring.datasource.password=root
server.port=1010
spring.jpa.hibernate.ddl-auto=update
```

> The app auto-seeds 4 demo accounts on first run: `ankush@bank`, `bob@bank`, `carol@bank`, `dave@bank`.

## Running the Application

### From terminal

```bash
# Navigate to project directory
cd "E:\UPI Project\upi-offline-meta"

# Run with Maven wrapper (Windows)
.\mvnw.cmd spring-boot:run

# Or on Linux/Mac
./mvnw spring-boot:run
```

### From IDE

Run `UpiOfflineMetaApplication.java` as a Spring Boot application.

Once started, you'll see logs for key generation, Hikari pool connection and app startup. The server runs on `http://localhost:1010`.

## Testing

Run the entire test suite:

```powershell
.\mvnw.cmd test
```

### Test Coverage

| Test Class | Tests | Coverage |
| :-- | :-- | :-- |
| `UpiOfflineMetaApplicationTests` | 19 | Crypto round-trip & tamper, seeding, mesh spread, idempotency, settlement (happy/reject/duplicate), ingestion pipeline (valid/stale/tampered/insufficient), flush flow |
| `IdempotencyConcurrencyTest` | 1 | 25 concurrent deliveries of the same packet → exactly-once settlement |

**Expected:** `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

## Dashboard Demo

The easiest way to see everything in action.

1. Start the app
2. Open [http://localhost:1010/](http://localhost:----)
3. **"▶ Run full demo"**

This will automatically:

1. Reset the mesh
2. Send a payment from `ankush@bank` → `bob@bank` for `₹100` from `phone-ankush`
3. Gossip once (packet spreads to all 5 devices)
4. Flush to server (bridge uploads → decrypt → settle)
5. Refresh UI to show updated balances and a new `SETTLED` transaction

You can also step through manually with **Send**, **Gossip once**, **Flush to server**, **Reset mesh**.

## Core Concepts Explained

| Concept | Explanation |
| :-- | :-- |
| **Hybrid Cryptography** | Combines the speed of symmetric encryption (AES) with the secure key exchange of asymmetric encryption (RSA). |
| **Gossip Protocol** | Each device shares packets with every other device it can reach. Simple, resilient and works without central routing. |
| **TTL (Time-To-Live)** | Hop limit. Prevents packets from circulating forever. Decrements on each forward. |
| **Idempotency** | "Do it once, no matter how many times asked". Critical to prevent double payments on retry/network duplication. |
| **Optimistic Locking** | Uses a version counter on the DB row. If two transactions try to update the same account simultaneously, one wins and the other fails safely. |
| **Store-and-Forward** | Data is held on intermediate nodes until a forwarding path exists (internet for the bridge). |
| **Exactly-Once Effect** | At-least-once delivery is inevitable in unreliable networks, but exactly-once settlement is achieved here through deduplication. |

## Troubleshooting

| Issue | Cause | Fix |
| :-- | :-- | :-- |
| `405 Method Not Allowed` | Called a `POST` endpoint via GET (browser address bar). | Use the dashboard buttons or `Invoke-RestMethod -Method Post`. |
| `Tests fail: count < 4` | Old accounts exist in MySQL from previous runs. | `TRUNCATE TABLE accounts;` and re-run tests. |
| `NullPointerException` in concurrency test | Missing `@Autowired` in test class. | Add `@Autowired` to fields or move test to `UpiOfflineMetaApplicationTests`. |
| `Cannot find template location: classpath:/templates/` | Thymeleaf on classpath but folder missing. | Ensure `src/main/resources/templates/dashboard.html` exists. (Just a warning, app still runs). |
| `java.lang.Exception` on `hashCiphertext` | Older code declaring `throws Exception`. | Ensure `hashCiphertext` catches `NoSuchAlgorithmException` and wraps in `IllegalStateException`. |
| Port 1010 already in use | Another process using it. | Kill it or change `server.port` in `application.properties`. |

## Roadmap

| Step | Milestone | Status |
| :-- | :-- | :-- |
| 1 | Boot + MySQL connection | ✅ Done |
| 2 | Hybrid RSA+AES-GCM encryption | ✅ Done |
| 3 | DemoService & seeded accounts | ✅ Done |
| 4 | Mesh simulator with gossip/TTL | ✅ Done |
| 5 | In-memory idempotency service | ✅ Done |
| 6 | Atomic settlement with optimistic locking | ✅ Done |
| 7 | Bridge ingestion pipeline | ✅ Done |
| 8 | One-call flush endpoint | ✅ Done |
| 9 | Thymeleaf dashboard | ✅ Done |
| 10 | Concurrency proof (exactly-once) | ✅ Done |

## Author

**Ankush** — Built as a hands-on learning project to understand offline payments, secure mesh networking, cryptography, idempotency, and distributed transaction safety.

> *This is an educational prototype. It is not intended for production use with real money.*
