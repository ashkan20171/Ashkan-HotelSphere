package com.ashkan.hotel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Maps domain events from RabbitMQ into the notification outbox. */
public final class NotificationEventConsumer implements RabbitMqAmqpTransport.EventHandler {
    private final NotificationService notifications;
    private final java.util.function.Supplier<LocalDateTime> clock;
    public NotificationEventConsumer(NotificationService notifications) { this(notifications, LocalDateTime::now); }
    public NotificationEventConsumer(NotificationService notifications, java.util.function.Supplier<LocalDateTime> clock) {
        this.notifications = Objects.requireNonNull(notifications); this.clock = Objects.requireNonNull(clock);
    }
    @Override public void handle(RabbitMqAmqpTransport.HotelEvent event) {
        Map<String,String> p = parse(event.payload());
        NotificationService.Template template = switch (event.type()) {
            case "booking.confirmed" -> NotificationService.Template.BOOKING_CONFIRMED;
            case "payment.received" -> NotificationService.Template.PAYMENT_RECEIVED;
            case "booking.checkin-reminder" -> NotificationService.Template.CHECKIN_REMINDER;
            case "complaint.updated" -> NotificationService.Template.COMPLAINT_UPDATED;
            default -> throw new IllegalArgumentException("Unsupported notification event: " + event.type());
        };
        NotificationService.Channel channel = NotificationService.Channel.valueOf(p.getOrDefault("channel", "EMAIL").toUpperCase(Locale.ROOT));
        notifications.enqueueTemplateForEvent(event.id(), template, channel, required(p,"recipient"), required(p,"guestName"),
                p.getOrDefault("bookingId", event.aggregateId()), p.getOrDefault("language", "en"), clock.get());
    }
    private static Map<String,String> parse(String payload) {
        Map<String,String> values = new HashMap<>();
        for (String pair : Objects.requireNonNullElse(payload, "").split("&")) { int i=pair.indexOf('='); if(i>0)
            values.put(URLDecoder.decode(pair.substring(0,i), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(i+1), StandardCharsets.UTF_8)); }
        return values;
    }
    private static String required(Map<String,String> p,String key) { String v=p.get(key); if(v==null||v.isBlank())throw new IllegalArgumentException("Event payload requires " + key);return v; }
}
