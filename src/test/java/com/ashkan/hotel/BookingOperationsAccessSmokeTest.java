package com.ashkan.hotel;

public final class BookingOperationsAccessSmokeTest {
    public static void main(String[] args) {
        if(!GatewayAccessPolicy.allowed("Receptionist","booking-operations","GET"))throw new AssertionError("Receptionist must read calendar");
        if(!GatewayAccessPolicy.allowed("Receptionist","booking-operations","POST"))throw new AssertionError("Receptionist must perform front desk actions");
        if(!GatewayAccessPolicy.allowed("Receptionist","booking-operations","PATCH"))throw new AssertionError("Receptionist must reassign room");
        if(GatewayAccessPolicy.allowed("Guest","booking-operations","POST"))throw new AssertionError("Guest must not perform front desk actions");
        if(GatewayAccessPolicy.allowed("Receptionist","booking-operations","DELETE"))throw new AssertionError("Receptionist must not delete bookings");
        System.out.println("BookingOperationsAccessSmokeTest: PASS");
    }
}
