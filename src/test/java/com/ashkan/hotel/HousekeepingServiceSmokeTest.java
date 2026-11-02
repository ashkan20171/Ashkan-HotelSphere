package com.ashkan.hotel;
import java.nio.file.*;
public final class HousekeepingServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("hotel-housekeeping-test");
        Path file = dir.resolve("states.properties");
        HousekeepingService service = new HousekeepingService(file);
        if (service.status("101") != HousekeepingService.Status.CLEAN) throw new AssertionError();
        service.update("101", HousekeepingService.Status.DIRTY);
        if (new HousekeepingService(file).status("101") != HousekeepingService.Status.DIRTY) throw new AssertionError();
        service.update("101", HousekeepingService.Status.INSPECTION);
        if (new HousekeepingService(file).status("101") != HousekeepingService.Status.INSPECTION) throw new AssertionError();
        System.out.println("HousekeepingServiceSmokeTest passed");
    }
}
