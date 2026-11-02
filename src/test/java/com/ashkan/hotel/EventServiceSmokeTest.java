package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;

public final class EventServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("event-service-test");Path file=dir.resolve("events.tsv");Clock clock=Clock.fixed(Instant.parse("2026-01-01T08:00:00Z"),ZoneOffset.UTC);
        EventService service=new EventService(file,clock);
        service.saveHall(new EventService.Hall("CONF-A","Conference A",100,new BigDecimal("120"),"Projector,Audio"));
        LocalDateTime start=LocalDateTime.of(2026,1,2,10,0),end=start.plusHours(3);
        EventService.Event event=service.reserve("CONF-A","Annual meeting","Ashkan",40,start,end,new BigDecimal("250"),new BigDecimal("50"),"Coffee break");
        check(service.hallCost(event).compareTo(new BigDecimal("360.00"))==0,"hall cost");
        check(service.totalCost(event).compareTo(new BigDecimal("660.00"))==0,"total cost");
        expect(IllegalStateException.class,()->service.reserve("CONF-A","Overlap","Team",10,start.plusHours(1),end.plusHours(1),BigDecimal.ZERO,BigDecimal.ZERO,""));
        expect(IllegalArgumentException.class,()->service.reserve("CONF-A","Too many","Team",101,start.plusDays(1),end.plusDays(1),BigDecimal.ZERO,BigDecimal.ZERO,""));
        EventService loaded=new EventService(file,clock);check(loaded.events().size()==1,"persistence");check(loaded.halls().size()==1,"hall persistence");
        loaded.updateStatus(event.id(),EventService.Status.CANCELLED);
        EventService.Event second=loaded.reserve("CONF-A","Replacement","Team",20,start,end,BigDecimal.ZERO,BigDecimal.ZERO,"");check(second.id()==2,"cancelled reservation releases slot");
        check(loaded.csv().contains("Replacement"),"csv export");
        System.out.println("EventServiceSmokeTest: OK");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void expect(Class<? extends Throwable> type,Throwing action){try{action.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable t){if(!type.isInstance(t))throw new AssertionError(t);}}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
}
