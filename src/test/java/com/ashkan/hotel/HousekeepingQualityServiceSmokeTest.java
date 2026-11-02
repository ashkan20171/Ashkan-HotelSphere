package com.ashkan.hotel;
import java.nio.file.*;import java.time.*;
public final class HousekeepingQualityServiceSmokeTest {
 public static void main(String[] args)throws Exception{
  Path f=Files.createTempDirectory("quality-test").resolve("quality.tsv");HousekeepingQualityService s=new HousekeepingQualityService(f);
  var ok=s.inspect("101","Supervisor",5,5,4,5,5,"Ready","before.jpg","after.jpg",LocalDateTime.of(2026,10,30,9,0));
  if(ok.status()!=HousekeepingQualityService.Status.APPROVED||ok.score()!=96)throw new AssertionError("approval/score");
  var bad=s.inspect("102","Supervisor",2,3,2,3,2,"Bathroom issue","","",LocalDateTime.now());
  if(bad.status()!=HousekeepingQualityService.Status.REWORK_REQUIRED)throw new AssertionError("rework");
  if(s.approved()!=1||s.rework()!=1||s.all().size()!=2)throw new AssertionError("metrics");
  HousekeepingQualityService reloaded=new HousekeepingQualityService(f);if(reloaded.all().size()!=2||!reloaded.forRoom("101").get(0).beforePhoto().equals("before.jpg"))throw new AssertionError("persistence");
  if(!reloaded.csv().contains("REWORK_REQUIRED"))throw new AssertionError("csv");
  try{reloaded.inspect("103","S",0,1,1,1,1,"","","",LocalDateTime.now());throw new AssertionError("invalid score accepted");}catch(IllegalArgumentException expected){}
  System.out.println("HousekeepingQualityServiceSmokeTest passed");
 }
}
