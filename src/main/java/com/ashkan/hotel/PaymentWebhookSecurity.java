package com.ashkan.hotel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Verifies provider callbacks using HMAC-SHA256 over "timestamp.body". */
public final class PaymentWebhookSecurity {
    private PaymentWebhookSecurity() {}
    public static String sign(String secret, long timestamp, String body) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("Webhook secret must contain at least 32 characters");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("HMAC is unavailable",e); }
    }
    public static boolean verify(String secret,long timestamp,String body,String signature,Instant now,long toleranceSeconds) {
        if(signature==null||!signature.matches("(?i)^[0-9a-f]{64}$")||now==null||toleranceSeconds<1)return false;
        if(Math.abs(now.getEpochSecond()-timestamp)>toleranceSeconds)return false;
        byte[] expected=sign(secret,timestamp,body).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied=signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected,supplied);
    }
}
