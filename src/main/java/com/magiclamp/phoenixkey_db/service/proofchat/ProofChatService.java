package com.magiclamp.phoenixkey_db.service.proofchat;

public interface ProofChatService {

    /**
     * Open a ProofChat session between a user and a Genie/agent.
     *
     * @param userDid        end-user identity
     * @param agentDid       Genie or support agent identity
     * @param walletAddress  user's wallet — passed to chat context for convenience
     * @param intent         "ACTIVATION_PACKAGE" | "SUPPORT" | etc.
     * @param contextId      activation_id or other reference for the chat context
     * @return session details (sessionId + embed URL with auth JWT)
     */
    ProofChatSession openSession(String userDid,
                                 String agentDid,
                                 String walletAddress,
                                 String intent,
                                 String contextId);

    record ProofChatSession(String sessionId, String embedUrl, long expiresAt) {}
}
