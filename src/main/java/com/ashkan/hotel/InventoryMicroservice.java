package com.ashkan.hotel;

import java.nio.file.Path;

/** Standalone inventory bounded-context service process. */
public final class InventoryMicroservice {
    private InventoryMicroservice() {}
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(System.getenv().getOrDefault("PORT", "8094"));
        Path data = Path.of(System.getenv().getOrDefault("DATA_FILE", "data/inventory.tsv"));
        MicroserviceRuntime runtime = new MicroserviceRuntime("inventory", "inventory-items", "inventory.updated", port, data);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
        runtime.start();
        System.out.println("Inventory service listening on port " + runtime.port());
    }
}
