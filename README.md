# PhoenixKey Database

**Version:** v1.0 | **Tech Stack:** Spring Boot 3.3 + PostgreSQL + Redis + Flyway + HashiCorp Vault
**Source of Truth:** Blockchain Cardano (qua TAAD)

---

## Mục lục

1. [PhoenixKey Database là gì?](#1-phoenixkey-database-là-gì)
2. [Vai trò trong hệ thống lớn](#2-vai-trò-trong-hệ-thống-lớn)
3. [Nguyên tắc thiết kế](#3-nguyên-tắc-thiết-kế)
4. [Lược đồ CSDL](#4-lược-đồ-csdl)
5. [Kiến trúc Cache (Redis)](#5-kiến-trúc-cache-redis)
6. [Quy tắc vận hành](#6-quy-tắc-vận-hành)
7. [Phạm vi & Ranh giới](#7-phạm-vi--ranh-giới)
8. [Cấu trúc dự án](#8-cấu-trúc-dự-án)
9. [Cài đặt & Chạy](#9-cài-đặt--chạy)

---

## 1. PhoenixKey Database là gì?

PhoenixKey Database **không phải** nơi lưu trữ dữ liệu người dùng (file, hình ảnh, hợp đồng, sản phẩm).

Nhiệm vụ duy nhất của nó là làm một **"Trạm trung chuyển & Bộ đệm Định danh"** (Identity Routing & Cache Hub):

> **Tra cứu nhanh:** _"User này là ai?"_ | _"Khóa này có hợp lệ không?"_

Mọi dữ liệu nghiệp vụ thuộc về các App khác (OriLife, Aladin Work...) — PhoenixKey Database **tuyệt đối không được phép chạm vào**.

---

## 2. Vai trò trong hệ thống lớn

```
┌─────────────────────────────────────────────────────────┐
│                    Người dùng (App Mobile)              │
│           OriLife  │  Aladin Work  │  ProofChat         │
└──────────┬───────────────────────────────────┬──────────┘
           │                                   │
           │  PhoenixKey SDK                   │
           ▼                                   ▼
┌─────────────────────────────────────────────────────────┐
│              PhoenixKey Backend (NestJS)                │
│         /auth  │  /identity  │  /key/rotate             │
└──────────┬────────────────────────────┬─────────────────┘
           │                            │
           ▼                            ▼
┌──────────────────────┐   ┌──────────────────────────────┐
│   Redis              │   │   PostgreSQL                 │
│  (OTP, Session,      │   │   PhoenixKey Database        │  ← ĐÂY
│   Rate Limit)        │   │                              │
└──────────────────────┘   └──────────────────────────────┘
                                         │
                                         ▼
                             ┌───────────────────────────┐
                             │   Blockchain Cardano      │
                             │   (DID, TAAD, Smart       │
                             │    Contracts)             │  ← SSoT
                             └───────────────────────────┘
```

**Quan hệ:**

- **PhoenixKey Database ← ĐỌC ← Cardano Blockchain** (Indexer Worker sync)
- Nếu DB mâu thuẫn với Blockchain → **Blockchain thắng**. DB chỉ là cache, không phải nguồn chân lý.
- Các App (OriLife, AladinWork...) gọi PhoenixKey API chỉ để hỏi: _"User này có tồn tại không?"_, _"Khóa này hợp lệ không?"_

---

## 3. Nguyên tắc thiết kế

4 nguyên tắc thép — **vi phạm bất kỳ điều nào = phải có lý do chính đáng + approve từ Tech Lead**.

| #   | Nguyên tắc               | Chi tiết                                                                                                                                                         |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Zero-PII**             | Không lưu SĐT/Email ở dạng plaintext. Dùng Blind Index (HMAC-SHA256 + Pepper). Pepper nằm trong HashiCorp Vault, không bao giờ lưu trong `.env` hay source code. |
| 2   | **Stateless by Default** | Nguồn chân lý tối thượng (SSoT) nằm trên Blockchain Cardano. DB chỉ đóng vai trò Indexer/Cache — không lưu logic nghiệp vụ.                                      |
| 3   | **Decoupled**            | Không lưu bất kỳ logic/cột nào của app khác. Nếu OriLife sập, PhoenixKey vẫn sống bình thường.                                                                   |
| 4   | **O(1) Scalability**     | UUIDv7 (timestamp-prefixed) thay vì UUIDv4 để tránh B-Tree fragmentation. Cấu trúc phẳng, sẵn sàng Sharding.                                                     |

### Tại sao dùng UUIDv7 thay vì UUIDv4?

```
UUIDv4: a3f8c2e1-...  ← Ngẫu nhiên hoàn toàn
        ↓
        B-Tree Index bị phân mảnh khi Insert hàng triệu dòng
        → Chậm ghi (HDD/SSD phải tìm vị trí ngẫu nhiên)

UUIDv7: 0192f4a1-...  ← Tiền tố = timestamp (tăng dần)
        ↓
        B-Tree luôn append vào cuối
        → Ghi nhanh cực đại trên ổ SSD/NVMe
```

---

## 4. Lược đồ CSDL

### 4.1. users — Lõi Định danh

```sql
CREATE TABLE users (
  id          UUID PRIMARY KEY,         -- UUIDv7, do Backend tạo
  user_did    VARCHAR(128) UNIQUE NOT NULL, -- did:prism:... hoặc did:cardano:...
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

> Bảng đơn giản nhất có thể. Mọi thứ gắn với `user_did` — không dùng email hay SĐT làm khóa.

### 4.2. auth_methods — Ánh xạ Web2 Auth → DID (Blind Index)

```sql
CREATE TYPE auth_provider AS ENUM ('google', 'apple', 'phone');

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

> Khi user đăng nhập bằng Gmail: Backend hash Gmail với Pepper từ Vault → tra `blind_index_hash` → trả về `user_did`. Không ai (kể cả DBA) đọc được Gmail/SĐT.

### 4.3. authorized_keys — Quản lý đa thiết bị / LampNet

```sql
CREATE TABLE authorized_keys (
  id                   UUID PRIMARY KEY,
  user_did             VARCHAR(128) REFERENCES users(user_did) ON DELETE CASCADE,
  public_key_hex       VARCHAR(128) NOT NULL,
  key_role             VARCHAR(50) DEFAULT 'owner',  -- 'owner', 'farm_manager', 'read_only'
  lampnet_locator_id   VARCHAR(128),                 -- Bản đồ tìm kiếm trên LampNet
  added_by_signature   VARCHAR(128) NOT NULL,        -- Zero-Trust: chữ ký từ Root Key
  status               VARCHAR(20) DEFAULT 'active',  -- 'active', 'revoked'
  created_at           TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_did, public_key_hex)
);
```

> **Zero-Trust:** Backend không thể tự ý thêm khóa. Phải có `added_by_signature` chứng minh user đồng ý.

### 4.4. guardians — Mạng lưới bảo hộ khôi phục

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

> Guardian giúp khôi phục danh tính khi mất thiết bị. Mỗi user nên có 3–5 Guardian.

### 4.5. onchain_taad_state_cache — Bộ đệm trạng thái on-chain

```sql
CREATE TYPE taad_status AS ENUM ('ACTIVE', 'RECOVERING', 'MIGRATED');

CREATE TABLE onchain_taad_state_cache (
  user_did              VARCHAR(128) PRIMARY KEY REFERENCES users(user_did),
  current_controller_pkh VARCHAR(64) NOT NULL,    -- PKH của người đang kiểm soát
  sequence              BIGINT NOT NULL,           -- Chống replay
  status                taad_status NOT NULL,
  recovery_deadline     TIMESTAMPTZ,               -- Từ Time-lock Smart Contract
  last_synced_block     BIGINT NOT NULL,           -- Chống Blind Overwrite
  block_hash            VARCHAR(64) NOT NULL,      -- Chống Reorg (Rollback)
  updated_at            TIMESTAMPTZ DEFAULT NOW()
);
```

> ⚠️ **Chỉ đọc từ Blockchain.** Không nhận lệnh trực tiếp từ App. Indexer Worker chịu trách nhiệm sync.

### 4.6. activity_logs — Nhật ký kiểm toán (Append-only)

```sql
CREATE TABLE activity_logs (
  id          UUID PRIMARY KEY,
  user_id     UUID REFERENCES users(id),
  action      VARCHAR(50) NOT NULL,   -- VD: 'login_success', 'init_recovery_onchain'
  metadata    JSONB,                  -- Chứa IP hash, OS version. TUYỆT ĐỐI KHÔNG CHỨA PII
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

> Trigger bảo vệ tính bất biến: `BEFORE UPDATE OR DELETE` → raise exception. Không ai được sửa hay xóa log.

---

## 5. Kiến trúc Cache (Redis)

**Nguyên tắc:** Dữ liệu "sống ngắn" (OTP, Session, Rate Limit) **tuyệt đối không được lưu vào SQL**.

| Key Pattern                   | Dữ liệu                                           | TTL           | Mục đích                                   |
| ----------------------------- | ------------------------------------------------- | ------------- | ------------------------------------------ |
| `otp:auth:{blind_index_hash}` | `{"code": "812345", "attempts": 0}`               | 300s (5 phút) | Xác thực OTP đăng nhập                     |
| `ratelimit:ip:{ip_hash}`      | Integer: số lần request                           | 3600s (1 giờ) | Chống spam: khóa 1h nếu vượt 5 lần gửi OTP |
| `session:token:{jwt_hash}`    | `{"user_did": "did:...", "device_pubkey": "..."}` | 86400s (24h)  | Phiên đăng nhập Web2                       |

---

## 6. Quy tắc vận hành

### Quy tắc 1: Indexer Worker (Sync on-chain)

- Update bảng `onchain_taad_state_cache` **luôn phải có điều kiện**: `WHERE user_did = $1 AND last_synced_block < $new_block`
- Nếu `$new_block` nhỏ hơn giá trị đang có → **từ chối ghi đè**
- Nếu `block_hash` lệch (Reorg/Rollback) → **xóa cache** của user đó và sync lại từ đầu

### Quy tắc 2: Vault Operations (Pepper)

- `SERVER_PEPPER` **tuyệt đối không được** lưu trong `.env` thô trên máy chủ
- Phải gọi API từ HashiCorp Vault (hoặc AWS KMS) khi khởi động App
- Định kỳ **6 tháng xoay vòng** Pepper: tăng `pepper_version`, hash cũ vẫn verify được qua logic multi-version

### Quy tắc 3: Zero-Trust cho đa thiết bị

- Thêm khóa mới vào `authorized_keys` → Backend **phải verify** `added_by_signature`
- Nếu Backend bị hack, Hacker không thể tự ý chèn khóa của chúng vào DB

### Quy tắc 4: Anti-Scope Creep (Nguyên tắc vàng)

> **Tuyệt đối cấm** thêm các cột như `farm_id`, `job_id`, `contract_status`, `crop_type`, `harvest_date` vào PhoenixKey Database.

Nếu team OriLife hoặc AladinWork yêu cầu → gửi RFC lên Tech Steering Committee của MagicLamp.

---

## 7. Phạm vi & Ranh giới

### ✅ PhoenixKey Database CHỊU TRÁCH NHIỆM

- Định danh người dùng (DID ↔ Auth credentials)
- Quản lý khóa và thiết bị
- Cache trạng thái on-chain
- Nhật ký kiểm toán
- Rate limiting, OTP, Session

### ❌ PhoenixKey Database TUYỆT ĐỐI KHÔNG CHẠM

- Dữ liệu Farm, Crop, Contract, Harvest (thuộc OriLife)
- Tin nhắn chat, channel, contact (thuộc AladinWork/ProofChat)
- Bất kỳ dữ liệu nghiệp vụ nào của app khác
- File, hình ảnh, document
- Logic Smart Contract (thuộc Aiken/Cardano)

---

## 8. Cấu trúc dự án

```
PhoenixKey-Database/
├── src/main/
│   ├── java/com/magiclamp/phoenixkey_db/
│   │   ├── PhoenixkeyDbApplication.java
│   │   ├── config/
│   │   │   ├── VaultConfig.java          # Đọc SERVER_PEPPER từ Vault
│   │   │   ├── RedisConfig.java
│   │   │   └── SecurityConfig.java
│   │   ├── domain/                       # JPA Entities (1-1 với schema)
│   │   │   ├── User.java
│   │   │   ├── AuthMethod.java
│   │   │   ├── AuthProvider.java         # Enum: google, apple, phone
│   │   │   ├── AuthorizedKey.java
│   │   │   ├── Guardian.java
│   │   │   ├── OnchainTaadStateCache.java
│   │   │   ├── ActivityLog.java
│   │   │   └── TaadStatus.java           # Enum: ACTIVE, RECOVERING, MIGRATED
│   │   ├── repository/                   # Spring Data JPA
│   │   │   └── *.java
│   │   └── crypto/
│   │       └── BlindIndexService.java    # HMAC-SHA256 + Pepper logic
│   └── resources/
│       ├── application.yml
│       └── db/migration/                  # Flyway migrations
│           ├── V1__create_identity_core.sql
│           ├── V2__create_authorized_keys.sql
│           ├── V3__create_guardians.sql
│           ├── V4__create_onchain_cache.sql
│           └── V5__create_activity_logs.sql
└── README.md
```

---

## 9. Cài đặt & Chạy

### Yêu cầu

- Java 21+
- PostgreSQL 15+
- Redis 7+
- HashiCorp Vault (hoặc config mock cho dev local)

### Cấu hình

```bash
# Tạo database
psql -U postgres -c "CREATE DATABASE phoenixkey_db;"

# Copy config
cp src/main/resources/application.yml src/main/resources/application-local.yml
# Chỉnh sửa: host, port, credentials

# Chạy migration (Flyway tự động khi start)
./mvnw spring-boot:run
```

### Env bắt buộc (Vault)

```env
# KHÔNG lưu trực tiếp trong .env — bắt buộc qua Vault
SPRING_CLOUD_VAULT_URI=https://vault.magiclamp.internal
SPRING_CLOUD_VAULT_KV_ENABLED=true
SPRING_CLOUD_VAULT_KV_DEFAULT_CONTEXT=phoenixkey
```

Pepper được đọc từ Vault path: `secret/phoenixkey/server_pepper`

### Các lệnh hữu ích

```bash
# Chạy tests
./mvnw test

# Build JAR
./mvnw clean package -DskipTests

# Kiểm tra migration status
./mvnw flyway:info
```

---

## Tài liệu liên quan

| Tài liệu                        | Mô tả                                             |
| ------------------------------- | ------------------------------------------------- |
| `[PhoenixKey]-Database.docx`    | Đặc tả CSDL chi tiết (v1.1)                       |
| `PhoenixKey-Development.docx`   | Lộ trình MVP + PoC + Milestone                    |
| `PhoenixKey_Database_Plan.docx` | Kế hoạch triển khai                               |
| `docs/TAAD-Specification.md`    | Đặc tả TAAD (Token-Anchored Authority Delegation) |

---

> **Ghi nhớ một dòng:** PhoenixKey Database là "Kính lúp" đọc nhanh từ Blockchain — không phải kho lưu trữ, không phải nguồn chân lý, không phải nơi chứa dữ liệu nghiệp vụ.
