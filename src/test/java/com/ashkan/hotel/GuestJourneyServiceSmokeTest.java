package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class GuestJourneyServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("guest-journey-test");Path file=dir.resolve("folios.tsv");
        Clock clock=Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);
        GuestJourneyService service=new GuestJourneyService(file,clock);
        service.addCharge("B-1","Restaurant","Dinner",new BigDecimal("35.50"));
        service.addCharge("B-1","Laundry","Express service",new BigDecimal("12.00"));
        BigDecimal accommodation=new BigDecimal("200.00");
        check(service.total("B-1",accommodation).compareTo(new BigDecimal("247.50"))==0,"folio total");
        service.settle("B-1","Card",new BigDecimal("100.00"),accommodation);
        check(service.balance("B-1",accommodation).compareTo(new BigDecimal("147.50"))==0,"remaining balance");
        boolean overpay=false;try{service.settle("B-1","Cash",new BigDecimal("150.00"),accommodation);}catch(IllegalArgumentException expected){overpay=true;}
        check(overpay,"reject overpayment");
        GuestJourneyService restored=new GuestJourneyService(file,clock);
        check(restored.charges("B-1").size()==2,"charge persistence");
        check(restored.settlements("B-1").size()==1,"settlement persistence");
        check(restored.csv().contains("Restaurant"),"CSV export");
        boolean invalid=false;try{restored.addCharge("B-1","Other","Invalid",BigDecimal.ZERO);}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"reject zero charge");
        System.out.println("GuestJourneyServiceSmokeTest: PASS");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
