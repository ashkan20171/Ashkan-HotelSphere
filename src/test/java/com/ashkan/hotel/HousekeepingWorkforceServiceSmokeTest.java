package com.ashkan.hotel;
import java.nio.file.*;
import java.time.*;
public final class HousekeepingWorkforceServiceSmokeTest {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("hk-workforce-test");Path file=dir.resolve("workforce.tsv");
  HousekeepingWorkforceService s=new HousekeepingWorkforceService(file);LocalDate d=LocalDate.of(2026,10,30);
  var a=s.assign(d,HousekeepingWorkforceService.Shift.MORNING,"Sara","101",HousekeepingWorkforceService.TaskType.CHECKOUT_CLEAN,HousekeepingWorkforceService.Priority.URGENT,"checkout");
  check(s.forDate(d).size()==1,"assignment created");
  boolean duplicate=false;try{s.assign(d,HousekeepingWorkforceService.Shift.EVENING,"Ali","101",HousekeepingWorkforceService.TaskType.INSPECTION,HousekeepingWorkforceService.Priority.NORMAL,"");}catch(IllegalStateException ex){duplicate=true;}check(duplicate,"duplicate active room blocked");
  s.start(a.id(),LocalDateTime.of(2026,10,30,8,0));s.complete(a.id(),LocalDateTime.of(2026,10,30,8,45));
  var load=s.employeeLoads(d,d).get(0);check(load.completed()==1&&load.totalMinutes()==45&&load.completionRate()==100.0,"productivity calculated");
  check(s.shiftSummary(d).get(0).completed()==1,"shift summary");check(s.csv().contains("Sara"),"csv");
  HousekeepingWorkforceService reopened=new HousekeepingWorkforceService(file);check(reopened.all().size()==1&&reopened.all().get(0).status()==HousekeepingWorkforceService.Status.COMPLETED,"persistence");
  boolean invalid=false;try{reopened.start(a.id(),LocalDateTime.now());}catch(IllegalStateException ex){invalid=true;}check(invalid,"completed task immutable");
  System.out.println("HousekeepingWorkforceServiceSmokeTest passed");
 }
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
