package com.ashkan.hotel;

import java.nio.file.*;
import java.time.*;

public final class CrmServiceSmokeTest {
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempDirectory("crm-test").resolve("crm.tsv");Clock clock=Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"),ZoneId.of("UTC"));CrmService c=new CrmService(file,clock);
        c.saveGuest(new CrmService.Guest("G-1","Taylor Guest","t@example.com","123","Prefers quiet room",false));
        c.adjustPoints("G-1",600,"First stay");check(c.points("G-1")==600,"points earned");check(c.tier("G-1")==CrmService.Tier.SILVER,"silver tier");
        expect(IllegalStateException.class,()->c.adjustPoints("G-1",-700,"Invalid redemption"));c.adjustPoints("G-1",-100,"Reward redemption");check(c.points("G-1")==500,"points redeemed");
        CrmService.Feedback f=c.addFeedback("G-1",5,"Excellent stay");check(c.averageRating()==5.0,"average rating");c.updateFeedback(f.id(),CrmService.FeedbackStatus.RESOLVED);
        CrmService loaded=new CrmService(file,clock);check(loaded.guests().size()==1,"guest persistence");check(loaded.points("G-1")==500,"ledger persistence");check(loaded.feedback().get(0).status()==CrmService.FeedbackStatus.RESOLVED,"feedback persistence");check(loaded.csv().contains("PLATINUM")||loaded.csv().contains("SILVER"),"CSV export");
        expect(IllegalArgumentException.class,()->loaded.addFeedback("G-1",6,"Bad rating"));System.out.println("CrmServiceSmokeTest: OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}private static void expect(Class<? extends Throwable> type,Throwing a){try{a.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable e){if(!type.isInstance(e))throw new AssertionError("Unexpected exception",e);}}@FunctionalInterface private interface Throwing{void run()throws Exception;}
}
