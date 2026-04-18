package com.magiclamp.phoenixkey_db.domain;

import java.io.Serializable;

/**
 * [V1.5] Composite Primary Key cho UsedNonce.
 *
 * Composite PK (nonce, user_did) đảm bảo không bao giờ
 * có 2 nonce trùng nhau cho cùng 1 user.
 */
public class UsedNonceId implements Serializable {

    private String nonce;
    private String userDid;

    public UsedNonceId() {}

    public UsedNonceId(String nonce, String userDid) {
        this.nonce = nonce;
        this.userDid = userDid;
    }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getUserDid() { return userDid; }
    public void setUserDid(String userDid) { this.userDid = userDid; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsedNonceId that = (UsedNonceId) o;
        return nonce.equals(that.nonce) && userDid.equals(that.userDid);
    }

    @Override
    public int hashCode() {
        return 31 * nonce.hashCode() + userDid.hashCode();
    }
}