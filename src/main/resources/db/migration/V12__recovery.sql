-- V12: Recovery attempts tracking (wire to Plutus recovery validator)

CREATE TABLE recovery_attempts (
  recovery_id            UUID         PRIMARY KEY,
  user_did               VARCHAR(120) NOT NULL REFERENCES users(user_did),
  initiator_type         VARCHAR(20)  NOT NULL,  -- GUARDIAN | OWNER_CANCEL | FINALIZE
  pending_pubkey_hex     VARCHAR(200),
  pending_controller_pkh VARCHAR(60),
  guardian_signatures    JSONB,                  -- [{guardian_did, sig, pubkey}]
  collateral_address     VARCHAR(150),
  collateral_lovelace    BIGINT,
  deadline_slot          BIGINT,
  status                 VARCHAR(20)  NOT NULL,
  -- INITIATED | CANCELLED | FINALIZED | EXPIRED | FAILED
  init_tx_hash           VARCHAR(64),
  cancel_tx_hash         VARCHAR(64),
  finalize_tx_hash       VARCHAR(64),
  fail_reason            VARCHAR(300),
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  finalized_at           TIMESTAMPTZ
);

CREATE INDEX idx_recovery_user      ON recovery_attempts (user_did, created_at DESC);
CREATE INDEX idx_recovery_status    ON recovery_attempts (status);
CREATE INDEX idx_recovery_deadline  ON recovery_attempts (deadline_slot) WHERE status = 'INITIATED';
