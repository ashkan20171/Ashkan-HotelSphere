package com.ashkan.hotel;

import java.math.BigDecimal;
import java.util.UUID;

/** Local-only provider adapter: records a simulated authorization/refund; no money moves. */
public final class DemoPaymentGateway implements PaymentGateway {
    @Override public Authorization authorize(String paymentId, BigDecimal amount, String currency) {
        return new Authorization("demo_auth_" + UUID.randomUUID(), "AUTHORIZED", true);
    }
    @Override public Refund refund(String paymentId, String providerReference, BigDecimal amount, String currency) {
        return new Refund("demo_refund_" + UUID.randomUUID(), "SIMULATED", true);
    }
}
