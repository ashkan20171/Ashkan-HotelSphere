package com.ashkan.hotel;

import java.nio.file.Path;

/** Standalone booking bounded-context service process. */
public final class BookingMicroservice {
    private BookingMicroservice() {}
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8091"));
        Path data = Path.of(System.getenv().getOrDefault("DATA_FILE", "data/booking.tsv"));
        MicroserviceRuntime runtime = new MicroserviceRuntime("booking", "bookings", "booking.created", port, data);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
        runtime.start();
        System.out.println("Booking service listening on port " + runtime.port());
    }
}
