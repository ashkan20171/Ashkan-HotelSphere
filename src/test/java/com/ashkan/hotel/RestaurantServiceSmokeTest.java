package com.ashkan.hotel;
import java.math.BigDecimal;import java.nio.file.*;import java.time.*;import java.util.*;
public final class RestaurantServiceSmokeTest {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);} private static BigDecimal n(String x){return new BigDecimal(x);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("restaurant-test");Path invFile=dir.resolve("inventory.tsv"),restFile=dir.resolve("restaurant.tsv");Clock clock=Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"),ZoneOffset.UTC);
  InventoryService inv=new InventoryService(invFile,clock);inv.saveItem(new InventoryService.Item("COF","Coffee beans","Beverage","kg",n("1"),n("10"),"Supplier"));inv.receive("COF",n("5"),n("10"),"Supplier","PO1","");
  RestaurantService rs=new RestaurantService(restFile,clock,inv);rs.saveMenuItem(new RestaurantService.MenuItem("LATTE","Latte","Coffee",n("4.50"),"COF",n("0.2"),true));
  RestaurantService.Order order=rs.placeOrder("A1","Guest",Map.of("LATTE",2));check(order.total().compareTo(n("9.00"))==0,"order total");check(inv.stockOf("COF").compareTo(n("4.6"))==0,"recipe stock consumption");
  rs.updateStatus(order.id(),RestaurantService.OrderStatus.PREPARING);rs.updateStatus(order.id(),RestaurantService.OrderStatus.CANCELLED);check(inv.stockOf("COF").compareTo(n("5.0"))==0,"cancel restores stock");check(rs.salesTotal().signum()==0,"cancel excluded from sales");
  boolean insufficient=false;try{rs.placeOrder("A2","Guest",Map.of("LATTE",100));}catch(IllegalStateException e){insufficient=true;}check(insufficient,"insufficient stock rejected");
  RestaurantService reloaded=new RestaurantService(restFile,clock,inv);check(reloaded.menu().size()==1,"menu persistence");check(reloaded.orders().size()==1,"order persistence");check(reloaded.ordersCsv().contains("LATTE"),"CSV export");
  System.out.println("RestaurantServiceSmokeTest: PASS");
 }
}
