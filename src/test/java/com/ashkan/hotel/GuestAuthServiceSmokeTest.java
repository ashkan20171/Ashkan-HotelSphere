package com.ashkan.hotel;

import java.util.concurrent.atomic.AtomicReference;

public final class GuestAuthServiceSmokeTest {
 public static void main(String[] args)throws Exception{
  AtomicReference<String> sent=new AtomicReference<>();
  GuestAuthService auth=new GuestAuthService("test-secret-value-that-is-longer-than-32-chars",(email,code)->sent.set(code));
  auth.request("guest@example.com","G-1");
  String code=sent.get();if(code==null||!code.matches("\\d{6}"))throw new AssertionError("OTP was not delivered");
  try{auth.verify("guest@example.com",code.equals("000000")?"000001":"000000");}catch(IllegalArgumentException expected){}
  String token=auth.verify("guest@example.com",code);
  if(!auth.guestId(token).orElseThrow().equals("G-1"))throw new AssertionError("Session did not resolve guest");
  auth.logout(token);if(auth.guestId(token).isPresent())throw new AssertionError("Logout did not revoke session");
  System.out.println("GuestAuthServiceSmokeTest: 5 checks passed");
 }
}
