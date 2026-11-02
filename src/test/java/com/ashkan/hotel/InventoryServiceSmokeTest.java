package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;

public final class InventoryServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("inventory-test"); Path file=dir.resolve("inventory.tsv");
        Clock clock=Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"),ZoneOffset.UTC);
        InventoryService service=new InventoryService(file,clock);
        service.saveItem(new InventoryService.Item("SOAP-01","Hand soap","Housekeeping","bottle",new BigDecimal("5"),new BigDecimal("2.50"),"CleanCo"));
        service.receive("SOAP-01",new BigDecimal("10"),new BigDecimal("2.75"),"CleanCo","PO-1","Initial receipt");
        service.consume("SOAP-01",new BigDecimal("4"),"ROOM-101","Guest room restock");
        check(service.stockOf("SOAP-01").compareTo(new BigDecimal("6"))==0,"receipt and consumption balance");
        check(service.lowStock().isEmpty(),"stock above minimum");
        service.consume("SOAP-01",new BigDecimal("2"),"ROOM-102","");
        check(service.lowStock().size()==1,"low stock alert at minimum");
        check(service.totalValue().compareTo(new BigDecimal("10.00"))==0,"valuation uses configured unit cost");
        boolean rejected=false;try{service.consume("SOAP-01",new BigDecimal("5"),"","overspend");}catch(IllegalStateException ex){rejected=true;}
        check(rejected,"insufficient stock rejected");
        service.adjust("SOAP-01",new BigDecimal("-1"),"COUNT-1","Damaged");
        check(service.stockOf("SOAP-01").compareTo(new BigDecimal("3"))==0,"negative adjustment");
        InventoryService reopened=new InventoryService(file,clock);
        check(reopened.stockOf("SOAP-01").compareTo(new BigDecimal("3"))==0,"persistence reload");
        check(reopened.movements().size()==4,"movement ledger persisted");
        check(InventoryService.stockCsv(reopened.stock()).contains("SOAP-01"),"stock CSV");
        check(reopened.movementCsv().contains("CONSUMPTION"),"movement CSV");
        try{reopened.saveItem(new InventoryService.Item("BAD","Bad","","unit",new BigDecimal("-1"),BigDecimal.ZERO,""));throw new AssertionError("negative minimum accepted");}catch(IllegalArgumentException expected){}
        Files.deleteIfExists(file);Files.deleteIfExists(dir);
        System.out.println("InventoryServiceSmokeTest: OK");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
