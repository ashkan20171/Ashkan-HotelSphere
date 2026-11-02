package com.ashkan.hotel;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class HotelApiGatewaySmokeTest {
    public static void main(String[] args) throws Exception {
        try(NotificationHttpService notification=new NotificationHttpService(0,new NotificationService(java.nio.file.Files.createTempDirectory("gateway-test").resolve("outbox.tsv"),m->{}));
            HotelApiGateway gateway=new HotelApiGateway(0,Map.of("/notifications",URI.create("http://127.0.0.1:"+notification.port()+"/")))) {
            notification.start();gateway.start();HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> health=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gateway.port()+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(health.statusCode()!=200||!health.body().contains("UP"))throw new AssertionError("gateway health failed");
            HttpResponse<String> metrics=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gateway.port()+"/notifications/api/v1/metrics")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(metrics.statusCode()!=200||!metrics.body().contains("queued"))throw new AssertionError("gateway proxy failed: "+metrics.body());
        }
        System.out.println("HotelApiGatewaySmokeTest passed");
    }
}
