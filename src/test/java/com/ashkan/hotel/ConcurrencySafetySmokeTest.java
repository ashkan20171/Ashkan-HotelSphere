package com.ashkan.hotel;

import java.time.LocalDate;
import java.math.BigDecimal;

public final class ConcurrencySafetySmokeTest {
    public static void main(String[] args) {
        LocalDate a=LocalDate.parse("2026-11-01"), b=LocalDate.parse("2026-11-05");
        if(!ConcurrencyPolicy.overlaps(a,b,LocalDate.parse("2026-11-04"),LocalDate.parse("2026-11-07"))) throw new AssertionError("Overlapping stays must conflict");
        if(ConcurrencyPolicy.overlaps(a,b,b,LocalDate.parse("2026-11-08"))) throw new AssertionError("Same-day turnover must be allowed");
        if(ConcurrencyPolicy.overlaps(a,b,LocalDate.parse("2026-10-28"),a)) throw new AssertionError("Adjacent past stay must not overlap");
        boolean invalid=false;try{ConcurrencyPolicy.overlaps(a,a,b,b);}catch(IllegalArgumentException expected){invalid=true;}if(!invalid)throw new AssertionError("Invalid date range accepted");
        boolean negative=false;try{new InventoryRepository.Product("p","sku","name","unit",new BigDecimal("-1"),BigDecimal.ZERO);}catch(IllegalArgumentException expected){negative=true;}if(!negative)throw new AssertionError("Negative stock accepted");
        System.out.println("ConcurrencySafetySmokeTest: PASS");
    }
}
