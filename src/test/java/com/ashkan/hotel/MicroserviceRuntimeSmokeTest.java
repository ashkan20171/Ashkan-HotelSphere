package com.ashkan.hotel;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

public final class MicroserviceRuntimeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path data=Files.createTempDirectory("ashkan-ms-test").resolve("bookings.tsv");
        String id;
        try(MicroserviceRuntime service=new MicroserviceRuntime("booking","bookings","booking.created",0,data)) {
            service.start(); HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> health=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+service.port()+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(health.statusCode()!=200||!health.body().contains("UP"))throw new AssertionError("health failed");
            HttpResponse<String> create=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+service.port()+"/api/v1/bookings")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"guest\":\"Alex\"}")).build(),HttpResponse.BodyHandlers.ofString());
            if(create.statusCode()!=201)throw new AssertionError("create failed: "+create.body());
            id=create.body().split("\\\"")[3];
            HttpResponse<String> get=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+service.port()+"/api/v1/bookings/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(get.statusCode()!=200||!get.body().contains("Alex"))throw new AssertionError("get failed");
        }
        try(MicroserviceRuntime restarted=new MicroserviceRuntime("booking","bookings","booking.created",0,data)) {
            restarted.start();HttpClient client=HttpClient.newHttpClient();
            HttpResponse<String> get=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+restarted.port()+"/api/v1/bookings/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(get.statusCode()!=200)throw new AssertionError("persistence failed");
            HttpResponse<String> delete=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+restarted.port()+"/api/v1/bookings/"+id)).DELETE().build(),HttpResponse.BodyHandlers.ofString());
            if(delete.statusCode()!=200)throw new AssertionError("delete failed");
        }
        System.out.println("MicroserviceRuntimeSmokeTest passed");
    }
}
