package com.ashkan.hotel;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Guest folio ledger for accommodation extras and settlements. */
public final class GuestJourneyService {
    public record Charge(long id,String bookingId,String category,String description,BigDecimal amount,LocalDateTime createdAt) {
        public Charge { bookingId=req(bookingId);category=req(category);description=req(description);Objects.requireNonNull(amount);Objects.requireNonNull(createdAt);if(amount.signum()<=0)throw new IllegalArgumentException("Charge must be positive"); }
    }
    public record Settlement(long id,String bookingId,String method,BigDecimal amount,LocalDateTime createdAt) {
        public Settlement { bookingId=req(bookingId);method=req(method);Objects.requireNonNull(amount);Objects.requireNonNull(createdAt);if(amount.signum()<=0)throw new IllegalArgumentException("Settlement must be positive"); }
    }
    private final Path file; private final Clock clock; private final List<Charge> charges=new ArrayList<>(); private final List<Settlement> settlements=new ArrayList<>(); private long nextId=1;
    public GuestJourneyService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","guest-folios.tsv"),Clock.systemDefaultZone());}
    public GuestJourneyService(Path file,Clock clock){this.file=Objects.requireNonNull(file);this.clock=Objects.requireNonNull(clock);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load guest folios",e);}}
    private static String req(String s){String v=s==null?"":s.strip();if(v.isEmpty())throw new IllegalArgumentException("Required value is missing");return v;}
    private static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    public synchronized Charge addCharge(String bookingId,String category,String description,BigDecimal amount)throws IOException{Charge c=new Charge(nextId++,bookingId,category,description,amount,LocalDateTime.now(clock));charges.add(c);try{persist();}catch(IOException e){charges.remove(c);nextId--;throw e;}return c;}
    public synchronized Settlement settle(String bookingId,String method,BigDecimal amount,BigDecimal accommodation)throws IOException{String id=req(bookingId);BigDecimal due=balance(id,accommodation);if(amount==null||amount.signum()<=0||amount.compareTo(due)>0)throw new IllegalArgumentException("Payment must be positive and cannot exceed the outstanding balance");Settlement s=new Settlement(nextId++,id,method,amount,LocalDateTime.now(clock));settlements.add(s);try{persist();}catch(IOException e){settlements.remove(s);nextId--;throw e;}return s;}
    public synchronized BigDecimal chargesTotal(String bookingId){return charges.stream().filter(c->c.bookingId().equals(bookingId)).map(Charge::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized BigDecimal paidTotal(String bookingId){return settlements.stream().filter(s->s.bookingId().equals(bookingId)).map(Settlement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized BigDecimal total(String bookingId,BigDecimal accommodation){return accommodation.add(chargesTotal(bookingId));}
    public synchronized BigDecimal balance(String bookingId,BigDecimal accommodation){return total(bookingId,accommodation).subtract(paidTotal(bookingId)).max(BigDecimal.ZERO);}
    public synchronized List<Charge> charges(String bookingId){return charges.stream().filter(c->c.bookingId().equals(bookingId)).toList();}
    public synchronized List<Settlement> settlements(String bookingId){return settlements.stream().filter(s->s.bookingId().equals(bookingId)).toList();}
    public synchronized String csv(){StringBuilder b=new StringBuilder("Type,ID,Booking,Category/Method,Description,Amount,Date\n");for(Charge c:charges)b.append("CHARGE,").append(c.id()).append(',').append(q(c.bookingId())).append(',').append(q(c.category())).append(',').append(q(c.description())).append(',').append(c.amount()).append(',').append(c.createdAt()).append('\n');for(Settlement s:settlements)b.append("PAYMENT,").append(s.id()).append(',').append(q(s.bookingId())).append(',').append(q(s.method())).append(",Settlement,").append(s.amount()).append(',').append(s.createdAt()).append('\n');return b.toString();}
    private static String q(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"folios-",".tmp");try{List<String> rows=new ArrayList<>();for(Charge c:charges)rows.add(String.join("\t","C",Long.toString(c.id()),enc(c.bookingId()),enc(c.category()),enc(c.description()),c.amount().toPlainString(),c.createdAt().toString()));for(Settlement s:settlements)rows.add(String.join("\t","P",Long.toString(s.id()),enc(s.bookingId()),enc(s.method()),s.amount().toPlainString(),s.createdAt().toString()));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){if(line.isBlank())continue;String[] p=line.split("\t",-1);if(p[0].equals("C")&&p.length==7){Charge c=new Charge(Long.parseLong(p[1]),dec(p[2]),dec(p[3]),dec(p[4]),new BigDecimal(p[5]),LocalDateTime.parse(p[6]));charges.add(c);nextId=Math.max(nextId,c.id()+1);}else if(p[0].equals("P")&&p.length==6){Settlement s=new Settlement(Long.parseLong(p[1]),dec(p[2]),dec(p[3]),new BigDecimal(p[4]),LocalDateTime.parse(p[5]));settlements.add(s);nextId=Math.max(nextId,s.id()+1);}}}
}
