package com.ashkan.hotel;

import java.nio.file.Path;

/** Standalone payment bounded-context service process. */
public final class PaymentMicroservice {
    private PaymentMicroservice() {}
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8092"));
        Path data = Path.of(System.getenv().getOrDefault("DATA_FILE", "data/payment.tsv"));
        MicroserviceRuntime runtime = new MicroserviceRuntime("payment", "payments", "payment.received", port, data);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
        runtime.start();
        System.out.println("Payment service listening on port " + runtime.port());
    }
}
