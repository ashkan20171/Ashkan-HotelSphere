package com.ashkan.hotel;
public final class GatewayAccessPolicySmokeTest {
 public static void main(String[] args){if(!GatewayAccessPolicy.allowed("Receptionist","bookings","POST"))throw new AssertionError("staff booking create denied");if(GatewayAccessPolicy.allowed("Receptionist","payments","POST"))throw new AssertionError("staff payment write allowed");if(GatewayAccessPolicy.allowed("Receptionist","inventory","DELETE"))throw new AssertionError("staff inventory delete allowed");if(!GatewayAccessPolicy.allowed("Admin","inventory","DELETE"))throw new AssertionError("admin delete denied");if(GatewayAccessPolicy.allowed("Unknown","bookings","GET"))throw new AssertionError("unknown role allowed");System.out.println("GatewayAccessPolicySmokeTest passed");}
}
