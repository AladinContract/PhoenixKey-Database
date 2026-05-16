package com.magiclamp.phoenixkey_db.domain;

public enum ActivationStatus {
    /** User đã initiate, đang chờ trả tiền */
    PENDING_PAYMENT,
    /** Payment gateway xác nhận đã nhận tiền — chờ Genie ký tx */
    PAYMENT_CONFIRMED,
    /** Tx Cardano đã submit + confirm — balance đã update */
    ACTIVATED,
    /** User hủy trước khi trả tiền */
    CANCELLED,
    /** Hết timeout không trả tiền hoặc Genie không ký được */
    EXPIRED,
    /** Tx Cardano fail hoặc lỗi khác */
    FAILED
}
