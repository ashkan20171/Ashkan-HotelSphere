package com.ashkan.hotel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived email OTP challenges and opaque guest sessions. Challenges/sessions are node-local. */
public final class GuestAuthService {
    @FunctionalInterface public interface OtpDelivery { void send(String email, String code) throws Exception; }
    private record Challenge(String guestId, String codeMac, long expiresAt, int attempts) {}
    private record Session(String guestId, long expiresAt) {}
    private final Map<String,Challenge> challenges=new ConcurrentHashMap<>();
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final byte[] secret;
    private final OtpDelivery delivery;
    private final long challengeTtlMillis=Duration.ofMinutes(5).toMillis();
    private final long sessionTtlMillis=Duration.ofHours(8).toMillis();

    public GuestAuthService(String secret, OtpDelivery delivery) {
        if(secret==null || secret.length()<32) throw new IllegalArgumentException("GUEST_OTP_SECRET must contain at least 32 characters");
        this.secret=secret.getBytes(StandardCharsets.UTF_8); this.delivery=Objects.requireNonNull(delivery);
    }
    public void request(String email,String guestId)throws Exception {
        byte[] bytes=new byte[4];random.nextBytes(bytes);int n=Math.floorMod(java.nio.ByteBuffer.wrap(bytes).getInt(),1_000_000);
        String code=String.format(Locale.ROOT,"%06d",n);
        challenges.put(email,new Challenge(guestId,mac(email+":"+code),System.currentTimeMillis()+challengeTtlMillis,0));
        delivery.send(email,code);
    }
    public String verify(String email,String code) {
        Challenge c=challenges.get(email);long now=System.currentTimeMillis();
        if(c==null||c.expiresAt()<now) { challenges.remove(email); throw new IllegalArgumentException("Invalid or expired verification code"); }
        if(c.attempts()>=5) { challenges.remove(email); throw new IllegalArgumentException("Too many verification attempts; request a new code"); }
        if(code==null||!code.matches("\\d{6}")||!MessageDigest.isEqual(c.codeMac().getBytes(StandardCharsets.UTF_8),mac(email+":"+code).getBytes(StandardCharsets.UTF_8))) {
            challenges.replace(email,c,new Challenge(c.guestId(),c.codeMac(),c.expiresAt(),c.attempts()+1));
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        challenges.remove(email,c);byte[] token=new byte[32];random.nextBytes(token);String session=Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.put(sha256(session),new Session(c.guestId(),now+sessionTtlMillis));return session;
    }
    public Optional<String> guestId(String token) {
        if(token==null||token.isBlank())return Optional.empty();String key=sha256(token);Session s=sessions.get(key);
        if(s==null)return Optional.empty();if(s.expiresAt()<System.currentTimeMillis()){sessions.remove(key);return Optional.empty();}return Optional.of(s.guestId());
    }
    public void logout(String token){if(token!=null)sessions.remove(sha256(token));}
    public static OtpDelivery consoleDelivery(){return (email,code)->System.getLogger(GuestAuthService.class.getName()).log(System.Logger.Level.INFO,"DEV OTP for {0}: {1}",email,code);}
    private String mac(String value){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
