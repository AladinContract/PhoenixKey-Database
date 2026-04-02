# Auth

POST /api/v1/auth/otp/send
POST /api/v1/auth/otp/verify

# Identity (internal — chỉ Phoenix SDK gọi)

POST /api/v1/identity/register
GET /api/v1/identity/{did}/pubkey ← OriLife, Aladin gọi vào đây
GET /api/v1/identity/{did}/status

# Keys

POST /api/v1/keys/authorize ← thêm thiết bị mới
POST /api/v1/keys/revoke

# Guardians

POST /api/v1/guardians/add
POST /api/v1/guardians/approve-recovery

# Indexer (internal webhook — Cardano worker gọi vào)

POST /api/v1/internal/sync-taad
