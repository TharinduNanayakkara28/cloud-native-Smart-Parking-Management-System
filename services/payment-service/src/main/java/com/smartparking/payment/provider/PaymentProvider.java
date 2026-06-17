package com.smartparking.payment.provider;

import java.math.BigDecimal;

public interface PaymentProvider {
    /**
     * Pre-authorise (hold) an amount. Returns a providerRef for future capture or refund.
     * @throws PaymentProviderException if the provider rejects the request
     */
    String hold(BigDecimal amount, String idempotencyKey);

    /**
     * Capture (settle) a previously held amount, which may differ from the held amount.
     */
    void capture(String providerRef, BigDecimal amount);

    /**
     * Refund a held or charged transaction in full.
     */
    void refund(String providerRef);
}
