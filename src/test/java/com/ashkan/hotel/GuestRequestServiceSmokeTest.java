package com.ashkan.hotel;
import java.nio.file.*;
public final class GuestRequestServiceSmokeTest {
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("hotel-requests-test-");Path file=dir.resolve("requests.tsv");
        GuestRequestService svc=new GuestRequestService(file);
        var r=svc.create("101","Maintenance","Air conditioner","Sara",GuestRequestService.Priority.HIGH);
        if(r.status()!=GuestRequestService.Status.OPEN)throw new AssertionError();
        try{svc.changeStatus(r.id(),GuestRequestService.Status.RESOLVED);throw new AssertionError("Skipped progress");}
        catch(IllegalStateException expected){}
        svc.changeStatus(r.id(),GuestRequestService.Status.IN_PROGRESS);
        svc.changeStatus(r.id(),GuestRequestService.Status.RESOLVED);
        if(new GuestRequestService(file).all().get(0).status()!=GuestRequestService.Status.RESOLVED)throw new AssertionError("Persistence failed");
        Files.deleteIfExists(file);Files.deleteIfExists(dir);
        System.out.println("GuestRequestServiceSmokeTest passed");
    }
}
