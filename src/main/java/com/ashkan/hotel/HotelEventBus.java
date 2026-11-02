package com.ashkan.hotel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Small durable local event journal. It provides at-least-once delivery semantics for desktop/dev mode. */
public final class HotelEventBus {
    public enum Status { PENDING, ACKNOWLEDGED, DEAD_LETTER }
    public record Event(String id, String type, String aggregateId, String payload, LocalDateTime occurredAt,
                        Status status, int attempts, String lastError) {}
    @FunctionalInterface public interface Handler { void handle(Event event) throws Exception; }
    private final Path journal;
    private final List<Event> events = new ArrayList<>();
    private final int maxAttempts;

    public HotelEventBus() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "event-journal.tsv"), 4); }
    public HotelEventBus(Path journal, int maxAttempts) {
        this.journal = Objects.requireNonNull(journal);
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts; load();
    }
    public synchronized Event publish(String type, String aggregateId, String payload, LocalDateTime now) {
        String t = required(type, "event type");
        Event event = new Event(UUID.randomUUID().toString(), t, clean(aggregateId), clean(payload),
                Objects.requireNonNull(now), Status.PENDING, 0, "");
        events.add(event); persist(); return event;
    }
    /** Dispatch pending events. Failed events remain pending until maxAttempts, then enter the dead-letter state. */
    public synchronized int dispatch(Handler handler) {
        Objects.requireNonNull(handler); int count = 0;
        for (Event e : List.copyOf(events)) {
            if (e.status() != Status.PENDING) continue;
            count++; int attempt = e.attempts() + 1;
            try {
                handler.handle(e);
                replace(e, new Event(e.id(), e.type(), e.aggregateId(), e.payload(), e.occurredAt(), Status.ACKNOWLEDGED, attempt, ""));
            } catch (Exception ex) {
                replace(e, new Event(e.id(), e.type(), e.aggregateId(), e.payload(), e.occurredAt(),
                        attempt >= maxAttempts ? Status.DEAD_LETTER : Status.PENDING, attempt, clean(ex.getMessage())));
            }
            persist();
        }
        return count;
    }
    public synchronized List<Event> events() { return List.copyOf(events); }
    public synchronized long pendingCount() { return events.stream().filter(e -> e.status() == Status.PENDING).count(); }
    public synchronized long deadLetterCount() { return events.stream().filter(e -> e.status() == Status.DEAD_LETTER).count(); }
    public synchronized String csv() {
        StringBuilder b = new StringBuilder("Id,Type,AggregateId,Payload,OccurredAt,Status,Attempts,LastError\n");
        for (Event e : events) { Object[] values={e.id(),e.type(),e.aggregateId(),e.payload(),e.occurredAt(),e.status(),e.attempts(),e.lastError()};
            for(int i=0;i<values.length;i++){if(i>0)b.append(',');String s=String.valueOf(values[i]);b.append('"').append(s.replace("\"","\"\"")).append('"');}b.append('\n'); }
        return b.toString();
    }
    private void replace(Event old, Event next) { int i=events.indexOf(old); if(i>=0)events.set(i,next); }
    private void load() {
        if (!Files.exists(journal)) return;
        try { for(String line:Files.readAllLines(journal,StandardCharsets.UTF_8)) {
            String[] p=line.split("\\t",-1); if(p.length!=9 || !p[0].equals("E")) throw new IOException("Invalid event journal record");
            events.add(new Event(p[1],dec(p[2]),dec(p[3]),dec(p[4]),LocalDateTime.parse(p[5]),Status.valueOf(p[6]),Integer.parseInt(p[7]),dec(p[8])));
        }} catch(Exception ex){throw new IllegalStateException("Cannot load event journal",ex);}
    }
    private void persist() {
        try { Path dir=journal.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"hotel-events-",".tmp");
            try { List<String> lines=new ArrayList<>();for(Event e:events)lines.add(String.join("\t","E",e.id(),enc(e.type()),enc(e.aggregateId()),enc(e.payload()),e.occurredAt().toString(),e.status().name(),String.valueOf(e.attempts()),enc(e.lastError())));
                Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,journal,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException x){Files.move(tmp,journal,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(tmp);}
        } catch(IOException ex){throw new IllegalStateException("Cannot persist event journal",ex);}
    }
    private static String required(String s,String label){String x=clean(s);if(x.isBlank())throw new IllegalArgumentException(label+" is required");return x;}
    private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\r',' ').trim();}
    private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
}
