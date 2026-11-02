package com.ashkan.hotel;

import java.net.URI;
import java.net.http.*;

public final class FinancialDashboardHttpServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        String secret="stage69-test-secret-with-at-least-32-bytes";
        try (FinancialDashboardHttpService app = new FinancialDashboardHttpService(0,"jdbc:postgresql://127.0.0.1:1/unused","unused","unused",1,secret)) {
            app.start();
            HttpClient client=HttpClient.newHttpClient();
            URI uri=URI.create("http://127.0.0.1:"+app.port()+"/admin/finance");
            HttpResponse<String> missing=client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString());
            check(missing.statusCode()==401,"unauthenticated access denied");
            JwtTokenService tokens=new JwtTokenService(secret,3600);
            HttpResponse<String> receptionist=client.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer "+tokens.issue("staff01","Receptionist")).GET().build(),HttpResponse.BodyHandlers.ofString());
            check(receptionist.statusCode()==403,"non-admin access denied");
            HttpResponse<String> admin=client.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer "+tokens.issue("admin01","Admin")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(!(admin.statusCode()==200&&admin.body().contains("Ashkan HotelSphere")&&admin.body().contains("loadReport")))throw new AssertionError("admin dashboard rendered: "+admin.statusCode()+" "+admin.body().substring(0,Math.min(160,admin.body().length())));
        }
        var date=java.time.LocalDate.of(2026,10,3);
        var report=FinancialAnalyticsService.summarize(
            java.util.List.of(new FinancialAnalyticsService.Payment("pay-eur",null,date,new java.math.BigDecimal("12"),"EUR","CAPTURED","CARD"),new FinancialAnalyticsService.Payment("pay-usd",null,date.plusDays(1),new java.math.BigDecimal("20"),"USD","CAPTURED","CARD")),
            java.util.List.of(),java.util.List.of(),date,date.plusDays(1),java.math.BigDecimal.ZERO);
        var method=FinancialDashboardHttpService.class.getDeclaredMethod("toJson",FinancialAnalyticsService.Report.class,String.class);
        method.setAccessible(true);
        String monthly=(String)method.invoke(null,report,"month");
        check(monthly.contains("2026-10")&&monthly.contains("EUR")&&monthly.contains("USD")&&monthly.contains("12")&&monthly.contains("20"),"monthly aggregation preserves currency separation");
        expect(()->new FinancialDashboardHttpService(0,"jdbc:unused","u","p",1,"short"),"weak JWT secret rejected");
        System.out.println("FinancialDashboardHttpServiceSmokeTest: OK");
    }
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static void expect(Throwing r,String m)throws Exception{try{r.run();throw new AssertionError(m);}catch(IllegalArgumentException expected){}}
    @FunctionalInterface interface Throwing{void run()throws Exception;}
}
