package com.magiclamp.phoenixkey_db.service.push;

/**
 * Push notification facade — interface stays stable while underlying senders
 * (FCM, APNs, stub) are swapped via Spring profiles.
 */
public interface PushService {

    void notifySignRequest(String userDid, String requestId);

    void notifySessionApproval(String userDid, String sessionId);

    void notifySeedExportRequest(String userDid, String requestId);

    /** Notify a Genie that a new activation request was assigned. */
    void notifyActivationRequest(String genieDid, String activationId);

    /** Notify the Genie that the user has paid; time to sign the Cardano tx. */
    void notifyActivationPaymentReceived(String genieDid, String activationId);
}
