package com.ashkan.hotel;

import java.time.LocalDate;

/** Fast contract checks for guest-facing input validation; no external database is required. */
public final class GuestPortalContractSmokeTest {
    public static void main(String[] args) {
        check("guest@example.com".equals(GuestPortalHttpService.validEmail("Guest@Example.com")), "email normalization");
        expect(() -> GuestPortalHttpService.validEmail("not-an-email"), "invalid email rejected");
        LocalDate today=LocalDate.of(2026,10,30);
        GuestPortalHttpService.validateStayDates(today,today.plusDays(1),today);
        expect(() -> GuestPortalHttpService.validateStayDates(today.minusDays(1),today,today), "past check-in rejected");
        expect(() -> GuestPortalHttpService.validateStayDates(today,today,today), "zero-night stay rejected");
        expect(() -> GuestPortalHttpService.validateStayDates(today,today.minusDays(1),today), "negative stay rejected");
        System.out.println("GuestPortalContractSmokeTest: 6 checks passed");
    }
    private static void expect(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){}}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
