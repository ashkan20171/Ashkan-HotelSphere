package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Guest relationship profiles, loyalty ledger and feedback/case tracking. */
public final class CrmService {
    public enum Tier { STANDARD, SILVER, GOLD, PLATINUM }
    public enum FeedbackStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
    public record Guest(String id,String name,String email,String phone,String notes,boolean vip) {
        public Guest { id=req(id);name=req(name);email=clean(email);phone=clean(phone);notes=clean(notes); }
    }
    public record Ledger(long id,String guestId,int points,String reason,LocalDateTime occurredAt) {
        public Ledger { guestId=req(guestId);reason=req(reason);Objects.requireNonNull(occurredAt);if(points==0)throw new IllegalArgumentException("Points cannot be zero"); }
    }
    public record Feedback(long id,String guestId,int rating,String comment,FeedbackStatus status,LocalDateTime createdAt) {
        public Feedback { guestId=req(guestId);if(rating<1||rating>5)throw new IllegalArgumentException("Rating must be between 1 and 5");comment=req(comment);Objects.requireNonNull(status);Objects.requireNonNull(createdAt); }
    }
    private final Path file;private final Clock clock;private final Map<String,Guest> guests=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);private final List<Ledger> ledger=new ArrayList<>();private final List<Feedback> feedback=new ArrayList<>();private long nextLedger=1,nextFeedback=1;
    public CrmService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","crm.tsv"),Clock.systemDefaultZone());}
    public CrmService(Path file,Clock clock){this.file=Objects.requireNonNull(file);this.clock=Objects.requireNonNull(clock);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load CRM data",e);}}
    private static String clean(String s){return s==null?"":s.strip();}private static String req(String s){String x=clean(s);if(x.isBlank())throw new IllegalArgumentException("Required value is missing");return x;}
    private static String enc(String s){return Base64.getEncoder().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    public synchronized void saveGuest(Guest guest)throws IOException{Guest old=guests.put(guest.id(),guest);try{persist();}catch(IOException e){if(old==null)guests.remove(guest.id());else guests.put(old.id(),old);throw e;}}
    public synchronized List<Guest> guests(){return List.copyOf(guests.values());}public synchronized Guest guest(String id){Guest g=guests.get(req(id));if(g==null)throw new IllegalArgumentException("Unknown guest");return g;}
    public synchronized int points(String id){guest(id);return ledger.stream().filter(x->x.guestId().equalsIgnoreCase(id)).mapToInt(Ledger::points).sum();}
    public synchronized void adjustPoints(String id,int points,String reason)throws IOException{guest(id);if(points==0)throw new IllegalArgumentException("Points cannot be zero");if(points<0&&points(id)<-points)throw new IllegalStateException("Insufficient loyalty points");Ledger x=new Ledger(nextLedger++,id,points,reason,LocalDateTime.now(clock));ledger.add(x);try{persist();}catch(IOException e){ledger.remove(x);nextLedger--;throw e;}}
    public synchronized Tier tier(String id){Guest g=guest(id);int p=points(id);if(g.vip()||p>=5000)return Tier.PLATINUM;if(p>=2000)return Tier.GOLD;if(p>=500)return Tier.SILVER;return Tier.STANDARD;}
    public synchronized List<Ledger> ledger(){return ledger.stream().sorted(Comparator.comparing(Ledger::occurredAt).reversed()).toList();}
    public synchronized Feedback addFeedback(String guestId,int rating,String comment)throws IOException{guest(guestId);Feedback f=new Feedback(nextFeedback++,guestId,rating,comment,FeedbackStatus.OPEN,LocalDateTime.now(clock));feedback.add(f);try{persist();}catch(IOException e){feedback.remove(f);nextFeedback--;throw e;}return f;}
    public synchronized void updateFeedback(long id,FeedbackStatus status)throws IOException{for(int i=0;i<feedback.size();i++){Feedback old=feedback.get(i);if(old.id()==id){Feedback n=new Feedback(old.id(),old.guestId(),old.rating(),old.comment(),status,old.createdAt());feedback.set(i,n);try{persist();}catch(IOException e){feedback.set(i,old);throw e;}return;}}throw new IllegalArgumentException("Unknown feedback");}
    public synchronized List<Feedback> feedback(){return feedback.stream().sorted(Comparator.comparing(Feedback::createdAt).reversed()).toList();}
    public synchronized double averageRating(){return feedback.stream().mapToInt(Feedback::rating).average().orElse(0);}
    public synchronized String csv(){StringBuilder b=new StringBuilder("Guest ID,Name,Email,Phone,VIP,Loyalty Points,Tier,Notes\n");for(Guest g:guests())b.append(q(g.id())).append(',').append(q(g.name())).append(',').append(q(g.email())).append(',').append(q(g.phone())).append(',').append(g.vip()).append(',').append(points(g.id())).append(',').append(tier(g.id())).append(',').append(q(g.notes())).append('\n');b.append("\nLedger ID,Guest ID,Points,Reason,Date\n");for(Ledger x:ledger())b.append(x.id()).append(',').append(q(x.guestId())).append(',').append(x.points()).append(',').append(q(x.reason())).append(',').append(x.occurredAt()).append('\n');b.append("\nFeedback ID,Guest ID,Rating,Comment,Status,Date\n");for(Feedback x:feedback())b.append(x.id()).append(',').append(q(x.guestId())).append(',').append(x.rating()).append(',').append(q(x.comment())).append(',').append(x.status()).append(',').append(x.createdAt()).append('\n');return b.toString();}
    private static String q(String s){return "\""+clean(s).replace("\"","\"\"")+"\"";}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"crm-",".tmp");try{List<String> rows=new ArrayList<>();for(Guest g:guests.values())rows.add(String.join("\t","G",enc(g.id()),enc(g.name()),enc(g.email()),enc(g.phone()),enc(g.notes()),Boolean.toString(g.vip())));for(Ledger x:ledger)rows.add(String.join("\t","L",Long.toString(x.id()),enc(x.guestId()),Integer.toString(x.points()),enc(x.reason()),x.occurredAt().toString()));for(Feedback x:feedback)rows.add(String.join("\t","F",Long.toString(x.id()),enc(x.guestId()),Integer.toString(x.rating()),enc(x.comment()),x.status().name(),x.createdAt().toString()));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String row:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] f=row.split("\t",-1);try{switch(f[0]){case "G"->{if(f.length==7){Guest g=new Guest(dec(f[1]),dec(f[2]),dec(f[3]),dec(f[4]),dec(f[5]),Boolean.parseBoolean(f[6]));guests.put(g.id(),g);}}case "L"->{if(f.length==6){Ledger x=new Ledger(Long.parseLong(f[1]),dec(f[2]),Integer.parseInt(f[3]),dec(f[4]),LocalDateTime.parse(f[5]));ledger.add(x);nextLedger=Math.max(nextLedger,x.id()+1);}}case "F"->{if(f.length==7){Feedback x=new Feedback(Long.parseLong(f[1]),dec(f[2]),Integer.parseInt(f[3]),dec(f[4]),FeedbackStatus.valueOf(f[5]),LocalDateTime.parse(f[6]));feedback.add(x);nextFeedback=Math.max(nextFeedback,x.id()+1);}}}}catch(RuntimeException ignored){}}}
}
