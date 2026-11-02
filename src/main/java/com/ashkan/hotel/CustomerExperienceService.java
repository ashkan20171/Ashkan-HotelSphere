package com.ashkan.hotel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Independent customer-experience bounded-context service. */
public final class CustomerExperienceService {
    public enum ComplaintStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
    public record Feedback(String id, String bookingId, String guestName, LocalDateTime submittedAt,
                           int cleanliness, int reception, int restaurant, int facilities,
                           int overall, int nps, String comment) {
        public double csat(){ return overall >= 4 ? 100.0 : 0.0; }
        public String sentiment(){ return overall>=4 ? "POSITIVE" : overall==3 ? "NEUTRAL" : "NEGATIVE"; }
    }
    public record Complaint(String id,String bookingId,String guestName,String category,String subject,
                            String description,String assignedTo,ComplaintStatus status,
                            LocalDateTime createdAt,LocalDateTime updatedAt,String resolution) {}
    public record Metrics(long responses,double csat,double nps,double averageOverall,long openComplaints,
                          Map<String,Long> categoryCounts) {}
    private final Path file; private final List<Feedback> feedback=new ArrayList<>(); private final List<Complaint> complaints=new ArrayList<>();
    public CustomerExperienceService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","customer-experience.tsv"));}
    public CustomerExperienceService(Path file){this.file=Objects.requireNonNull(file);load();}
    public synchronized Feedback submitFeedback(String bookingId,String guestName,int cleanliness,int reception,int restaurant,int facilities,int overall,int nps,String comment,LocalDateTime at){
        String booking=req(bookingId,"Booking ID"),guest=req(guestName,"Guest name");int[] ratings={cleanliness,reception,restaurant,facilities,overall};for(int n:ratings)if(n<1||n>5)throw new IllegalArgumentException("Ratings must be between 1 and 5");if(nps<0||nps>10)throw new IllegalArgumentException("NPS must be between 0 and 10");
        if(feedback.stream().anyMatch(x->x.bookingId().equalsIgnoreCase(booking)))throw new IllegalStateException("Feedback already exists for this booking");
        Feedback x=new Feedback(UUID.randomUUID().toString(),booking,guest,Objects.requireNonNull(at),cleanliness,reception,restaurant,facilities,overall,nps,clean(comment));feedback.add(x);persist();return x;
    }
    public synchronized Complaint createComplaint(String bookingId,String guestName,String category,String subject,String description,String assignedTo,LocalDateTime at){
        Complaint x=new Complaint(UUID.randomUUID().toString(),req(bookingId,"Booking ID"),req(guestName,"Guest name"),req(category,"Category"),req(subject,"Subject"),clean(description),clean(assignedTo),ComplaintStatus.OPEN,Objects.requireNonNull(at),at,"");complaints.add(x);persist();return x;
    }
    public synchronized Complaint updateComplaint(String id,ComplaintStatus status,String assignedTo,String resolution,LocalDateTime at){int i=index(id);Complaint old=complaints.get(i);if(old.status()==ComplaintStatus.CLOSED)throw new IllegalStateException("Closed complaint cannot be changed");if(status==ComplaintStatus.RESOLVED&&clean(resolution).isBlank())throw new IllegalArgumentException("Resolution is required");Complaint next=new Complaint(old.id(),old.bookingId(),old.guestName(),old.category(),old.subject(),old.description(),clean(assignedTo).isBlank()?old.assignedTo():clean(assignedTo),Objects.requireNonNull(status),old.createdAt(),Objects.requireNonNull(at),clean(resolution));complaints.set(i,next);persist();return next;}
    public synchronized List<Feedback> feedback(){return feedback.stream().sorted(Comparator.comparing(Feedback::submittedAt).reversed()).toList();}
    public synchronized List<Complaint> complaints(){return complaints.stream().sorted(Comparator.comparing(Complaint::updatedAt).reversed()).toList();}
    public synchronized Metrics metrics(){long n=feedback.size(),promoters=feedback.stream().filter(x->x.nps()>=9).count(),detractors=feedback.stream().filter(x->x.nps()<=6).count(),satisfied=feedback.stream().filter(x->x.overall()>=4).count();double csat=n==0?0:satisfied*100.0/n,nps=n==0?0:(promoters-detractors)*100.0/n,avg=n==0?0:feedback.stream().mapToInt(Feedback::overall).average().orElse(0);Map<String,Long> cats=new TreeMap<>();for(Complaint c:complaints)cats.merge(c.category(),1L,Long::sum);long open=complaints.stream().filter(c->c.status()==ComplaintStatus.OPEN||c.status()==ComplaintStatus.IN_PROGRESS).count();return new Metrics(n,csat,nps,avg,open,Collections.unmodifiableMap(cats));}
    public synchronized String feedbackCsv(){StringBuilder b=new StringBuilder("ID,Booking,Guest,SubmittedAt,Cleanliness,Reception,Restaurant,Facilities,Overall,NPS,Sentiment,Comment\n");for(Feedback x:feedback){Object[] a={x.id(),x.bookingId(),x.guestName(),x.submittedAt(),x.cleanliness(),x.reception(),x.restaurant(),x.facilities(),x.overall(),x.nps(),x.sentiment(),x.comment()};csvRow(b,a);}return b.toString();}
    public synchronized String complaintsCsv(){StringBuilder b=new StringBuilder("ID,Booking,Guest,Category,Subject,Description,AssignedTo,Status,CreatedAt,UpdatedAt,Resolution\n");for(Complaint x:complaints)csvRow(b,new Object[]{x.id(),x.bookingId(),x.guestName(),x.category(),x.subject(),x.description(),x.assignedTo(),x.status(),x.createdAt(),x.updatedAt(),x.resolution()});return b.toString();}
    private int index(String id){for(int i=0;i<complaints.size();i++)if(complaints.get(i).id().equals(id))return i;throw new NoSuchElementException("Complaint not found");}
    private void load(){if(!Files.exists(file))return;try{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] p=line.split("\\t",-1);if(p[0].equals("F")&&p.length==12)feedback.add(new Feedback(p[1],dec(p[2]),dec(p[3]),LocalDateTime.parse(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),Integer.parseInt(p[8]),Integer.parseInt(p[9]),Integer.parseInt(p[10]),dec(p[11])));else if(p[0].equals("C")&&p.length==12)complaints.add(new Complaint(p[1],dec(p[2]),dec(p[3]),dec(p[4]),dec(p[5]),dec(p[6]),dec(p[7]),ComplaintStatus.valueOf(p[8]),LocalDateTime.parse(p[9]),LocalDateTime.parse(p[10]),dec(p[11])));else throw new IOException("Invalid customer experience record");}}catch(Exception e){throw new IllegalStateException("Cannot load customer experience data",e);}}
    private void persist(){try{Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"experience-",".tmp");try{List<String> lines=new ArrayList<>();for(Feedback x:feedback)lines.add(String.join("\t","F",x.id(),enc(x.bookingId()),enc(x.guestName()),x.submittedAt().toString(),""+x.cleanliness(),""+x.reception(),""+x.restaurant(),""+x.facilities(),""+x.overall(),""+x.nps(),enc(x.comment())));for(Complaint x:complaints)lines.add(String.join("\t","C",x.id(),enc(x.bookingId()),enc(x.guestName()),enc(x.category()),enc(x.subject()),enc(x.description()),enc(x.assignedTo()),x.status().name(),x.createdAt().toString(),x.updatedAt().toString(),enc(x.resolution())));Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}catch(IOException e){throw new IllegalStateException("Cannot persist customer experience data",e);}}
    private static void csvRow(StringBuilder b,Object[] values){for(int i=0;i<values.length;i++){if(i>0)b.append(',');String s=String.valueOf(values[i]);if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)s="'"+s;b.append('"').append(s.replace("\"","\"\"")).append('"');}b.append('\n');}
    private static String req(String s,String n){String v=clean(s);if(v.isBlank())throw new IllegalArgumentException(n+" is required");return v;}private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
}
