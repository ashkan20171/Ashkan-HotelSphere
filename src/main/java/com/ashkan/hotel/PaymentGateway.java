package com.ashkan.hotel;

import java.math.BigDecimal;

/** Provider boundary. Implementations must never receive raw card numbers from this application. */
public interface PaymentGateway {
    record Authorization(String providerReference, String status, boolean simulated) {}
    record Refund(String providerReference, String status, boolean simulated) {}
    Authorization authorize(String paymentId, BigDecimal amount, String currency) throws Exception;
    Refund refund(String paymentId, String providerReference, BigDecimal amount, String currency) throws Exception;
}
