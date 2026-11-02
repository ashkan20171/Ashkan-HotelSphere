package com.ashkan.hotel;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * RabbitMQ AMQP 0-9-1 transport. Uses the official RabbitMQ Java client at runtime;
 * reflection keeps the desktop source compilable with plain javac when Maven dependencies are not installed.
 */
public final class RabbitMqAmqpTransport implements AutoCloseable {
    public record Config(String host, int port, String username, String password, String virtualHost,
                         String exchange, String queue, String deadLetterExchange, String deadLetterQueue) {
        public Config {
            host = value(host, "localhost"); username = value(username, "guest"); password = value(password, "guest");
            virtualHost = value(virtualHost, "/"); exchange = value(exchange, "ashkan.hotel.events");
            queue = value(queue, "ashkan.hotel.notifications"); deadLetterExchange = value(deadLetterExchange, "ashkan.hotel.events.dlx");
            deadLetterQueue = value(deadLetterQueue, "ashkan.hotel.notifications.dead");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid AMQP port");
        }
        public static Config fromEnvironment() {
            return new Config(System.getenv("RABBITMQ_HOST"), integerEnv("RABBITMQ_PORT", 5672), System.getenv("RABBITMQ_USER"),
                    System.getenv("RABBITMQ_PASSWORD"), System.getenv("RABBITMQ_VHOST"), System.getenv("RABBITMQ_EXCHANGE"),
                    System.getenv("RABBITMQ_QUEUE"), System.getenv("RABBITMQ_DLX"), System.getenv("RABBITMQ_DLQ"));
        }
        private static int integerEnv(String key, int fallback) { try { return Integer.parseInt(System.getenv().getOrDefault(key, "" + fallback)); } catch (Exception e) { return fallback; } }
        private static String value(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim(); }
    }
    public record HotelEvent(String id, String type, String aggregateId, String payload, LocalDateTime occurredAt) {}
    @FunctionalInterface public interface EventHandler { void handle(HotelEvent event) throws Exception; }
    private final Config config;
    private Object connection;
    private Object publisherChannel;
    private Object consumerChannel;

    public RabbitMqAmqpTransport(Config config) { this.config = Objects.requireNonNull(config); }

    public synchronized void connect() throws Exception {
        if (connection != null) return;
        Class<?> factoryType = Class.forName("com.rabbitmq.client.ConnectionFactory");
        Object factory = factoryType.getConstructor().newInstance();
        call(factory, "setHost", config.host()); call(factory, "setPort", config.port());
        call(factory, "setUsername", config.username()); call(factory, "setPassword", config.password());
        call(factory, "setVirtualHost", config.virtualHost()); call(factory, "setAutomaticRecoveryEnabled", true);
        call(factory, "setNetworkRecoveryInterval", 5000L);
        connection = call(factory, "newConnection");
        publisherChannel = call(connection, "createChannel"); consumerChannel = call(connection, "createChannel");
        declareTopology(publisherChannel);
        call(publisherChannel, "confirmSelect");
        call(consumerChannel, "basicQos", 10);
    }

    public synchronized void publish(HotelEvent event) throws Exception {
        Objects.requireNonNull(event); connect();
        String body = encode(event);
        Object properties = persistentProperties(event.id());
        call(publisherChannel, "basicPublish", config.exchange(), event.type(), properties, body.getBytes(StandardCharsets.UTF_8));
        call(publisherChannel, "waitForConfirmsOrDie", 5000L);
    }

    /** Starts a manual-ack consumer. Handler failures are nacked without requeue so RabbitMQ routes to DLQ. */
    public synchronized String consume(EventHandler handler, EventIdempotencyStore inbox) throws Exception {
        Objects.requireNonNull(handler); Objects.requireNonNull(inbox); connect();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> deliverType = Class.forName("com.rabbitmq.client.DeliverCallback", true, loader);
        Class<?> cancelType = Class.forName("com.rabbitmq.client.CancelCallback", true, loader);
        Object deliver = Proxy.newProxyInstance(loader, new Class<?>[]{deliverType}, (proxy, method, args) -> {
            if (!method.getName().equals("handle")) return null;
            String tag = (String) args[0]; Object delivery = args[1];
            Object envelope = call(delivery, "getEnvelope"); long deliveryTag = ((Number) call(envelope, "getDeliveryTag")).longValue();
            Object props = call(delivery, "getProperties"); String messageId = (String) call(props, "getMessageId");
            try {
                if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("AMQP messageId is missing");
                if (!inbox.contains(messageId)) { HotelEvent event = decode(new String((byte[]) call(delivery, "getBody"), StandardCharsets.UTF_8));
                    if (!messageId.equals(event.id())) throw new IllegalArgumentException("AMQP messageId does not match event ID");
                    handler.handle(event); inbox.markProcessed(messageId);
                }
                call(consumerChannel, "basicAck", deliveryTag, false);
            } catch (Throwable failure) {
                call(consumerChannel, "basicNack", deliveryTag, false, false);
            }
            return null;
        });
        Object cancel = Proxy.newProxyInstance(loader, new Class<?>[]{cancelType}, (p, m, a) -> null);
        return (String) call(consumerChannel, "basicConsume", config.queue(), false, deliver, cancel);
    }

    private void declareTopology(Object channel) throws Exception {
        call(channel, "exchangeDeclare", config.exchange(), "topic", true);
        call(channel, "exchangeDeclare", config.deadLetterExchange(), "direct", true);
        Map<String, Object> args = new HashMap<>(); args.put("x-dead-letter-exchange", config.deadLetterExchange()); args.put("x-dead-letter-routing-key", "failed");
        call(channel, "queueDeclare", config.queue(), true, false, false, args);
        call(channel, "queueDeclare", config.deadLetterQueue(), true, false, false, new HashMap<>());
        call(channel, "queueBind", config.queue(), config.exchange(), "#");
        call(channel, "queueBind", config.deadLetterQueue(), config.deadLetterExchange(), "failed");
    }
    private static Object persistentProperties(String id) throws Exception {
        Class<?> builderType = Class.forName("com.rabbitmq.client.AMQP$BasicProperties$Builder");
        Object builder = builderType.getConstructor().newInstance(); call(builder, "contentType", "application/json; charset=utf-8");
        call(builder, "deliveryMode", 2); call(builder, "messageId", id); return call(builder, "build");
    }
    private static String encode(HotelEvent e) {
        return String.join("\n", "ASHKAN-EVENT-V1", b64(e.id()), b64(e.type()), b64(e.aggregateId()), b64(e.payload()), b64(e.occurredAt().toString()));
    }
    private static HotelEvent decode(String body) {
        String[] p = body.split("\\n", -1); if (p.length != 6 || !p[0].equals("ASHKAN-EVENT-V1")) throw new IllegalArgumentException("Invalid event envelope");
        return new HotelEvent(un64(p[1]), un64(p[2]), un64(p[3]), un64(p[4]), LocalDateTime.parse(un64(p[5])));
    }
    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(Objects.requireNonNullElse(s, "").getBytes(StandardCharsets.UTF_8)); }
    private static String un64(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    private static Object call(Object target, String name, Object... args) throws Exception {
        Method found = null;
        for (Method m : target.getClass().getMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length && compatible(m.getParameterTypes(), args)) { found = m; break; }
        if (found == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
        try { return found.invoke(target, args); } catch (InvocationTargetException e) { Throwable c = e.getCause(); if (c instanceof Exception ex) throw ex; throw new RuntimeException(c); }
    }
    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i=0;i<types.length;i++) { if (args[i] == null) { if (types[i].isPrimitive()) return false; continue; }
            Class<?> t=types[i].isPrimitive()?wrap(types[i]):types[i]; if (!t.isAssignableFrom(args[i].getClass())) return false; }
        return true;
    }
    private static Class<?> wrap(Class<?> c) { if(c==int.class)return Integer.class;if(c==long.class)return Long.class;if(c==boolean.class)return Boolean.class;return c; }
    @Override public synchronized void close() {
        closeQuietly(consumerChannel); closeQuietly(publisherChannel); closeQuietly(connection);
        consumerChannel=null;publisherChannel=null;connection=null;
    }
    private static void closeQuietly(Object value) { if(value!=null)try{call(value,"close");}catch(Exception ignored){} }
}
