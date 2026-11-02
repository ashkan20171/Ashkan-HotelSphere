package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;

public final class ChannelManagerServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("channel-manager-test");Path file=dir.resolve("channels.tsv");Clock clock=Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"),ZoneId.of("UTC"));
        ChannelManagerService s=new ChannelManagerService(file,clock);
        s.saveChannel(new ChannelManagerService.Channel("OTA1","Example OTA",new BigDecimal("15"),true));
        s.saveChannel(new ChannelManagerService.Channel("DIRECT","Direct website",BigDecimal.ZERO,true));
        s.saveRoomType(new ChannelManagerService.RoomType("DLX","Deluxe",4));
        LocalDate in=LocalDate.of(2026,11,1),out=LocalDate.of(2026,11,3);
        s.saveRate(new ChannelManagerService.Rate("DLX","OTA1",in,out,new BigDecimal("120"),2));
        check(s.available("DLX","OTA1",in)==2,"initial availability");
        ChannelManagerService.ExternalBooking b=s.importBooking("OTA1","REF-001","Guest A","DLX",in,out,2,null);
        check(b.gross().compareTo(new BigDecimal("480.00"))==0,"gross calculation");
        check(s.available("DLX","OTA1",in)==0,"channel allotment");
        expect(IllegalStateException.class,()->s.importBooking("OTA1","REF-002","Guest B","DLX",in,out,1,null));
        expect(IllegalStateException.class,()->s.importBooking("OTA1","REF-001","Guest A","DLX",in,out,1,null));
        check(s.commission().compareTo(new BigDecimal("72.00"))==0,"commission calculation");
        check(s.netRevenue().compareTo(new BigDecimal("408.00"))==0,"net revenue");
        s.updateStatus(b.id(),ChannelManagerService.BookingStatus.CANCELLED);
        check(s.available("DLX","OTA1",in)==2,"cancelled booking releases inventory");
        s.importBooking("OTA1","REF-003","Guest C","DLX",in,out,1,new BigDecimal("130"));
        ChannelManagerService loaded=new ChannelManagerService(file,clock);
        check(loaded.channels().size()==2,"channel persistence");check(loaded.roomTypes().size()==1,"room type persistence");check(loaded.rates().size()==1,"rate persistence");check(loaded.bookings().size()==2,"booking persistence");check(loaded.csv().contains("REF-003"),"CSV export");
        expect(IllegalArgumentException.class,()->new ChannelManagerService.Channel("X","Bad",new BigDecimal("101"),true));
        System.out.println("ChannelManagerServiceSmokeTest: OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void expect(Class<? extends Throwable> type,Throwing action){try{action.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable e){if(!type.isInstance(e))throw new AssertionError("Unexpected exception",e);}}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
}
