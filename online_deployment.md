# UPI Offline Mesh — Online Deployment Guide

**Project:** UPI Offline Mesh  
**Version:** 0.0.1-SNAPSHOT  
**Spring Boot:** 3.5.14  
**Java:** 17  
**Database:** MySQL 8.0  
**Target Port:** 1010  

---

## 1. Project Analysis

### 1.1 Technologies in Use

| Layer | Technology | Version |
| :-- | :-- | :-- |
| Language | Java | 17 |
| Backend Framework | Spring Boot | 3.5.14 |
| Web Layer | Spring Web | 3.5.14 |
| ORM / Database Access | Spring Data JPA (Hibernate) | 3.5.14 |
| Frontend Templates | Thymeleaf (server-side HTML) | — |
| Build Tool | Maven | 3.x |
| Database Driver | MySQL Connector/J | — |
| JSON | Jackson | — |
| Logging | SLF4J / Logback | — |

### 1.2 Architecture Summary

Your project is a **monolithic Spring Boot application**. The backend (REST API + services) and frontend (Thymeleaf dashboard) are packaged together into a **single executable JAR**.

**Core components:**
- `UpiOfflineMetaApplication.java` — Entry point with `@EnableScheduling`
- `ApiController.java` — REST endpoints (`/api/*`)
- `DashboardController.java` — Serves `dashboard.html` at `/`
- `DashboardController.java` — Serves `dashboard.html` at `/`
- `HybridCryptoService.java` — RSA-OAEP + AES-GCM hybrid encryption
- `ServerKeyHolder.java` — Generates RSA key pair on startup
- `DemoService.java` — Seeds accounts and creates encrypted packets
- `MeshSimulatorService.java` — Simulated mesh phones (5 devices, gossip, TTL)
- `IdempotencyService.java` — Duplicate detection (`ConcurrentHashMap`, scheduled eviction)
- `SettlementService.java` — `@Transactional` money movement with `@Version` optimistic locking
- `BridgeIngestionService.java` — Pipeline: hash → claim → decrypt → freshness → settle
- `MeshFlushService.java` — One-call flush: collect → ingest → clear
- `MySQL 8.0` — `accounts` and `transactions` tables

### 1.3 What Needs Hosting

| Component | Details |
| :-- | :-- |
| **Spring Boot App** | Single JAR containing REST API + Thymeleaf dashboard |
| **MySQL Database** | Stores `accounts` and `transactions` tables |

There is **no separate frontend to host** — the dashboard is embedded in the JAR and served by Spring Boot.

---

## 2. Free Hosting Options

| Platform | App Hosting | Database | Permanence | Notes |
| :-- | :-- | :-- | :-- | :-- |
| **Oracle Cloud Always Free** | ✅ ARM VM (4 vCPU, 24 GB RAM) | ✅ Always Free MySQL or self-installed | Permanent (no trial expiry) | **Recommended** |
| **AWS Free Tier** | ✅ EC2 t3.micro (12 months) | ⚠️ RDS free 12 months only | Expires after 1 year | Good for testing |
| **Render** | ✅ Free web service | ❌ No free managed MySQL | Permanent | DB must be external |
| **Fly.io / Koyeb** | ✅ Small free tier | ❌ No free managed MySQL | Limited | DB must be external |
| **Aiven / TiDB Cloud** | ❌ No app hosting | ✅ Free tier | Limited | App must be hosted elsewhere |

**Best choice:** Oracle Cloud Always Free — you can run both MySQL and your Spring Boot app on one ARM VM with no expiry and no credit card required after signup.

---

## 3. Pre-Deployment Checklist (Do Not Skip)

### 3.1 Verify Build & Tests Locally

```powershell
cd "E:\UPI Project\upi-offline-meta"
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd test
```

Expected: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`

### 3.2 Externalize Configuration (Important)

Your current `application.properties` hardcodes:
- `root/root` credentials
- `localhost:3306` database host
- Port `1010`

For deployment, convert these to environment variables:

```properties
spring.application.name=upi-offline-meta
server.port=${SERVER_PORT:1010}

spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/upi_meta?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

logging.level.com.ankush=INFO
logging.level.com.ankush.upi_offline_meta=DEBUG

upi.mesh.packet-max-age-seconds=86400
```

> **Security warning:** Never commit `application.properties` with real passwords to a public git repository. Use environment variables on the server instead.

### 3.3 Database Requirements

- MySQL 8.0+
- Database name: `upi_meta`
- User with read/write privileges on `upi_meta`
- Remote access enabled (if using managed MySQL)

---

## 4. Deployment Order (Recommended)

```
Phase 1: Build locally
Phase 2: Provision cloud database
Phase 3: Deploy app to cloud VM
Phase 4: Configure environment variables
Phase 5: Open firewall ports
Phase 6: Verify public access
Phase 7: Optional: Domain + HTTPS + Auto-restart
```

---

## 5. Deployment Steps

### Step 1: Build the Production JAR

```powershell
cd "E:\UPI Project\upi-offline-meta"
.\mvnw.cmd clean package -DskipTests
```

Output: `target/upi-offline-meta-0.0.1-SNAPSHOT.jar`

### Step 2: Provision MySQL Database

**Option A: Oracle Cloud Always Free (Recommended)**
1. Sign up at https://www.oracle.com/cloud/free/
2. Create an ARM Ampere A1 Compute instance (4 vCPU, 24 GB RAM)
3. Install MySQL 8.0 on the VM, OR use Oracle Cloud Free Tier MySQL Database service
4. Create database `upi_meta` and a dedicated user (not `root`)
5. Note the public IP and port 3306

**Option B: Other Providers**
- AWS RDS MySQL (free 12 months)
- Aiven free tier MySQL
- Any managed MySQL with remote access enabled

### Step 3: Deploy Application JAR

**On your cloud VM (Linux example):**

```bash
# Install Java 17 JRE
sudo apt update
sudo apt install openjdk-17-jre-headless

# Create app directory
sudo mkdir -p /opt/upi-offline-meta
sudo cp upi-offline-meta-0.0.1-SNAPSHOT.jar /opt/upi-offline-meta/app.jar

# Create systemd service for auto-restart
sudo tee /etc/systemd/system/upi-offline-meta.service <<EOF
[Unit]
Description=UPI Offline Mesh
After=network.target

[Service]
Type=simple
User=upi
WorkingDirectory=/opt/upi-offline-meta
ExecStart=/usr/bin/java -jar /opt/upi-offline-meta/app.jar
Environment="DB_URL=jdbc:mysql://<DB_HOST>:3306/upi_meta?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
Environment="DB_USERNAME=upi_user"
Environment="DB_PASSWORD=<your-secure-password>"
Environment="SERVER_PORT=1010"
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable upi-offline-meta
sudo systemctl start upi-offline-meta
```

### Step 4: Open Firewall Ports

| Port | Purpose |
| :-- | :-- |
| 1010 (or your `server.port`) | Application HTTP |
| 22 | SSH (administration) |
| 3306 | MySQL (only if accessed externally; restrict to app VM IP) |

**Oracle Cloud:** Go to Instance → Security Lists → Add Ingress Rule for port 1010 (0.0.0.0/0).

### Step 5: Verify Deployment

Open in browser:
```
http://<YOUR_PUBLIC_IP>:1010/
```

You should see the **UPI Offline Mesh Dashboard**.

Test the full flow:
1. **Send offline** — create a payment packet
2. **Gossip once** — spread through mesh
3. **Flush to server** — bridge uploads and settles
4. Check **Accounts** and **Recent Transactions** tables update

---

## 6. Optional Polish

### 6.1 Custom Domain + HTTPS

1. Get a free domain (e.g., DuckDNS, Freenom) or use your cloud provider's DNS.
2. Point an A record to your VM public IP.
3. Install **Caddy** or **Nginx** as a reverse proxy on port 443.
4. Obtain a free SSL certificate via **Let's Encrypt**.
5. Proxy `https://your-domain.com` → `http://localhost:1010`.

Example Caddyfile:
```caddy
your-domain.com {
    reverse_proxy localhost:1010
}
```

### 6.2 Security Hardening (Production)

- **Basic Auth:** Add Spring Security with a username/password for `/api/*` endpoints.
- **Disable devtools:** `spring-boot-devtools` is scoped to runtime but optional; remove from `pom.xml` for production builds.
- **Logging:** Set `logging.level.root=WARN` in production to reduce disk usage.
- **Database:** Restrict MySQL port 3306 to only accept connections from your app VM IP.

### 6.3 CI/CD (Optional)

Add a GitHub Actions workflow to build and deploy on every push:

```yaml
name: Deploy
on:
  push:
    branches: [main]
jobs:
  build-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: temurin
      - name: Build with Maven
        run: ./mvnw clean package -DskipTests
      - name: Deploy to VM
        run: scp target/*.jar user@<VM_IP>:/opt/upi-offline-meta/app.jar && ssh user@<VM_IP> 'sudo systemctl restart upi-offline-meta'
```

---

## 7. Important Limitations (Read Before Going Public)

| Limitation | Impact | Mitigation |
| :-- | :-- | :-- |
| **ServerKeyHolder regenerates keys on every restart** | Old encrypted packets cannot be decrypted after restart | Acceptable for demo; persist key pair for production |
| **Idempotency map is in-memory** | Wiped on restart; DB duplicate guard still works | DB unique index on `packetHash` is the durable backstop |
| **No authentication** | Anyone can send/reset/flush | Add Spring Security basic auth before public exposure |
| **`ddl-auto=update`** | Auto-changes schema on startup | Change to `validate` in production after initial setup |
| **Port 1010** | May be blocked by some cloud/firewall rules | Use port 8080 or a reverse proxy |

---

## 8. Quick Reference: Environment Variables

| Variable | Default | Description |
| :-- | :-- | :-- |
| `SERVER_PORT` | `1010` | Application HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3306/upi_meta...` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `root` | Database password |

---

## 9. Troubleshooting

| Symptom | Likely Cause | Fix |
| :-- | :-- | :-- |
| App won't start | MySQL not reachable | Check DB URL, credentials, firewall |
| `405 Method Not Allowed` | Calling POST endpoint via GET | Use POST for `/api/demo/send`, `/api/mesh/gossip`, etc. |
| Dashboard blank | Thymeleaf template missing | Ensure `src/main/resources/templates/dashboard.html` exists |
| Port already in use | Another process on 1010 | Change `SERVER_PORT` env var |
| Tests fail locally | Stale MySQL rows | `TRUNCATE TABLE accounts;` then re-run |

---

## Author

**Ankush** — UPI Offline Mesh project

> *This is an educational prototype demonstrating offline payments, mesh networking, hybrid cryptography, idempotency, and atomic settlement. Not intended for production use with real money.*