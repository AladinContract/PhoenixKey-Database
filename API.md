# PhoenixKey-Server — API Reference

**Base URL:** `http://localhost:8080/api/v1`
**Format:** `Content-Type: application/json`
**Response wrapper:** `{ code, message, result? }` — code `1000` = OK, các code khác xem [ErrorCode](src/main/java/com/magiclamp/phoenixkey_db/exception/ErrorCode.java).
**Auth:** `Authorization: Bearer <jwt>` cho các endpoint yêu cầu session.

> Live Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## Token types

| Type            | Lifetime | sub        | Issued by                    | Used for                     |
| --------------- | -------- | ---------- | ---------------------------- | ---------------------------- |
| `temp`          | 5 phút   | session_id | `/auth/session/init`         | SSE stream + status fallback |
| `session`       | 24 giờ   | user_did   | `/auth/session/{id}/approve` | API mutations cho user       |
| `linked-device` | 30 ngày  | user_did   | `/auth/session/{id}/approve` | Web → push thay QR           |

---

## 1. Identity

### POST /identity/register

Mobile genesis flow — sinh DID Document trên Cardano.

**Request:**

```json
{
  "public_key_hex": "02b5b66150...74999e257",
  "key_origin": "SECURE_ENCLAVE",
  "key_role": "owner",
  "added_by_signature": "3044022016...e2d42b"
}
```

- `added_by_signature`: DER ECDSA secp256k1 chữ ký trên `"PHOENIXKEY_GENESIS:" + public_key_hex`

**Response:** `{ user_id, user_did, tx_hash }` — `user_did = did:cardano:<network>:<tx_hash>`

### GET /identity/{did}/pubkey

Trả owner public key của user. Dùng cho backend khác verify chữ ký.

### GET /identity/{did}/status

Trạng thái TAAD (`ACTIVE` | `RECOVERING` | `MIGRATED`) từ cache `onchain_taad_state_cache`.

### GET /identity/{did}/document

W3C DID Document đầy đủ — đọc inline datum từ Cardano qua Blockfrost.

### GET /identity/health

🔒 Bearer `session_token`. Dashboard health snapshot.

**Response:**

```json
{
  "seed_exported": false,
  "exported_at": null,
  "active_key_count": 1,
  "guardian_count": 0
}
```

### GET /identity/nodes

LampNet node map. **MVP stub:** trả 12 mock nodes (SG, JP, US, ...).

---

## 2. Session (QR pairing — spec §6)

### POST /auth/session/init

Web khởi tạo session.

**Response:** `{ session_id, challenge (32B hex), temp_token, expires_at }`

QR payload encode:

```json
{ "v": 1, "sid": "<session_id>", "ch": "<challenge>", "dom": "phoenixkey.me", "exp": <expires_at> }
```

### GET /auth/session/{id}/stream

🔒 Bearer `temp_token`. SSE stream — emit event `approved` khi mobile xác nhận.
Heartbeat: SSE comment `:ping` mỗi 30s (chống proxy timeout).

### GET /auth/session/{id}/status

🔒 Bearer `temp_token`. Fallback poll sau khi SSE reconnect.

**Response:** `{ session_id, status: "pending"|"approved"|"rejected"|"expired", session_token?, linked_device_token?, user_did? }`

### POST /auth/session/{id}/approve

Mobile gọi sau khi quét QR + biometric + ký challenge.

**Request:**

```json
{
  "user_did": "did:cardano:preprod:...",
  "public_key_hex": "02...",
  "signature": "3044...",
  "domain": "phoenixkey.me",
  "timestamp": 1714201200
}
```

- `signature`: DER trên `challenge + ":" + domain + ":" + timestamp`
- `timestamp` skew tolerance: ±60s

**Response:** `{ status: "approved", linked_device_token }`. SSE event `approved` emit về web.

### POST /auth/session/push

Web → backend → mobile push (linked device flow).

**Request:** `{ session_id, linked_device_token }`

---

## 3. Sign Request (spec §7)

### POST /sign/request

🔒 Bearer `session_token`. Web tạo sign request.

**Request:**

```json
{
  "session_id": "<current web sid>",
  "intent": {
    "type": "TRANSFER",
    "body": { "amount": "100 LAMP", "to": "addr1q..." },
    "domain": "phoenixkey.me",
    "app_id": "phoenixkey-web-v1",
    "nonce": "<32B hex>",
    "timestamp": 1714201200,
    "display_text": "Chuyển 100 LAMP đến addr1q..."
  }
}
```

**Response:** `{ request_id, expires_at }`

### GET /sign/request/{id}

Mobile fetch chi tiết sau khi nhận push (push payload chỉ chứa `request_id`).

### POST /sign/{id}/approve

Mobile ký canonical intent JSON (keys sorted) → server verify → emit SSE `signed` về web.

**Request:** `{ public_key_hex, signature }`

### POST /sign/{id}/cancel

🔒 Bearer `session_token`. Web huỷ request đang chờ.

---

## 4. Keys (spec §11)

### POST /keys/authorize

Thêm device/key mới. Yêu cầu chữ ký từ root key.

**Request:** `{ user_did, public_key_hex, key_origin, key_role, nonce, added_by_signature }`

### POST /keys/revoke

Soft revoke (status → `revoked`). Re-authorize được.

### POST /keys/rotate

Cardano updateDID + DB swap. Old key ký `"PHOENIXKEY_ROTATE:" + newPubkey + ":" + nonce`.

**Request:** `{ user_did, new_public_key_hex, key_origin, nonce, old_key_signature }`

**Response:** `{ tx_hash, new_key_id }`

⚠ **MVP caveat**: fee wallet ký full Cardano tx (vi phạm Zero-Trust spec §11). Phase H sẽ yêu cầu mobile cung cấp partial-signed CBOR.

---

## 5. Guardian (spec §5.4)

### POST /guardians/add

Thêm guardian (cần đạt threshold ≥ 3 cho Social Recovery).

**Request:** `{ user_did, guardian_did, nonce, proof_signature }`

### POST /guardians/remove

Soft revoke guardian.

---

## 6. Misc

### POST /seed/export-request

🔒 Bearer `session_token`. Trigger Seed Phrase export flow.

**Request:** `{ session_id, display_text? }`

Server tạo SignRequest type `SEED_EXPORT`. Khi mobile approve → set `users.seed_exported_at = NOW()` (spec §9.5 dashboard cảnh báo).

### GET /activity-logs

🔒 Bearer `session_token`. Cursor pagination.

**Query params:**

- `limit` (1-100, default 20)
- `cursor`: UUID id của item cuối trang trước
- `filter`: action name (vd `key_rotated`)
- `range`: `7d` | `30d` | `all` (default `all`)

**Response:** `{ logs: [...], next_cursor? }`. Zero-PII: user_id truncate 8 char, ip_hash mask.

### GET /tx/estimate?type=...

Cardano fee estimate. **MVP hardcode**: 12 MAGIC cho key_rotation/createDID/updateDID, 0 cho seed_export, 8 cho guardian_add/remove.

### POST /devices/register

🔒 Bearer `session_token`. Mobile lưu push token sau khi login.

**Request:** `{ platform: "ios"|"android", fcm_token?, apns_token? }` — cần ít nhất 1 token.

### POST /support/session/init

Get LAMP support session. **MVP stub:** trả URL placeholder ProofChat.

---

## 7. Internal

### POST /internal/sync-taad

Indexer Worker process riêng (out of scope) gọi để cập nhật `onchain_taad_state_cache`.

**Request:** `{ user_did, current_controller_pkh, sequence, status, recovery_deadline?, last_synced_block, block_hash }`

---

## 8. Health

### GET /actuator/health

Spring Boot health: DB + Redis status.

---

## Error codes

| Code | HTTP | Meaning                     |
| ---- | ---- | --------------------------- |
| 1000 | 200  | OK                          |
| 1301 | 404  | Session not found / expired |
| 1302 | 410  | Session expired             |
| 1303 | 409  | Session already approved    |
| 1401 | 404  | Sign request not found      |
| 1402 | 410  | Sign request expired        |
| 1403 | 403  | Signature invalid           |
| 2001 | 404  | User not found              |
| 2002 | 404  | User DID not found          |
| 2003 | 409  | DID already registered      |
| 3001 | 409  | Key already authorized      |
| 3002 | 404  | Key not found               |
| 3003 | 403  | Key signature invalid       |
| 3004 | 409  | Key already revoked         |
| 3005 | 400  | Key status invalid          |
| 3006 | 409  | Nonce already used          |
| 4001 | 404  | Guardian not found          |
| 4002 | 409  | Guardian already exists     |
| 4003 | 403  | Guardian signature invalid  |
| 4004 | 400  | Insufficient guardians (<3) |
| 4005 | 409  | Guardian already revoked    |
| 5001 | 404  | TAAD state not found        |
| 5002 | 409  | TAAD stale (block low)      |
| 5003 | 409  | Reorg detected              |
| 5004 | 403  | Account in recovery mode    |
| 5005 | 400  | Sequence mismatch           |
| 5101 | 502  | Cardano transaction failed  |
| 5102 | 502  | Cardano resolve failed      |
| 9999 | 500  | Internal server error       |

---

## Tooling

### Sinh keypair + ký test message

```bash
./tools/keygen genesis                      # Hardware Key + GENESIS sig + curl /register
./tools/keygen rotate <oldPriv> <newPub>    # ROTATE sig + curl /keys/rotate
./tools/keygen wallet                       # Fee wallet mnemonic + Cardano address
```

### Postman

Import: [docs/PhoenixKey.postman_collection.json](./docs/PhoenixKey.postman_collection.json)
