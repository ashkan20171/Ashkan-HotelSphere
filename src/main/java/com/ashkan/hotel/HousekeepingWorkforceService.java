package com.ashkan.hotel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Shift planning, room assignment and measurable housekeeping task execution. */
public final class HousekeepingWorkforceService {
    public enum Shift { MORNING, EVENING, NIGHT }
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { ASSIGNED, IN_PROGRESS, COMPLETED }
    public enum TaskType { CHECKOUT_CLEAN, STAYOVER_SERVICE, ARRIVAL_PREPARATION, DEEP_CLEAN, INSPECTION }
    public record Assignment(String id, LocalDate workDate, Shift shift, String employee, String roomId,
                             TaskType type, Priority priority, Status status, LocalDateTime startedAt,
                             LocalDateTime completedAt, String notes) {}
    public record EmployeeLoad(String employee, int assigned, int completed, double completionRate,
                               long totalMinutes, double averageMinutes) {}
    public record ShiftSummary(Shift shift, int assigned, int completed, int urgent) {}

    private final Path file;
    private final List<Assignment> assignments = new ArrayList<>();
    public HousekeepingWorkforceService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "housekeeping-workforce.tsv")); }
    public HousekeepingWorkforceService(Path file) { this.file=Objects.requireNonNull(file); load(); }
    public synchronized List<Assignment> all() { return assignments.stream().sorted(Comparator.comparing(Assignment::workDate).thenComparing(Assignment::shift).thenComparing(Assignment::priority).reversed()).toList(); }

    public synchronized Assignment assign(LocalDate date, Shift shift, String employee, String roomId,
                                          TaskType type, Priority priority, String notes) {
        Objects.requireNonNull(date); Objects.requireNonNull(shift); Objects.requireNonNull(type); Objects.requireNonNull(priority);
        employee=required(employee,"Employee"); roomId=required(roomId,"Room"); final String targetRoom=roomId;
        if(assignments.stream().anyMatch(a -> a.workDate().equals(date) && a.roomId().equalsIgnoreCase(targetRoom)
                && a.status()!=Status.COMPLETED)) throw new IllegalStateException("Room already has an active housekeeping assignment for this date");
        Assignment a=new Assignment(UUID.randomUUID().toString(),date,shift,employee,roomId,type,priority,Status.ASSIGNED,null,null,clean(notes));
        assignments.add(a); try { save(); } catch(RuntimeException ex) { assignments.remove(a); throw ex; } return a;
    }
    public synchronized void start(String id, LocalDateTime at) { update(id, Status.IN_PROGRESS, Objects.requireNonNull(at)); }
    public synchronized void complete(String id, LocalDateTime at) { update(id, Status.COMPLETED, Objects.requireNonNull(at)); }
    private void update(String id, Status next, LocalDateTime at) {
        for(int i=0;i<assignments.size();i++) if(assignments.get(i).id().equals(id)) {
            Assignment old=assignments.get(i); if(old.status()==Status.COMPLETED) throw new IllegalStateException("Completed assignment cannot be changed");
            if(next==Status.IN_PROGRESS && old.status()!=Status.ASSIGNED) throw new IllegalStateException("Assignment is not waiting to start");
            if(next==Status.COMPLETED && old.status()!=Status.IN_PROGRESS) throw new IllegalStateException("Start the assignment before completing it");
            if(old.startedAt()!=null && at.isBefore(old.startedAt())) throw new IllegalArgumentException("Timestamp cannot precede start time");
            Assignment n=new Assignment(old.id(),old.workDate(),old.shift(),old.employee(),old.roomId(),old.type(),old.priority(),next,
                    next==Status.IN_PROGRESS?at:old.startedAt(), next==Status.COMPLETED?at:null,old.notes());
            assignments.set(i,n); try{save();}catch(RuntimeException ex){assignments.set(i,old);throw ex;} return;
        }
        throw new IllegalArgumentException("Assignment not found");
    }
    public synchronized List<Assignment> forDate(LocalDate date) { return all().stream().filter(a->a.workDate().equals(date)).toList(); }
    public synchronized List<EmployeeLoad> employeeLoads(LocalDate from, LocalDate to) {
        if(to.isBefore(from)) throw new IllegalArgumentException("Invalid date range");
        Map<String,List<Assignment>> grouped=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        assignments.stream().filter(a->!a.workDate().isBefore(from)&&!a.workDate().isAfter(to)).forEach(a->grouped.computeIfAbsent(a.employee(),k->new ArrayList<>()).add(a));
        List<EmployeeLoad> result=new ArrayList<>();
        grouped.forEach((name,list)->{int done=0;long minutes=0;int timed=0;for(Assignment a:list)if(a.status()==Status.COMPLETED){done++;if(a.startedAt()!=null&&a.completedAt()!=null){minutes+=ChronoUnit.MINUTES.between(a.startedAt(),a.completedAt());timed++;}}
            result.add(new EmployeeLoad(name,list.size(),done,list.isEmpty()?0:done*100.0/list.size(),minutes,timed==0?0:minutes/(double)timed));});
        return List.copyOf(result);
    }
    public synchronized List<ShiftSummary> shiftSummary(LocalDate date) {
        List<ShiftSummary> out=new ArrayList<>();for(Shift s:Shift.values()){List<Assignment> xs=forDate(date).stream().filter(a->a.shift()==s).toList();out.add(new ShiftSummary(s,xs.size(),(int)xs.stream().filter(a->a.status()==Status.COMPLETED).count(),(int)xs.stream().filter(a->a.priority()==Priority.URGENT&&a.status()!=Status.COMPLETED).count()));}return List.copyOf(out);
    }
    public synchronized String csv() {StringBuilder b=new StringBuilder("ID,Date,Shift,Employee,Room,Type,Priority,Status,Started,Completed,Notes\n");for(Assignment a:assignments)b.append(String.join(",",Arrays.stream(new String[]{a.id(),a.workDate().toString(),a.shift().name(),a.employee(),a.roomId(),a.type().name(),a.priority().name(),a.status().name(),Objects.toString(a.startedAt(),""),Objects.toString(a.completedAt(),""),a.notes()}).map(HousekeepingWorkforceService::csvCell).toList())).append('\n');return b.toString();}
    private static String csvCell(String s){String x=Objects.requireNonNullElse(s,"");if(!x.isEmpty()&&"=+-@".indexOf(x.charAt(0))>=0)x="'"+x;return "\""+x.replace("\"","\"\"")+"\"";}
    private void load(){if(!Files.exists(file))return;try{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] p=line.split("\\t",-1);if(p.length!=11)throw new IOException("Invalid workforce record");assignments.add(new Assignment(p[0],LocalDate.parse(p[1]),Shift.valueOf(p[2]),dec(p[3]),dec(p[4]),TaskType.valueOf(p[5]),Priority.valueOf(p[6]),Status.valueOf(p[7]),p[8].isBlank()?null:LocalDateTime.parse(p[8]),p[9].isBlank()?null:LocalDateTime.parse(p[9]),dec(p[10])));}}catch(Exception ex){throw new IllegalStateException("Cannot load housekeeping workforce data",ex);}}
    private void save(){try{Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"housekeeping-workforce-",".tmp");try{List<String> lines=new ArrayList<>();for(Assignment a:assignments)lines.add(String.join("\t",a.id(),a.workDate().toString(),a.shift().name(),enc(a.employee()),enc(a.roomId()),a.type().name(),a.priority().name(),a.status().name(),Objects.toString(a.startedAt(),""),Objects.toString(a.completedAt(),""),enc(a.notes())));Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}catch(IOException ex){throw new IllegalStateException("Cannot save housekeeping workforce data",ex);}}
    private static String required(String s,String field){String x=clean(s);if(x.isBlank())throw new IllegalArgumentException(field+" is required");return x;}
    private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}
    private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
}
