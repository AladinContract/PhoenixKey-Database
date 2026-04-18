# PhoenixKey Database

**Version:** v1.5 | **Tech Stack:** Spring Boot 3.3 + PostgreSQL + Redis + Flyway + HashiCorp Vault
**Source of Truth:** Blockchain Cardano (qua TAAD)

---

## Mục lục

1. [PhoenixKey Database là gì?](#1-phoenixkey-database-là-gì)
2. [Vai trò trong hệ thống lớn](#2-vai-trò-trong-hệ-thống-lớn)
3. [Luồng API hoàn chỉnh](#3-luồng-api-hoàn-chỉnh)
4. [Nguyên tắc thiết kế](#4-nguyên-tắc-thiết-kế)
5. [Lược đồ CSDL](#5-lược-đồ-csdl)
   - [Diagram](#5-diagram)
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
         │ OTP đã gửi (Twilio/SES)     │                          │ /identity/{did}/pubkey
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

<img src="https://github.com/user-attachments/assets/480febf8-db27-4460-bbd9-238937af3c2e" width="600"/>

### 3.2. Identity Register

<img src="https://github.com/user-attachments/assets/f872c18b-ce05-4169-9fea-399d4eeccec0" width="600"/>

### 3.3. Tra cứu pubkey

<img src="https://github.com/user-attachments/assets/0ecd4758-8809-420f-8953-ccd4bab8d33d" width="600"/>

### 3.4. Tra cứu status (TAAD)

<img src="https://github.com/user-attachments/assets/dc73c910-3425-44b6-9048-8c4e9604e405" width="600"/>

### 3.5. Authorize / Revoke Key

<img src="https://github.com/user-attachments/assets/ed4d993d-354b-49cb-9672-6f644859d14b" width="600"/>

### 3.6. Guardian

<img src="https://github.com/user-attachments/assets/9a081849-ea6f-4d40-8c2a-3b36bf515156" width="600"/>

### 3.7. Indexer sync

<img src="https://github.com/user-attachments/assets/b61990cb-e713-4716-b145-b91d976001a8" width="600"/>

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
CREATE TYPE auth_provider AS ENUM ('PHONE', 'EMAIL');

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
-- [V1.5] Key Origin Type: SDK cần biết để quyết định tìm mảnh trên LampNet khi Recovery
CREATE TYPE key_origin_type AS ENUM (
    'secure_enclave', -- Key sinh trong Secure Enclave/TEE — có mảnh LampNet
    'imported_bip39', -- Seed phrase nhập từ ngoài (Yoroi, Eternl) — KHÔNG có mảnh LampNet
    'derived_child'   -- Key derive từ seed gốc
);

CREATE TABLE authorized_keys (
  id                 UUID PRIMARY KEY,
  user_did           VARCHAR(128) NOT NULL REFERENCES users(user_did) ON DELETE CASCADE,
  public_key_hex     VARCHAR(128) NOT NULL,
  -- [V1.5] Nguồn gốc key — quyết định LampNet có được dùng khi Recovery không
  key_origin         key_origin_type NOT NULL DEFAULT 'secure_enclave',
  -- Quyền hạn: 'owner' > 'manager' > 'viewer'
  -- [V1.5] Đổi tên: farm_manager → manager, read_only → viewer
  key_role           VARCHAR(50) NOT NULL DEFAULT 'owner',
  added_by_signature VARCHAR(256) NOT NULL,           -- Zero-Trust: chữ ký từ Root Key
  status             VARCHAR(20) NOT NULL DEFAULT 'active', -- 'active' | 'revoked'
  created_at         TIMESTAMPTZ DEFAULT NOW(),
  CONSTRAINT uq_did_pubkey UNIQUE (user_did, public_key_hex),
  CONSTRAINT chk_key_role CHECK (key_role IN ('owner', 'manager', 'viewer')),
  CONSTRAINT chk_status CHECK (status IN ('active', 'revoked'))
);
```

> **Zero-Trust:** Backend phải verify `added_by_signature` trước khi INSERT. Nếu Backend bị hack, không thể tự thêm khóa.
>
> **[V1.5] Bỏ `lampnet_locator_id`:** LampNet topology thay đổi liên tục — locator được tính on-the-fly bằng `Hash(public_key_hex + SALT)`, không lưu DB.

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

> ⚠️ **Planned — chưa triển khai trong v1.5.** Bảng này sẽ được thêm trong v2.0 khi luồng Social Recovery on-chain được hoàn thiện.
>
> Luồng dự kiến: khi đủ threshold guardian phê duyệt (off-chain) → Indexer sync TAAD state `RECOVERING`.

```sql
-- TODO v2.0
-- CREATE TABLE recovery_approvals (
--   id                   UUID PRIMARY KEY,
--   user_did             VARCHAR(128) NOT NULL,
--   guardian_did         VARCHAR(128) NOT NULL,
--   new_controller_pkh   VARCHAR(64) NOT NULL,
--   guardian_signature   VARCHAR(256) NOT NULL,
--   approved_at          TIMESTAMPTZ DEFAULT NOW(),
--   UNIQUE(user_did, guardian_did)
-- );
```

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

### 5.7. activity_logs — Nhật ký kiểm toán (Immutable Audit Trail)

```sql
-- [V1.5] Partitioned by RANGE(created_at) — tạo partition mới mỗi quý
CREATE TABLE activity_logs (
  id         UUID NOT NULL,
  user_id    UUID,                  -- nullable: GDPR ON DELETE SET NULL
  action     VARCHAR(50) NOT NULL,  -- VD: 'login_success', 'key_authorized'
  metadata   JSONB,                 -- TUYỆT ĐỐI KHÔNG CHỨA PII
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, created_at)      -- composite PK bắt buộc với partitioned table
) PARTITION BY RANGE (created_at);

-- Partitions hiện tại (tạo thêm mỗi đầu quý)
CREATE TABLE activity_logs_2026_q2 PARTITION OF activity_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE activity_logs_2026_q3 PARTITION OF activity_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
```

> **Smart Trigger** (`BEFORE UPDATE OR DELETE`):
> - Chặn tất cả UPDATE — logs là immutable.
> - Chặn DELETE khi `user_id IS NOT NULL` — bảo vệ audit trail đang active.
> - Cho phép DELETE khi `user_id IS NULL` — GDPR erasure hợp lệ (sau khi user xóa tài khoản, ON DELETE SET NULL).

### 5.8. used_nonces — Chống Replay Attack

```sql
-- [V1.5] Đảm bảo mỗi nonce chỉ được dùng một lần — PostgreSQL PK thay vì Redis TTL
CREATE TABLE used_nonces (
    nonce      VARCHAR(64) NOT NULL,
    user_did   VARCHAR(128) NOT NULL REFERENCES users(user_did) ON DELETE CASCADE,
    used_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (nonce, user_did)
);
```

> **Tại sao PostgreSQL thay vì Redis?** Redis TTL không đảm bảo tính duy nhất tuyệt đối — nonce có thể bị tái sử dụng nếu TTL hết đúng lúc request thứ hai đến. PostgreSQL PRIMARY KEY đảm bảo atomically.
>
> Các endpoint yêu cầu nonce: `/keys/authorize`, `/keys/revoke`, `/guardians/add`, `/guardians/remove`.
>
> Cleanup: `ScheduledTasksService` chạy mỗi giờ xóa các nonce hết hạn.

### 5.9. pending_invitations — Discovery Bridge

```sql
-- [V1.5] Lời mời chờ xử lý — dành cho user chưa có app
CREATE TABLE pending_invitations (
    id                UUID PRIMARY KEY,
    inviter_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_blind_hash VARCHAR(64) NOT NULL, -- HMAC_SHA256(phone_or_email, SERVER_PEPPER)
    invite_type       VARCHAR(20) NOT NULL,  -- 'guardian' | 'manager'
    expires_at        TIMESTAMPTZ NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending' | 'resolved' | 'expired'
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> **Discovery Bridge Flow:**
> 1. User A nhập SĐT/Email của User B để mời làm Guardian.
> 2. Backend tính `blind_index_hash` của User B.
> 3. Nếu User B chưa có app → ghi vào `pending_invitations`.
> 4. Khi User B đăng ký bằng SĐT/Email đó → Backend match blind hash → tự động resolve → ghi guardian vào `guardians`.
>
> Cleanup: `ScheduledTasksService` chạy mỗi giờ đánh dấu lời mời hết hạn thành `expired`.

---

### Diagram
<img width="1544" height="1414" alt="Screenshot From 2026-04-19 01-08-03" src="https://github.com/user-attachments/assets/495f21e7-3ad9-4aae-a9b2-3c71f456cb11" />



---

## 6. Kiến trúc Cache (Redis)

**Nguyên tắc:** Dữ liệu "sống ngắn" (OTP, Session, Rate Limit) **tuyệt đối không được lưu vào SQL**.

| Key Pattern                      | Ai ghi                | Dữ liệu                    | TTL           | Mục đích                            |
| -------------------------------- | --------------------- | -------------------------- | ------------- | ----------------------------------- |
| `otp:auth:{blind_hash}`          | NestJS → PK_DB (save) | OTP đã generate + attempts | 300s (5 phút) | Verify OTP đăng nhập                |
| `otp:auth:{blind_hash}:attempts` | PK_DB (increment)     | Số lần nhập sai            | 300s          | Chống brute-force                   |
| `ratelimit:ip:{ip_hash}`         | PK_DB                 | Số request/IP              | 3600s (1 giờ) | Chống spam: khóa 1h nếu vượt ngưỡng |
| `session:token:{jwt_hash}`       | PK_DB                 | `{user_did\|pubkey}`       | 86400s (24h)  | Phiên đăng nhập Web2                |

**Lưu ý:** OTP được NestJS gửi qua SMS/Email (Twilio/SendGrid). PK_DB nhận `blind_hash + otp + credential` từ NestJS rồi lưu vào Redis. `credential` (email/phone thuần) được dùng để re-hash blind_index_hash khi pepper được rotate — không lưu vào DB.

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

- **Gửi OTP qua SMS/Email** (NestJS làm qua Twilio/SendGrid)
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
│   │   ├── PhoenixkeyDbApplication.java     # @EnableScheduling
│   │   ├── common/
│   │   │   ├── DataResponse.java            # Wrapper response
│   │   │   └── UuidGenerator.java           # UUIDv7 generator
│   │   ├── config/
│   │   │   ├── VaultConfig.java             # RestTemplate cho Vault HTTP API
│   │   │   ├── RedisConfig.java
│   │   │   ├── OpenApiConfig.java           # Swagger/OpenAPI 3
│   │   │   └── SecurityConfig.java
│   │   ├── domain/                          # JPA Entities
│   │   │   ├── User.java
│   │   │   ├── AuthMethod.java
│   │   │   ├── AuthProvider.java            # Enum: PHONE | EMAIL
│   │   │   ├── AuthorizedKey.java
│   │   │   ├── KeyOriginType.java           # [V1.5] Enum: secure_enclave | imported_bip39 | derived_child
│   │   │   ├── Guardian.java
│   │   │   ├── OnchainTaadStateCache.java
│   │   │   ├── TaadStatus.java              # Enum: ACTIVE | RECOVERING | MIGRATED
│   │   │   ├── ActivityLog.java             # Partitioned entity
│   │   │   ├── ActivityLogId.java           # [V1.5] Composite PK cho partitioned table
│   │   │   ├── UsedNonce.java               # [V1.5] Replay Attack protection
│   │   │   ├── UsedNonceId.java             # [V1.5] Composite PK (nonce, user_did)
│   │   │   └── PendingInvitation.java       # [V1.5] Discovery Bridge
│   │   ├── dto/
│   │   │   ├── request/                     # *Request.java (record)
│   │   │   └── response/                    # *Response.java (record)
│   │   ├── repository/                      # Spring Data JPA
│   │   ├── service/
│   │   │   ├── PepperVaultService.java      # Đọc pepper từ HashiCorp Vault
│   │   │   ├── RedisService.java            # OTP/Session/RateLimit Redis
│   │   │   ├── ActivityLogService.java      # Audit trail logging
│   │   │   ├── NonceService.java            # [V1.5] Replay Attack: check/store nonce
│   │   │   ├── InvitationService.java       # [V1.5] Discovery Bridge
│   │   │   ├── AuthService.java / impl/
│   │   │   ├── IdentityService.java / impl/
│   │   │   ├── KeyService.java / impl/
│   │   │   ├── GuardianService.java / impl/
│   │   │   ├── IndexerService.java / impl/
│   │   │   └── impl/ScheduledTasksService.java  # [V1.5] Cleanup nonces + invitations mỗi giờ
│   │   ├── crypto/
│   │   │   └── BlindIndexService.java       # HMAC-SHA256 + Pepper
│   │   └── exception/
│   │       ├── AppException.java
│   │       ├── ErrorCode.java
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__create_identity_core.sql
│           ├── V2__create_authorized_keys.sql   # [V1.5] key_origin_type + xóa lampnet_locator_id
│           ├── V3__create_guardians.sql
│           ├── V4__create_onchain_cache.sql
│           ├── V5__create_activity_logs.sql     # [V1.5] Partitioned table
│           ├── V6__create_used_nonces.sql        # [V1.5] Replay Attack protection
│           └── V7__create_pending_invitations.sql # [V1.5] Discovery Bridge
├── PhoenixKey.postman_collection.json       # Postman collection đầy đủ
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
- HashiCorp Vault (HCP Vault cloud)

### Khởi động

```bash
# 1. Chạy infrastructure (PostgreSQL + Redis)
docker compose up -d

# 2. Tạo HCP Vault Cluster
#    https://portal.cloud.hashicorp.com → Create Vault → Starter tier
#    Copy: Public Cluster URL + Generate token

# 3. Seed pepper vào HCP Vault
vault kv put secret/phoenixkey/pepper \
  current_version=1 \
  pepper_1="$(openssl rand -hex 32)"

# 4. Cập nhật .env với HCP Vault info (xem .env.example)

# 5. Chạy app
mvn spring-boot:run
```

### Env bắt buộc

```env
# HashiCorp Vault — dùng HCP Vault (cloud)
# Setup: docs/HashiCorpVault.md
VAULT_ENABLED=true
VAULT_ADDR=https://xxxxx.cluster.hashicorp.cloud:8200
VAULT_TOKEN=hvs.XXXXXXXXXXXXXXXXXXXX
VAULT_NAMESPACE=admin

# Database
DB_HOST=${DB_HOST:localhost}
DB_PASSWORD=${DB_PASSWORD:phoenixkey_dev_password}
```

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

| Tài liệu                                  | Mô tả                                            |
| ----------------------------------------- | ------------------------------------------------ |
| `[PhoenixKey]-Database.docx`              | Đặc tả CSDL chi tiết (v1.1)                     |
| `PhoenixKey-Development.docx`             | Lộ trình MVP + PoC + Milestone                  |
| `PhoenixKey_Database_Plan.docx`           | Kế hoạch triển khai                             |
| `docs/Update_V1_5.md`                     | Changelog đầy đủ v1.5 — Done 2026-04-19        |
| `docs/HashiCorpVault.md`                  | Hướng dẫn setup HCP Vault + seed pepper         |
| `PhoenixKey.postman_collection.json`      | Postman collection — tất cả API endpoints v1.5  |

---

> **Ghi nhớ một dòng:** PhoenixKey Database là "Kính lúp" đọc nhanh từ Blockchain — không generate OTP, không gửi SMS, không tạo DID, chỉ lưu/truy vấu định danh.
