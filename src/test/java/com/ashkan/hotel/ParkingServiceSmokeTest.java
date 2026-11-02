package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;

public final class ParkingServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("parking-test");Path file=dir.resolve("parking.tsv");Clock clock=Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"),ZoneId.of("UTC"));
        ParkingService p=new ParkingService(file,clock);
        p.saveSlot(new ParkingService.Slot("A01","A","Covered",true));
        p.saveSlot(new ParkingService.Slot("A02","A","Outdoor",true));
        p.saveDriver(new ParkingService.Driver("D1","Alex","123","Van","B-123",true));
        ParkingService.Visit v=p.checkIn("A01","Guest One","b-123","Car",new BigDecimal("12.50"));
        check(p.slotStatus("A01")==ParkingService.SlotStatus.OCCUPIED,"slot occupied");
        expect(IllegalStateException.class,()->p.checkIn("A01","Guest Two","B-222","Car",BigDecimal.ZERO));
        expect(IllegalStateException.class,()->p.checkIn("A02","Guest Two","b-123","Car",BigDecimal.ZERO));
        p.checkOut(v.id());check(p.slotStatus("A01")==ParkingService.SlotStatus.AVAILABLE,"slot released");
        check(p.parkingRevenue().compareTo(new BigDecimal("12.50"))==0,"parking revenue");
        LocalDateTime at=LocalDateTime.of(2026,10,31,10,0);
        p.bookTransfer("Guest One","Hotel","Airport","D1",at,2,new BigDecimal("30"),"Arrival");
        expect(IllegalStateException.class,()->p.bookTransfer("Guest Two","Hotel","Station","D1",at.plusMinutes(30),1,BigDecimal.ZERO,"Conflict"));
        p.updateTransfer(1,ParkingService.TransferStatus.COMPLETED);
        check(p.transferRevenue().compareTo(new BigDecimal("30.00"))==0,"transfer revenue");
        ParkingService loaded=new ParkingService(file,clock);check(loaded.slots().size()==2,"slot persistence");check(loaded.visits().size()==1,"visit persistence");check(loaded.transfers().size()==1,"transfer persistence");check(loaded.csv().contains("AIRPORT")||loaded.csv().contains("Airport"),"CSV export");
        System.out.println("ParkingServiceSmokeTest: OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void expect(Class<? extends Throwable> type,Throwing action){try{action.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable e){if(!type.isInstance(e))throw new AssertionError("Unexpected exception",e);}}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
}
