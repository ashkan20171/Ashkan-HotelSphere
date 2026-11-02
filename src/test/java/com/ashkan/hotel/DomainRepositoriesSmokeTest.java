package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DomainRepositoriesSmokeTest {
 public static void main(String[] args) {
  new BookingRepository.Booking("B1","G1","R1",LocalDate.of(2026,11,1),LocalDate.of(2026,11,3),"PENDING",new BigDecimal("120.00"),"EUR");
  expect(() -> new BookingRepository.Booking("B2","G1","R1",LocalDate.of(2026,11,3),LocalDate.of(2026,11,1),"PENDING",BigDecimal.ONE,"EUR"));
  expect(() -> new PaymentRepository.Payment("P1","B1",BigDecimal.ZERO,"EUR","CARD","INITIATED",null));
  expect(() -> new GuestRepository.Guest("G1","Ashkan","Motaei","not-an-email",null,null));
  expect(() -> new InventoryRepository.Product("I1","SKU1","Soap","piece",new BigDecimal("-1"),BigDecimal.ZERO));
  expect(() -> new InventoryRepository.Product("I1","SKU1","Soap","piece",BigDecimal.ONE,new BigDecimal("-2")));
  System.out.println("DomainRepositoriesSmokeTest passed");
 }
 private static void expect(Runnable r){try{r.run();throw new AssertionError("Expected validation failure");}catch(IllegalArgumentException expected){}}
}
