package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DynamicPricingEngineSmokeTest {
    public static void main(String[] args) {
        DynamicPricingEngine engine = new DynamicPricingEngine();
        var rules = new DynamicPricingEngine.Rules(new BigDecimal("100.00"), new BigDecimal("1.20"), new BigDecimal("1.30"), "EUR", 3);
        var quote = engine.quote(rules, LocalDate.parse("2026-10-30"), LocalDate.parse("2026-11-02"), 2, new BigDecimal("0.85"));
        if (quote.numberOfNights() != 3) throw new AssertionError("Expected three nights");
        if (quote.total().compareTo(new BigDecimal("442.00")) != 0) throw new AssertionError("Unexpected dynamic total: " + quote.total());
        if (quote.nights().get(0).finalRate().compareTo(new BigDecimal("156.00")) != 0) throw new AssertionError("Friday demand/weekend rate mismatch");
        if (quote.nights().get(1).finalRate().compareTo(new BigDecimal("156.00")) != 0) throw new AssertionError("Saturday demand/weekend rate mismatch");
        if (quote.nights().get(2).finalRate().compareTo(new BigDecimal("130.00")) != 0) throw new AssertionError("Sunday demand rate mismatch");
        expectFailure(() -> engine.quote(rules, LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-02"), 1, BigDecimal.ZERO));
        expectFailure(() -> engine.quote(rules, LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"), 4, BigDecimal.ZERO));
        expectFailure(() -> engine.quote(rules, LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"), 1, new BigDecimal("1.2")));
        System.out.println("DynamicPricingEngineSmokeTest: PASS");
    }
    private static void expectFailure(Runnable r) { try { r.run(); throw new AssertionError("Expected validation failure"); } catch (IllegalArgumentException expected) { } }
}
