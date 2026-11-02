package com.ashkan.hotel;

import java.time.Instant;

public final class PaymentWebhookSecuritySmokeTest {
    public static void main(String[] args) {
        String secret="0123456789abcdef0123456789abcdef";
        String body="{\"eventId\":\"evt-1\",\"status\":\"CAPTURED\"}";
        long now=Instant.parse("2026-10-30T12:00:00Z").getEpochSecond();
        String signature=PaymentWebhookSecurity.sign(secret,now,body);
        check(PaymentWebhookSecurity.verify(secret,now,body,signature,Instant.ofEpochSecond(now),300),"valid signature");
        check(!PaymentWebhookSecurity.verify(secret,now,body+" ",signature,Instant.ofEpochSecond(now),300),"tampered body rejected");
        check(!PaymentWebhookSecurity.verify(secret,now,body,signature,Instant.ofEpochSecond(now+301),300),"stale timestamp rejected");
        check(!PaymentWebhookSecurity.verify(secret,now,body,"00",Instant.ofEpochSecond(now),300),"malformed signature rejected");
        check(PaymentWebhookSecurity.verify(secret,now,body,signature.toUpperCase(),Instant.ofEpochSecond(now),300),"hex signature is case insensitive");
        System.out.println("PaymentWebhookSecuritySmokeTest: 5 checks passed");
    }
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
