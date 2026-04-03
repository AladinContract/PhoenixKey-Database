# PhoenixKey Database

**Version:** v1.0 | **Tech Stack:** Spring Boot 3.3 + PostgreSQL + Redis + Flyway + HashiCorp Vault
**Source of Truth:** Blockchain Cardano (qua TAAD)

---

## Mục lục

1. [PhoenixKey Database là gì?](#1-phoenixkey-database-là-gì)
2. [Vai trò trong hệ thống lớn](#2-vai-trò-trong-hệ-thống-lớn)
3. [Luồng API hoàn chỉnh](#3-luồng-api-hoàn-chỉnh)
4. [Nguyên tắc thiết kế](#4-nguyên-tắc-thiết-kế)
5. [Lược đồ CSDL](#5-lược-đồ-csdl)
6. [Kiến trúc Cache (Redis)](#6-kiến-trúc-cache-redis)
7. [Quy tắc vận hành](#7-quy-tắc-vận-hành)
8. [Phạm vi & Ranh giới](#8-phạm-vi--ranh-giới)
9. [Cấu trúc dự án](#9-cấu-trúc-dự-án)
10. [Cài đặt & Chạy](#10-cài-đặt--chạy)

---

## 1. PhoenixKey Database là gì?

PhoenixKey Database **không phải** nơi lưu trữ dữ liệu người dùng (file, hình ảnh, hợp đồng, sản phẩm).

Nhiệm vụ duy nhất của nó là làm một **"Trạm trung chuyển & Bộ đệm Định danh"** (Identity Routing & Cache Hub):

> **Tra cứu nhanh:** _"User này là ai?"_ | _"Khóa này có hợp lệ không?"_

Mọi dữ liệu nghiệp vụ thuộc về các App khác (OriLife, Aladin Work...) — PhoenixKey Database **tuyệt đối không được phép chạm vào**.

---

## 2. Vai trò trong hệ thống lớn

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           Người dùng (App Mobile)                                │
│                    OriLife  │  Aladin Work  │  ProofChat                         │
└────────┬─────────────────────────────┬──────────────────────────┬────────────────┘
         │ OTP + Auth                  │                          │ Tra cứu pubkey/status
         ▼                             ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          NestJS Backend (OriLife / AladinWork)                   │
│  • Generate OTP (Twilio/SendGrid)                                                │
│  • Gửi SMS/Email cho user                                                        │
│  • Tạo DID trên Cardano qua Identus SDK                                          │
│  • Xây dựng transaction qua Lucid-Evolution                                      │
└────────┬─────────────────────────────┬──────────────────────────┬────────────────┘
         │ OTP đã generate             │                          │ /identity/{did}/pubkey
         │ /auth/otp/save              │                          │ /identity/{did}/status
         │ /auth/otp/verify            │                          │ /keys/authorize
         ▼                             ▼                          │ /keys/revoke
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         PhoenixKey Database (Spring Boot)                         │
│  • Lưu OTP vào Redis (key: otp:auth:{blind_hash})                                 │
│  • Verify OTP → set is_verified                                                   │
│  • Insert users + auth_methods + authorized_keys                                  │
│  • Query pubkey, status, guardian, TAAD cache                                     │
└────────┬──────────────────────────────────────────────────────────────────────────┘
         │                                                                 Indexer Worker
         │◀────────────────────────── Sync state ──────────────────────────▶│
         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            Blockchain Cardano  ← SSoT                           │
│                            (DID, TAAD, Smart Contracts)                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Quan hệ:**

- **PK_DB ← ĐỌC ← Cardano Blockchain** (Indexer Worker sync)
- **NestJS gửi OTP** qua SMS/Email (Twilio/SendGrid) — PK_DB không làm việc này
- **NestJS tạo DID** trên Cardano — PK_DB không làm việc này
- Nếu DB mâu thuẫn với Blockchain → **Blockchain thắng**. DB chỉ là cache.

---

## 3. Luồng API hoàn chỉnh

### 3.1. Đăng ký + OTP

```
┌──────┐    Nhập email/phone     ┌──────────────────┐    hash(cred)     ┌──────────┐
│  App │ ───────────────────────▶│  NestJS Backend  │ ─────────────────▶│   PK_DB  │
└──┬───┘                         └────────┬─────────┘                   └────┬─────┘
   │                                      │ save OTP                         │
   │   OTP qua SMS/Email                  │ ───────────────────────▶ Redis   │
   │◀─────────────────────────────────────┤           otp:auth:{blind_hash}  │
   │                                      │                                  │
   │   Nhập mã OTP                        │ verify(blind_hash, otp)          │
   │ ──────────────────────────────────▶ │ ────────────────────────────────▶         │
   │                                      │ ◀── lookup OTP ─────────────────│
   │                                      │ ◀─── { user_did } ──────────────│
   │   Đăng nhập OK                       │                                  │
   │◀────────────────────────────────────┤                                  │
                       │                                  │
```
<img width="1758" height="1314" alt="mermaid-diagram" src="https://github.com/user-attachments/assets/480febf8-db27-4460-bbd9-238937af3c2e" />


### 3.2. Identity Register

```
┌──────┐  Credential + pubkey     ┌──────────────────┐                     ┌──────────┐
│  App │ ──────────────────────▶ │  NestJS Backend  │                     │   PK_DB  │
└──┬───┘                          └────────┬─────────┘                     └────┬─────┘
   │                                       │                                    │
   │                                       │ register({ cred, pubkey, sig })    │
   │                                       │ ────────────────────────────────▶ │
   │                                       │                                    │ INSERT users
   │                                       │                                    │ + auth_methods
   │                                       │                                    │ + authorized_keys
   │                                       │ ◀── { user_id, "pending" } ───────│
   │                                       │                                    │
   │                                       │ Mint DID trên Cardano              │
   │                                       │ update users.user_did = DID        │
   │   { user_id, DID }                    │                                    │
   │◀─────────────────────────────────────┤                                    │
   │                                       │                                    │
```

### 3.3. Tra cứu pubkey

```
┌──────────────────┐                      ┌──────────┐
│OriLife / Aladin  │                      │   PK_DB  │
│     Work         │                      └────┬─────┘
└───────┬──────────┘                           │
        │                                      │
        │ GET /identity/{did}/pubkey           │
        │ ──────────────────────────────────▶ │
        │                                      │
        │ ◀── { pubkey, role }                │
        │◀────────────────────────────────────│
        │                                      │
```

### 3.4. Tra cứu status (TAAD)

```
┌──────────────────┐                      ┌──────────┐
│OriLife / Aladin  │                      │   PK_DB  │
│     Work         │                      └────┬─────┘
└───────┬──────────┘                           │
        │                                      │
        │ GET /identity/{did}/status           │
        │ ──────────────────────────────────▶ │
        │                                      │
        │ ◀── { status, pkh, seq }            │
        │◀────────────────────────────────────│
        │                                      │
```

### 3.5. Authorize / Revoke Key

```
┌──────┐                    ┌──────────┐
│  App │                    │   PK_DB  │
└──┬───┘                    └────┬─────┘
   │                             │
   │ POST /keys/authorize        │
   │ ─────────────────────────▶ │
   │                             │
   │ POST /keys/revoke           │
   │ ─────────────────────────▶ │
   │                             │
```

### 3.6. Guardian

```
┌──────┐                    ┌──────────┐
│  App │                    │   PK_DB  │
└──┬───┘                    └────┬─────┘
   │                             │
   │ POST /guardians/add         │
   │ ─────────────────────────▶ │
   │                             │
   │ POST /guardians/            │
   │ approve-recovery            │
   │ ─────────────────────────▶ │
   │                             │
```

### 3.7. Indexer sync

```
┌───────────────┐    ┌──────────┐          ┌──────────┐
│ Indexer Worker│    │   PK_DB  │          │ Cardano  │
└───────┬───────┘    └────┬─────┘          └────┬─────┘
        │                 │                     │
        │                 │                     │
        │ sync-taad       │                     │
        │ ─────────────▶ │                     │
        │                 │                     │
        │◀── Tx confirmed ─────────────────────│
        │                 │                     │
```

---

## 4. Nguyên tắc thiết kế

4 nguyên tắc thép — **vi phạm bất kỳ điều nào = phải có lý do chính đáng + approve từ Tech Lead**.

| #   | Nguyên tắc               | Chi tiết                                                                                                                                                         |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Zero-PII**             | Không lưu SĐT/Email ở dạng plaintext. Dùng Blind Index (HMAC-SHA256 + Pepper). Pepper nằm trong HashiCorp Vault, không bao giờ lưu trong `.env` hay source code. |
| 2   | **Stateless by Default** | Nguồn chân lý tối thượng (SSoT) nằm trên Blockchain Cardano. DB chỉ đóng vai trò Indexer/Cache — không lưu logic nghiệp vụ.                                      |
| 3   | **Decoupled**            | Không lưu bất kỳ logic/cột nào của app khác. Nếu OriLife sập, PhoenixKey vẫn sống bình thường.                                                                   |
| 4   | **O(1) Scalability**     | UUIDv7 (timestamp-prefixed) thay vì UUIDv4 để tránh B-Tree fragmentation. Cấu trúc phẳng, sẵn sàng Sharding.                                                     |

### Tại sao dùng UUIDv7 thay vì UUIDv4?

```
UUIDv4:  a3f8c2e1-...    ← Ngẫu nhiên hoàn toàn
         │
         ▼
         B-Tree Index bị phân mảnh khi Insert hàng triệu dòng
         → Chậm ghi

UUIDv7:  0192f4a1-...    ← Tiền tố = timestamp (tăng dần)
         │
         ▼
         B-Tree luôn append vào cuối
         → Ghi nhanh cực đại trên ổ SSD/NVMe
```

---

## 5. Lược đồ CSDL

### 5.1. users — Lõi Định danh

```sql
CREATE TABLE users (
  id          UUID PRIMARY KEY,             -- UUIDv7, do Backend tạo
  user_did    VARCHAR(128) UNIQUE NOT NULL, -- did:prism:... hoặc did:cardano:...
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

> Bảng đơn giản nhất có thể. Mọi thứ gắn với `user_did` — không dùng email hay SĐT làm khóa.

### 5.2. auth_methods — Ánh xạ Web2 Auth → DID (Blind Index)

```sql
CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'APPLE', 'PHONE');

CREATE TABLE auth_methods (
  id               UUID PRIMARY KEY,
  user_id          UUID REFERENCES users(id) ON DELETE CASCADE,
  provider         auth_provider NOT NULL,
  blind_index_hash VARCHAR(64) UNIQUE NOT NULL, -- HMAC_SHA256(phone/email, SERVER_PEPPER)
  pepper_version   SMALLINT DEFAULT 1 NOT NULL,  -- Phục vụ xoay vòng Pepper
  is_verified      BOOLEAN DEFAULT FALSE,
  added_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_blind_index ON auth_methods(blind_index_hash);
```

> NestJS hash email/SĐT với Pepper → gửi `blind_index_hash` cho PK_DB. PK_DB chỉ lưu hash, không biết credential thật.

### 5.3. authorized_keys — Quản lý đa thiết bị / LampNet

```sql
CREATE TABLE authorized_keys (
  id                   UUID PRIMARY KEY,
  user_did             VARCHAR(128) REFERENCES users(user_did) ON DELETE CASCADE,
  public_key_hex       VARCHAR(128) NOT NULL,
  key_role             VARCHAR(50) DEFAULT 'owner',
  lampnet_locator_id   VARCHAR(128),
  added_by_signature   VARCHAR(128) NOT NULL,        -- Zero-Trust: chữ ký từ Root Key
  status               VARCHAR(20) DEFAULT 'active',  -- 'active', 'revoked'
  created_at           TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_did, public_key_hex)
);
```

> **Zero-Trust:** Backend phải verify `added_by_signature` trước khi INSERT. Nếu Backend bị hack, không thể tự thêm khóa.

### 5.4. guardians — Mạng lưới bảo hộ khôi phục

```sql
CREATE TABLE guardians (
  id              UUID PRIMARY KEY,
  user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
  guardian_did    VARCHAR(128) NOT NULL,
  proof_signature VARCHAR(128) NOT NULL,  -- Zero-Trust: user chứng minh mời người này
  status          VARCHAR(20) DEFAULT 'active',
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_id, guardian_did)
);
```

### 5.5. recovery_approvals — Phê duyệt khôi phục

```sql
CREATE TABLE recovery_approvals (
  id                   UUID PRIMARY KEY,
  user_did             VARCHAR(128) NOT NULL,
  guardian_did         VARCHAR(128) NOT NULL,
  new_controller_pkh   VARCHAR(64) NOT NULL,
  guardian_signature   VARCHAR(256) NOT NULL,
  approved_at         TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_did, guardian_did)
);
```

> Khi đủ threshold guardian phê duyệt → Indexer sync TAAD state.

### 5.6. onchain_taad_state_cache — Bộ đệm trạng thái on-chain

```sql
CREATE TYPE taad_status AS ENUM ('ACTIVE', 'RECOVERING', 'MIGRATED');

CREATE TABLE onchain_taad_state_cache (
  user_did                VARCHAR(128) PRIMARY KEY REFERENCES users(user_did),
  current_controller_pkh   VARCHAR(64) NOT NULL,
  sequence                BIGINT NOT NULL,
  status                  taad_status NOT NULL,
  recovery_deadline       TIMESTAMPTZ,
  last_synced_block       BIGINT NOT NULL,           -- Chống Blind Overwrite
  block_hash              VARCHAR(64) NOT NULL,      -- Chống Reorg
  updated_at              TIMESTAMPTZ DEFAULT NOW()
);
```

> ⚠️ **Chỉ Indexer Worker được ghi.** Không nhận lệnh trực tiếp từ App.

### 5.7. activity_logs — Nhật ký kiểm toán (Append-only)

```sql
CREATE TABLE activity_logs (
  id          UUID PRIMARY KEY,
  user_id     UUID REFERENCES users(id),
  action      VARCHAR(50) NOT NULL,   -- VD: 'login_success', 'key_authorized'
  metadata    JSONB,                  -- TUYỆT ĐỐI KHÔNG CHỨA PII
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

> Trigger: `BEFORE UPDATE OR DELETE` → raise exception. Không ai được sửa hay xóa log.

---

## 6. Kiến trúc Cache (Redis)

**Nguyên tắc:** Dữ liệu "sống ngắn" (OTP, Session, Rate Limit) **tuyệt đối không được lưu vào SQL**.

| Key Pattern                      | Ai ghi                | Dữ liệu                    | TTL           | Mục đích                            |
| -------------------------------- | --------------------- | -------------------------- | ------------- | ----------------------------------- |
| `otp:auth:{blind_hash}`          | NestJS → PK_DB (save) | OTP đã generate + attempts | 300s (5 phút) | Verify OTP đăng nhập                |
| `otp:auth:{blind_hash}:attempts` | PK_DB (increment)     | Số lần nhập sai            | 300s          | Chống brute-force                   |
| `ratelimit:ip:{ip_hash}`         | PK_DB                 | Số request/IP              | 3600s (1 giờ) | Chống spam: khóa 1h nếu vượt ngưỡng |
| `session:token:{jwt_hash}`       | PK_DB                 | `{user_did\|pubkey}`       | 86400s (24h)  | Phiên đăng nhập Web2                |

**Lưu ý:** OTP được **NestJS generate** và gửi qua SMS/Email. PK_DB nhận `blind_hash + otp + credential` từ NestJS rồi lưu vào Redis. `credential` (email/phone thuần) được dùng để re-hash blind_index_hash khi pepper được rotate — không lưu vào DB.

> **Zero-PII:** PK_DB chỉ lưu `blind_hash`, không lưu credential thật. Re-hash xảy ra in-memory trong quá trình verify OTP.

---

## 7. Quy tắc vận hành

### Quy tắc 1: Indexer Worker (Sync on-chain)

- Update bảng `onchain_taad_state_cache` **luôn phải có điều kiện**: `WHERE user_did = $1 AND last_synced_block < $new_block`
- Nếu `$new_block` nhỏ hơn giá trị đang có → **từ chối ghi đè**
- Nếu `block_hash` lệch (Reorg/Rollback) → **xóa cache** của user đó và sync lại từ đầu

### Quy tắc 2: Vault Operations (Pepper — Multi-Version)

Vault là **Single Source of Truth** cho tất cả pepper (hiện tại và lịch sử).

**Vault path:** `secret/phoenixkey/pepper`

```json
{
  "current_version": 2,
  "pepper_1": "pepper_v1_6tháng_cũ",
  "pepper_2": "pepper_v2_hiện_tại"
}
```

**Pepper Rotation Flow (6 tháng/lần):**

1. Tạo `pepper_v{N+1}` mới trên Vault (giữ nguyên `pepper_N` cũ)
2. Update `current_version = N+1` trong Vault
3. Restart PK_DB → đọc `current_version` và tất cả `pepper_N` từ Vault

**Multi-Version Lookup (credential migration on login):**

```
User login (credential + blind_hash)
  │
  ├─ lookup auth_method → pepper_version = 1 (cũ)
  ├─ verify: hash(credential, pepper_1) == stored_hash  ✓
  ├─ re-hash: blind_hash_v2 = hash(credential, pepper_2)
  └─ UPDATE: auth_method
      blind_index_hash = blind_hash_v2
      pepper_version = 2
```

**Lưu ý:** `credential` (email/phone thuần) được NestJS gửi kèm trong OTP verify request. PK_DB không lưu credential — chỉ dùng in-memory để re-hash rồi discard.

### Quy tắc 3: Zero-Trust cho đa thiết bị

- Thêm khóa mới vào `authorized_keys` → Backend **phải verify** `added_by_signature`
- Nếu Backend bị hack, Hacker không thể tự ý chèn khóa của chúng vào DB

### Quy tắc 4: Anti-Scope Creep (Nguyên tắc vàng)

> **Tuyệt đối cấm** thêm các cột như `farm_id`, `job_id`, `contract_status`, `crop_type`, `harvest_date` vào PhoenixKey Database.

Nếu team OriLife hoặc AladinWork yêu cầu → gửi RFC lên Tech Steering Committee của MagicLamp.

---

## 8. Phạm vi & Ranh giới

### ✅ PhoenixKey Database CHỊU TRÁCH NHIỆM

- Hash credential → blind_hash (HMAC-SHA256 + Pepper)
- Lưu OTP vào Redis (NestJS generate, PK_DB chỉ lưu)
- Verify OTP trong Redis
- Insert/Update/Delete dữ liệu định danh
- Quản lý khóa và thiết bị
- Cache trạng thái on-chain (từ Indexer Worker)
- Nhật ký kiểm toán
- Rate limiting

### ❌ PhoenixKey Database TUYỆT ĐỐI KHÔNG CHẠM

- **Generate OTP** (NestJS làm qua Twilio/SendGrid)
- **Gửi SMS/Email** (NestJS làm)
- **Tạo DID trên Cardano** (NestJS làm qua Identus SDK)
- **Gửi transaction Cardano** (NestJS làm qua Lucid-Evolution)
- Dữ liệu Farm, Crop, Contract, Harvest (thuộc OriLife)
- Tin nhắn chat, channel, contact (thuộc AladinWork/ProofChat)
- Logic Smart Contract (thuộc Aiken/Cardano)

---

## 9. Cấu trúc dự án

```
PhoenixKey-Database/
├── src/main/
│   ├── java/com/magiclamp/phoenixkey_db/
│   │   ├── PhoenixkeyDbApplication.java
│   │   ├── common/
│   │   │   ├── ApiResponse.java              # Wrapper response
│   │   │   └── UuidGenerator.java           # UUIDv7 generator
│   │   ├── config/
│   │   │   ├── VaultConfig.java             # Đọc SERVER_PEPPER từ Vault
│   │   │   ├── RedisConfig.java
│   │   │   └── SecurityConfig.java
│   │   ├── domain/                          # JPA Entities
│   │   ├── dto/
│   │   │   ├── request/                     # *Request.java (record)
│   │   │   └── response/                     # *Response.java (record)
│   │   ├── repository/                      # Spring Data JPA
│   │   ├── service/
│   │   │   ├── RedisService.java            # OTP/Session/RateLimit Redis
│   │   │   ├── ActivityLogService.java      # Audit trail logging
│   │   │   ├── AuthService.java             # Interface
│   │   │   ├── impl/AuthServiceImpl.java
│   │   │   ├── IdentityService.java         # Interface
│   │   │   ├── impl/IdentityServiceImpl.java
│   │   │   ├── KeyService.java             # Interface
│   │   │   ├── impl/KeyServiceImpl.java
│   │   │   ├── GuardianService.java         # Interface
│   │   │   ├── impl/GuardianServiceImpl.java
│   │   │   ├── IndexerService.java         # Interface
│   │   │   └── impl/IndexerServiceImpl.java
│   │   ├── crypto/
│   │   │   └── BlindIndexService.java      # HMAC-SHA256 + Pepper
│   │   └── exception/
│   │       ├── AppException.java
│   │       ├── ErrorCode.java
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__create_identity_core.sql
│           ├── V2__create_authorized_keys.sql
│           ├── V3__create_guardians.sql
│           ├── V4__create_onchain_cache.sql
│           ├── V5__create_activity_logs.sql
│           └── V6__create_recovery_approvals.sql
├── docker-compose.yml
└── README.md
```

---

## 10. Cài đặt & Chạy

### Yêu cầu

- Java 21+
- Docker & Docker Compose
- PostgreSQL 15+ (qua docker-compose)
- Redis 7+ (qua docker-compose)
- HashiCorp Vault (qua docker-compose, dev mode)

### Khởi động

```bash
# 1. Chạy infrastructure
docker compose up -d

# 2. (Một lần) Seed pepper vào Vault
docker exec -it phoenixkey-vault-1 vault login phoenixkey-dev-token
docker exec -it phoenixkey-vault-1 vault kv put secret/phoenixkey/pepper \
  current_version=1 \
  pepper_1="dev-pepper-for-local-testing-only-32bytes"

# 3. Chạy app
mvn spring-boot:run
```

### Env bắt buộc

```env
# Development & Production: luôn dùng HashiCorp Vault
# Vault chứa TẤT CẢ pepper (hiện tại + lịch sử)
# Dev: Vault chạy local qua docker-compose
VAULT_ENABLED=true
VAULT_ADDR=${VAULT_ADDR:http://localhost:8200}
VAULT_TOKEN=${VAULT_TOKEN:phoenixkey-dev-token}

# Database
DB_HOST=${DB_HOST:localhost}
DB_PASSWORD=${DB_PASSWORD:phoenixkey_dev_password}
```

> **Lý do dev luôn dùng Vault:** Multi-version pepper yêu cầu Vault lưu cả lịch sử pepper. Không có fallback config đơn lẻ cho dev vì cần nhiều giá trị pepper cùng lúc.

### Các lệnh hữu ích

```bash
# Chạy tests
mvn test

# Build JAR
mvn clean package -DskipTests

# Kiểm tra migration status
mvn flyway:info
```

---

## Tài liệu liên quan

| Tài liệu                        | Mô tả                          |
| ------------------------------- | ------------------------------ |
| `[PhoenixKey]-Database.docx`    | Đặc tả CSDL chi tiết (v1.1)    |
| `PhoenixKey-Development.docx`   | Lộ trình MVP + PoC + Milestone |
| `PhoenixKey_Database_Plan.docx` | Kế hoạch triển khai            |

---

> **Ghi nhớ một dòng:** PhoenixKey Database là "Kính lúp" đọc nhanh từ Blockchain — không generate OTP, không gửi SMS, không tạo DID, chỉ lưu/truy vấu định danh.
