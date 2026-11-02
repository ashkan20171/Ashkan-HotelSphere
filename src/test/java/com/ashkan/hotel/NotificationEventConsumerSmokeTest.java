package com.ashkan.hotel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;

public final class NotificationEventConsumerSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("ashkan-event-notify");
        NotificationService service=new NotificationService(dir.resolve("outbox.tsv"), m -> {});
        NotificationEventConsumer consumer=new NotificationEventConsumer(service, () -> LocalDateTime.of(2026,10,30,10,0));
        String payload="channel=EMAIL&recipient="+enc("guest@example.com")+"&guestName="+enc("Ashkan")+"&bookingId=BK-100&language=en";
        RabbitMqAmqpTransport.HotelEvent event=new RabbitMqAmqpTransport.HotelEvent("event-100","booking.confirmed","BK-100",payload,LocalDateTime.now());
        consumer.handle(event); consumer.handle(event);
        if(service.messages().size()!=1)throw new AssertionError("replayed event must not create duplicate notification");
        var message=service.messages().get(0);
        if(!message.subject().contains("confirmed")||!message.recipient().equals("guest@example.com"))throw new AssertionError("event mapping failed");
        boolean rejected=false;
        try { consumer.handle(new RabbitMqAmqpTransport.HotelEvent("event-101","unknown.event","BK-101",payload,LocalDateTime.now())); }
        catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("unsupported event must fail for dead-letter routing");
        System.out.println("NotificationEventConsumerSmokeTest passed");
    }
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
