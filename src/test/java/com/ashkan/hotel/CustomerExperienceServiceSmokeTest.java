package com.ashkan.hotel;
import java.nio.file.*;import java.time.*;
public final class CustomerExperienceServiceSmokeTest {
 public static void main(String[] args)throws Exception{
  Path f=Files.createTempDirectory("experience-test").resolve("data.tsv");CustomerExperienceService s=new CustomerExperienceService(f);LocalDateTime now=LocalDateTime.of(2026,10,30,10,0);
  var a=s.submitFeedback("B-1","Guest One",5,4,5,4,5,10,"Excellent stay",now);
  var b=s.submitFeedback("B-2","Guest Two",2,3,2,3,2,4,"Needs improvement",now.plusMinutes(1));
  if(a.sentiment().equals("NEGATIVE")||!b.sentiment().equals("NEGATIVE"))throw new AssertionError("sentiment");
  var c=s.createComplaint("B-2","Guest Two","Housekeeping","Room not clean","Please inspect","Supervisor",now);
  s.updateComplaint(c.id(),CustomerExperienceService.ComplaintStatus.IN_PROGRESS,"Manager","",now.plusMinutes(2));
  var m=s.metrics();if(m.responses()!=2||m.csat()!=50||m.nps()!=0||m.openComplaints()!=1)throw new AssertionError("metrics: "+m);
  try{s.submitFeedback("B-1","Guest One",5,5,5,5,5,10,"duplicate",now);throw new AssertionError("duplicate allowed");}catch(IllegalStateException expected){}
  try{s.updateComplaint(c.id(),CustomerExperienceService.ComplaintStatus.RESOLVED,"","",now);throw new AssertionError("resolution without note allowed");}catch(IllegalArgumentException expected){}
  s.updateComplaint(c.id(),CustomerExperienceService.ComplaintStatus.RESOLVED,"Manager","Re-cleaned and verified",now.plusMinutes(3));
  CustomerExperienceService loaded=new CustomerExperienceService(f);if(loaded.feedback().size()!=2||loaded.complaints().get(0).status()!=CustomerExperienceService.ComplaintStatus.RESOLVED||loaded.metrics().openComplaints()!=0)throw new AssertionError("persistence");
  if(!loaded.feedbackCsv().contains("Excellent stay")||!loaded.complaintsCsv().contains("Re-cleaned"))throw new AssertionError("csv");
  try{loaded.submitFeedback("B-3","Guest",6,1,1,1,1,10,"",now);throw new AssertionError("invalid rating");}catch(IllegalArgumentException expected){}
  try(CustomerExperienceHttpService api=new CustomerExperienceHttpService(0,loaded)){api.start();var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+api.port()+"/api/v1/metrics")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200||!response.body().contains("\"responses\":2"))throw new AssertionError("http adapter");}
  System.out.println("CustomerExperienceServiceSmokeTest passed");
 }
}
