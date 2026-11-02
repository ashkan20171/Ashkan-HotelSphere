package com.ashkan.hotel;
import java.util.*;
public final class OperationsServiceSmokeTest {
    public static void main(String[] args) {
        var requests = List.of(
            new GuestRequestService.Request("1","101","Maintenance","Broken AC","Sara",GuestRequestService.Priority.URGENT,GuestRequestService.Status.OPEN,"now"),
            new GuestRequestService.Request("2","102","Food","Tea","Ali",GuestRequestService.Priority.LOW,GuestRequestService.Status.RESOLVED,"now"));
        var result = OperationsService.summarize(List.of(), requests);
        if(result.openRequests()!=1 || result.urgentRequests()!=1) throw new AssertionError(result);
        if(OperationsService.filterRequests(requests,"sara","OPEN").size()!=1) throw new AssertionError("filter");
        if(!OperationsService.filterRequests(requests,"","IN_PROGRESS").isEmpty()) throw new AssertionError("status");
        System.out.println("OperationsServiceSmokeTest PASS");
    }
}
