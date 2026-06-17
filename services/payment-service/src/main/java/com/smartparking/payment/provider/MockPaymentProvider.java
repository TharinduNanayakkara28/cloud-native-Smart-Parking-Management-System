package com.smartparking.payment.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Component
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    private final double failureRate;
    private final RandomGenerator rng = RandomGenerator.getDefault();

    public MockPaymentProvider(
            @Value("${payment.mock.failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public String hold(BigDecimal amount, String idempotencyKey) {
        simulateFailure("hold");
        String providerRef = "mock-" + UUID.randomUUID();
        log.info("[MockProvider] HOLD ${} | key={} | ref={}", amount, idempotencyKey, providerRef);
        return providerRef;
    }

    @Override
    public void capture(String providerRef, BigDecimal amount) {
        simulateFailure("capture");
        log.info("[MockProvider] CAPTURE ${} | ref={}", amount, providerRef);
    }

    @Override
    public void refund(String providerRef) {
        simulateFailure("refund");
        log.info("[MockProvider] REFUND | ref={}", providerRef);
    }

    private void simulateFailure(String operation) {
        if (failureRate > 0.0 && rng.nextDouble() < failureRate) {
            throw new PaymentProviderException(
                    "Mock provider simulated failure during " + operation);
        }
    }
}
