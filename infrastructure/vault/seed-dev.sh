#!/bin/sh
# ============================================================
# Vault auto-seed watchdog cho PhoenixKey-Server (DEV ONLY).
#
# Cách dùng:
#   - Manual: bash infrastructure/vault/seed-dev.sh
#   - Auto: docker-compose có service `vault-seed` chạy script này như daemon
#     (poll mỗi 30s, tự re-seed nếu vault inmem bị wipe sau restart).
#
# ⚠ Vault dev mode = inmem → restart container = mất data. Watchdog auto re-seed.
#   - Fee-wallet mnemonic: idempotent (bỏ qua nếu đã có) → ví giữ nguyên giữa
#     các lần restart, tADA đã fund không mất.
#   - JWT secret: regen mỗi lần re-seed → session/JWT cũ invalidate.
# ============================================================
set -u

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-phoenixkey-dev-token}"
export VAULT_ADDR VAULT_TOKEN

# Run once flag — set bởi cli arg (--once). Default daemon mode loop.
ONCE=false
[ "${1:-}" = "--once" ] && ONCE=true

wait_vault_ready() {
    i=0
    until vault status >/dev/null 2>&1; do
        i=$((i + 1))
        if [ "$i" -gt 60 ]; then
            echo "✗ Vault not reachable after 60s" >&2
            return 1
        fi
        sleep 1
    done
}

is_seeded() {
    vault kv get secret/phoenixkey/fee-wallet/mnemonic >/dev/null 2>&1
}

seed() {
    echo "==> [$(date -u +%FT%TZ)] Seeding secrets to $VAULT_ADDR"

    # ── Fee wallet mnemonic — idempotent (giữ ví đã fund tADA) ──
    # Format: space-separated BIP-39. Vault CLI lưu nguyên dưới dạng STRING.
    # App đọc qua VaultSecretService → split whitespace → String[] cho BloxBean.
    if vault kv get secret/phoenixkey/fee-wallet/mnemonic >/dev/null 2>&1; then
        echo "    fee-wallet/mnemonic: skip (đã có)"
    else
        # DEV: standard test vector 24-word mnemonic — entropy = 0x00...00 (256 bit).
        # Checksum word "art" = SHA-256(zeros)[0:8] mapped to BIP-39 index 102.
        # KHÔNG được dùng cho prod — public test mnemonic, ai cũng spend được.
        # PROD: tạo mnemonic mới, lưu HCP Vault, không commit vào git.
        FEE_WALLET_WORDS="abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        vault kv put secret/phoenixkey/fee-wallet/mnemonic words="$FEE_WALLET_WORDS" >/dev/null
        echo "    fee-wallet/mnemonic: seeded (test vector, 23×abandon + art)"
    fi

    # ── JWT secret — random mỗi lần seed (dev only) ──
    JWT_KEY="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    vault kv put secret/phoenixkey/jwt/secret key="$JWT_KEY" >/dev/null
    echo "    jwt/secret: seeded (random 32-byte)"

    # ── Optional: Blockfrost API key ──
    if [ -n "${BLOCKFROST_API_KEY:-}" ]; then
        vault kv put secret/phoenixkey/blockfrost/api-key key="$BLOCKFROST_API_KEY" >/dev/null
        echo "    blockfrost/api-key: seeded"
    fi

    # ── Optional: FCM service account (Phase D) ──
    if [ -n "${FCM_SERVICE_ACCOUNT_PATH:-}" ] && [ -f "$FCM_SERVICE_ACCOUNT_PATH" ]; then
        vault kv put secret/phoenixkey/fcm/service-account json=@"$FCM_SERVICE_ACCOUNT_PATH" >/dev/null
        echo "    fcm/service-account: seeded from $FCM_SERVICE_ACCOUNT_PATH"
    fi

    # ── Optional: APNs auth key (Phase D) ──
    if [ -n "${APNS_AUTH_KEY_PATH:-}" ] && [ -f "$APNS_AUTH_KEY_PATH" ]; then
        vault kv put secret/phoenixkey/apns/auth-key \
            key=@"$APNS_AUTH_KEY_PATH" \
            keyId="${APNS_KEY_ID:?APNS_KEY_ID required}" \
            teamId="${APNS_TEAM_ID:?APNS_TEAM_ID required}" >/dev/null
        echo "    apns/auth-key: seeded from $APNS_AUTH_KEY_PATH"
    fi

    echo "✓ [$(date -u +%FT%TZ)] Seed complete."
}

# ── Initial seed ──
echo "==> Targeting Vault at $VAULT_ADDR"
wait_vault_ready || exit 1
seed

if [ "$ONCE" = "true" ]; then
    exit 0
fi

# ── Watchdog daemon ──
echo "==> Watchdog mode: poll mỗi 30s, re-seed nếu vault bị wipe (Ctrl+C để dừng)"
while true; do
    sleep 30
    if vault status >/dev/null 2>&1; then
        if ! is_seeded; then
            echo "==> [$(date -u +%FT%TZ)] Vault data missing — re-seeding"
            seed
        fi
    fi
done
