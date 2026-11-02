package com.ashkan.hotel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Predictive maintenance plans and tracked engineering work orders. */
public final class MaintenanceIntelligenceService {
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { OPEN, IN_PROGRESS, COMPLETED }
    public record WorkOrder(String id, String roomId, String asset, String description, Priority priority,
                            Status status, LocalDate reportedOn, LocalDate dueOn, String assignee, String notes) {}
    public record Plan(String id, String roomId, String asset, int intervalDays, LocalDate lastServiced,
                       LocalDate nextDue, String assignee) {}
    public record DueItem(String roomId, String asset, LocalDate dueOn, String reason, Priority priority) {}

    private final Path file;
    private final List<WorkOrder> orders = new ArrayList<>();
    private final List<Plan> plans = new ArrayList<>();

    public MaintenanceIntelligenceService() {
        this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "maintenance-intelligence.tsv"));
    }
    public MaintenanceIntelligenceService(Path file) { this.file=Objects.requireNonNull(file); load(); }
    public List<WorkOrder> workOrders() { return List.copyOf(orders); }
    public List<Plan> plans() { return List.copyOf(plans); }

    public WorkOrder report(String roomId, String asset, String description, Priority priority,
                            LocalDate reportedOn, LocalDate dueOn, String assignee, String notes) {
        roomId=required(roomId,"Room"); asset=required(asset,"Asset"); description=required(description,"Description");
        Objects.requireNonNull(priority); Objects.requireNonNull(reportedOn); Objects.requireNonNull(dueOn);
        if(dueOn.isBefore(reportedOn)) throw new IllegalArgumentException("Due date cannot precede report date");
        WorkOrder w=new WorkOrder(UUID.randomUUID().toString(),roomId,asset,description,priority,Status.OPEN,reportedOn,dueOn,clean(assignee),clean(notes));
        orders.add(w); try{save();}catch(RuntimeException ex){orders.remove(w);throw ex;} return w;
    }
    public void changeStatus(String id, Status next) {
        Objects.requireNonNull(next);
        for(int i=0;i<orders.size();i++) if(orders.get(i).id().equals(id)) {
            WorkOrder old=orders.get(i);
            if(old.status()==Status.COMPLETED && next!=Status.COMPLETED) throw new IllegalStateException("Completed work order cannot be reopened");
            WorkOrder updated=new WorkOrder(old.id(),old.roomId(),old.asset(),old.description(),old.priority(),next,old.reportedOn(),old.dueOn(),old.assignee(),old.notes());
            orders.set(i,updated); try{save();}catch(RuntimeException ex){orders.set(i,old);throw ex;} return;
        }
        throw new IllegalArgumentException("Work order not found");
    }
    public Plan savePlan(String roomId,String asset,int intervalDays,LocalDate lastServiced,String assignee) {
        roomId=required(roomId,"Room"); asset=required(asset,"Asset");
        if(intervalDays<1||intervalDays>3650) throw new IllegalArgumentException("Interval must be between 1 and 3650 days");
        Objects.requireNonNull(lastServiced);
        Plan p=new Plan(UUID.randomUUID().toString(),roomId,asset,intervalDays,lastServiced,lastServiced.plusDays(intervalDays),clean(assignee));
        plans.add(p); try{save();}catch(RuntimeException ex){plans.remove(p);throw ex;} return p;
    }
    public List<DueItem> dueItems(LocalDate today) {
        Objects.requireNonNull(today); List<DueItem> out=new ArrayList<>();
        for(Plan p:plans) if(!p.nextDue().isAfter(today.plusDays(30))) {
            Priority priority=p.nextDue().isBefore(today)?Priority.HIGH:(p.nextDue().isBefore(today.plusDays(8))?Priority.NORMAL:Priority.LOW);
            out.add(new DueItem(p.roomId(),p.asset(),p.nextDue(),p.nextDue().isBefore(today)?"Overdue preventive service":"Scheduled preventive service",priority));
        }
        for(WorkOrder w:orders) if(w.status()!=Status.COMPLETED && !w.dueOn().isAfter(today.plusDays(30)))
            out.add(new DueItem(w.roomId(),w.asset(),w.dueOn(),w.description(),w.dueOn().isBefore(today)?Priority.URGENT:w.priority()));
        out.sort(Comparator.comparing(DueItem::dueOn).thenComparing(DueItem::priority).reversed());
        return List.copyOf(out);
    }
    public String csv() {
        StringBuilder s=new StringBuilder("ID,Room,Asset,Description,Priority,Status,Reported,Due,Assignee,Notes\n");
        for(WorkOrder w:orders) s.append(row(w.id(),w.roomId(),w.asset(),w.description(),w.priority().name(),w.status().name(),w.reportedOn().toString(),w.dueOn().toString(),w.assignee(),w.notes())).append('\n');
        return s.toString();
    }
    private static String row(String... values){return String.join(",",Arrays.stream(values).map(MaintenanceIntelligenceService::csvCell).toList());}
    private static String csvCell(String v){String x=Objects.requireNonNullElse(v,"");if(!x.isEmpty()&&"=+-@".indexOf(x.charAt(0))>=0)x="'"+x;return "\""+x.replace("\"","\"\"")+"\"";}
    private void load(){
        if(!Files.exists(file))return;
        try{for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] p=line.split("\\t",-1);if(p.length==11&&p[0].equals("W"))orders.add(new WorkOrder(p[1],dec(p[2]),dec(p[3]),dec(p[4]),Priority.valueOf(p[5]),Status.valueOf(p[6]),LocalDate.parse(p[7]),LocalDate.parse(p[8]),dec(p[9]),dec(p[10])));else if(p.length==8&&p[0].equals("P"))plans.add(new Plan(p[1],dec(p[2]),dec(p[3]),Integer.parseInt(p[4]),LocalDate.parse(p[5]),LocalDate.parse(p[6]),dec(p[7])));else throw new IOException("Invalid maintenance row");}}
        catch(Exception ex){throw new IllegalStateException("Cannot read maintenance data",ex);}
    }
    private void save(){
        try{Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"maintenance-",".tmp");
            try{List<String> lines=new ArrayList<>();for(WorkOrder w:orders)lines.add(String.join("\t","W",w.id(),enc(w.roomId()),enc(w.asset()),enc(w.description()),w.priority().name(),w.status().name(),w.reportedOn().toString(),w.dueOn().toString(),enc(w.assignee()),enc(w.notes())));for(Plan p:plans)lines.add(String.join("\t","P",p.id(),enc(p.roomId()),enc(p.asset()),String.valueOf(p.intervalDays()),p.lastServiced().toString(),p.nextDue().toString(),enc(p.assignee())));Files.write(tmp,lines,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
            finally{Files.deleteIfExists(tmp);}
        }catch(IOException ex){throw new IllegalStateException("Cannot save maintenance data",ex);}
    }
    private static String required(String s,String name){String v=clean(s);if(v.isBlank())throw new IllegalArgumentException(name+" is required");return v;}
    private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}
    private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
}
