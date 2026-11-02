package com.ashkan.hotel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Currency-safe financial aggregation, tax estimates and provider settlement reconciliation. */
public final class FinancialAnalyticsService {
    private FinancialAnalyticsService() {}
    public record Payment(String id, String providerReference, LocalDate date, BigDecimal amount, String currency, String status, String method) {
        public Payment { required(id); Objects.requireNonNull(date); positive(amount); currency=normalizeCurrency(currency); status=normalizeStatus(status); method=Objects.requireNonNullElse(method,"Unspecified"); }
    }
    public record Refund(String id, String paymentId, LocalDate date, BigDecimal amount, String currency, String status) {
        public Refund { required(id); required(paymentId); Objects.requireNonNull(date); positive(amount); currency=normalizeCurrency(currency); status=Objects.requireNonNullElse(status,"PENDING").toUpperCase(Locale.ROOT); }
    }
    public record Expense(String id, LocalDate date, BigDecimal amount, String currency, String category) {
        public Expense { required(id); Objects.requireNonNull(date); positive(amount); currency=normalizeCurrency(currency); category=Objects.requireNonNullElse(category,"Uncategorized"); }
    }
    public record ProviderSettlement(String reference, BigDecimal amount, String currency) {
        public ProviderSettlement { required(reference); positive(amount); currency=normalizeCurrency(currency); }
    }
    public record CurrencySummary(String currency, BigDecimal captured, BigDecimal refunded, BigDecimal expenses,
                                  BigDecimal netBeforeTax, BigDecimal estimatedTax, BigDecimal netAfterTax,
                                  long capturedCount, long failedCount) {}
    public record DailyTotal(LocalDate date, String currency, BigDecimal captured, BigDecimal refunded, BigDecimal expenses) {}
    public record Reconciliation(String reference, String result, BigDecimal ledgerAmount, BigDecimal providerAmount, String currency) {}
    public record Report(LocalDate from, LocalDate through, List<CurrencySummary> currencies, List<DailyTotal> daily) {
        public Report { currencies=List.copyOf(currencies); daily=List.copyOf(daily); }
    }

    public static Report summarize(List<Payment> payments, List<Refund> refunds, List<Expense> expenses,
                                   LocalDate from, LocalDate through, BigDecimal taxRatePercent) {
        Objects.requireNonNull(payments); Objects.requireNonNull(refunds); Objects.requireNonNull(expenses);
        Objects.requireNonNull(from); Objects.requireNonNull(through); Objects.requireNonNull(taxRatePercent);
        if (through.isBefore(from)) throw new IllegalArgumentException("End date must not precede start date");
        if (taxRatePercent.signum()<0 || taxRatePercent.compareTo(new BigDecimal("100"))>0) throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        Map<String,Mutable> totals=new TreeMap<>(); Map<String,DailyMutable> days=new TreeMap<>();
        for(Payment p:payments) if(inRange(p.date(),from,through)) {
            if(!"CAPTURED".equals(p.status()) && !"FAILED".equals(p.status())) continue;
            Mutable m=totals.computeIfAbsent(p.currency(),k->new Mutable());
            if("CAPTURED".equals(p.status())) {m.captured=m.captured.add(p.amount());m.capturedCount++;}
            if("FAILED".equals(p.status())) m.failedCount++;
            if("CAPTURED".equals(p.status())) days.computeIfAbsent(p.date()+"|"+p.currency(),k->new DailyMutable(p.date(),p.currency())).captured=days.get(p.date()+"|"+p.currency()).captured.add(p.amount());
        }
        for(Refund r:refunds) if(inRange(r.date(),from,through)&&"SUCCEEDED".equals(r.status())) {
            Mutable m=totals.computeIfAbsent(r.currency(),k->new Mutable());m.refunded=m.refunded.add(r.amount());
            DailyMutable d=days.computeIfAbsent(r.date()+"|"+r.currency(),k->new DailyMutable(r.date(),r.currency()));d.refunded=d.refunded.add(r.amount());
        }
        for(Expense e:expenses) if(inRange(e.date(),from,through)) {
            Mutable m=totals.computeIfAbsent(e.currency(),k->new Mutable());m.expenses=m.expenses.add(e.amount());
            DailyMutable d=days.computeIfAbsent(e.date()+"|"+e.currency(),k->new DailyMutable(e.date(),e.currency()));d.expenses=d.expenses.add(e.amount());
        }
        List<CurrencySummary> summaries=new ArrayList<>();
        totals.forEach((currency,m)->{BigDecimal net=m.captured.subtract(m.refunded).subtract(m.expenses);BigDecimal tax=net.max(BigDecimal.ZERO).multiply(taxRatePercent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);summaries.add(new CurrencySummary(currency,m.captured,m.refunded,m.expenses,net,tax,net.subtract(tax),m.capturedCount,m.failedCount));});
        List<DailyTotal> daily=days.values().stream().sorted(Comparator.comparing((DailyMutable d)->d.date).thenComparing(d->d.currency)).map(d->new DailyTotal(d.date,d.currency,d.captured,d.refunded,d.expenses)).toList();
        return new Report(from,through,summaries,daily);
    }

    /** Compares captured ledger transactions with provider settlement rows by exact provider reference. */
    public static List<Reconciliation> reconcile(List<Payment> payments,List<ProviderSettlement> providerRows) {
        Objects.requireNonNull(payments);Objects.requireNonNull(providerRows);
        Map<String,Payment> ledger=new HashMap<>();
        for(Payment p:payments) if("CAPTURED".equals(p.status())) {
            String reference=p.providerReference()==null||p.providerReference().isBlank()?p.id():p.providerReference();
            if(ledger.putIfAbsent(reference,p)!=null) throw new IllegalArgumentException("Duplicate ledger reference: "+reference);
        }
        Map<String,ProviderSettlement> external=new HashMap<>();
        for(ProviderSettlement p:providerRows) if(external.putIfAbsent(p.reference(),p)!=null) throw new IllegalArgumentException("Duplicate provider reference: "+p.reference());
        Set<String> refs=new TreeSet<>();refs.addAll(ledger.keySet());refs.addAll(external.keySet());List<Reconciliation> result=new ArrayList<>();
        for(String ref:refs){Payment l=ledger.get(ref);ProviderSettlement p=external.get(ref);String status;
            if(l==null)status="PROVIDER_ONLY";else if(p==null)status="LEDGER_ONLY";else if(!l.currency().equals(p.currency()))status="CURRENCY_MISMATCH";else if(l.amount().compareTo(p.amount())!=0)status="AMOUNT_MISMATCH";else status="MATCHED";
            result.add(new Reconciliation(ref,status,l==null?null:l.amount(),p==null?null:p.amount(),l!=null?l.currency():p.currency()));}
        return List.copyOf(result);
    }
    public static String toCsv(Report report){StringBuilder s=new StringBuilder("Currency,Captured,Refunded,Expenses,Net before tax,Estimated tax,Net after tax,Captured count,Failed count\r\n");for(CurrencySummary x:report.currencies())s.append(x.currency()).append(',').append(x.captured()).append(',').append(x.refunded()).append(',').append(x.expenses()).append(',').append(x.netBeforeTax()).append(',').append(x.estimatedTax()).append(',').append(x.netAfterTax()).append(',').append(x.capturedCount()).append(',').append(x.failedCount()).append("\r\n");s.append("\r\nDate,Currency,Captured,Refunded,Expenses\r\n");for(DailyTotal x:report.daily())s.append(x.date()).append(',').append(x.currency()).append(',').append(x.captured()).append(',').append(x.refunded()).append(',').append(x.expenses()).append("\r\n");return "\uFEFF"+s;}
    private static boolean inRange(LocalDate d,LocalDate f,LocalDate t){return !d.isBefore(f)&&!d.isAfter(t);}
    private static String normalizeCurrency(String c){String x=Objects.requireNonNullElse(c,"EUR").trim().toUpperCase(Locale.ROOT);if(!x.matches("[A-Z]{3}"))throw new IllegalArgumentException("Invalid ISO currency code");return x;}
    private static String normalizeStatus(String s){String x=Objects.requireNonNullElse(s,"INITIATED").toUpperCase(Locale.ROOT);if(!Set.of("INITIATED","AUTHORIZED","CAPTURED","FAILED","REFUNDED").contains(x))throw new IllegalArgumentException("Invalid payment status");return x;}
    private static void positive(BigDecimal x){if(x==null||x.signum()<=0)throw new IllegalArgumentException("Amount must be positive");}
    private static void required(String x){if(x==null||x.isBlank())throw new IllegalArgumentException("Required value missing");}
    private static final class Mutable{BigDecimal captured=BigDecimal.ZERO,refunded=BigDecimal.ZERO,expenses=BigDecimal.ZERO;long capturedCount,failedCount;}
    private static final class DailyMutable{final LocalDate date;final String currency;BigDecimal captured=BigDecimal.ZERO,refunded=BigDecimal.ZERO,expenses=BigDecimal.ZERO;DailyMutable(LocalDate d,String c){date=d;currency=c;}}
}
