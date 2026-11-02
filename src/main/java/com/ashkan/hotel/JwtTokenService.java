package com.ashkan.hotel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Minimal HS256 JWT issuer/verifier using only the JDK. Keep the secret outside source control. */
public final class JwtTokenService {
    public record Claims(String subject, String role, long expiresAt) {}
    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private final byte[] secret;
    private final long lifetimeSeconds;

    public JwtTokenService(String secret, long lifetimeSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        if (lifetimeSeconds < 60 || lifetimeSeconds > 86_400) throw new IllegalArgumentException("JWT lifetime must be 60..86400 seconds");
        this.secret = secret.getBytes(StandardCharsets.UTF_8); this.lifetimeSeconds = lifetimeSeconds;
    }
    public String issue(String subject, String role) {
        if (subject == null || !subject.matches("[A-Za-z0-9_.-]{3,64}")) throw new IllegalArgumentException("Invalid subject");
        if (!"Admin".equals(role) && !"Receptionist".equals(role)) throw new IllegalArgumentException("Invalid role");
        long now = Instant.now().getEpochSecond(), exp = now + lifetimeSeconds;
        String payload = b64(("{\"sub\":\""+subject+"\",\"role\":\""+role+"\",\"iat\":"+now+",\"exp\":"+exp+"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = HEADER + "." + payload;
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
    }
    public Claims verify(String token) {
        try {
            if (token == null || token.length() > 8192) return null;
            String[] p=token.split("\\.",-1); if(p.length!=3 || !HEADER.equals(p[0])) return null;
            byte[] actual=Base64.getUrlDecoder().decode(p[2]); if(!java.security.MessageDigest.isEqual(sign(p[0]+"."+p[1]),actual))return null;
            String json=new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8);
            String sub=field(json,"sub"), role=field(json,"role"); long exp=number(json,"exp");
            if(sub==null || (!"Admin".equals(role)&&!"Receptionist".equals(role)) || exp<=Instant.now().getEpochSecond())return null;
            return new Claims(sub,role,exp);
        } catch(Exception e){return null;}
    }
    private byte[] sign(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String field(String json,String name){var m=java.util.regex.Pattern.compile("\\\""+name+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);return m.find()?m.group(1):null;}
    private static long number(String json,String name){var m=java.util.regex.Pattern.compile("\\\""+name+"\\\"\\s*:\\s*(\\d+)").matcher(json);return m.find()?Long.parseLong(m.group(1)):0;}
    private static String b64(String value){return b64(value.getBytes(StandardCharsets.UTF_8));}
    private static String b64(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
}
