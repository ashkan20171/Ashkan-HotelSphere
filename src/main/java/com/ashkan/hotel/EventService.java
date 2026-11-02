package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Conference halls, equipment and event reservations with overlap protection. */
public final class EventService {
    public enum Status { RESERVED, CONFIRMED, COMPLETED, CANCELLED }
    public record Hall(String code, String name, int capacity, BigDecimal hourlyRate, String equipment) {
        public Hall { code=req(code); name=req(name); if(capacity<1)throw new IllegalArgumentException("Capacity must be positive"); hourlyRate=money(hourlyRate); equipment=clean(equipment); }
    }
    public record Event(long id,String hallCode,String title,String organizer,int attendees,LocalDateTime start,LocalDateTime end,BigDecimal cateringCost,BigDecimal otherCost,Status status,String notes) {
        public Event { hallCode=req(hallCode);title=req(title);organizer=clean(organizer);if(attendees<1)throw new IllegalArgumentException("Attendees must be positive");Objects.requireNonNull(start);Objects.requireNonNull(end);if(!end.isAfter(start))throw new IllegalArgumentException("End must be after start");cateringCost=money(cateringCost);otherCost=money(otherCost);Objects.requireNonNull(status);notes=clean(notes); }
    }
    private final Path file; private final Clock clock; private final Map<String,Hall> halls=new TreeMap<>(String.CASE_INSENSITIVE_ORDER); private final List<Event> events=new ArrayList<>(); private long nextId=1;
    public EventService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","events.tsv"),Clock.systemDefaultZone());}
    public EventService(Path file,Clock clock){this.file=Objects.requireNonNull(file);this.clock=Objects.requireNonNull(clock);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load event data",e);}}
    private static String clean(String s){return s==null?"":s.strip();} private static String req(String s){String v=clean(s);if(v.isBlank())throw new IllegalArgumentException("Required value is missing");return v;}
    private static BigDecimal money(BigDecimal n){Objects.requireNonNull(n);if(n.signum()<0)throw new IllegalArgumentException("Cost cannot be negative");return n.setScale(2,RoundingMode.HALF_UP);}
    public synchronized void saveHall(Hall hall)throws IOException{Hall old=halls.put(hall.code(),hall);try{persist();}catch(IOException e){if(old==null)halls.remove(hall.code());else halls.put(old.code(),old);throw e;}}
    public synchronized List<Hall> halls(){return List.copyOf(halls.values());} public synchronized List<Event> events(){return events.stream().sorted(Comparator.comparing(Event::start).reversed()).toList();}
    public synchronized Event reserve(String hallCode,String title,String organizer,int attendees,LocalDateTime start,LocalDateTime end,BigDecimal catering,BigDecimal other,String notes)throws IOException{
        Hall hall=halls.get(req(hallCode));if(hall==null)throw new IllegalArgumentException("Unknown hall");if(attendees>hall.capacity())throw new IllegalArgumentException("Attendees exceed hall capacity");
        if(start.isBefore(LocalDateTime.now(clock)))throw new IllegalArgumentException("Event cannot start in the past");
        for(Event e:events)if(e.hallCode().equalsIgnoreCase(hallCode)&&e.status()!=Status.CANCELLED&&overlap(start,end,e.start(),e.end()))throw new IllegalStateException("Hall is already reserved during this time");
        Event e=new Event(nextId++,hallCode,title,organizer,attendees,start,end,catering,other,Status.RESERVED,notes);events.add(e);try{persist();}catch(IOException ex){events.remove(e);nextId--;throw ex;}return e;
    }
    public synchronized void updateStatus(long id,Status status)throws IOException{int i=index(id);Event old=events.get(i);if(old.status()==Status.CANCELLED||old.status()==Status.COMPLETED)throw new IllegalStateException("Closed event cannot be changed");Event updated=new Event(old.id(),old.hallCode(),old.title(),old.organizer(),old.attendees(),old.start(),old.end(),old.cateringCost(),old.otherCost(),status,old.notes());events.set(i,updated);try{persist();}catch(IOException e){events.set(i,old);throw e;}}
    private int index(long id){for(int i=0;i<events.size();i++)if(events.get(i).id()==id)return i;throw new IllegalArgumentException("Unknown event: "+id);}
    private static boolean overlap(LocalDateTime a,LocalDateTime b,LocalDateTime c,LocalDateTime d){return a.isBefore(d)&&c.isBefore(b);}
    public synchronized BigDecimal hallCost(Event e){Hall h=halls.get(e.hallCode());long minutes=ChronoUnit.MINUTES.between(e.start(),e.end());return h.hourlyRate().multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);}
    public synchronized BigDecimal totalCost(Event e){return hallCost(e).add(e.cateringCost()).add(e.otherCost()).setScale(2,RoundingMode.HALF_UP);}
    public synchronized BigDecimal revenueEstimate(){return events.stream().filter(e->e.status()!=Status.CANCELLED).map(this::totalCost).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized String csv(){StringBuilder b=new StringBuilder("ID,Hall,Title,Organizer,Attendees,Start,End,Status,HallCost,Catering,Other,Total,Notes\n");for(Event e:events())b.append(e.id()).append(',').append(q(e.hallCode())).append(',').append(q(e.title())).append(',').append(q(e.organizer())).append(',').append(e.attendees()).append(',').append(e.start()).append(',').append(e.end()).append(',').append(e.status()).append(',').append(hallCost(e)).append(',').append(e.cateringCost()).append(',').append(e.otherCost()).append(',').append(totalCost(e)).append(',').append(q(e.notes())).append('\n');return b.toString();}
    private static String q(String s){return "\""+clean(s).replace("\"","\"\"")+"\"";} private static String enc(String s){return Base64.getEncoder().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));} private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"events-",".tmp");try{List<String> rows=new ArrayList<>();for(Hall h:halls.values())rows.add(String.join("\t","H",enc(h.code()),enc(h.name()),Integer.toString(h.capacity()),h.hourlyRate().toPlainString(),enc(h.equipment())));for(Event e:events)rows.add(String.join("\t","E",Long.toString(e.id()),enc(e.hallCode()),enc(e.title()),enc(e.organizer()),Integer.toString(e.attendees()),e.start().toString(),e.end().toString(),e.cateringCost().toPlainString(),e.otherCost().toPlainString(),e.status().name(),enc(e.notes())));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException x){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String row:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] f=row.split("\\t",-1);try{if(f.length==6&&f[0].equals("H")){Hall h=new Hall(dec(f[1]),dec(f[2]),Integer.parseInt(f[3]),new BigDecimal(f[4]),dec(f[5]));halls.put(h.code(),h);}else if(f.length==12&&f[0].equals("E")){Event e=new Event(Long.parseLong(f[1]),dec(f[2]),dec(f[3]),dec(f[4]),Integer.parseInt(f[5]),LocalDateTime.parse(f[6]),LocalDateTime.parse(f[7]),new BigDecimal(f[8]),new BigDecimal(f[9]),Status.valueOf(f[10]),dec(f[11]));events.add(e);nextId=Math.max(nextId,e.id()+1);}}catch(RuntimeException ignored){}}}
}
