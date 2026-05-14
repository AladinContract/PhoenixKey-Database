-- V11: Activation package flow + user wallet address

-- 1. Wallet address + accrual tracking + entity type for users
ALTER TABLE users
  ADD COLUMN wallet_address      VARCHAR(150),
  ADD COLUMN entity_type         VARCHAR(20) NOT NULL DEFAULT 'person',
  ADD COLUMN last_accrual_slot   BIGINT,
  ADD COLUMN genesis_tx_hash     VARCHAR(64),
  ADD COLUMN genesis_slot        BIGINT;

CREATE UNIQUE INDEX idx_users_wallet_address ON users (wallet_address)
  WHERE wallet_address IS NOT NULL;

-- 2. Genie operator registry
CREATE TABLE genies (
  genie_did            VARCHAR(120) PRIMARY KEY REFERENCES users(user_did) ON DELETE CASCADE,
  wallet_address       VARCHAR(150) NOT NULL,
  status               VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | BUSY | OFFLINE
  min_balance_lamp     BIGINT       NOT NULL DEFAULT 1001,
  min_balance_lovelace BIGINT       NOT NULL DEFAULT 10000000,
  current_activations  INT          NOT NULL DEFAULT 0,
  max_concurrent       INT          NOT NULL DEFAULT 3,
  registered_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  last_seen_at         TIMESTAMPTZ
);

CREATE INDEX idx_genies_status_load ON genies (status, current_activations);

-- 3. Activation sessions
CREATE TABLE activations (
  activation_id        UUID         PRIMARY KEY,
  user_did             VARCHAR(120) NOT NULL REFERENCES users(user_did),
  wallet_address       VARCHAR(150) NOT NULL,
  genie_did            VARCHAR(120) REFERENCES users(user_did),
  status               VARCHAR(30)  NOT NULL,
  -- PENDING_PAYMENT | PAYMENT_CONFIRMED | ACTIVATED | CANCELLED | FAILED | EXPIRED
  amount_vnd           INTEGER      NOT NULL DEFAULT 200000,
  amount_lamp          BIGINT       NOT NULL DEFAULT 1001,
  amount_lovelace      BIGINT       NOT NULL DEFAULT 10000000,
  payment_qr_url       VARCHAR(500),
  payment_reference    VARCHAR(100),
  proofchat_session_id VARCHAR(100),
  cardano_tx_hash      VARCHAR(64),
  fail_reason          VARCHAR(300),
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  paid_at              TIMESTAMPTZ,
  activated_at         TIMESTAMPTZ,
  expires_at           TIMESTAMPTZ  NOT NULL,
  UNIQUE (cardano_tx_hash)
);

CREATE INDEX idx_activations_user ON activations (user_did, created_at DESC);
CREATE INDEX idx_activations_status ON activations (status, expires_at);
CREATE INDEX idx_activations_genie  ON activations (genie_did, status);

-- 4. LAMP holding history (for MAGIC emission calc)
CREATE TABLE lamp_history (
  id                BIGSERIAL    PRIMARY KEY,
  user_did          VARCHAR(120) NOT NULL REFERENCES users(user_did),
  block_slot        BIGINT       NOT NULL,
  lamp_balance      BIGINT       NOT NULL,
  recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lamp_history_user_slot ON lamp_history (user_did, block_slot DESC);

-- 5. MAGIC claim ledger (idempotency + audit)
CREATE TABLE magic_claims (
  claim_id          UUID         PRIMARY KEY,
  user_did          VARCHAR(120) NOT NULL REFERENCES users(user_did),
  amount_magic      BIGINT       NOT NULL,
  claimed_slot      BIGINT       NOT NULL,
  cardano_tx_hash   VARCHAR(64)  UNIQUE,
  status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SUBMITTED | CONFIRMED | FAILED
  fail_reason       VARCHAR(300),
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  confirmed_at      TIMESTAMPTZ
);

CREATE INDEX idx_magic_claims_user ON magic_claims (user_did, created_at DESC);
