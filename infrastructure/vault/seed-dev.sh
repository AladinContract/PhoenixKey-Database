#!/bin/sh
# ============================================================
# Seed local Vault dev mode với secrets cho PhoenixKey-Server.
# Chạy SAU KHI `docker compose up -d vault` (1 lần).
#
# Yêu cầu: vault CLI installed trên host (brew install vault / apt install vault)
#          hoặc dùng `docker compose exec vault vault ...`.
#
# Dev token mặc định: phoenixkey-dev-token (từ docker-compose.yml)
# ============================================================
set -eu

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-phoenixkey-dev-token}"

export VAULT_ADDR VAULT_TOKEN

echo "==> Targeting Vault at $VAULT_ADDR"
vault status >/dev/null

# ── Fee wallet mnemonic (24 từ BIP-39) ──────────────────────────
# PRODUCTION: thay bằng mnemonic THẬT từ wallet generator.
# DEV: dùng mnemonic dummy mà ai cũng biết — KHÔNG nạp tADA giá trị vào đây.
FEE_WALLET_WORDS='["abandon","ability","able","about","above","absent","absorb","abstract","absurd","abuse","access","accident","account","accuse","achieve","acid","acoustic","acquire","across","act","action","actor","actress","actual"]'
echo "==> Seeding secret/phoenixkey/fee-wallet/mnemonic"
vault kv put secret/phoenixkey/fee-wallet/mnemonic words="$FEE_WALLET_WORDS"

# ── JWT secret (32 byte base64) ─────────────────────────────────
JWT_KEY="$(openssl rand -base64 32)"
echo "==> Seeding secret/phoenixkey/jwt/secret"
vault kv put secret/phoenixkey/jwt/secret key="$JWT_KEY"

# ── Blockfrost API key (low-value, có thể bỏ) ────────────────────
if [ -n "${BLOCKFROST_API_KEY:-}" ]; then
    echo "==> Seeding secret/phoenixkey/blockfrost/api-key"
    vault kv put secret/phoenixkey/blockfrost/api-key key="$BLOCKFROST_API_KEY"
fi

# ── FCM service account (Phase D) ────────────────────────────────
if [ -n "${FCM_SERVICE_ACCOUNT_PATH:-}" ] && [ -f "$FCM_SERVICE_ACCOUNT_PATH" ]; then
    echo "==> Seeding secret/phoenixkey/fcm/service-account from $FCM_SERVICE_ACCOUNT_PATH"
    vault kv put secret/phoenixkey/fcm/service-account json=@"$FCM_SERVICE_ACCOUNT_PATH"
fi

# ── APNs auth key (Phase D) ──────────────────────────────────────
if [ -n "${APNS_AUTH_KEY_PATH:-}" ] && [ -f "$APNS_AUTH_KEY_PATH" ]; then
    echo "==> Seeding secret/phoenixkey/apns/auth-key from $APNS_AUTH_KEY_PATH"
    vault kv put secret/phoenixkey/apns/auth-key \
        key=@"$APNS_AUTH_KEY_PATH" \
        keyId="${APNS_KEY_ID:?APNS_KEY_ID required}" \
        teamId="${APNS_TEAM_ID:?APNS_TEAM_ID required}"
fi

echo
echo "✓ Seed complete. Verify:"
echo "    vault kv list secret/phoenixkey"
