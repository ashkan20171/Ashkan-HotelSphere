package com.ashkan.hotel;

import java.nio.file.*;

public final class EventIdempotencyStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("ashkan-inbox-test");Path file=dir.resolve("inbox.txt");
        EventIdempotencyStore store=new EventIdempotencyStore(file);
        if(!store.markProcessed("evt-1"))throw new AssertionError("first mark must succeed");
        if(store.markProcessed("evt-1"))throw new AssertionError("duplicate mark must be rejected");
        EventIdempotencyStore reloaded=new EventIdempotencyStore(file);
        if(!reloaded.contains("evt-1")||reloaded.size()!=1)throw new AssertionError("inbox must survive restart");
        boolean rejected=false;try{reloaded.contains(" ");}catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("blank event id must be rejected");
        System.out.println("EventIdempotencyStoreSmokeTest passed");
    }
}
