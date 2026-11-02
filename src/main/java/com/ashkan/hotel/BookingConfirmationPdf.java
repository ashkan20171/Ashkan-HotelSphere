package com.ashkan.hotel;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free, single-page PDF receipt using built-in Helvetica; labels are English for font portability. */
public final class BookingConfirmationPdf {
    private BookingConfirmationPdf() {}
    public static byte[] create(BookingRepository.Booking b,String guestName,String email){
        List<String> lines=List.of("ASHKAN HOTELSPHERE","BOOKING CONFIRMATION","Booking reference: "+b.id(),"Guest: "+safe(guestName),"Email: "+safe(email),"Room: "+b.roomId(),"Check-in: "+b.checkIn(),"Check-out: "+b.checkOut(),"Status: "+b.status(),"Total: "+b.total().toPlainString()+" "+b.currency(),"","This document confirms the booking record stored by the hotel.");
        StringBuilder stream=new StringBuilder("BT\n/F1 18 Tf\n50 790 Td\n");
        for(int i=0;i<lines.size();i++){if(i==1)stream.append("/F1 13 Tf\n");else if(i>1)stream.append("/F1 10 Tf\n");if(i>0)stream.append("0 -30 Td\n");stream.append('(').append(pdfEscape(lines.get(i))).append(") Tj\n");}stream.append("ET");
        byte[] content=stream.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects=new ArrayList<>();objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"));objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));objects.add(concat(bytes("<< /Length "+content.length+" >>\nstream\n"),content,bytes("\nendstream")));
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try{out.write(bytes("%PDF-1.4\n%ASHKAN\n"));List<Integer> offsets=new ArrayList<>();for(int i=0;i<objects.size();i++){offsets.add(out.size());out.write(bytes((i+1)+" 0 obj\n"));out.write(objects.get(i));out.write(bytes("\nendobj\n"));}int xref=out.size();out.write(bytes("xref\n0 "+(objects.size()+1)+"\n0000000000 65535 f \n"));for(int offset:offsets)out.write(bytes(String.format(Locale.ROOT,"%010d 00000 n \n",offset)));out.write(bytes("trailer\n<< /Size "+(objects.size()+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF"));return out.toByteArray();}catch(java.io.IOException impossible){throw new IllegalStateException(impossible);}
    }
    private static String safe(String s){if(s==null)return "-";return s.replaceAll("[^\\x20-\\x7E]","?");}
    private static String pdfEscape(String s){return safe(s).replace("\\","\\\\").replace("(","\\(").replace(")","\\)");}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.US_ASCII);}private static byte[] concat(byte[]...a){int n=0;for(byte[]b:a)n+=b.length;byte[]o=new byte[n];int p=0;for(byte[]b:a){System.arraycopy(b,0,o,p,b.length);p+=b.length;}return o;}
}
