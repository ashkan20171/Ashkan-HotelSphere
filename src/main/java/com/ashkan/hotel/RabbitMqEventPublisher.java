package com.ashkan.hotel;

import java.time.LocalDateTime;
import java.util.UUID;

/** Small command-line publisher useful for integration checks and external hotel services. */
public final class RabbitMqEventPublisher {
    private RabbitMqEventPublisher() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: RabbitMqEventPublisher <event.type> <aggregateId> <url-encoded-payload> [eventId]");
            System.out.println("Example payload: channel=EMAIL&recipient=guest%40example.com&guestName=Alex&bookingId=BK-1&language=en");
            return;
        }
        String id=args.length>3?args[3]:UUID.randomUUID().toString();
        RabbitMqAmqpTransport.HotelEvent event=new RabbitMqAmqpTransport.HotelEvent(id,args[0],args[1],args[2],LocalDateTime.now());
        try(RabbitMqAmqpTransport transport=new RabbitMqAmqpTransport(RabbitMqAmqpTransport.Config.fromEnvironment())) {
            transport.publish(event);
        }
        System.out.println("Published event " + id + " (" + args[0] + ")");
    }
}
