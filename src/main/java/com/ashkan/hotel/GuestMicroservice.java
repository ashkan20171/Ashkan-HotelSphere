package com.ashkan.hotel;

import java.nio.file.Path;

/** Standalone guest bounded-context service process. */
public final class GuestMicroservice {
    private GuestMicroservice() {}
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8093"));
        Path data = Path.of(System.getenv().getOrDefault("DATA_FILE", "data/guest.tsv"));
        MicroserviceRuntime runtime = new MicroserviceRuntime("guest", "guests", "guest.created", port, data);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
        runtime.start();
        System.out.println("Guest service listening on port " + runtime.port());
    }
}
