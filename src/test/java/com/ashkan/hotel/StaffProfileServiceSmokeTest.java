package com.ashkan.hotel;

import java.nio.file.Files;

public final class StaffProfileServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("staff-profile-test");
        var file = dir.resolve("profiles.properties");
        var service = new StaffProfileService(file);
        var profile = new StaffProfileService.Profile("Receptionist", "Alex Morgan", "alex@example.com", "+49 123", "Front Office", "Receptionist", "Night shift");
        service.save(profile);
        if (service.find("Receptionist").isEmpty()) throw new AssertionError("Profile was not persisted");
        if (service.search("alex").size() != 1) throw new AssertionError("Search by name failed");
        if (service.search("Front Office").size() != 1) throw new AssertionError("Search by department failed");
        var reopened = new StaffProfileService(file);
        if (!reopened.find("Receptionist").orElseThrow().email().equals("alex@example.com")) throw new AssertionError("Reload failed");
        try { new StaffProfileService.Profile("user", "Name", "not-an-email", "", "", "", ""); throw new AssertionError("Invalid email accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new StaffProfileService.Profile("user", "x".repeat(101), "", "", "", "", ""); throw new AssertionError("Oversized field accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("StaffProfileServiceSmokeTest PASSED");
    }
}
