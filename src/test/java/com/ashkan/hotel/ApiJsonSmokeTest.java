package com.ashkan.hotel;

import java.math.BigDecimal;
import java.util.Map;

public final class ApiJsonSmokeTest {
    public static void main(String[] args) {
        Map<String,Object> parsed=ApiJson.object("{\"id\":\"bk-1\",\"amount\":125.50,\"active\":true,\"meta\":{\"source\":\"web\"}}");
        if(!"bk-1".equals(parsed.get("id")))throw new AssertionError("String JSON value lost");
        if(!(parsed.get("amount") instanceof BigDecimal d)||d.compareTo(new BigDecimal("125.50"))!=0)throw new AssertionError("Decimal precision lost");
        if(!Boolean.TRUE.equals(parsed.get("active")))throw new AssertionError("Boolean JSON value lost");
        String encoded=ApiJson.write(parsed); if(!ApiJson.object(encoded).equals(parsed))throw new AssertionError("JSON round trip failed");
        boolean rejected=false;try{ApiJson.object("[1,2]");}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("Array accepted as object");
        System.out.println("ApiJsonSmokeTest passed");
    }
}
