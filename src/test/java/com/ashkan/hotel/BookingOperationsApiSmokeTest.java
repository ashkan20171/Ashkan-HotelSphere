package com.ashkan.hotel;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BookingOperationsApiSmokeTest {
    public static void main(String[] args) throws Exception {
        BookingOperationsApiV2 api=new BookingOperationsApiV2("jdbc:postgresql://127.0.0.1:5432/unused","u","p",1);
        try {
            if(!api.supports("/api/v2/booking-operations"))throw new AssertionError("Calendar route missing");
            for(String action:new String[]{"check-in","check-out","cancel","room"})if(!api.supports("/api/v2/booking-operations/BK-1/"+action))throw new AssertionError("Action route missing: "+action);
        } finally {api.close();}
        String sql=Files.readString(Path.of("src/main/resources/db/migration/booking/V6__front_desk_workflow.sql"));
        for(String token:new String[]{"cancellation_policy","free_cancellation_hours","late_cancellation_fee_percent","booking_status_history","ix_booking_status_history_booking"})if(!sql.contains(token))throw new AssertionError("Migration missing "+token);
        System.out.println("BookingOperationsApiSmokeTest: PASS");
    }
}
