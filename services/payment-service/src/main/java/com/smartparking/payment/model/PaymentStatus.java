package com.smartparking.payment.model;

public enum PaymentStatus {
    PENDING,    // record created, provider call not yet made
    HELD,       // pre-authorisation succeeded
    CHARGED,    // actual amount captured after checkout
    REFUNDED,   // full refund issued on cancellation
    FAILED      // provider rejected the pre-auth
}
