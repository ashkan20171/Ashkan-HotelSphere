package com.ashkan.hotel;

import java.nio.file.Path;

/** Standalone RabbitMQ consumer process for notification-related hotel domain events. */
public final class RabbitMqNotificationWorker {
    private RabbitMqNotificationWorker() {}
    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere");
        RabbitMqAmqpTransport transport = new RabbitMqAmqpTransport(RabbitMqAmqpTransport.Config.fromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(transport::close));
        String tag = transport.consume(new NotificationEventConsumer(new NotificationService()), new EventIdempotencyStore(home.resolve("notification-event-inbox.txt")));
        System.out.println("RabbitMQ notification worker consuming; consumerTag=" + tag);
        Thread.currentThread().join();
    }
}
