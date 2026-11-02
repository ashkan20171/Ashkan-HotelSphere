package com.ashkan.hotel;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NotificationServiceSmokeTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("notification-test"), file=dir.resolve("outbox.tsv");
        LocalDateTime now=LocalDateTime.of(2026,10,30,10,0); AtomicInteger calls=new AtomicInteger();
        NotificationService.DeliveryGateway flaky=m->{if(calls.incrementAndGet()<3)throw new java.io.IOException("temporary gateway failure");};
        NotificationService s=new NotificationService(file,flaky);
        var n=s.enqueueTemplate(NotificationService.Template.BOOKING_CONFIRMED,NotificationService.Channel.EMAIL,"guest@example.com","Guest","BK-77","en",now);
        if(!n.body().contains("BK-77")||n.status()!=NotificationService.Status.QUEUED)throw new AssertionError("template enqueue");
        if(s.processDue(now)!=1||s.messages().get(0).attempts()!=1||s.messages().get(0).status()!=NotificationService.Status.QUEUED)throw new AssertionError("first retry");
        if(s.processDue(now.plusSeconds(30))!=0)throw new AssertionError("early retry");
        if(s.processDue(now.plusMinutes(1))!=1||s.messages().get(0).attempts()!=2)throw new AssertionError("second retry");
        if(s.processDue(now.plusMinutes(3))!=1||s.messages().get(0).status()!=NotificationService.Status.SENT||s.messages().get(0).attempts()!=3)throw new AssertionError("successful delivery");
        try{s.enqueue(NotificationService.Channel.EMAIL,"not-an-email","x","body",now);throw new AssertionError("invalid email accepted");}catch(IllegalArgumentException expected){}
        try{s.enqueue(NotificationService.Channel.SMS,"12","x","body",now);throw new AssertionError("invalid phone accepted");}catch(IllegalArgumentException expected){}
        NotificationService loaded=new NotificationService(file,m->{});if(loaded.messages().size()!=1||loaded.metrics(now).sent()!=1||!loaded.csv().contains("guest@example.com"))throw new AssertionError("persistence/csv");

        AtomicInteger failCalls=new AtomicInteger();NotificationService fail=new NotificationService(dir.resolve("failed.tsv"),m->{failCalls.incrementAndGet();throw new java.io.IOException("offline");});
        fail.enqueue(NotificationService.Channel.SMS,"+4915112345678","Hotel","Hello",now);
        LocalDateTime t=now;for(int i=0;i<4;i++){if(fail.processDue(t)!=1)throw new AssertionError("expected due retry "+i);t=t.plusMinutes(1L<<i);}
        if(fail.metrics(t).failed()!=1||failCalls.get()!=4)throw new AssertionError("retry exhaustion");

        Path previewLog=dir.resolve("preview.log");NotificationService preview=new NotificationService(dir.resolve("preview.tsv"),new NotificationService.PreviewGateway(previewLog));
        preview.enqueueTemplate(NotificationService.Template.PAYMENT_RECEIVED,NotificationService.Channel.SMS,"+4915112345678","Guest","BK-88","fa",now);
        if(preview.processDue(now)!=1||!Files.readString(previewLog).contains("BK-88"))throw new AssertionError("preview gateway");

        try(NotificationHttpService api=new NotificationHttpService(0,loaded)){api.start();HttpClient client=HttpClient.newHttpClient();URI base=URI.create("http://127.0.0.1:"+api.port());
            var health=client.send(HttpRequest.newBuilder(base.resolve("/health")).GET().build(),HttpResponse.BodyHandlers.ofString());if(health.statusCode()!=200||!health.body().contains("notification"))throw new AssertionError("health endpoint");
            var post=client.send(HttpRequest.newBuilder(base.resolve("/api/v1/notifications")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("channel=EMAIL&recipient=api%40example.com&subject=Hello&body=Welcome")).build(),HttpResponse.BodyHandlers.ofString());if(post.statusCode()!=202||!post.body().contains("QUEUED"))throw new AssertionError("enqueue endpoint: "+post.body());
            var metrics=client.send(HttpRequest.newBuilder(base.resolve("/api/v1/metrics")).GET().build(),HttpResponse.BodyHandlers.ofString());if(metrics.statusCode()!=200||!metrics.body().contains("\"queued\":1"))throw new AssertionError("metrics endpoint");
        }
        System.out.println("NotificationServiceSmokeTest passed");
    }
}
