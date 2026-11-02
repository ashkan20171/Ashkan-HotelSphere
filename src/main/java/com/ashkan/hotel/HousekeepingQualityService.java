package com.ashkan.hotel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Room cleaning inspections, checklist scoring and corrective re-clean requests. */
public final class HousekeepingQualityService {
    public enum Status { APPROVED, REWORK_REQUIRED }
    public record Inspection(String id, LocalDateTime inspectedAt, String roomId, String inspector,
                             int cleanliness, int bathroom, int amenities, int linen, int safety,
                             Status status, String notes, String beforePhoto, String afterPhoto) {
        public int score(){ return (cleanliness+bathroom+amenities+linen+safety)*20/5; }
    }
    private final Path file; private final List<Inspection> inspections=new ArrayList<>();
    public HousekeepingQualityService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","housekeeping-quality.tsv"));}
    public HousekeepingQualityService(Path file){this.file=Objects.requireNonNull(file);load();}
    public synchronized Inspection inspect(String room,String inspector,int cleanliness,int bathroom,int amenities,int linen,int safety,String notes,String before,String after,LocalDateTime at){
        room=req(room,"Room");inspector=req(inspector,"Inspector");int[] scores={cleanliness,bathroom,amenities,linen,safety};for(int s:scores)if(s<1||s>5)throw new IllegalArgumentException("Checklist scores must be between 1 and 5");
        int total=Arrays.stream(scores).sum();Status status=total>=20?Status.APPROVED:Status.REWORK_REQUIRED;
        Inspection x=new Inspection(UUID.randomUUID().toString(),Objects.requireNonNull(at),room,inspector,cleanliness,bathroom,amenities,linen,safety,status,clean(notes),clean(before),clean(after));inspections.add(x);try{save();}catch(RuntimeException e){inspections.remove(x);throw e;}return x;
    }
    public synchronized List<Inspection> all(){return inspections.stream().sorted(Comparator.comparing(Inspection::inspectedAt).reversed()).toList();}
    public synchronized List<Inspection> forRoom(String room){return all().stream().filter(x->x.roomId().equalsIgnoreCase(room)).toList();}
    public synchronized long approved(){return inspections.stream().filter(x->x.status()==Status.APPROVED).count();}
    public synchronized long rework(){return inspections.stream().filter(x->x.status()==Status.REWORK_REQUIRED).count();}
    public synchronized double averageScore(){return inspections.isEmpty()?0:inspections.stream().mapToInt(Inspection::score).average().orElse(0);}
    public synchronized String csv(){StringBuilder b=new StringBuilder("ID,InspectedAt,Room,Inspector,Cleanliness,Bathroom,Amenities,Linen,Safety,Score,Status,Notes,BeforePhoto,AfterPhoto\n");for(Inspection x:inspections){Object[] v={x.id(),x.inspectedAt(),x.roomId(),x.inspector(),x.cleanliness(),x.bathroom(),x.amenities(),x.linen(),x.safety(),x.score(),x.status(),x.notes(),x.beforePhoto(),x.afterPhoto()};for(int i=0;i<v.length;i++){if(i>0)b.append(',');b.append(cell(String.valueOf(v[i])));}b.append('\n');}return b.toString();}
    private void load(){if(!Files.exists(file))return;try{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] p=line.split("\\t",-1);if(p.length!=13)throw new IOException("Invalid quality record");inspections.add(new Inspection(p[0],LocalDateTime.parse(p[1]),dec(p[2]),dec(p[3]),Integer.parseInt(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),Integer.parseInt(p[8]),Status.valueOf(p[9]),dec(p[10]),dec(p[11]),dec(p[12])));}}catch(Exception e){throw new IllegalStateException("Cannot load housekeeping quality data",e);}}
    private void save(){try{Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"quality-",".tmp");try{List<String> lines=new ArrayList<>();for(Inspection x:inspections)lines.add(String.join("\t",x.id(),x.inspectedAt().toString(),enc(x.roomId()),enc(x.inspector()),""+x.cleanliness(),""+x.bathroom(),""+x.amenities(),""+x.linen(),""+x.safety(),x.status().name(),enc(x.notes()),enc(x.beforePhoto()),enc(x.afterPhoto())));Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}catch(IOException e){throw new IllegalStateException("Cannot save housekeeping quality data",e);}}
    private static String req(String s,String n){String x=clean(s);if(x.isBlank())throw new IllegalArgumentException(n+" is required");return x;}private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}private static String cell(String s){if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
