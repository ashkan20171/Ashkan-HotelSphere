package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Revenue analytics and advisory pricing rules. Recommendations never mutate room rates. */
public final class RevenueManagementService {
    public record Stay(String bookingId,String roomId,String roomType,LocalDate checkIn,LocalDate checkOut,String status,BigDecimal baseRate) {
        public Stay { Objects.requireNonNull(bookingId);Objects.requireNonNull(roomId);Objects.requireNonNull(roomType);Objects.requireNonNull(checkIn);Objects.requireNonNull(checkOut);Objects.requireNonNull(status);Objects.requireNonNull(baseRate);if(!checkOut.isAfter(checkIn)||baseRate.signum()<=0)throw new IllegalArgumentException("Invalid stay"); }
    }
    /** A null weekday applies every day; room type '*' applies to every room type. End date is inclusive. */
    public record RateRule(String roomType,LocalDate from,LocalDate to,DayOfWeek weekday,BigDecimal nightlyRate,String label) {
        public RateRule { roomType=required(roomType).toUpperCase(Locale.ROOT);Objects.requireNonNull(from);Objects.requireNonNull(to);Objects.requireNonNull(nightlyRate);label=label==null?"":label.strip();if(to.isBefore(from)||nightlyRate.signum()<=0)throw new IllegalArgumentException("Invalid rate rule"); }
    }
    public record DailyMetric(LocalDate date,int availableRooms,int roomsSold,double occupancyPercent,BigDecimal roomRevenue,BigDecimal adr,BigDecimal revPar) {}
    public record Recommendation(LocalDate date,double occupancyPercent,BigDecimal currentAverageRate,BigDecimal suggestedRate,String action,String reason) {}
    private final Path file; private final List<RateRule> rules=new ArrayList<>();
    public RevenueManagementService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","revenue-rules.tsv"));}
    public RevenueManagementService(Path file){this.file=Objects.requireNonNull(file);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load revenue rules",e);}}
    private static String required(String s){String x=s==null?"":s.strip();if(x.isEmpty())throw new IllegalArgumentException("Required value is missing");return x;}
    private static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    public synchronized void saveRule(RateRule rule)throws IOException{rules.removeIf(r->r.roomType().equals(rule.roomType())&&Objects.equals(r.weekday(),rule.weekday())&&!r.to().isBefore(rule.from())&&!r.from().isAfter(rule.to()));rules.add(rule);persist();}
    public synchronized List<RateRule> rules(){return rules.stream().sorted(Comparator.comparing(RateRule::from).thenComparing(RateRule::roomType)).toList();}
    public synchronized BigDecimal rateFor(String roomType,LocalDate date,BigDecimal base){return rules.stream().filter(r->(r.roomType().equals("*")||r.roomType().equalsIgnoreCase(roomType))&&!date.isBefore(r.from())&&!date.isAfter(r.to())&&(r.weekday()==null||r.weekday()==date.getDayOfWeek())).max(Comparator.comparingInt((RateRule r)->r.weekday()==null?0:1).thenComparing(RateRule::from)).map(RateRule::nightlyRate).orElse(base).setScale(2,RoundingMode.HALF_UP);}
    public List<DailyMetric> metrics(LocalDate from,LocalDate to,int totalRooms,List<Stay> stays){if(to.isBefore(from)||totalRooms<1)throw new IllegalArgumentException("Invalid report range or room count");List<DailyMetric> out=new ArrayList<>();for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){int sold=0;BigDecimal revenue=BigDecimal.ZERO;for(Stay s:stays){if(active(s.status())&&!d.isBefore(s.checkIn())&&d.isBefore(s.checkOut())){sold++;revenue=revenue.add(rateFor(s.roomType(),d,s.baseRate()));}}int available=totalRooms;double occ=100d*sold/available;BigDecimal adr=sold==0?BigDecimal.ZERO.setScale(2):revenue.divide(BigDecimal.valueOf(sold),2,RoundingMode.HALF_UP);BigDecimal revpar=revenue.divide(BigDecimal.valueOf(available),2,RoundingMode.HALF_UP);out.add(new DailyMetric(d,available,sold,occ,revenue.setScale(2,RoundingMode.HALF_UP),adr,revpar));}return List.copyOf(out);}
    public List<Recommendation> recommendations(List<DailyMetric> metrics){List<Recommendation> out=new ArrayList<>();for(DailyMetric m:metrics){BigDecimal current=m.adr().signum()>0?m.adr():new BigDecimal("100.00");BigDecimal factor;String action,reason;if(m.occupancyPercent()>=85){factor=new BigDecimal("1.15");action="INCREASE";reason="High demand: occupancy is at least 85%";}else if(m.occupancyPercent()>=65){factor=new BigDecimal("1.05");action="INCREASE";reason="Healthy demand: occupancy is at least 65%";}else if(m.occupancyPercent()<30){factor=new BigDecimal("0.90");action="DECREASE";reason="Low demand: occupancy is below 30%";}else{factor=BigDecimal.ONE;action="HOLD";reason="Occupancy is within the balanced range";}BigDecimal suggested=current.multiply(factor).setScale(2,RoundingMode.HALF_UP);out.add(new Recommendation(m.date(),m.occupancyPercent(),current,suggested,action,reason));}return List.copyOf(out);}
    public String csv(List<DailyMetric> metrics){StringBuilder b=new StringBuilder("Date,Available rooms,Rooms sold,Occupancy %,Room revenue,ADR,RevPAR\n");for(DailyMetric m:metrics)b.append(m.date()).append(',').append(m.availableRooms()).append(',').append(m.roomsSold()).append(',').append(String.format(Locale.ROOT,"%.2f",m.occupancyPercent())).append(',').append(m.roomRevenue()).append(',').append(m.adr()).append(',').append(m.revPar()).append('\n');return b.toString();}
    private static boolean active(String s){return "CONFIRMED".equalsIgnoreCase(s)||"CHECKED_IN".equalsIgnoreCase(s);}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"revenue-",".tmp");try{List<String> rows=new ArrayList<>();for(RateRule r:rules)rows.add(String.join("\t",enc(r.roomType()),r.from().toString(),r.to().toString(),r.weekday()==null?"*":r.weekday().name(),r.nightlyRate().toPlainString(),enc(r.label())));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] f=line.split("\t",-1);try{if(f.length==6)rules.add(new RateRule(dec(f[0]),LocalDate.parse(f[1]),LocalDate.parse(f[2]),f[3].equals("*")?null:DayOfWeek.valueOf(f[3]),new BigDecimal(f[4]),dec(f[5])));}catch(RuntimeException ignored){}}}
}
