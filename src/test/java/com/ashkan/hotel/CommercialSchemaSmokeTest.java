package com.ashkan.hotel;

import java.nio.file.*;

public final class CommercialSchemaSmokeTest {
    public static void main(String[] args) throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/booking/V5__room_catalog_and_rate_plans.sql"));
        for(String token:new String[]{"CREATE TABLE IF NOT EXISTS hotel_rooms","CREATE TABLE IF NOT EXISTS rate_plans","idx_hotel_rooms_type_status","idx_rate_plans_type_active","CHECK (capacity BETWEEN 1 AND 20)","CHECK (base_rate > 0)"}) if(!sql.contains(token)) throw new AssertionError("Missing schema feature: "+token);
        var room=new CommercialRepository.Room("r1","DELUXE","204",2,2,"ACTIVE",java.util.List.of("WiFi","TV"));
        if(room.amenities().size()!=2) throw new AssertionError("Room amenities missing");
        try { new CommercialRepository.Room("r2","DELUXE","205",-1,2,"ACTIVE",java.util.List.of()); throw new AssertionError("Negative floor accepted"); } catch(IllegalArgumentException expected) { }
        System.out.println("CommercialSchemaSmokeTest: PASS");
    }
}
