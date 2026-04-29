# PhoenixKey-Server

Backend duy nhất phục vụ **mobile app** (Aladin/PhoenixKey) và **web** (phoenixkey.me).

> Triết lý: **Web là cửa sổ, mobile là két sắt**.
> Hardware Key (private key) chỉ tồn tại trong Secure Enclave/TEE của mobile,
> **không bao giờ rời thiết bị**. Server chỉ relay messages, verify signatures,
> và publish DID lên Cardano. Cardano là Source of Truth cho DID Document.

---

## 1. Server làm gì

```
┌──────────────────┐            ┌────────────────────────┐            ┌────────────────────┐
│ Mobile           │            │ phoenixkey-server      │            │ Cardano blockchain │
│ (Hardware Key    │◀───────▶ │ (THIS REPO)            │◀───────▶ │ (Preprod/Mainnet)  │
│  trong Enclave)  │  HTTPS     │  • Verify signatures   │  Blockfrost│  DID Document =    │
└──────────────────┘            │  • Mint JWT tokens     │            │  inline datum trên │
                                │  • Submit Cardano tx   │            │  UTxO              │
┌──────────────────┐            │  • Push notification   │            └────────────────────┘
│ Web              │◀───────▶ │  • Relay sign-request  │
│ (phoenixkey.me)  │  HTTPS+SSE │  • Audit log           │
└──────────────────┘            └────────────────────────┘
```

3 việc cốt lõi:

1. **Verify Hardware Key signatures** (ECDSA secp256k1 DER) — chứng minh request từ user thật
2. **Manage DID lifecycle** — tạo/đọc/rotate DID Document trên Cardano qua BloxBean
3. **Web ↔ mobile relay** — QR pairing, sign-request push, SSE notification

Server **không** lưu private key, email, SĐT, password. Mọi danh tính bind với DID + Hardware Key.

---

## 2. Mobile / Web phân nhiệm

### Mobile (Aladin app — Tùng làm)

- Sinh + giữ Hardware Key trong Secure Enclave (iOS) hoặc TEE (Android)
- Quét QR từ web → ký challenge bằng Hardware Key
- Hiển thị sign-request intent → user xem + biometric → ký
- Đăng ký FCM/APNs token để nhận push notification

### Web (phoenixkey.me — Long làm)

- Hiển thị QR cho login lần đầu
- Hiển thị dashboard sau khi mobile approve
- Tạo sign-request khi user thực hiện hành động (rotate key, seed export, ...)
- Lắng nghe SSE để nhận kết quả từ mobile

### Server (this repo)

- Verify Hardware Key signatures (BouncyCastle secp256k1)
- Cardano integration (BloxBean): createDID, updateDID, resolve W3C DID Document
- JWT session tokens (jjwt HS256)
- SSE channel cho web realtime
- Push notification stub (FCM/APNs sẽ wire khi Long có Firebase project)

---

## 3. Luồng hoạt động chính

### Đăng ký (Genesis)

```
Mobile                           Server                      Cardano
   │                                │                            │
   │ Sinh Hardware Key              │                            │
   │ ký "PHOENIXKEY_GENESIS:" + pub │                            │
   ├──── POST /identity/register ──▶│                            │
   │                                │ Verify ECDSA signature     │
   │                                ├── BloxBean createDID ─────▶│
   │                                │                       (5 ADA UTxO + inline datum)
   │                                │◀── tx hash ────────────────│
   │                                │ Insert users + authorized_keys
   │◀── { userDid, txHash } ────────┤
```

`userDid = did:cardano:<network>:<txHash>`. DID Document là JSON W3C lưu trong inline datum của UTxO trên Cardano.

### Đăng nhập web (Cross-device QR pairing)

```
Web                       Server                        Mobile
 │ POST /auth/session/init  │                               │
 ├────────────────────────▶│ Generate sessionId + 32B challenge
 │◀── { sid, challenge,    │                               │
 │     tempToken }          │                               │
 │                          │                               │
 │ GET /auth/session/       │                               │
 │   {sid}/stream (SSE)     │                               │
 │◀════════════════════════│ (open stream, ping mỗi 30s)   │
 │                          │                               │
 │ Hiển thị QR              │                               │
 │  {sid, challenge,        │                               │
 │   domain, exp}           │                               │
 │                          │                               │
 │  ─────── User quét QR ──────────────────────────────▶   │
 │                         │  Mobile FaceID/vân tay         │
 │                         │  ký (challenge:domain:ts)      │
 │                         │◀── POST /auth/session/        │
 │                         │      {sid}/approve             │
 │                         │ Verify signature + pubkey      │
 │                         │ Mint sessionToken (24h) +      │
 │                         │      linkedDeviceToken (30d)   │
 │◀═══ SSE event "approved"╪── Save state                  │
 │     { sessionToken,     │                                │
 │       linkedDeviceToken,│                                │
 │       userDid }         │                                │
 │                         │                                │
 │ Vào dashboard           │                                │
```

Lần sau, web dùng `linkedDeviceToken` (localStorage 30d) → `POST /auth/session/push` để gửi push thay vì quét QR.

### Ký giao dịch (Sign request relay)

```
Web                       Server                        Mobile
 │ POST /sign/request      │                               │
 │  { sid, intent }        │                               │
 ├───────────────────────▶│ Save Redis (TTL 120s)         │
 │◀── { requestId }       │ pushService.notifySignRequest │
 │                         ├── push (FCM/APNs payload:     │
 │                         │      { requestId } only) ───▶│
 │                         │                               │
 │                         │  GET /sign/request/{id}       │
 │                         │◀─────────────────────────────│
 │                         │ Trả intent payload            │
 │                         ├─────────────────────────────▶│
 │                         │                               │
 │                         │  Mobile hiển thị intent:      │
 │                         │   "Ký 100 LAMP đến..."        │
 │                         │  User xác nhận biometric      │
 │                         │  Ký canonical intent JSON     │
 │                         │                               │
 │                         │◀── POST /sign/{id}/approve ──│
 │                         │ Verify signature              │
 │                         │ Consume nonce (anti-replay)   │
 │◀══ SSE event "signed" ─┤                               │
 │   { signature }         │                               │
 │                         │                               │
 │ Submit signature to     │                               │
 │  Cardano (web tự làm)   │                               │
```

Spec §15.5: push payload **không chứa intent** — chỉ requestId. Mobile fetch chi tiết qua HTTPS authenticated → tránh leak qua notification service.

### Rotate Hardware Key

```
Mobile (key cũ)             Server                        Cardano
 │ Sinh key mới             │                                │
 │ Ký "PHOENIXKEY_ROTATE:"  │                                │
 │  + newPub + nonce        │                                │
 │  bằng KEY CŨ             │                                │
 ├── POST /keys/rotate ───▶│                                │
 │                          │ Verify old key signature       │
 │                          │ Consume nonce                  │
 │                          ├── BloxBean updateDID ────────▶│
 │                          │                  (consume UTxO cũ + tạo UTxO mới)
 │                          │◀── new tx hash ───────────────│
 │                          │ DB: revoke key cũ + insert key mới
 │◀─ { txHash, newKeyId } ─┤                                │
```

User DID **không đổi** (vẫn là genesis tx hash) — chỉ Hardware Key đổi.

---

## 4. Endpoints (23)

| Group            | Method | Path                         | Purpose                                   |
| ---------------- | ------ | ---------------------------- | ----------------------------------------- |
| **Identity**     | POST   | `/identity/register`         | Mobile genesis: tạo DID + insert user     |
|                  | GET    | `/identity/{did}/pubkey`     | Internal verify                           |
|                  | GET    | `/identity/{did}/status`     | Dashboard banner state                    |
|                  | GET    | `/identity/{did}/document`   | W3C DID Document từ Cardano               |
|                  | GET    | `/identity/health`           | Health snapshot (seed/key/guardian count) |
|                  | GET    | `/identity/nodes`            | LampNet node map (MVP stub)               |
| **Session**      | POST   | `/auth/session/init`         | Web tạo QR challenge                      |
|                  | GET    | `/auth/session/{id}/stream`  | SSE channel                               |
|                  | GET    | `/auth/session/{id}/status`  | Fallback poll                             |
|                  | POST   | `/auth/session/{id}/approve` | Mobile approve sau khi ký                 |
|                  | POST   | `/auth/session/push`         | Web → mobile push (linked device)         |
| **Sign Request** | POST   | `/sign/request`              | Web tạo sign request                      |
|                  | GET    | `/sign/request/{id}`         | Mobile fetch payload                      |
|                  | POST   | `/sign/{id}/approve`         | Mobile ký + relay về web qua SSE          |
|                  | POST   | `/sign/{id}/cancel`          | Web huỷ                                   |
| **Keys**         | POST   | `/keys/authorize`            | Thêm thiết bị/key mới                     |
|                  | POST   | `/keys/revoke`               | Thu hồi key                               |
|                  | POST   | `/keys/rotate`               | Rotate Hardware Key (Cardano updateDID)   |
| **Guardian**     | POST   | `/guardians/add`             | Thêm guardian                             |
|                  | POST   | `/guardians/remove`          | Xoá guardian                              |
| **Misc**         | POST   | `/seed/export-request`       | Trigger Seed Phrase export flow           |
|                  | GET    | `/activity-logs`             | Audit trail (cursor pagination)           |
|                  | GET    | `/tx/estimate`               | Cardano fee estimate                      |
|                  | POST   | `/devices/register`          | Mobile FCM/APNs token                     |
|                  | POST   | `/support/session/init`      | Get LAMP support (MVP stub)               |
| **Internal**     | POST   | `/internal/sync-taad`        | Indexer Worker only                       |
| **Health**       | GET    | `/actuator/health`           | Spring Boot health check                  |

Full reference: [API.md](./API.md). Postman collection: [docs/PhoenixKey.postman_collection.json](./docs/PhoenixKey.postman_collection.json).

---

## 5. Setup

```bash
# 1. Copy env
cp .env.example .env
# Edit .env: set DB_PASSWORD, REDIS_PASSWORD, BLOCKFROST_API_KEY (preprod...)

# 2. Sinh fee wallet riêng (1 lần)
./tools/keygen wallet
# Paste mnemonic in ra → vào .env (FEE_WALLET_MNEMONIC=...)
# Fund address từ https://docs.cardano.org/cardano-testnet/tools/faucet

# 3. Up infra (postgres, redis, vault dev mode + auto-seed)
docker compose up -d

# 4. Run app native
./mvnw spring-boot:run
```

App listen `localhost:8080` (context-path `/api/v1`). Smoke test:

```bash
curl http://localhost:8080/api/v1/actuator/health
curl http://localhost:8080/api/v1/auth/session/init -X POST | jq
```

---

## 6. Tech stack

| Layer   | Lib                                                    |
| ------- | ------------------------------------------------------ |
| Runtime | Java 21, Spring Boot 3.3                               |
| DB      | PostgreSQL 16 + Flyway 10                              |
| Cache   | Redis 7 (Lettuce)                                      |
| Secrets | HashiCorp Vault (KMS)                                  |
| Cardano | BloxBean cardano-client-lib 0.6.4 + Blockfrost backend |
| Crypto  | BouncyCastle (secp256k1 ECDSA verify)                  |
| JWT     | jjwt 0.12 (HS256)                                      |
| Push    | Stub (FCM/APNs sẽ wire ở Phase H)                      |
| Tools   | exec-maven-plugin, dotenv-java                         |

---

## 7. Bảo mật

- **Zero-PII**: không lưu email, phone, password. Mỗi user = 1 DID + 1 Hardware Key
- **Hardware Key non-exportable**: tồn tại duy nhất trong Secure Enclave/TEE mobile
- **Mọi mutation**: cần signature từ Hardware Key, server verify bằng pubkey trong `authorized_keys`
- **Nonce anti-replay**: keys/guardians/sign-request đều require nonce duy nhất, lưu `used_nonces`
- **Cardano = SSoT**: DID Document trên blockchain, server chỉ cache/relay
- **Vault KMS**: fee wallet mnemonic + JWT secret + FCM/APNs creds — không bao giờ trong git/env plaintext
- **JWT type claim**: tách `temp` / `session` / `linked-device` — chống dùng nhầm token cross-flow

---

## 8. Documentation

| File                                                                                 | Purpose                                        |
| ------------------------------------------------------------------------------------ | ---------------------------------------------- |
| [API.md](./API.md)                                                                   | Endpoint reference đầy đủ                      |
| [PLAN-Server.md](./PLAN-Server.md)                                                   | Migration plan từ PhoenixKey-Database → Server |
| [docs/PhoenixKey_Interface.md](./docs/PhoenixKey_Interface.md)                       | UI specification v1.4.3                        |
| [docs/HashiCorpVault.md](./docs/HashiCorpVault.md)                                   | Vault setup guide                              |
| [docs/PhoenixKey.postman_collection.json](./docs/PhoenixKey.postman_collection.json) | Postman collection                             |

---

## 9. Out of scope (Phase H+)

Xem [PLAN-Server.md "Out of scope"](./PLAN-Server.md):

- **FCM/APNs push thật**: hiện stub, cần Long setup Firebase project + APNs cert
- **Mobile-signed UpdateDID tx**: hiện fee wallet ký full tx, vi phạm Zero-Trust §11. Cần Tùng integrate BloxBean Kotlin
- **Indexer Worker**: process riêng watch Cardano + sync `onchain_taad_state_cache`. Endpoint `/internal/sync-taad` đã sẵn sàng
- **ProofChat embed**: Get LAMP support flow (spec §15.8)
- **Multi-instance SSE**: hiện in-memory, prod cần Redis Pub/Sub
- **Real LampNet node data**: hiện 12 mock, cần LampNet API public

---

> **Một dòng**: PhoenixKey-Server là cầu nối an toàn giữa mobile (két sắt private key) và web (cửa sổ user-facing) — verify chữ ký, relay messages, publish DID lên Cardano. Không bao giờ thấy private key user.
