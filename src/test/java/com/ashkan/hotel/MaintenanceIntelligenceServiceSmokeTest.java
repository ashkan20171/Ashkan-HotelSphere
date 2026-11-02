package com.ashkan.hotel;

import java.nio.file.Files;
import java.time.LocalDate;

public final class MaintenanceIntelligenceServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        var dir=Files.createTempDirectory("ashkan-maintenance-test");var file=dir.resolve("maintenance.tsv");
        var service=new MaintenanceIntelligenceService(file);LocalDate today=LocalDate.of(2026,10,30);
        var plan=service.savePlan("101","HVAC",30,today.minusDays(35),"Alex");
        if(!plan.nextDue().equals(today.minusDays(5)))throw new AssertionError("next due date");
        if(service.dueItems(today).size()!=1||service.dueItems(today).get(0).priority()!=MaintenanceIntelligenceService.Priority.HIGH)throw new AssertionError("overdue plan alert");
        var order=service.report("101","Boiler","Pressure inspection",MaintenanceIntelligenceService.Priority.HIGH,today,today.plusDays(2),"Sam","Noise reported");
        if(service.workOrders().size()!=1)throw new AssertionError("work order creation");
        service.changeStatus(order.id(),MaintenanceIntelligenceService.Status.IN_PROGRESS);
        var reloaded=new MaintenanceIntelligenceService(file);
        if(reloaded.workOrders().get(0).status()!=MaintenanceIntelligenceService.Status.IN_PROGRESS||reloaded.plans().size()!=1)throw new AssertionError("persistence");
        reloaded.changeStatus(order.id(),MaintenanceIntelligenceService.Status.COMPLETED);
        try{reloaded.changeStatus(order.id(),MaintenanceIntelligenceService.Status.OPEN);throw new AssertionError("completed order reopened");}catch(IllegalStateException expected){}
        if(!reloaded.csv().contains("Pressure inspection"))throw new AssertionError("CSV export");
        try{reloaded.savePlan("101","Lift",0,today,"Alex");throw new AssertionError("invalid interval accepted");}catch(IllegalArgumentException expected){}
        try{reloaded.report("101","HVAC","Bad date",MaintenanceIntelligenceService.Priority.LOW,today,today.minusDays(1),"","");throw new AssertionError("invalid due date accepted");}catch(IllegalArgumentException expected){}
        System.out.println("MaintenanceIntelligenceServiceSmokeTest passed");
    }
}
