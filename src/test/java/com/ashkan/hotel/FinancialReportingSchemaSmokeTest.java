package com.ashkan.hotel;
import java.nio.file.*;
public final class FinancialReportingSchemaSmokeTest {
 public static void main(String[] args)throws Exception{
  String migration=Files.readString(Path.of("src/main/resources/db/migration/payment/V6__financial_expenses.sql"));
  String repo=Files.readString(Path.of("src/main/java/com/ashkan/hotel/FinancialReportingRepository.java"));
  if(!migration.contains("financial_expenses")||!migration.contains("CHECK (amount > 0)"))throw new AssertionError("expense schema constraints");
  if(!repo.contains("created_at::date BETWEEN ? AND ?")||!repo.contains("capturedPayments"))throw new AssertionError("date-bounded payment query");
  if(!repo.contains("payment_refunds")||!repo.contains("financial_expenses"))throw new AssertionError("financial source queries");
  System.out.println("FinancialReportingSchemaSmokeTest: OK");
 }
}
