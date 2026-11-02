package com.ashkan.hotel;

import java.nio.file.*;
import java.time.LocalDateTime;

public final class HotelEventBusSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("hotel-event-test-");Path file=dir.resolve("events.tsv");LocalDateTime now=LocalDateTime.of(2026,10,30,10,0);
        HotelEventBus bus=new HotelEventBus(file,2);var event=bus.publish("booking.confirmed","B-100","{\"bookingId\":\"B-100\"}",now);
        if(bus.pendingCount()!=1)throw new AssertionError("pending event not stored");
        HotelEventBus restored=new HotelEventBus(file,2);if(restored.events().size()!=1)throw new AssertionError("journal persistence failed");
        restored.dispatch(e->{throw new IllegalStateException("temporary failure");});
        if(restored.events().get(0).attempts()!=1||restored.pendingCount()!=1)throw new AssertionError("retry state failed");
        restored.dispatch(e->{throw new IllegalStateException("still failing");});
        if(restored.deadLetterCount()!=1)throw new AssertionError("dead letter state failed");
        restored.publish("payment.received","P-1","paid",now);
        restored.dispatch(e->{});
        if(restored.events().stream().noneMatch(e->e.status()==HotelEventBus.Status.ACKNOWLEDGED))throw new AssertionError("acknowledgement failed");
        if(!restored.csv().contains("booking.confirmed"))throw new AssertionError("CSV export failed");
        Files.deleteIfExists(file);Files.deleteIfExists(dir);
        System.out.println("HotelEventBusSmokeTest passed: "+event.id());
    }
}
