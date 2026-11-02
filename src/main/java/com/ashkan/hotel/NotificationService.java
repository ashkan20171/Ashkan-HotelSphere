package com.ashkan.hotel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Durable notification outbox with pluggable delivery gateways and bounded exponential retries. */
public final class NotificationService {
    public enum Channel { EMAIL, SMS }
    public enum Status { QUEUED, SENT, FAILED }
    public enum Template { BOOKING_CONFIRMED, PAYMENT_RECEIVED, CHECKIN_REMINDER, COMPLAINT_UPDATED }
    public record Message(String id, Channel channel, String recipient, String subject, String body,
                          Status status, int attempts, LocalDateTime createdAt,
                          LocalDateTime nextAttemptAt, LocalDateTime sentAt, String lastError) {}
    public record Metrics(long queued, long sent, long failed, long total, long dueNow) {}
    @FunctionalInterface public interface DeliveryGateway { void deliver(Message message) throws Exception; }

    private static final int MAX_ATTEMPTS = 4;
    private final Path file;
    private final DeliveryGateway gateway;
    private final List<Message> messages = new ArrayList<>();

    public NotificationService() {
        this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "notification-outbox.tsv"),
             new PreviewGateway(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "notification-preview.log")));
    }
    public NotificationService(Path file, DeliveryGateway gateway) {
        this.file = Objects.requireNonNull(file); this.gateway = Objects.requireNonNull(gateway); load();
    }

    public synchronized Message enqueue(Channel channel, String recipient, String subject, String body, LocalDateTime now) {
        Objects.requireNonNull(channel); String to = required(recipient, "Recipient");
        if (channel == Channel.EMAIL && !to.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email address");
        if (channel == Channel.SMS && !to.matches("^\\+?[0-9][0-9 -]{6,18}[0-9]$")) throw new IllegalArgumentException("Invalid phone number");
        String text = required(body, "Message body"); if (text.length() > 4000) throw new IllegalArgumentException("Message body is too long");
        Message m = new Message(UUID.randomUUID().toString(), channel, to, clean(subject), text, Status.QUEUED, 0,
                Objects.requireNonNull(now), now, null, "");
        messages.add(m); persist(); return m;
    }

    public synchronized Message enqueueTemplate(Template template, Channel channel, String recipient, String guest,
                                                  String booking, String language, LocalDateTime now) {
        Objects.requireNonNull(template); String name = required(guest, "Guest name"); String ref = clean(booking);
        boolean fa = "fa".equalsIgnoreCase(language);
        String subject, body;
        switch (template) {
            case BOOKING_CONFIRMED -> { subject = fa ? "تأیید رزرو هتل" : "Hotel booking confirmed"; body = fa ? "مهمان گرامی " + name + "، رزرو شما با کد " + ref + " تأیید شد." : "Dear " + name + ", your booking " + ref + " is confirmed."; }
            case PAYMENT_RECEIVED -> { subject = fa ? "تأیید دریافت پرداخت" : "Payment received"; body = fa ? "مهمان گرامی " + name + "، پرداخت مربوط به رزرو " + ref + " دریافت شد." : "Dear " + name + ", payment for booking " + ref + " has been received."; }
            case CHECKIN_REMINDER -> { subject = fa ? "یادآوری ورود به هتل" : "Hotel check-in reminder"; body = fa ? "مهمان گرامی " + name + "، منتظر دیدار شما برای رزرو " + ref + " هستیم." : "Dear " + name + ", we look forward to welcoming you for booking " + ref + "."; }
            case COMPLAINT_UPDATED -> { subject = fa ? "به‌روزرسانی درخواست شما" : "Your request has been updated"; body = fa ? "مهمان گرامی " + name + "، وضعیت درخواست شما با کد " + ref + " به‌روزرسانی شد." : "Dear " + name + ", your request " + ref + " has been updated."; }
            default -> throw new IllegalStateException("Unsupported template");
        }
        return enqueueWithId(UUID.randomUUID().toString(), channel, recipient, subject, body, now);
    }

    /** Idempotent event-driven enqueue: the same source event always maps to the same outbox message ID. */
    public synchronized Message enqueueTemplateForEvent(String sourceEventId, Template template, Channel channel, String recipient,
                                                          String guest, String booking, String language, LocalDateTime now) {
        String source = required(sourceEventId, "Source event ID");
        String stableId = UUID.nameUUIDFromBytes(("ashkan-notification:" + source).getBytes(StandardCharsets.UTF_8)).toString();
        Message existing = messages.stream().filter(m -> m.id().equals(stableId)).findFirst().orElse(null);
        if (existing != null) return existing;
        Objects.requireNonNull(template); String name = required(guest, "Guest name"); String ref = clean(booking);
        boolean fa = "fa".equalsIgnoreCase(language); String subject, body;
        switch (template) {
            case BOOKING_CONFIRMED -> { subject = fa ? "تأیید رزرو هتل" : "Hotel booking confirmed"; body = fa ? "مهمان گرامی " + name + "، رزرو شما با کد " + ref + " تأیید شد." : "Dear " + name + ", your booking " + ref + " is confirmed."; }
            case PAYMENT_RECEIVED -> { subject = fa ? "تأیید دریافت پرداخت" : "Payment received"; body = fa ? "مهمان گرامی " + name + "، پرداخت مربوط به رزرو " + ref + " دریافت شد." : "Dear " + name + ", payment for booking " + ref + " has been received."; }
            case CHECKIN_REMINDER -> { subject = fa ? "یادآوری ورود به هتل" : "Hotel check-in reminder"; body = fa ? "مهمان گرامی " + name + "، منتظر دیدار شما برای رزرو " + ref + " هستیم." : "Dear " + name + ", we look forward to welcoming you for booking " + ref + "."; }
            case COMPLAINT_UPDATED -> { subject = fa ? "به‌روزرسانی درخواست شما" : "Your request has been updated"; body = fa ? "مهمان گرامی " + name + "، وضعیت درخواست شما با کد " + ref + " به‌روزرسانی شد." : "Dear " + name + ", your request " + ref + " has been updated."; }
            default -> throw new IllegalStateException("Unsupported template");
        }
        return enqueueWithId(stableId, channel, recipient, subject, body, now);
    }

    private synchronized Message enqueueWithId(String id, Channel channel, String recipient, String subject, String body, LocalDateTime now) {
        Message existing = messages.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        if (existing != null) return existing;
        Objects.requireNonNull(channel); String to = required(recipient, "Recipient");
        if (channel == Channel.EMAIL && !to.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email address");
        if (channel == Channel.SMS && !to.matches("^\\+?[0-9][0-9 -]{6,18}[0-9]$")) throw new IllegalArgumentException("Invalid phone number");
        String text = required(body, "Message body"); if (text.length() > 4000) throw new IllegalArgumentException("Message body is too long");
        Message m = new Message(id, channel, to, clean(subject), text, Status.QUEUED, 0, Objects.requireNonNull(now), now, null, "");
        messages.add(m); persist(); return m;
    }

    /** Attempts due messages once. Failed deliveries are rescheduled with exponential backoff. */
    public synchronized int processDue(LocalDateTime now) {
        int processed = 0;
        List<Message> due = messages.stream().filter(m -> m.status() == Status.QUEUED && !m.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(Message::createdAt)).toList();
        for (Message m : due) {
            processed++; int attempt = m.attempts() + 1;
            try {
                gateway.deliver(m);
                replace(m, new Message(m.id(), m.channel(), m.recipient(), m.subject(), m.body(), Status.SENT,
                        attempt, m.createdAt(), m.nextAttemptAt(), now, ""));
            } catch (Exception ex) {
                boolean exhausted = attempt >= MAX_ATTEMPTS;
                LocalDateTime next = exhausted ? m.nextAttemptAt() : now.plusMinutes(1L << (attempt - 1));
                replace(m, new Message(m.id(), m.channel(), m.recipient(), m.subject(), m.body(),
                        exhausted ? Status.FAILED : Status.QUEUED, attempt, m.createdAt(), next, null, clean(ex.getMessage())));
            }
            persist();
        }
        return processed;
    }

    public synchronized List<Message> messages() { return messages.stream().sorted(Comparator.comparing(Message::createdAt).reversed()).toList(); }
    public synchronized Metrics metrics(LocalDateTime now) {
        long q=messages.stream().filter(x->x.status()==Status.QUEUED).count(), s=messages.stream().filter(x->x.status()==Status.SENT).count(), f=messages.stream().filter(x->x.status()==Status.FAILED).count();
        long due=messages.stream().filter(x->x.status()==Status.QUEUED&&!x.nextAttemptAt().isAfter(now)).count(); return new Metrics(q,s,f,messages.size(),due);
    }
    public synchronized String csv() {
        StringBuilder b=new StringBuilder("ID,Channel,Recipient,Subject,Body,Status,Attempts,CreatedAt,NextAttemptAt,SentAt,LastError\n");
        for(Message m:messages) { Object[] v={m.id(),m.channel(),m.recipient(),m.subject(),m.body(),m.status(),m.attempts(),m.createdAt(),m.nextAttemptAt(),m.sentAt(),m.lastError()};
            for(int i=0;i<v.length;i++){if(i>0)b.append(',');String x=String.valueOf(v[i]);if(!x.isEmpty()&&"=+-@".indexOf(x.charAt(0))>=0)x="'"+x;b.append('"').append(x.replace("\"","\"\"")).append('"');}b.append('\n'); }
        return b.toString();
    }
    private void replace(Message old, Message next) { int i=messages.indexOf(old); if(i>=0)messages.set(i,next); }
    private void load() {
        if(!Files.exists(file))return;
        try { for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) { String[] p=line.split("\\t",-1); if(p.length!=13||!p[0].equals("N"))throw new IOException("Invalid notification outbox record");
            messages.add(new Message(p[1],Channel.valueOf(p[2]),dec(p[3]),dec(p[4]),dec(p[5]),Status.valueOf(p[6]),Integer.parseInt(p[7]),LocalDateTime.parse(p[8]),LocalDateTime.parse(p[9]),p[10].isEmpty()?null:LocalDateTime.parse(p[10]),dec(p[11])));
        }} catch(Exception e){throw new IllegalStateException("Cannot load notification outbox",e);}
    }
    private void persist() {
        try { Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"notifications-",".tmp");
            try { List<String> lines=new ArrayList<>();for(Message m:messages)lines.add(String.join("\t","N",m.id(),m.channel().name(),enc(m.recipient()),enc(m.subject()),enc(m.body()),m.status().name(),String.valueOf(m.attempts()),m.createdAt().toString(),m.nextAttemptAt().toString(),m.sentAt()==null?"":m.sentAt().toString(),enc(m.lastError()),"v1"));
                Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(tmp);}
        } catch(IOException e){throw new IllegalStateException("Cannot persist notification outbox",e);}
    }
    private static String required(String s,String n){String x=clean(s);if(x.isBlank())throw new IllegalArgumentException(n+" is required");return x;}
    private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\r',' ').trim();}
    private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}

    /** Safe development gateway: writes previews to disk; it does not send real email or SMS. */
    public static final class PreviewGateway implements DeliveryGateway {
        private final Path log;
        public PreviewGateway(Path log){this.log=Objects.requireNonNull(log);}
        @Override public synchronized void deliver(Message m)throws IOException {
            Path dir=log.toAbsolutePath().getParent();Files.createDirectories(dir);
            String line= m.createdAt()+" | "+m.channel()+" | "+m.recipient()+" | "+m.subject()+" | "+m.body().replace('\n',' ')+System.lineSeparator();
            Files.writeString(log,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
    }
}
