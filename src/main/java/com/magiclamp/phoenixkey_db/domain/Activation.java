package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "activations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activation {

    @Id
    @Column(name = "activation_id")
    private UUID activationId;

    @Column(name = "user_did", nullable = false, length = 120)
    private String userDid;

    @Column(name = "wallet_address", nullable = false, length = 150)
    private String walletAddress;

    @Column(name = "genie_did", length = 120)
    private String genieDid;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ActivationStatus status;

    @Column(name = "amount_vnd", nullable = false)
    private Integer amountVnd;

    @Column(name = "amount_lamp", nullable = false)
    private Long amountLamp;

    @Column(name = "amount_lovelace", nullable = false)
    private Long amountLovelace;

    @Column(name = "payment_qr_url", length = 500)
    private String paymentQrUrl;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "proofchat_session_id", length = 100)
    private String proofchatSessionId;

    @Column(name = "cardano_tx_hash", length = 64, unique = true)
    private String cardanoTxHash;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}
