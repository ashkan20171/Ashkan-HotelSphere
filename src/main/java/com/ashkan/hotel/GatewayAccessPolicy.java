package com.ashkan.hotel;

import java.util.Locale;
import java.util.Set;

/** Central role policy for gateway-protected domain routes. */
final class GatewayAccessPolicy {
    private static final Set<String> STAFF_CREATE=Set.of("bookings","guests");
    private GatewayAccessPolicy() {}
    static boolean allowed(String role, String resource, String method) {
        if ("Admin".equals(role)) return Set.of("GET","POST","PUT","PATCH","DELETE").contains(method.toUpperCase(Locale.ROOT));
        if (!"Receptionist".equals(role)) return false;
        String m=method.toUpperCase(Locale.ROOT);
        return m.equals("GET") || (m.equals("POST") && (STAFF_CREATE.contains(resource) || "quotes".equals(resource) || "booking-operations".equals(resource))) || (m.equals("PATCH") && "booking-operations".equals(resource));
    }
}
