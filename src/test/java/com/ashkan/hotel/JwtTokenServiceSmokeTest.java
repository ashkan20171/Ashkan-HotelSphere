package com.ashkan.hotel;
public final class JwtTokenServiceSmokeTest {
 public static void main(String[] args){JwtTokenService s=new JwtTokenService("0123456789abcdef0123456789abcdef",3600);String t=s.issue("Admin","Admin");var c=s.verify(t);if(c==null||!c.subject().equals("Admin")||!c.role().equals("Admin"))throw new AssertionError("valid token rejected");if(s.verify(t.substring(0,t.length()-2)+"aa")!=null)throw new AssertionError("tampered token accepted");if(s.verify("bad.token")!=null)throw new AssertionError("malformed token accepted");try{new JwtTokenService("short",3600);throw new AssertionError("weak secret accepted");}catch(IllegalArgumentException expected){}System.out.println("JwtTokenServiceSmokeTest passed");}
}
