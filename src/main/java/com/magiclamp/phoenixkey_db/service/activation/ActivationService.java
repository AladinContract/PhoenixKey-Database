package com.magiclamp.phoenixkey_db.service.activation;

import com.magiclamp.phoenixkey_db.dto.activation.ActivationDtos.*;

import java.util.UUID;

public interface ActivationService {

    /**
     * User initiate gói kích hoạt — server match Genie, generate payment QR,
     * mở ProofChat session, trả về cho web hiển thị.
     */
    ActivationInitiateResponse initiate(String userDid, String walletAddress);

    /**
     * Payment gateway webhook hoặc admin manual confirm. Đổi status sang
     * PAYMENT_CONFIRMED, push event tới Genie's app + emit SSE tới web user.
     */
    void confirmPayment(UUID activationId, String paymentReference);

    /**
     * Genie's mobile app submit signed Cardano tx CBOR. Server submit lên
     * Cardano, lưu tx_hash, set status=ACTIVATED khi confirm.
     */
    ActivationSubmitTxResponse submitTx(UUID activationId, String genieDid, String signedTxCbor);

    /**
     * User cancel khi chưa trả tiền.
     */
    void cancel(UUID activationId, String userDid);

    /**
     * Status query (cho polling fallback + SSE init state).
     */
    ActivationStatusResponse getStatus(UUID activationId);
}
