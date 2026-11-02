package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;

public final class SpaServiceSmokeTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spa-test");Path file=dir.resolve("spa.tsv");Clock clock=Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"),ZoneId.of("UTC"));
        SpaService spa=new SpaService(file,clock);spa.saveFacility(new SpaService.Facility("POOL","Swimming pool",8,"Indoor pool"));spa.saveFacility(new SpaService.Facility("ROOM1","Massage room",1,"Massage suite"));spa.saveOffering(new SpaService.Offering("MASS","Massage","Spa",60,new BigDecimal("45"),true));spa.saveOffering(new SpaService.Offering("SWIM","Pool access","Leisure",90,new BigDecimal("12.50"),true));
        LocalDateTime start=LocalDateTime.of(2026,10,31,10,0);SpaService.Appointment a=spa.book("ROOM1","MASS","Guest A","Therapist A",start,1,new BigDecimal("5"),"Test");
        check(spa.total(a).compareTo(new BigDecimal("50.00"))==0,"total calculation");
        expect(IllegalStateException.class,()->spa.book("ROOM1","MASS","Guest B","Therapist B",start.plusMinutes(30),1,BigDecimal.ZERO,"Overlap facility"));
        expect(IllegalStateException.class,()->spa.book("POOL","SWIM","Guest B","Therapist A",start.plusMinutes(30),1,BigDecimal.ZERO,"Overlap staff"));
        expect(IllegalArgumentException.class,()->spa.book("POOL","SWIM","Group","",start,9,BigDecimal.ZERO,"Capacity"));
        SpaService.Appointment pool=spa.book("POOL","SWIM","Guest C","",start,2,BigDecimal.ZERO,"");check(spa.total(pool).compareTo(new BigDecimal("25.00"))==0,"participant pricing");
        spa.updateStatus(a.id(),SpaService.Status.CANCELLED);SpaService.Appointment replacement=spa.book("ROOM1","MASS","Guest D","Therapist A",start,1,BigDecimal.ZERO,"");check(replacement.id()>pool.id(),"cancelled slot released");
        SpaService reloaded=new SpaService(file,clock);check(reloaded.appointments().size()==3,"persistence");check(reloaded.facilities().size()==2&&reloaded.offerings().size()==2,"catalog persistence");check(reloaded.csv().contains("Guest D"),"CSV output");
        expect(IllegalArgumentException.class,()->reloaded.book("UNKNOWN","MASS","X","",start,1,BigDecimal.ZERO,""));
        System.out.println("SpaServiceSmokeTest: PASS");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void expect(Class<? extends Throwable> type,Throwing action)throws Exception{try{action.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable e){if(!type.isInstance(e))throw e;}}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
}
