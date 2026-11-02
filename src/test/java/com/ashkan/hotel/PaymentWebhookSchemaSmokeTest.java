package com.ashkan.hotel;

import java.nio.file.*;

public final class PaymentWebhookSchemaSmokeTest {
    public static void main(String[] args) throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/payment/V5__signed_webhooks_and_financial_audit.sql"));
        check(sql.contains("UNIQUE(provider,event_id)"),"unique provider event constraint");
        check(sql.contains("financial_audit_events"),"financial audit table");
        check(sql.contains("payload_sha256"),"payload digest persisted");
        String service=Files.readString(Path.of("src/main/java/com/ashkan/hotel/GuestPortalHttpService.java"));
        check(service.contains("/api/portal/webhooks/payment"),"webhook route registered");
        check(service.contains("Invalid payment state transition"),"state transition guard");
        System.out.println("PaymentWebhookSchemaSmokeTest: 5 checks passed");
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
