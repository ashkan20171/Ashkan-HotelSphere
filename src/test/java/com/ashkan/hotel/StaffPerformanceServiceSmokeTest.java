package com.ashkan.hotel;
import java.util.List;
public final class StaffPerformanceServiceSmokeTest {
 public static void main(String[] args) {
  var events=List.of(new AuditService.Event(1,"2026-01-01T00:00:00Z","task","CREATED","1","","alice"),new AuditService.Event(2,"2026-01-02T00:00:00Z","task","STATUS_CHANGED","1","","alice"),new AuditService.Event(3,"2026-01-03T00:00:00Z","task","CREATED","2","","SYSTEM"));
  var summaries=StaffPerformanceService.aggregate(events);
  if(summaries.size()!=1 || summaries.get(0).total()!=2 || summaries.get(0).created()!=1 || summaries.get(0).statusChanges()!=1 || !summaries.get(0).actor().equals("alice")) throw new AssertionError("Staff activity aggregation failed");
  System.out.println("StaffPerformanceServiceSmokeTest OK");
 }
}
