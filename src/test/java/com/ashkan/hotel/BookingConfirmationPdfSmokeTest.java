package com.ashkan.hotel;
import java.math.BigDecimal;import java.time.LocalDate;import java.nio.charset.StandardCharsets;
public final class BookingConfirmationPdfSmokeTest{
 public static void main(String[]args){var b=new BookingRepository.Booking("BK-PDF-1","G1","R101",LocalDate.of(2027,2,1),LocalDate.of(2027,2,4),"CONFIRMED",new BigDecimal("450.00"),"EUR");byte[] pdf=BookingConfirmationPdf.create(b,"Ashkan Motaei","guest@example.com");String s=new String(pdf,StandardCharsets.ISO_8859_1);check(s.startsWith("%PDF-1.4"),"PDF header");check(s.contains("xref")&&s.contains("startxref"),"PDF cross reference");check(s.contains("BK-PDF-1")&&s.contains("450.00 EUR"),"booking details");System.out.println("BookingConfirmationPdfSmokeTest: 3 checks passed");}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
