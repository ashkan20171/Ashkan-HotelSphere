package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class RevenueManagementServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("revenue-test");Path file=dir.resolve("rules.tsv");RevenueManagementService service=new RevenueManagementService(file);
        LocalDate from=LocalDate.of(2026,11,2); // Monday
        var stays=List.of(
            new RevenueManagementService.Stay("B1","101","STANDARD",from,from.plusDays(2),"CONFIRMED",new BigDecimal("100")),
            new RevenueManagementService.Stay("B2","102","STANDARD",from.plusDays(1),from.plusDays(2),"CHECKED_IN",new BigDecimal("120")),
            new RevenueManagementService.Stay("B3","103","DELUXE",from,from.plusDays(1),"CANCELLED",new BigDecimal("200")));
        var data=service.metrics(from,from.plusDays(1),4,stays);
        check(data.size()==2,"date range size");check(data.get(0).roomsSold()==1,"cancelled excluded");check(data.get(1).roomsSold()==2,"checkout exclusive");
        check(data.get(0).roomRevenue().compareTo(new BigDecimal("100.00"))==0,"room revenue");check(data.get(0).adr().compareTo(new BigDecimal("100.00"))==0,"ADR");check(data.get(0).revPar().compareTo(new BigDecimal("25.00"))==0,"RevPAR");
        service.saveRule(new RevenueManagementService.RateRule("STANDARD",from,from.plusDays(5),null,new BigDecimal("150"),"High season"));
        service.saveRule(new RevenueManagementService.RateRule("STANDARD",from,from.plusDays(5),DayOfWeek.TUESDAY,new BigDecimal("175"),"Tuesday premium"));
        check(service.rateFor("STANDARD",from,new BigDecimal("100")).compareTo(new BigDecimal("150.00"))==0,"seasonal override");check(service.rateFor("STANDARD",from.plusDays(1),new BigDecimal("100")).compareTo(new BigDecimal("175.00"))==0,"weekday override priority");
        var recs=service.recommendations(List.of(new RevenueManagementService.DailyMetric(from,4,4,100,new BigDecimal("400"),new BigDecimal("100"),new BigDecimal("100"))));check(recs.get(0).action().equals("INCREASE"),"high occupancy recommendation");check(recs.get(0).suggestedRate().compareTo(new BigDecimal("115.00"))==0,"suggested rate");
        RevenueManagementService reloaded=new RevenueManagementService(file);check(reloaded.rules().size()==2,"persistence");check(service.csv(data).contains("RevPAR"),"CSV export");
        boolean invalid=false;try{new RevenueManagementService.RateRule("*",from,from.minusDays(1),null,new BigDecimal("0"),"");}catch(IllegalArgumentException e){invalid=true;}check(invalid,"invalid rule rejected");
        System.out.println("RevenueManagementServiceSmokeTest: OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
