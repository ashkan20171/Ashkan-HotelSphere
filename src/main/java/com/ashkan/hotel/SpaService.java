package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Spa, pool and leisure service catalog with capacity-aware appointment booking. */
public final class SpaService {
    public enum Status { BOOKED, CONFIRMED, COMPLETED, CANCELLED }
    public record Facility(String code,String name,int capacity,String description) {
        public Facility { code=req(code); name=req(name); if(capacity<1)throw new IllegalArgumentException("Capacity must be positive"); description=clean(description); }
    }
    public record Offering(String code,String name,String category,int durationMinutes,BigDecimal price,boolean active) {
        public Offering { code=req(code);name=req(name);category=req(category);if(durationMinutes<1)throw new IllegalArgumentException("Duration must be positive");price=money(price); }
    }
    public record Appointment(long id,String facilityCode,String serviceCode,String guest,String staff,LocalDateTime start,LocalDateTime end,int participants,BigDecimal extraCharge,Status status,String notes) {
        public Appointment { facilityCode=req(facilityCode);serviceCode=req(serviceCode);guest=req(guest);staff=clean(staff);Objects.requireNonNull(start);Objects.requireNonNull(end);if(!end.isAfter(start))throw new IllegalArgumentException("End must be after start");if(participants<1)throw new IllegalArgumentException("Participants must be positive");extraCharge=money(extraCharge);Objects.requireNonNull(status);notes=clean(notes); }
    }
    private final Path file; private final Clock clock; private final Map<String,Facility> facilities=new TreeMap<>(String.CASE_INSENSITIVE_ORDER); private final Map<String,Offering> offerings=new TreeMap<>(String.CASE_INSENSITIVE_ORDER); private final List<Appointment> appointments=new ArrayList<>(); private long nextId=1;
    public SpaService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","spa.tsv"),Clock.systemDefaultZone());}
    public SpaService(Path file,Clock clock){this.file=Objects.requireNonNull(file);this.clock=Objects.requireNonNull(clock);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load spa data",e);}}
    private static String clean(String s){return s==null?"":s.strip();} private static String req(String s){String v=clean(s);if(v.isBlank())throw new IllegalArgumentException("Required value is missing");return v;}
    private static BigDecimal money(BigDecimal v){Objects.requireNonNull(v);if(v.signum()<0)throw new IllegalArgumentException("Price cannot be negative");return v.setScale(2,RoundingMode.HALF_UP);}
    public synchronized void saveFacility(Facility f)throws IOException{Facility old=facilities.put(f.code(),f);try{persist();}catch(IOException e){if(old==null)facilities.remove(f.code());else facilities.put(old.code(),old);throw e;}}
    public synchronized void saveOffering(Offering o)throws IOException{Offering old=offerings.put(o.code(),o);try{persist();}catch(IOException e){if(old==null)offerings.remove(o.code());else offerings.put(old.code(),old);throw e;}}
    public synchronized List<Facility> facilities(){return List.copyOf(facilities.values());} public synchronized List<Offering> offerings(){return List.copyOf(offerings.values());}
    public synchronized List<Appointment> appointments(){return appointments.stream().sorted(Comparator.comparing(Appointment::start).reversed()).toList();}
    public synchronized Appointment book(String facilityCode,String serviceCode,String guest,String staff,LocalDateTime start,int participants,BigDecimal extra,String notes)throws IOException{
        Facility f=facilities.get(req(facilityCode));Offering o=offerings.get(req(serviceCode));if(f==null)throw new IllegalArgumentException("Unknown facility");if(o==null||!o.active())throw new IllegalArgumentException("Service is unavailable");if(participants>f.capacity())throw new IllegalArgumentException("Participants exceed facility capacity");if(start.isBefore(LocalDateTime.now(clock)))throw new IllegalArgumentException("Appointment cannot start in the past");LocalDateTime end=start.plusMinutes(o.durationMinutes());
        for(Appointment a:appointments)if(a.status()!=Status.CANCELLED&&overlap(start,end,a.start(),a.end())&&(a.facilityCode().equalsIgnoreCase(facilityCode)||(!clean(staff).isBlank()&&a.staff().equalsIgnoreCase(staff))))throw new IllegalStateException("Facility or staff member is already booked");
        Appointment a=new Appointment(nextId++,facilityCode,serviceCode,guest,staff,start,end,participants,extra,Status.BOOKED,notes);appointments.add(a);try{persist();}catch(IOException e){appointments.remove(a);nextId--;throw e;}return a;
    }
    public synchronized void updateStatus(long id,Status status)throws IOException{int i=index(id);Appointment old=appointments.get(i);if(old.status()==Status.CANCELLED||old.status()==Status.COMPLETED)throw new IllegalStateException("Closed appointment cannot be changed");Appointment n=new Appointment(old.id(),old.facilityCode(),old.serviceCode(),old.guest(),old.staff(),old.start(),old.end(),old.participants(),old.extraCharge(),status,old.notes());appointments.set(i,n);try{persist();}catch(IOException e){appointments.set(i,old);throw e;}}
    private int index(long id){for(int i=0;i<appointments.size();i++)if(appointments.get(i).id()==id)return i;throw new IllegalArgumentException("Unknown appointment: "+id);}
    private static boolean overlap(LocalDateTime a,LocalDateTime b,LocalDateTime c,LocalDateTime d){return a.isBefore(d)&&c.isBefore(b);}
    public synchronized BigDecimal total(Appointment a){Offering o=offerings.get(a.serviceCode());if(o==null)return a.extraCharge();return o.price().multiply(BigDecimal.valueOf(a.participants())).add(a.extraCharge()).setScale(2,RoundingMode.HALF_UP);}
    public synchronized BigDecimal revenueEstimate(){return appointments.stream().filter(a->a.status()!=Status.CANCELLED).map(this::total).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized String csv(){StringBuilder b=new StringBuilder("ID,Facility,Service,Guest,Staff,Start,End,Participants,Status,Total,Notes\n");for(Appointment a:appointments())b.append(a.id()).append(',').append(q(a.facilityCode())).append(',').append(q(a.serviceCode())).append(',').append(q(a.guest())).append(',').append(q(a.staff())).append(',').append(a.start()).append(',').append(a.end()).append(',').append(a.participants()).append(',').append(a.status()).append(',').append(total(a)).append(',').append(q(a.notes())).append('\n');return b.toString();}
    private static String q(String s){return "\""+clean(s).replace("\"","\"\"")+"\"";} private static String enc(String s){return Base64.getEncoder().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));} private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"spa-",".tmp");try{List<String> rows=new ArrayList<>();for(Facility f:facilities.values())rows.add(String.join("\t","F",enc(f.code()),enc(f.name()),Integer.toString(f.capacity()),enc(f.description())));for(Offering o:offerings.values())rows.add(String.join("\t","S",enc(o.code()),enc(o.name()),enc(o.category()),Integer.toString(o.durationMinutes()),o.price().toPlainString(),Boolean.toString(o.active())));for(Appointment a:appointments)rows.add(String.join("\t","A",Long.toString(a.id()),enc(a.facilityCode()),enc(a.serviceCode()),enc(a.guest()),enc(a.staff()),a.start().toString(),a.end().toString(),Integer.toString(a.participants()),a.extraCharge().toPlainString(),a.status().name(),enc(a.notes())));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String row:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] f=row.split("\\t",-1);try{if(f.length==5&&f[0].equals("F")){Facility x=new Facility(dec(f[1]),dec(f[2]),Integer.parseInt(f[3]),dec(f[4]));facilities.put(x.code(),x);}else if(f.length==7&&f[0].equals("S")){Offering x=new Offering(dec(f[1]),dec(f[2]),dec(f[3]),Integer.parseInt(f[4]),new BigDecimal(f[5]),Boolean.parseBoolean(f[6]));offerings.put(x.code(),x);}else if(f.length==12&&f[0].equals("A")){Appointment a=new Appointment(Long.parseLong(f[1]),dec(f[2]),dec(f[3]),dec(f[4]),dec(f[5]),LocalDateTime.parse(f[6]),LocalDateTime.parse(f[7]),Integer.parseInt(f[8]),new BigDecimal(f[9]),Status.valueOf(f[10]),dec(f[11]));appointments.add(a);nextId=Math.max(nextId,a.id()+1);}}catch(RuntimeException ignored){}}}
}
