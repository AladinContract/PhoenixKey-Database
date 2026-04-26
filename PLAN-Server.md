# PhoenixKey-Server — Migration Plan

> **Mục tiêu:** Biến repo `PhoenixKey-Database` (hiện tại) thành **`PhoenixKey-Server`** — backend duy nhất phục vụ cả web (`phoenixkey.me`) lẫn mobile app.
>
> **Stack:** Spring Boot 3.3 + PostgreSQL 16 + Redis 7 + Flyway + BloxBean cardano-client-lib + BouncyCastle.
>
> **Ngôn ngữ:** Java 21 (giữ nguyên).
>
> **Spec dẫn đường:** `PhoenixKey_Interface.md` v1.4.3 (tài liệu UI + protocol).
>
> **Phạm vi sạch hơn:** Bỏ toàn bộ luồng email/phone/OTP/Vault — sếp đã chốt định danh chỉ qua Hardware Key + biometric trong Secure Enclave của mobile (xem mục _Bối cảnh_ bên dưới).

---

## Bối cảnh

### Hệ thống tổng quan (sau tái cơ cấu)

```
                 ┌──────────────────────────────────────────────┐
                 │            User Hardware (Phone)             │
                 │  ┌──────────────────────────────────────┐    │
                 │  │ Secure Enclave / TEE                 │    │
                 │  │ • Hardware Key (non-exportable)      │    │
                 │  │ • Biometric unlock                   │    │
                 │  └──────────────────────────────────────┘    │
                 │           ▲                                  │
                 │           │ FaceID / Vân tay                 │
                 │           │                                  │
                 │  ┌──────────────────────────────────────┐    │
                 │  │ Aladin / PhoenixKey App (Tùng làm)   │    │
                 │  │ • B1 cài app                         │    │
                 │  │ • B2 sinh trắc + ký challenge        │    │
                 │  │ • B5 phê duyệt giao dịch             │    │
                 │  └──────────────────────────────────────┘    │
                 └────────┬─────────────────────────────────────┘
                          │
                          │ HTTPS REST (sign payloads, register)
                          ▼
┌──────────────────────────────────────────────────────────────────────────┐
│              PhoenixKey-Server (THIS REPO — đang refactor)               │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ HTTP Layer                                                         │  │
│  │  • /auth/session/* (web QR pairing + SSE)                          │  │
│  │  • /sign/* (relay sign-request giữa web ↔ mobile)                  │  │
│  │  • /identity/* (register, pubkey, status, health)                  │  │
│  │  • /keys/* (authorize, revoke, rotate)                             │  │
│  │  • /guardians/* (add, remove)                                      │  │
│  │  • /api/v1/activity-logs (audit trail)                             │  │
│  │  • /seed/export-request (relay seed export intent)                 │  │
│  │  • /tx/estimate, /support/session/init                             │  │
│  │  • /internal/sync-taad (Indexer Worker)                            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Domain Services                                                    │  │
│  │  • IdentityService, KeyService, GuardianService                    │  │
│  │  • SessionService, SignRequestService (mới)                        │  │
│  │  • CardanoService, FeeWalletService, SignatureService (port từ TS) │  │
│  │  • IndexerService, NonceService, ActivityLogService                │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────┬─────────────────────┬─────────────────────────┐  │
│  │ PostgreSQL         │ Redis               │ BloxBean → Cardano      │  │
│  │ • users            │ • session:* (TTL)   │ • Build tx + datum      │  │
│  │ • authorized_keys  │ • sign:* (TTL 120s) │ • Submit qua Blockfrost │  │
│  │ • guardians        │ • ratelimit:*       │ • Resolve DID Document  │  │
│  │ • activity_logs    │                     │ • Wait for confirmation │  │
│  │ • used_nonces      │                     │                         │  │
│  │ • taad_state_cache │                     │                         │  │
│  └────────────────────┴─────────────────────┴─────────────────────────┘  │
└──────────────────────┬───────────────────────────────────────┬───────────┘
                       │                                       │
                       │ HTTPS REST + SSE                      │ Submit tx
                       ▼                                       ▼
              ┌──────────────────┐                  ┌──────────────────────┐
              │ phoenixkey.me    │                  │ Cardano Preprod      │
              │ Next.js web      │                  │ (Blockfrost backend) │
              │ (Long làm)       │                  │ DID Document = UTxO  │
              └──────────────────┘                  │ datum                │
                                                    └──────────────────────┘
```

### Triết lý vẫn giữ

- **Phone là vault:** Hardware Key chỉ tồn tại trong Secure Enclave mobile, **không bao giờ rời thiết bị**. Server không thấy private key user.
- **Server giữ fee wallet riêng** trả gas Cardano — không liên quan tới key user.
- **Cardano là SSoT** cho DID Document. PostgreSQL chỉ là cache (qua Indexer Worker).
- **Zero-Trust:** mọi mutation key/guardian phải kèm signature từ Hardware Key, server verify bằng pubkey đã lưu trước khi commit.

### Khác với v1.4.3 spec

Sếp đã chốt **bỏ email/phone/OTP/wallet connection** (xem snippet sếp gửi). Hệ quả:

| Spec v1.4.3                                        | New flow                                  | Hành động                                      |
| -------------------------------------------------- | ----------------------------------------- | ---------------------------------------------- |
| Email/SĐT làm secondary auth + Discovery Bridge    | Bỏ hoàn toàn                              | Xóa toàn bộ schema/service/code email/phone    |
| OTP qua Twilio/SendGrid (Step 5.2 onboarding)      | Bỏ                                        | Xóa AuthService + OTP Redis flow               |
| Blind Index (HMAC + Pepper từ Vault)               | Không cần (không lưu PII)                 | Xóa BlindIndexService + Vault config           |
| User_Secret = HKDF(JWT_sub + ...) cho Recovery KEK | Bỏ luồng Recovery Blob LampNet trong MVP        | MVP chỉ Seed Phrase backup (mobile-side, spec §9). Guardian recovery on-chain để Phase H+ |
| Pending invitations (mời guardian qua phone/email) | Bỏ                                        | Xóa pending_invitations + InvitationService    |

---

## Backend endpoints — danh sách cuối cùng

Tổng **23 endpoint**. Phân làm 4 nhóm theo trạng thái:

### Nhóm 1: Đã có, GIỮ NGUYÊN (8 endpoint)

| Method | Path                     | Service                        | Spec ref               |
| ------ | ------------------------ | ------------------------------ | ---------------------- |
| `POST` | `/keys/authorize`        | KeyService.authorize           | spec §11 + Update_V1_5 |
| `POST` | `/keys/revoke`           | KeyService.revoke              | spec §11               |
| `POST` | `/guardians/add`         | GuardianService.addGuardian    | spec §5.4              |
| `POST` | `/guardians/remove`      | GuardianService.removeGuardian | spec §5.4              |
| `GET`  | `/identity/{did}/pubkey` | IdentityService.getPubkey      | (internal verify)      |
| `GET`  | `/identity/{did}/status` | IdentityService.getStatus      | spec §13.0 banner      |
| `POST` | `/internal/sync-taad`    | IndexerService.syncTaad        | (Indexer Worker only)  |
| `GET`  | `/actuator/health`       | Spring Boot Actuator           | health check           |

### Nhóm 2: Đã có, REFACTOR (3 endpoint)

| Method | Path                                            | Thay đổi                                                                                                                                                                                                                                                                                                     |
| ------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `POST` | `/identity/register`                            | Bỏ `credential`/`provider`/`blindHash` khỏi request. Mobile gửi `{publicKeyHex, keyOrigin, keyRole, addedBySignature}`. Server verify signature → publish DID Cardano qua `CardanoService.createDID()` → trả `{userId, userDid, txHash}`. Bỏ luồng "userDid=pending → PUT /identity/did" — flow mới đồng bộ. |
| `PUT`  | `/identity/did`                                 | **GIỮ TÙY CHỌN** cho compat: nếu mobile chưa migrate, vẫn cho phép update DID sau. Có thể xóa Phase A nếu Tùng OK.                                                                                                                                                                                           |
| (xóa)  | `POST /auth/otp/save` & `POST /auth/otp/verify` | Xóa hoàn toàn (xem Phase A).                                                                                                                                                                                                                                                                                 |

### Nhóm 3: Mới — port từ NestJS (4 endpoint)

| Method | Path                                    | Logic                                                                                                                                              | Port từ                                 |
| ------ | --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| `POST` | `/identity/register` (đã list ở Nhóm 2) | createDID                                                                                                                                          | `apps/api/identity/identity.service.ts` |
| `POST` | `/keys/rotate`                          | Mobile signs "ROTATE_KEY" intent với old key + cung cấp new pubkey → Server build tx UpdateDID + submit → cập nhật `authorized_keys` và TAAD cache | `src/did-registry-onchain.ts:updateDID` |
| `GET`  | `/identity/{did}/document`              | Resolve W3C DID Document từ Cardano qua tx hash hiện tại                                                                                           | `src/did-registry-onchain.ts:resolve`   |
| `GET`  | `/tx/estimate?type=key_rotation`        | Estimate Cardano fee. MVP: trả constant 12 MAGIC. Phase G refine bằng BloxBean fee calculator.                                                     | (mới)                                   |

### Nhóm 4: Mới — viết từ đầu (8 endpoint)

| Method | Path                             | Logic                                                                                                                                                                                                    | Spec ref  |
| ------ | -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| `POST` | `/auth/session/init`             | Tạo `session_id` (UUIDv7) + 32-byte challenge + temp_token JWT (TTL 5min). Lưu Redis.                                                                                                                    | §6.3      |
| `GET`  | `/auth/session/{id}/stream`      | SSE — `SseEmitter` đăng ký vào registry. Heartbeat ping mỗi 30s.                                                                                                                                         | §15.1     |
| `GET`  | `/auth/session/{id}/status`      | Trả state hiện tại (pending/approved/rejected/expired). Web gọi sau khi SSE reconnect.                                                                                                                   | §15.1     |
| `POST` | `/auth/session/{id}/approve`     | **Mobile gọi.** Verify Hardware Key signature trên `(challenge + domain + timestamp)`. Thành công: mint `session_token` JWT (TTL 24h), push qua SSE emitter, trả `{linked_device_token}` cho mobile lưu. | §2.4, §6  |
| `POST` | `/auth/session/push`             | Web gửi `linked_device_token` → backend trigger push notification mobile qua FCM/APNs (Long chốt full integrate trong MVP — xem Phase D.10).                                                             | §6.3      |
| `POST` | `/sign/request`                  | Web tạo sign request `{intent, payload}`. Server lưu Redis (TTL 120s) → push notification mobile (stub) + sẵn sàng cho mobile fetch.                                                                     | §7        |
| `GET`  | `/sign/request/{id}`             | **Mobile gọi.** Lấy chi tiết payload.                                                                                                                                                                    | §7, §15.5 |
| `POST` | `/sign/{id}/approve`             | **Mobile gọi.** Verify signature → push qua SSE emitter cho web → web nhận signed result.                                                                                                                | §7        |
| `POST` | `/sign/{id}/cancel`              | Web cancel sign request đang chờ.                                                                                                                                                                        | §7.4      |
| `POST` | `/seed/export-request`           | Tạo special sign-request type `SEED_EXPORT`. Mobile approve → ghi `users.seed_exported_at = NOW()`.                                                                                                      | §9.2      |
| `GET`  | `/identity/health`               | Trả `{seed_exported, exported_at, has_guardian, key_count}` cho dashboard banner.                                                                                                                        | §9.5      |
| `GET`  | `/api/v1/activity-logs`          | Cursor pagination + filter + range.                                                                                                                                                                      | §10       |
| `GET`  | `/api/v1/identity/nodes?did=...` | LampNet node map. **MVP stub:** trả 12 node mock theo spec dev note (chờ Long quyết data source thật).                                                                                                   | §14.4     |
| `POST` | `/support/session/init`          | Get LAMP support session. **MVP stub:** trả URL placeholder. ProofChat integration là Phase H.                                                                                                           | §15.8     |

---

## Phase A — Cleanup (xoá thứ thừa, ~1.5 ngày)

> **Mục tiêu:** Loại bỏ code/schema/config liên quan email/phone/OTP. **Vault giữ lại** — refactor `PepperVaultService` thành KMS chung cho fee-wallet mnemonic, JWT secret, FCM/APNs credentials.

### A.1 — Xoá Java code

```
src/main/java/com/magiclamp/phoenixkey_db/
├── controller/AuthController.java                          ❌ DELETE
├── crypto/BlindIndexService.java                           ❌ DELETE
├── config/VaultConfig.java                                 ✏ KEEP (RestTemplate config cho Vault HTTP API)
├── service/AuthService.java + impl/AuthServiceImpl.java    ❌ DELETE
├── service/InvitationService.java + impl/InvitationServiceImpl.java  ❌ DELETE
├── service/PepperVaultService.java                         ✏ RENAME → service/secret/VaultSecretService.java
├── domain/AuthMethod.java                                  ❌ DELETE
├── domain/AuthProvider.java                                ❌ DELETE
├── domain/PendingInvitation.java                           ❌ DELETE
├── repository/AuthMethodRepository.java                    ❌ DELETE
├── repository/PendingInvitationRepository.java             ❌ DELETE
├── dto/request/OtpSendRequest.java                         ❌ DELETE
├── dto/request/OtpVerifyRequest.java                       ❌ DELETE
├── dto/response/OtpVerifyResponse.java                     ❌ DELETE
└── service/impl/ScheduledTasksService.java                 ✏ MODIFY (giữ nonce cleanup, bỏ invitation cleanup)
```

### A.1b — Refactor `PepperVaultService` → `VaultSecretService`

Generic getter cho mọi secret. Đường dẫn Vault paths chuẩn hoá theo prefix `secret/phoenixkey/`:

```java
@Service
@RequiredArgsConstructor
public class VaultSecretService {
    private final RestTemplate vaultRestTemplate;
    @Value("${spring.cloud.vault.uri}") private String vaultUri;
    @Value("${spring.cloud.vault.token}") private String vaultToken;

    /** secret/phoenixkey/fee-wallet/mnemonic → ["word1", "word2", ...] */
    public String[] getFeeWalletMnemonic();

    /** secret/phoenixkey/jwt/secret → 32 bytes */
    public byte[] getJwtSecret();

    /** secret/phoenixkey/fcm/service-account → JSON content (Firebase) */
    public String getFcmServiceAccount();

    /** secret/phoenixkey/apns/auth-key → { key, keyId, teamId } */
    public ApnsCredentials getApnsCredentials();

    /** Generic: read raw KV v2 secret as Map. */
    public Map<String, Object> readSecret(String path);
}
```

Vault layout đầy đủ:

```
secret/phoenixkey/
├── fee-wallet/mnemonic    { "words": ["word1", ...] }
├── jwt/secret             { "key": "<base64-32-byte>" }
├── fcm/service-account    { "json": "<full firebase json>" }
├── apns/auth-key          { "key": "<.p8 content>", "keyId": "...", "teamId": "..." }
└── blockfrost/api-key     { "key": "preprodXXX..." }   ← optional, low-value secret
```

### A.2 — Refactor `IdentityServiceImpl`

```diff
- import com.magiclamp.phoenixkey_db.crypto.BlindIndexService;
- import com.magiclamp.phoenixkey_db.domain.AuthMethod;
- import com.magiclamp.phoenixkey_db.repository.AuthMethodRepository;
- import com.magiclamp.phoenixkey_db.service.InvitationService;

  public class IdentityServiceImpl implements IdentityService {
      private final UserRepository userRepository;
-     private final AuthMethodRepository authMethodRepository;
      private final AuthorizedKeyRepository authorizedKeyRepository;
      private final OnchainTaadStateCacheRepository taadCacheRepository;
-     private final BlindIndexService blindIndexService;
      private final ActivityLogService activityLogService;
      private final UuidGenerator uuidGenerator;
-     private final InvitationService invitationService;
+     private final SignatureService signatureService;     // Phase B
+     private final CardanoService cardanoService;         // Phase B
```

`register()` body sẽ refactor ở Phase C (sau khi có CardanoService).

### A.3 — Migration V8: drop deprecated tables + add new column

`src/main/resources/db/migration/V8__drop_email_phone_artifacts.sql`:

```sql
-- ============================================================
-- V8: Drop email/phone artifacts (sếp ra flow mới — chỉ Hardware Key)
-- + thêm seed_exported_at cho dashboard health banner (spec §9.5)
-- ============================================================

-- Drop FK + tables (CASCADE để xoá luôn indexes)
DROP TABLE IF EXISTS auth_methods CASCADE;
DROP TABLE IF EXISTS pending_invitations CASCADE;
DROP TYPE IF EXISTS auth_provider;

-- Spec §9.5: dashboard banner ánh xạ trên trường này
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS seed_exported_at TIMESTAMPTZ;
```

### A.4 — Sửa `pom.xml`

```diff
     <!-- Spring Cloud BOM (cần cho Vault starter) -->
     <dependencyManagement>
         <dependencies>
             <dependency>
                 <groupId>org.springframework.cloud</groupId>
                 <artifactId>spring-cloud-dependencies</artifactId>
                 <version>2023.0.1</version>
                 <type>pom</type>
                 <scope>import</scope>
             </dependency>
         </dependencies>
     </dependencyManagement>
```

(`spring-cloud-dependencies` BOM **giữ nguyên** — Vault giờ là KMS chung cho fee wallet + JWT + FCM/APNs.)

### A.5 — Sửa `application.yml`

```diff
  spring:
    application:
      name: phoenixkey-db
    ...
    cloud:
      vault:
        enabled: ${VAULT_ENABLED:true}
        uri: ${VAULT_ADDR:http://localhost:8200}
        token: ${VAULT_TOKEN:phoenixkey-dev-token}
        namespace: ${VAULT_NAMESPACE:}      # KEEP — KMS chung cho mọi secret

  phoenixkey:
-   otp:
-     ttl-seconds: 300
-     max-attempts: 5
    session:
      ttl-seconds: 86400
+   challenge:
+     ttl-seconds: 300              # QR challenge TTL (spec §6.3)
+   sign-request:
+     ttl-seconds: 120              # Sign request TTL (spec §2.4)
    rate-limit:
-     otp-per-hour: 5
      ttl-seconds: 3600
+   cardano:
+     network: ${CARDANO_NETWORK:preprod}
+     blockfrost-api-key: ${BLOCKFROST_API_KEY}
+     # Fee wallet mnemonic được load từ Vault: secret/phoenixkey/fee-wallet/mnemonic
+     # FeeWalletService gọi vaultSecretService.getFeeWalletMnemonic() ở @PostConstruct.
+     # Boot fail-fast nếu Vault unreachable hoặc path không tồn tại.
+     # Dev fallback: nếu vault.enabled=false → đọc env FEE_WALLET_MNEMONIC (log WARN).
+     confirm-timeout-ms: 120000
```

### A.6 — Xoá ErrorCode 1xxx OTP-related

`exception/ErrorCode.java`:

- Xoá: `AUTH_METHOD_NOT_FOUND` (1001), `AUTH_METHOD_ALREADY_EXISTS` (1002), `OTP_INVALID` (1101), `OTP_EXCEEDED_ATTEMPTS` (1102), `OTP_EXPIRED` (1103), `AUTH_PROVIDER_INVALID` (1201), `AUTH_METHOD_NOT_VERIFIED` (1202)
- Thêm: `SESSION_NOT_FOUND` (1301), `SESSION_EXPIRED` (1302), `SESSION_ALREADY_APPROVED` (1303), `SIGN_REQUEST_NOT_FOUND` (1401), `SIGN_REQUEST_EXPIRED` (1402), `SIGNATURE_INVALID` (1403), `CARDANO_TX_FAILED` (5101), `CARDANO_RESOLVE_FAILED` (5102)

### A.7 — Sửa `RedisService`

Drop OTP methods (`saveOtp`, `getOtp`, `getCredential`, `deleteOtp`, `incrementOtpAttempts`, `resetOtpAttempts`, `getOtpAttempts`).
Giữ session + ratelimit.
Thêm methods mới (chi tiết Phase D): `saveChallenge`, `getChallenge`, `markChallengeApproved`, `getSessionPubkey`, `saveSignRequest`, `getSignRequest`, `markSignApproved`.

### A.8 — `docker-compose.yml`

Drop Vault service (nếu có). Thêm env vars cho Cardano (Phase B).

### Done criteria Phase A

- [ ] Compile `mvn clean compile` xanh
- [ ] `mvn flyway:migrate` chạy V8 thành công trên DB hiện có
- [ ] `mvn test` các test cũ vẫn pass (test OTP/Vault sẽ bị xoá kèm)
- [ ] App start không lỗi (verify bằng `curl localhost:8080/api/v1/actuator/health`)

---

## Phase B — Cardano integration (port từ NestJS, ~3 ngày)

> **Mục tiêu:** Có sẵn 3 service Java làm việc với Cardano: `CardanoService`, `FeeWalletService`, `SignatureService`. Test được trên Preprod testnet.

### B.1 — Thêm dependencies vào `pom.xml`

```xml
<!-- BloxBean cardano-client-lib — Java client cho Cardano, support Conway era + Plutus -->
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-lib</artifactId>
    <version>0.6.4</version>
</dependency>
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>cardano-client-backend-blockfrost</artifactId>
    <version>0.6.4</version>
</dependency>

<!-- BouncyCastle — ECDSA SECP256K1 signature verification -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78.1</version>
</dependency>

<!-- JJWT — JWT cho session_token + temp_token -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### B.2 — Tạo package `service/cardano/`

```
service/cardano/
├── CardanoConfig.java          @ConfigurationProperties("phoenixkey.cardano")
├── CardanoBackend.java         @Bean BlockfrostBackendService
├── FeeWalletService.java       Account từ mnemonic, signTx, address
├── CardanoService.java         createDID, updateDID, resolve, findDIDUtxo
└── dto/
    ├── W3CDIDDocument.java     POJO/record matching W3C spec
    ├── W3CVerificationMethod.java
    └── TxResult.java           {txHash, did, operation}
```

### B.3 — Port `did-registry-onchain.ts` → `CardanoService.java`

Mapping cụ thể TS → Java:

| TS (Mesh)                                                         | Java (BloxBean)                                                                                                                      |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `BlockfrostProvider(apiKey)`                                      | `BackendService backend = new BFBackendService(BlockfrostUrls.PREPROD, apiKey)`                                                      |
| `MeshWallet({key:{type:"mnemonic", words}})`                      | `Account.createFromMnemonic(Networks.preprod(), mnemonic)`                                                                           |
| `MeshTxBuilder.txOut(...).txOutInlineDatumValue(json).complete()` | `QuickTxBuilder.compose(new Tx().payToAddress(...).attachDatum(PlutusData.unit()))...` (dùng `QuickTxBuilder` cho idiomatic builder) |
| `wallet.signTx(unsigned)`                                         | `tx.sign(account)`                                                                                                                   |
| `wallet.submitTx(signed)`                                         | `txBuilder.completeAndWait()` hoặc `txBuilder.complete()` rồi `backend.getTransactionService().submitTransaction(...)`               |
| `provider.fetchUTxOs(txHash)`                                     | `backend.getUtxoService().getTxOutputs(txHash)`                                                                                      |
| `deserializeAddress(addr).pubKeyHash`                             | `Address.fromBech32(addr).getPaymentCredentialHash()`                                                                                |

Methods chính cần port (giữ semantic):

```java
public class CardanoService {
    /** Spec §5.3 Genesis: publish DID Document lên Cardano */
    public TxResult createDID(String publicKeyHex);

    /** Spec §11 Key Rotation: consume old UTxO, tạo UTxO mới với datum mới
     *  cần old controller signature làm required signer */
    public TxResult updateDID(String newPublicKeyHex, String previousTxHash, String oldControllerSignedTx);

    /** Resolve W3C DID Document từ tx hash */
    public W3CDIDDocument resolve(String txHash);

    /** Tìm UTxO chưa chi tiêu chứa DID Document hiện tại */
    public Optional<UtxoWithDoc> findDIDUtxo(String did);
}
```

### B.4 — Port `verification.ts` → `SignatureService.java`

```java
public class SignatureService {
    /**
     * Verify ECDSA SECP256K1 signature.
     * Dùng BouncyCastle vì JCE provider mặc định không support secp256k1.
     */
    public boolean verifyEcdsa(String publicKeyHex, byte[] message, byte[] signature) {
        // Setup: Security.addProvider(new BouncyCastleProvider()) trong @PostConstruct
        // Curve: secp256k1
        // Hash: SHA-256
        // Format chữ ký: DER hoặc raw r||s — confirm với Tùng (mobile)
    }

    /** Verify chữ ký challenge từ mobile khi /auth/session/{id}/approve */
    public boolean verifyChallengeSignature(String pubkeyHex, String sessionId, String challenge,
                                            String domain, long timestamp, byte[] signature);

    /** Verify chữ ký intent khi sign-request approve */
    public boolean verifySignRequestSignature(String pubkeyHex, SignIntent intent, byte[] signature);
}
```

### B.5 — Test với Preprod thật

Tạo `src/test/java/.../cardano/CardanoServiceManualTest.java` (manual run, không trong CI):

- Set env `BLOCKFROST_API_KEY` + `FEE_WALLET_MNEMONIC` (wallet có sẵn tADA — Long lấy từ faucet `https://docs.cardano.org/cardano-testnet/tools/faucet`)
- Test 1: `createDID(...)` → assert tx hash 64-hex
- Test 2: `resolve(txHash)` → assert lấy lại đúng publicKeyHex đã đẩy lên
- Test 3 (sau B.6): `updateDID(...)` → assert mới key trong Document

### B.6 — Cardano error handling

- Wrap tất cả lỗi BloxBean → `AppException(ErrorCode.CARDANO_*)`
- Timeout 120s mặc định cho confirm tx
- Retry exponential cho lỗi mạng tạm thời (max 3 attempts)

### Done criteria Phase B

- [ ] `CardanoService.createDID()` chạy trên Preprod, trả tx hash khớp Cardanoscan
- [ ] `CardanoService.resolve(txHash)` trả đúng W3CDIDDocument
- [ ] `SignatureService.verifyEcdsa()` pass test với fixture chữ ký từ Tùng (cần Tùng cung cấp 1-2 sample signed payload)
- [ ] Code coverage cho `service/cardano/` ≥ 70%

---

## Phase C — Refactor IdentityService (~0.5 ngày)

> **Mục tiêu:** Wire Cardano vào luồng register. Mobile gọi 1 lần là DID có ngay (không còn 2-step "pending → update").

### C.1 — Sửa `IdentityRegisterRequest`

```java
public record IdentityRegisterRequest(
    @NotBlank String publicKeyHex,
    @NotNull KeyOriginType keyOrigin,
    @NotBlank String keyRole,            // owner | manager | viewer
    @NotBlank String addedBySignature    // chữ ký Genesis từ Hardware Key
) {}
```

(Bỏ `credential` + `provider`. `userDid` server tự sinh từ tx hash.)

### C.2 — Sửa `IdentityServiceImpl.register()`

```java
@Transactional
public IdentityRegisterResponse register(IdentityRegisterRequest request) {
    // 1. Verify Genesis signature (proof rằng user có private key của publicKeyHex)
    //    Mobile ký chuỗi cố định "PHOENIXKEY_GENESIS:" + publicKeyHex
    byte[] genesisMessage = ("PHOENIXKEY_GENESIS:" + request.publicKeyHex()).getBytes(UTF_8);
    if (!signatureService.verifyEcdsa(request.publicKeyHex(), genesisMessage,
                                       Hex.decode(request.addedBySignature()))) {
        throw new AppException(ErrorCode.SIGNATURE_INVALID);
    }

    // 2. Publish DID lên Cardano
    TxResult tx = cardanoService.createDID(request.publicKeyHex());
    String userDid = tx.did();   // did:cardano:preprod:<txHash>

    // 3. Lưu user
    UUID userId = uuidGenerator.create();
    User user = User.builder()
            .id(userId)
            .userDid(userDid)
            .createdAt(OffsetDateTime.now())
            .build();
    userRepository.save(user);

    // 4. Lưu owner key
    AuthorizedKey ownerKey = AuthorizedKey.builder()
            .id(uuidGenerator.create())
            .userDid(userDid)
            .publicKeyHex(request.publicKeyHex())
            .keyOrigin(request.keyOrigin())
            .keyRole(request.keyRole())
            .addedBySignature(request.addedBySignature())
            .status("active")
            .createdAt(OffsetDateTime.now())
            .build();
    authorizedKeyRepository.save(ownerKey);

    // 5. Activity log
    activityLogService.log(userId, ActivityLogService.ACTION_USER_REGISTERED,
            Map.of("did", userDid, "tx_hash", tx.txHash()));

    return new IdentityRegisterResponse(userId.toString(), userDid, tx.txHash());
}
```

### C.3 — Sửa `IdentityRegisterResponse`

```java
public record IdentityRegisterResponse(String userId, String userDid, String txHash) {}
```

### C.4 — Quyết định về `PUT /identity/did`

- Hỏi Tùng: mobile đã hook endpoint cũ chưa?
- Nếu rồi: giữ làm compatibility layer → mobile tự gọi cũng OK, server bỏ qua nếu DID đã set.
- Nếu chưa: xoá `PUT /identity/did` + `UserDidUpdateRequest` + `IdentityServiceImpl.updateUserDid()`.

### Done criteria Phase C

- [ ] `POST /identity/register` E2E: Postman gửi request → server trả `{userId, userDid, txHash}` → tx hash verify được trên Cardanoscan Preprod
- [ ] `users` + `authorized_keys` có row mới
- [ ] `activity_logs` có entry `user_registered`
- [ ] Test: register 2 lần với cùng publicKeyHex → request thứ 2 fail vì signature/nonce hoặc business logic (cần thêm constraint)

---

## Phase D — Web session + sign request + FCM/APNs push (~4 ngày)

> **Mục tiêu:** Web `/login` hoạt động E2E qua QR + SSE. Mobile approve → web nhận session token và vào dashboard. **Push notification thật** đến mobile khi web tạo session/sign-request (không stub).

### D.1 — `service/SessionService.java`

```java
public interface SessionService {
    SessionInitResponse init();                                                  // tạo challenge
    SessionStatus getStatus(String sessionId, String tempToken);                 // poll fallback
    void approveByMobile(String sessionId, String userDid, String publicKeyHex,
                         String signature);                                      // mobile gọi
    void rejectByMobile(String sessionId, String reason);
    SseEmitter openStream(String sessionId, String tempToken);                   // web mở SSE
    void pushPing(String sessionId);                                             // heartbeat 30s
}
```

### D.2 — `service/SseEmitterRegistry.java`

In-memory `ConcurrentHashMap<String, SseEmitter>` keyed by sessionId. MVP single-instance OK (out-of-scope: multi-instance via Redis Pub/Sub).

### D.3 — `controller/SessionController.java`

```java
@RestController
@RequestMapping("/auth/session")
public class SessionController {
    @PostMapping("/init")
    public DataResponse<SessionInitResponse> init();

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id,
                             @RequestHeader("Authorization") String bearer);

    @GetMapping("/{id}/status")
    public DataResponse<SessionStatus> status(@PathVariable String id,
                                              @RequestHeader("Authorization") String bearer);

    @PostMapping("/{id}/approve")              // mobile
    public DataResponse<SessionApproveResponse> approve(@PathVariable String id,
                                                         @Valid @RequestBody SessionApproveRequest req);

    @PostMapping("/push")                       // web — trigger push to linked device
    public DataResponse<Void> push(@Valid @RequestBody SessionPushRequest req);
}
```

### D.4 — Storage layout (Redis)

```
session:init:{sessionId}             { challenge, tempToken, expiresAt, status: pending }     TTL 300s
session:approved:{sessionId}         { userDid, sessionToken, linkedDeviceToken }              TTL 86400s
linked-device:{token}                userDid                                                    TTL 30d
sign-req:{requestId}                 { intent, payload, status, signature?, expiresAt }        TTL 120s
```

### D.5 — `service/SignRequestService.java`

```java
public interface SignRequestService {
    SignRequestInitResponse create(SignRequestCreateRequest req, String webSessionToken);
    SignRequestPayload get(String requestId);                                       // mobile fetch
    void approve(String requestId, String signature, String mobilePubkey);
    void cancel(String requestId);
}
```

Trên approve: emit qua `SseEmitterRegistry` cho `requestId`'s parent session.

### D.6 — `controller/SignRequestController.java`

```java
@RestController
public class SignRequestController {
    @PostMapping("/sign/request") public DataResponse<...> create(...);   // web
    @GetMapping("/sign/request/{id}") public DataResponse<...> get(...);  // mobile
    @PostMapping("/sign/{id}/approve") public DataResponse<...> approve(...); // mobile
    @PostMapping("/sign/{id}/cancel") public DataResponse<...> cancel(...);   // web
}
```

### D.7 — JWT helper

`security/JwtService.java`:

- `mintTempToken(sessionId, expiresInSec)` — HMAC-SHA256, payload `{sub:sessionId, type:"temp", exp}`
- `mintSessionToken(userDid, expiresInSec)` — payload `{sub:userDid, type:"session", exp}`
- `mintLinkedDeviceToken(userDid)` — TTL 30d, payload `{sub:userDid, type:"linked-device"}`
- `parseAndVerify(token)` → `Claims`
- Secret key load từ env `PHOENIXKEY_JWT_SECRET` (32-byte random)

### D.8 — SSE heartbeat task

Spring `@Scheduled(fixedRate = 30000)` chạy `sseEmitterRegistry.pingAll()` để giữ proxy enterprise không kill connection (spec §15.1).

### D.9 — CORS config

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000", "https://phoenixkey.me",
                                "https://staging.phoenixkey.me")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(86400);
    }
}
```

### D.10 — FCM/APNs push notification (full integrate)

Spec §6.3 + §15.5: payload push **không chứa data nhạy cảm**, chỉ chứa `sign_request_id` hoặc `session_id`. Mobile nhận push → fetch chi tiết qua HTTPS authenticated.

#### D.10.1 — Bảng device tokens

Migration `V9__create_device_tokens.sql`:

```sql
CREATE TABLE device_tokens (
    id            UUID PRIMARY KEY,
    user_did      VARCHAR(128) NOT NULL REFERENCES users(user_did) ON DELETE CASCADE,
    platform      VARCHAR(10) NOT NULL,        -- 'ios' | 'android'
    fcm_token     VARCHAR(255),                -- FCM token (Android + iOS)
    apns_token    VARCHAR(255),                -- APNs device token (iOS native)
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ,
    CONSTRAINT chk_platform CHECK (platform IN ('ios', 'android'))
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_did);
```

Mobile gọi `POST /devices/register` (body: `{platform, fcmToken, apnsToken?}`) sau khi login để lưu token. Server dùng token gửi push khi cần.

#### D.10.2 — Dependencies pom.xml

```xml
<!-- Firebase Admin SDK — gửi FCM (Android + iOS qua FCM gateway) -->
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.4.1</version>
</dependency>

<!-- pushy — gửi APNs trực tiếp (iOS native, ổn định hơn FCM-iOS proxy) -->
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy</artifactId>
    <version>0.15.4</version>
</dependency>
```

#### D.10.3 — Service: `service/push/PushService.java`

```java
public interface PushService {
    /** Gửi push tới tất cả device đang đăng ký của user */
    void notifySignRequest(String userDid, String signRequestId);
    void notifySessionApproval(String userDid, String sessionId);
    void notifySeedExportRequest(String userDid, String requestId);
}
```

Implementation chia 2 backend:

- `FcmPushSender` — gửi qua Firebase Admin SDK cho Android + iOS (qua FCM)
- `ApnsPushSender` — gửi APNs native cho iOS (ưu tiên) qua pushy

Strategy: nếu `apns_token` có → dùng APNs trực tiếp; nếu chỉ có `fcm_token` → dùng FCM.

#### D.10.4 — Config

`.env.example`:

```env
# Firebase service account JSON (path hoặc base64 inline)
FCM_SERVICE_ACCOUNT_PATH=/secrets/firebase-service-account.json

# APNs (Apple Push Notification service)
APNS_AUTH_KEY_PATH=/secrets/AuthKey_XXXXXXXXXX.p8
APNS_KEY_ID=XXXXXXXXXX
APNS_TEAM_ID=YYYYYYYYYY
APNS_BUNDLE_ID=me.phoenixkey.aladin
APNS_PRODUCTION=false                  # true cho prod, false cho sandbox
```

`application.yml`:

```yaml
phoenixkey:
  push:
    fcm:
      enabled: ${FCM_ENABLED:true}
      service-account-path: ${FCM_SERVICE_ACCOUNT_PATH}
    apns:
      enabled: ${APNS_ENABLED:true}
      auth-key-path: ${APNS_AUTH_KEY_PATH}
      key-id: ${APNS_KEY_ID}
      team-id: ${APNS_TEAM_ID}
      bundle-id: ${APNS_BUNDLE_ID}
      production: ${APNS_PRODUCTION:false}
```

#### D.10.5 — Wire vào Session/SignRequest services

```java
// SessionServiceImpl.init() KHÔNG push (web tạo, mobile chưa có session_id)
// Web có thể gọi /auth/session/push sau:
public void pushToLinkedDevice(String linkedDeviceToken) {
    String userDid = jwtService.parseAndVerify(linkedDeviceToken).get("sub");
    pushService.notifySessionApproval(userDid, /* session_id từ context */);
}

// SignRequestServiceImpl.create() — auto push khi tạo
public SignRequestInitResponse create(SignRequestCreateRequest req, String webToken) {
    String userDid = jwtService.parseAndVerify(webToken).get("sub");
    String requestId = uuidGenerator.create().toString();
    redisService.saveSignRequest(requestId, payload, Duration.ofSeconds(120));
    pushService.notifySignRequest(userDid, requestId);   // ← push ngay
    return new SignRequestInitResponse(requestId);
}
```

#### D.10.6 — Endpoint mới: `POST /devices/register`

Mobile đăng ký device token sau login.

```java
@PostMapping("/devices/register")
public DataResponse<Void> register(@AuthenticationPrincipal UserDid userDid,
                                    @Valid @RequestBody DeviceRegisterRequest req);
```

#### D.10.7 — Setup Firebase project (cần Long làm trước)

- Tạo Firebase project tại https://console.firebase.google.com
- Bật FCM
- Generate service account JSON, lưu vào `secrets/firebase-service-account.json`
- Thêm `secrets/` vào `.gitignore`

#### D.10.8 — Setup APNs (cần Long làm trước)

- Vào Apple Developer Portal → Keys → Create APNs Auth Key (.p8)
- Lấy Key ID + Team ID
- Confirm Bundle ID của Tùng (Aladin app)

> ⚠ **Long lưu ý**: phần FCM/APNs chỉ chạy được khi có service account + APNs key. Nếu chưa có ngay, Phase D.10 sẽ build code ready nhưng feature flag `FCM_ENABLED=false` để skip — push sẽ no-op cho đến khi config đầy đủ.

### D.11 — Update `SecurityConfig`

Thêm: SSE endpoint cần để OPTIONS preflight đi qua, không filter Authorization (đã verify trong controller).

### Done criteria Phase D

- [ ] `POST /auth/session/init` → trả `session_id` + `challenge` + `temp_token`
- [ ] `GET /auth/session/{id}/stream` mở SSE thành công, nhận heartbeat 30s
- [ ] `POST /auth/session/{id}/approve` từ Postman với signature dummy → SSE emit `{status:"approved", session_token}`
- [ ] Web `/login` (Phoenixkey-Interface, branch `main`) chạy được full flow với mock mobile (Postman replay)
- [ ] `POST /sign/request` + `POST /sign/{id}/approve` đi 1 vòng OK

---

## Phase E — Misc spec endpoints (~1.5 ngày)

> **Mục tiêu:** Khoá nốt 7 endpoint spec yêu cầu, đủ cho web không còn 404 nào ở read path.

### E.1 — `GET /identity/health`

```java
@RestController
@RequestMapping("/identity")
public class IdentityHealthController {
    @GetMapping("/health")
    public DataResponse<HealthResponse> health(@AuthenticationPrincipal UserDid userDid) {
        boolean seedExported = userRepository.findByUserDid(userDid)
                .map(u -> u.getSeedExportedAt() != null)
                .orElse(false);
        OffsetDateTime exportedAt = ...; // optional
        long activeKeys = authorizedKeyRepository.countActiveByUserDid(userDid);
        long guardians = guardianRepository.countActiveByUserDid(userDid);
        return DataResponse.ok(new HealthResponse(seedExported, exportedAt, activeKeys, guardians));
    }
}
```

(Cần thêm Spring Security filter để trích `userDid` từ session JWT — Phase D đã có `JwtService`, ở đây bind vào `SecurityContext`.)

### E.2 — `POST /keys/rotate`

```java
@PostMapping("/keys/rotate")
public DataResponse<KeyRotationResponse> rotate(@Valid @RequestBody KeyRotateRequest req,
                                                 @AuthenticationPrincipal UserDid userDid) {
    // 1. Verify oldKeySignature trên payload {newPublicKeyHex, nonce}
    AuthorizedKey oldKey = authorizedKeyRepository.findActiveOwnerByUserDid(userDid);
    signatureService.verifyEcdsa(oldKey.publicKeyHex(), payload, req.oldKeySignature());

    // 2. Consume nonce
    nonceService.validateAndConsume(req.nonce(), userDid, Duration.ofMinutes(5));

    // 3. Build + submit Cardano UpdateDID tx
    //    MVP: gửi tx với fee wallet ký, KHÔNG dùng required signer của old key
    //    Phase H upgrade: yêu cầu mobile cung cấp signed CBOR partial từ old key
    String prevTxHash = extractTxHashFromDid(userDid);
    TxResult tx = cardanoService.updateDID(req.newPublicKeyHex(), prevTxHash, null);

    // 4. Cập nhật DB: revoke key cũ, insert key mới
    oldKey.setStatus("revoked");
    AuthorizedKey newKey = AuthorizedKey.builder()...build();
    authorizedKeyRepository.save(newKey);

    activityLogService.log(userDid, ACTION_KEY_ROTATED, Map.of("tx_hash", tx.txHash()));
    return DataResponse.ok(new KeyRotationResponse(tx.txHash()));
}
```

### E.3 — `POST /seed/export-request`

Tạo sign-request type `SEED_EXPORT` → mobile approve → server ghi `users.seed_exported_at = NOW()` + activity log `seed_phrase_exported`.

### E.4 — `GET /api/v1/activity-logs`

```java
@GetMapping("/api/v1/activity-logs")
public DataResponse<ActivityLogPage> list(
        @AuthenticationPrincipal UserDid userDid,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String cursor,        // last log ID
        @RequestParam(required = false) String filter,        // action_type
        @RequestParam(defaultValue = "all") String range)     // 7d | 30d | all
{
    // Build dynamic query với cursor (id < cursor) ORDER BY created_at DESC
    // Filter: action = filter
    // Range: created_at >= NOW() - INTERVAL ...
}
```

Repository query:

```java
@Query("""
    SELECT a FROM ActivityLog a
    WHERE a.userId = :userId
    AND (:filter IS NULL OR a.action = :filter)
    AND (:since IS NULL OR a.createdAt >= :since)
    AND (:cursor IS NULL OR a.id < :cursor)
    ORDER BY a.createdAt DESC
    """)
Page<ActivityLog> queryPage(...);
```

### E.5 — `GET /tx/estimate?type=...`

MVP: hard-coded map `{key_rotation: 12, seed_export: 0, ...}`. Phase H thay bằng BloxBean fee calculator real-time.

### E.6 — `GET /api/v1/identity/nodes?did=...`

MVP stub: return mock 12 nodes (giống `web/src/components/landing/visuals/NetworkPattern.tsx` data).
**Cần Long quyết:** data source thật là gì? LampNet API? Hay query metadata trên Cardano? Ghi chú đợi clarification.

### E.7 — `POST /support/session/init`

MVP stub: return `{session_id: UUIDv7, proofchat_url: "https://proofchat.aladin.work/session/" + sid}`. Phase H integrate ProofChat SDK.

### Done criteria Phase E

- [ ] Tất cả 7 endpoint trả 200 với data hợp lệ trên Postman
- [ ] Web `/dashboard`, `/activity`, `/seed-phrase`, `/key-rotation` không còn 404 — error state chỉ xuất hiện khi data bị thiếu logic chứ không phải endpoint missing

---

## Phase F — Web client integration + E2E (~1 ngày)

### F.1 — Update Phoenixkey-Interface `.env.local`

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_DOMAIN=phoenixkey.me
NEXT_PUBLIC_CARDANO_EXPLORER=https://preprod.cardanoscan.io
```

### F.2 — Verify CORS preflight

`curl -I -X OPTIONS http://localhost:8080/api/v1/auth/session/init -H "Origin: http://localhost:3000"` → expect 200 + `Access-Control-Allow-Origin: http://localhost:3000`.

### F.3 — Sửa Phoenixkey-Interface `src/lib/api.ts` (nếu cần)

Hiện tại đã có `apiFetch` + `ResilientSSE`. Confirm 1 lần nữa response shape của `DataResponse<T>` của Spring:

```json
{ "code": 1000, "message": "...", "result": { ... } }
```

- Web client cần unwrap `.result` — kiểm tra `src/lib/api.ts` xử lý đúng
- Nếu chưa: thêm middleware unwrap

### F.4 — Test E2E manual

Trên web:

1. Open `http://localhost:3000/login` → thấy QR
2. Decode QR JSON → manual `POST /auth/session/{id}/approve` từ Postman với mock signature
3. Web nhận SSE → vào dashboard
4. Dashboard fetch `/identity/health` → render banner state đúng

### Done criteria Phase F

- [ ] Web `/login` flow hoạt động full khi backend chạy (không cần mobile thật)
- [ ] Dashboard load không 404 nào, chỉ có inline error state nếu data thiếu

---

## Phase G — Deployment + docs (~1 ngày)

### G.1 — `docker-compose.yml`

```yaml
services:
  phoenixkey-server:
    build: .
    ports:
      - "8080:8080"
    env_file: .env
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: phoenixkey
      POSTGRES_USER: phoenixkey
      POSTGRES_PASSWORD: phoenixkey_dev_password
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck: ...
  redis:
    image: redis:7-alpine
    healthcheck: ...
volumes: { pgdata: {} }
```

(Bỏ Vault service nếu có.)

### G.2 — `.env.example`

```env
# Database
DB_HOST=postgres
DB_USERNAME=phoenixkey
DB_PASSWORD=phoenixkey_dev_password

# Redis
REDIS_HOST=redis

# Cardano
CARDANO_NETWORK=preprod
BLOCKFROST_API_KEY=preprodXXXXXXXXXXXXXXXXXXXXXXXX

# Vault — KMS chung (fee-wallet/JWT/FCM/APNs)
# Dev: local Vault dev mode trong docker-compose
# Prod: trỏ HCP Vault Cluster
VAULT_ENABLED=true
VAULT_ADDR=http://vault:8200
VAULT_TOKEN=phoenixkey-dev-token
VAULT_NAMESPACE=
# Vault paths chuẩn hoá: secret/phoenixkey/{fee-wallet,jwt,fcm,apns,blockfrost}/...

# Fallback dev-only (chỉ dùng khi VAULT_ENABLED=false):
# FEE_WALLET_MNEMONIC=word1 word2 ... word24
# PHOENIXKEY_JWT_SECRET=base64-encoded-32-byte-random

# JWT
PHOENIXKEY_JWT_SECRET=base64-encoded-32-byte-random
```

### G.3 — `README.md`

Rewrite hoàn toàn — tài liệu cũ mô tả "PK_DB là cache" không còn đúng. Section mới:

- Vai trò mới: **PhoenixKey Server** — backend duy nhất
- Cấu trúc folder
- API endpoints (link tới Swagger UI)
- Cardano integration notes
- Deployment guide

### G.4 — `API.md` rewrite

Bảng đầy đủ 23 endpoint nhóm theo concern (Session/Sign/Identity/Key/Guardian/Activity/Internal).

### G.5 — Postman collection

Update `docs/PhoenixKey.postman_collection.json` — thay OTP requests bằng session/sign-request, thêm Cardano endpoints.

### Done criteria Phase G

- [ ] `docker compose up` từ máy sạch chạy được
- [ ] README/API.md mô tả khớp 100% code thực tế
- [ ] Postman collection mới import → chạy được toàn bộ flow

---

## Out of scope (sau MVP)

Không làm trong scope này, ghi chú để Long khỏi quên:

- **Mobile-signed Cardano UpdateDID tx**: Phase E.2 đang để fee wallet ký full tx (warning mode). Đúng spec là old controller key (mobile) phải sign như required signer — cần Tùng tích hợp BloxBean Kotlin (hoặc cardano-multiplatform-lib) trên mobile để build partial signed tx.
- **ProofChat embed cho Get LAMP**: spec §15.8. Cần ProofChat SDK Java/Kotlin.
- **Real `/api/v1/identity/nodes` data source**: đợi Long quyết (xem Decisions deferred).
- **User_Secret derivation cho Recovery Blob**: spec §8.1 dùng JWT_sub OAuth. Flow mới không có OAuth → cần thiết kế lại.
- **Multi-instance SSE**: hiện in-memory map. Production cần Redis Pub/Sub hoặc broker.
- **Indexer Worker**: repo này có endpoint `POST /internal/sync-taad` — Long cần spawn 1 worker process riêng (Java hoặc Node) để watch Cardano và call endpoint. Out of scope cho repo Server.

---

## Quyết định đã chốt với Long (2026-04-25)

1. **DID method**: ✅ `did:cardano:<network>:<txHash>` — confirmed.
2. **Format chữ ký**: ✅ BouncyCastle ECDSA SECP256K1 với chữ ký **DER-encoded** — confirmed.
3. **`PUT /identity/did`**: ✅ **XOÁ** — register sẽ là 1-step thuần (mint DID + insert user trong cùng transaction). Không có code mobile nào đang hook endpoint này (Long bảo "mobile đã có người làm, không cần quá quan tâm"). Compat layer không cần.
4. **`GET /api/v1/identity/nodes`**: ⚠ **MVP stub** trả 12 node mock theo spec dev note (data giống `web/src/components/landing/visuals/NetworkPattern.tsx`). Long chưa quyết data source thật → ghi vào "Decisions deferred" để clarify khi LampNet API rõ ràng.
5. **FCM/APNs push**: ✅ **Full integrate trong MVP** — chuyển từ Phase H lên Phase D (xem mục Phase D bên dưới đã update).

## Decisions deferred (cần Long làm rõ trước khi block production rollout)

- **`GET /api/v1/identity/nodes` data source**: LampNet có public API hay phải hardcode danh sách node? Endpoint này là cosmetic cho dashboard, không block luồng login/sign — có thể trì hoãn đến sau MVP.
- **Mobile-signed UpdateDID tx**: Phase E.2 hiện để fee wallet ký full tx (warning mode). Spec yêu cầu old controller key (mobile) sign như required signer. Cần Tùng tích hợp BloxBean Kotlin trên mobile khi sẵn sàng.

## Secret management — HashiCorp Vault làm KMS chung

> **Quyết định (2026-04-25):** Long đang implement Vault → tận dụng làm KMS cho mọi secret high-value. Không tách file/env nữa, dùng Vault thống nhất từ MVP đến prod. Migrate giữa local Vault dev mode → HCP Vault sau chỉ cần đổi `VAULT_ADDR`.

### Vault layout

```
secret/phoenixkey/
├── fee-wallet/mnemonic    { "words": ["word1", ..., "word24"] }
├── jwt/secret             { "key": "<base64-32-byte>" }
├── fcm/service-account    { "json": "<full Firebase service account JSON>" }
├── apns/auth-key          { "key": "<.p8 content>", "keyId": "ABCD1234", "teamId": "XYZA9876" }
└── blockfrost/api-key     { "key": "preprodXXX..." }    ← optional (low-value, có thể giữ env)
```

(KHÔNG có pepper nữa — đã bỏ với PII.)

### `VaultSecretService` — generic getter

Refactor từ `PepperVaultService` (xem A.1b). Giữ pattern RestTemplate hiện tại, không add `spring-cloud-vault-config` để tránh conflict với code Long đang implement.

```java
@Service
public class VaultSecretService {
    public String[] getFeeWalletMnemonic();
    public byte[] getJwtSecret();
    public String getFcmServiceAccount();
    public ApnsCredentials getApnsCredentials();
    public Map<String, Object> readSecret(String kvPath);   // generic
}
```

Caching strategy: load 1 lần khi service init (`@PostConstruct`), keep in-memory với `char[]` cho mnemonic + `byte[]` cho key (zero-out khi shutdown). Không cache Vault token, mỗi request đọc lại token từ config (cho phép Vault Agent auto-rotate).

### Threat model

Fee wallet mnemonic mất = attacker drain ADA của fee wallet. **KHÔNG** kiểm soát được key user (Hardware Key vẫn ở Secure Enclave mobile). Blast radius giới hạn ở số dư fee wallet.
JWT secret mất = attacker mint session token giả → đăng nhập web với danh tính bất kỳ user nào (rất nghiêm trọng — phải rotate ngay nếu lộ).

### Local dev fallback

Nếu Long muốn dev offline (không Vault): set `spring.cloud.vault.enabled=false`. `VaultSecretService` sẽ:
1. Log WARN: `"Vault disabled — reading secrets from env vars (DEV ONLY)"`
2. Đọc fee wallet từ env `FEE_WALLET_MNEMONIC` (space-separated 24 từ)
3. Đọc JWT từ env `PHOENIXKEY_JWT_SECRET` (base64)
4. FCM/APNs nếu enabled → throw fail-fast (không được fallback secret quan trọng quá xa)

Mặc định Vault enabled → CI/staging/prod đều bắt buộc dùng Vault.

### Local Vault dev mode (docker-compose)

Thêm vào `docker-compose.yml`:

```yaml
services:
  vault:
    image: hashicorp/vault:1.17
    cap_add: [IPC_LOCK]
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: phoenixkey-dev-token
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    ports: ["8200:8200"]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8200/v1/sys/health"]
      interval: 10s
```

Init script `infrastructure/vault/seed-dev.sh` chạy sau khi Vault start để populate test secrets:

```bash
#!/bin/sh
export VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=phoenixkey-dev-token

vault kv put secret/phoenixkey/fee-wallet/mnemonic words='["word1","word2",...,"word24"]'
vault kv put secret/phoenixkey/jwt/secret key="$(openssl rand -base64 32)"
# FCM + APNs khi đã có
```

### Migrate sang HCP Vault (production)

Đổi 3 env var, không đổi code:

```env
VAULT_ADDR=https://xxxxx.cluster.hashicorp.cloud:8200
VAULT_TOKEN=hvs.XXXXXXXXXXXXXXXXXXXX
VAULT_NAMESPACE=admin
```

Re-seed secrets vào HCP Vault qua CLI giống dev mode. App restart, không phải đổi gì.

### Operational hygiene cho fee wallet

- **Fee wallet riêng cho từng environment** (dev/staging/prod), KHÔNG share
- **Số dư thấp**: chỉ giữ ~50-100 ADA, top-up tự động từ cold wallet (offline) mỗi tuần qua script manual
- **Monitor**: alert nếu balance < 10 ADA HOẶC tx anomaly (vd > 100 tx/h là dấu hiệu bất thường)
- **Rotate mỗi quý**: tạo wallet mới, drain ADA cũ về cold storage, `vault kv put secret/phoenixkey/fee-wallet/mnemonic` với mnemonic mới, restart server. Vault KV v2 versioning giữ lại bản cũ để rollback nếu cần. Runbook tại `docs/RUNBOOK-fee-wallet.md` (Phase G.4).

### Done criteria cho secret management (gộp vào Phase A)

- [ ] `VaultSecretService` refactored từ `PepperVaultService` với 5 getter methods
- [ ] `FeeWalletService.@PostConstruct` gọi Vault, fail-fast nếu unreachable hoặc path missing
- [ ] `docker-compose.yml` thêm Vault dev service + seed script
- [ ] `infrastructure/vault/seed-dev.sh` populate test secrets cho local dev
- [ ] Env `vault.enabled=false` mode đọc từ env có log WARN rõ ràng
- [ ] README section "Secret setup" hướng dẫn: (a) start Vault dev mode, (b) seed secrets, (c) migrate HCP cho prod

---

## Recovery strategy — quyết định MVP

Vấn đề: flow mới bỏ email/phone/OAuth → không có **JWT_sub** để derive `User_Secret` cho Recovery Blob KEK. Device-only KEK không khôi phục được trên thiết bị mới (mỗi biometric → KEK khác). Phải có cơ chế recovery khác.

Spec đã có **2 cơ chế độc lập với email/phone**, ghép cả 2 dùng dần:

### MVP — Seed Phrase backup (spec §9) ✅ trong scope

- Mobile-side hoàn toàn. Khi user opt-in setup ban đầu, mobile show 24 từ BIP-39 với 2-layer warning + tự ẩn 30s. User chép ra giấy/password manager.
- Khi mất thiết bị → cài app mới → nhập 24 từ → derive lại Hardware Key → reconnect DID đã có on-chain.
- **Server không làm gì thêm** ngoài việc đã track:
  - `users.seed_exported_at TIMESTAMPTZ` (migration V8)
  - `activity_logs` action `seed_phrase_exported` (đã có)
  - Endpoint `POST /seed/export-request` (Phase E.3) — relay sign-request type `SEED_EXPORT` để mobile xác nhận user thật mới được show 24 từ.
- Caveat truyền thống của crypto wallet: mất giấy = mất tài khoản. User được cảnh báo rõ.

### Phase H+ — Guardian Social Recovery (spec §5.4 + §11) ⏸ post-MVP

- Đã có infrastructure trong DB (`guardians` table, `recovery_approvals` table planned v2.0 trong PK-Database README mục 5.5).
- Flow on-chain: user mới cài app → tạo key mới → gửi `UpdateController` TAAD tx lên Cardano → đủ threshold guardian sign → smart contract chấp nhận key mới.
- **Cần thêm để bật flow này (chưa làm trong MVP):**
  - TAAD smart contract Aiken đã deploy trên Cardano (trong `PhoenixKey-Backend/PoCs/Core/` đang có draft, chưa production-ready)
  - Mobile UI cho guardian approval flow
  - Endpoint `POST /recovery/init` + `POST /recovery/approve` (mới, Phase H)
  - Migration V9 cho `recovery_approvals` table

### Recovery Blob + LampNet (Mirage/Carpet) ❌ ngoài scope dài hạn

Spec §5.3, §15.7 mô tả một cơ chế **bonus tự động hơn** Seed Phrase: encrypted blob được phân tán qua mạng LampNet, user khôi phục không cần nhập 24 từ — chỉ cần biometric trên thiết bị mới + một số định danh phụ.

Nhưng cơ chế này cần:
- LampNet network production thật (chưa sẵn sàng)
- `User_Secret` derivation chéo thiết bị (vấn đề chính khi bỏ OAuth) → cần thiết kế lại
- Mobile SDK có Mirage encode/decode + Carpet transport

→ Bỏ hoàn toàn ngoài scope. Khi LampNet và mobile SDK sẵn sàng, design lại từ đầu.

---

## Tổng thời gian estimate

| Phase    | Mô tả                                      | Thời gian        |
| -------- | ------------------------------------------ | ---------------- |
| A        | Cleanup                                    | 1.5 ngày         |
| B        | Cardano integration (port từ NestJS)       | 3 ngày           |
| C        | Refactor IdentityService                   | 0.5 ngày         |
| D        | Session + Sign request **+ FCM/APNs push** | 4 ngày (+1d FCM) |
| E        | Misc spec endpoints                        | 1.5 ngày         |
| F        | Web E2E                                    | 1 ngày           |
| G        | Deployment + docs                          | 1 ngày           |
| **Tổng** |                                            | **~12.5 ngày**   |

Với hiệu suất xấp xỉ 6h tập trung/ngày → ~11 ngày calendar (chưa kể block bởi Tùng cho fixture chữ ký DER + Firebase project setup).

---

## Tracking progress

Mỗi phase có ô **Done criteria** cụ thể. Tôi sẽ tick từng item khi xong. Long review sau mỗi phase trước khi tôi đẩy commit (giống flow bên web với PLAN.md).

Cập nhật cuối: 2026-04-25.
