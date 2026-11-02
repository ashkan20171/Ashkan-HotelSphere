package com.ashkan.hotel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP bootstrap and static bilingual portal checks; database routes are intentionally not invoked. */
public final class GuestPortalHttpServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("GUEST_OTP_SECRET","smoke-test-secret-value-longer-than-thirty-two-characters");
        try (GuestPortalHttpService portal = new GuestPortalHttpService(0,
                "jdbc:postgresql://127.0.0.1:1/booking", "jdbc:postgresql://127.0.0.1:1/guest", "unused", "unused", 1)) {
            portal.start();
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> health=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+portal.port()+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
            check(health.statusCode()==200 && health.body().contains("guest-portal"),"health endpoint");
            HttpResponse<String> page=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+portal.port()+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
            check(page.statusCode()==200 && page.body().contains("Ashkan HotelSphere") && page.body().contains("lang='fa'"),"bilingual portal page");
            check(page.headers().firstValue("content-type").orElse("").contains("text/html"),"HTML content type");
        }
        System.out.println("GuestPortalHttpServiceSmokeTest: 3 checks passed");
    }
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
