# PhoenixKey Database — API Reference

**Base URL:** `http://localhost:8080/api/v1`

**Swagger UI:** `http://localhost:8080/api/v1/swagger-ui.html`
**OpenAPI JSON:** `http://localhost:8080/api/v1/v3/api-docs`

---

# Auth

POST /api/v1/auth/otp/save ← NestJS gọi sau khi generate OTP
POST /api/v1/auth/otp/verify

# Identity (internal — chỉ Phoenix SDK gọi)

POST /api/v1/identity/register
PUT /api/v1/identity/did ← NestJS gọi sau khi mint DID trên Cardano
GET /api/v1/identity/{did}/pubkey ← OriLife, Aladin gọi vào đây
GET /api/v1/identity/{did}/status

# Keys

POST /api/v1/keys/authorize ← thêm thiết bị mới
POST /api/v1/keys/revoke

# Guardians

POST /api/v1/guardians/add
POST /api/v1/guardians/remove ← thay đổi/thay thế guardian

# Indexer (internal webhook — Cardano worker gọi vào)

POST /api/v1/internal/sync-taad
