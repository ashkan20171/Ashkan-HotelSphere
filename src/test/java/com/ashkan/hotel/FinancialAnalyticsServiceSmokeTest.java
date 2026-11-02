package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinancialAnalyticsServiceSmokeTest {
    public static void main(String[] args) {
        LocalDate d=LocalDate.of(2026,10,10);
        var payments=List.of(
            new FinancialAnalyticsService.Payment("p1","gw-1",d,new BigDecimal("120.00"),"EUR","CAPTURED","CARD"),
            new FinancialAnalyticsService.Payment("p2","gw-2",d,new BigDecimal("30.00"),"EUR","FAILED","CARD"),
            new FinancialAnalyticsService.Payment("p3",null,d,new BigDecimal("500.00"),"USD","AUTHORIZED","CARD"),
            new FinancialAnalyticsService.Payment("p4",null,d.plusDays(1),new BigDecimal("50.00"),"EUR","CAPTURED","CASH"),
            new FinancialAnalyticsService.Payment("p5","gw-3",d,new BigDecimal("80.00"),"EUR","CAPTURED","CARD"));
        var refunds=List.of(new FinancialAnalyticsService.Refund("r1","p1",d,new BigDecimal("20.00"),"EUR","SUCCEEDED"),new FinancialAnalyticsService.Refund("r2","p1",d,new BigDecimal("5.00"),"EUR","PENDING"));
        var expenses=List.of(new FinancialAnalyticsService.Expense("e1",d,new BigDecimal("25.00"),"EUR","Utilities"));
        var report=FinancialAnalyticsService.summarize(payments,refunds,expenses,d,d,new BigDecimal("10"));
        check(report.currencies().size()==1,"currency isolation and date filtering");
        var eur=report.currencies().get(0);
        check(eur.captured().compareTo(new BigDecimal("200.00"))==0,"captured only");
        check(eur.refunded().compareTo(new BigDecimal("20.00"))==0,"succeeded refunds only");
        check(eur.netBeforeTax().compareTo(new BigDecimal("155.00"))==0,"net before tax");
        check(eur.estimatedTax().compareTo(new BigDecimal("15.50"))==0,"estimated tax");
        check(eur.netAfterTax().compareTo(new BigDecimal("139.50"))==0,"net after tax");
        check(eur.failedCount()==1 && eur.capturedCount()==2,"transaction counts");
        var reconciliation=FinancialAnalyticsService.reconcile(payments,List.of(
            new FinancialAnalyticsService.ProviderSettlement("gw-1",new BigDecimal("120.00"),"EUR"),
            new FinancialAnalyticsService.ProviderSettlement("unknown",new BigDecimal("9.00"),"EUR"),
            new FinancialAnalyticsService.ProviderSettlement("gw-3",new BigDecimal("79.00"),"EUR")));
        check(reconciliation.stream().anyMatch(x->x.result().equals("MATCHED")),"matched provider row");
        check(reconciliation.stream().anyMatch(x->x.result().equals("PROVIDER_ONLY")),"provider-only row");
        check(reconciliation.stream().anyMatch(x->x.result().equals("AMOUNT_MISMATCH")),"amount mismatch row");
        check(reconciliation.stream().anyMatch(x->x.result().equals("LEDGER_ONLY")&&x.reference().equals("p4")),"ledger-only row");
        String csv=FinancialAnalyticsService.toCsv(report);check(csv.startsWith("\uFEFFCurrency")&&csv.contains("139.50"),"Excel-friendly CSV");
        expect(()->FinancialAnalyticsService.summarize(payments,refunds,expenses,d,d,new BigDecimal("101")),"tax validation");
        expect(()->FinancialAnalyticsService.reconcile(payments,List.of(new FinancialAnalyticsService.ProviderSettlement("gw-1",BigDecimal.ONE,"EUR"),new FinancialAnalyticsService.ProviderSettlement("gw-1",BigDecimal.ONE,"EUR"))),"duplicate provider reference");
        System.out.println("FinancialAnalyticsServiceSmokeTest: OK");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static void expect(Runnable r,String label){try{r.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){}}
}
