package com.ashkan.hotel;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Local task workflow; never deserialize untrusted files. */
public final class HousekeepingTaskService {
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { OPEN, IN_PROGRESS, DONE }
    public record Task(String id,String roomId,String title,String assignee,Priority priority,Status status) {}
    private final AuditService audit;
    private final Path file;
    private final OperationalSqliteStore sqlite;
    private final List<Task> items = new ArrayList<>();
    public HousekeepingTaskService() {this(OperationalSqliteStore.defaultStore());}
    public HousekeepingTaskService(Path file) {this.file=Objects.requireNonNull(file);this.sqlite=null;this.audit=null;load();}
    public HousekeepingTaskService(OperationalSqliteStore store) {
        this.file=null;this.sqlite=Objects.requireNonNull(store);this.audit=AuditService.defaultService();
        var rows=sqlite.read("tasks");
        if(rows.isEmpty()) {
            Path legacy=Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","tasks.tsv");
            if(Files.exists(legacy)) {
                HousekeepingTaskService previous=new HousekeepingTaskService(legacy);
                items.addAll(previous.items);
                persistSqlite();
                return;
            }
        }
        for(var e:rows.entrySet()) {String[] a=e.getValue().split("\t",-1);if(a.length!=5)throw new IllegalStateException("Invalid task row");items.add(new Task(e.getKey(),a[0],a[1],a[2],Priority.valueOf(a[3]),Status.valueOf(a[4])));}
    }
    private void persistSqlite() { persistSqlite(null,null,null); }
    private void persistSqlite(String action,String recordId,String detail) {
        Map<String,String> rows=new LinkedHashMap<>();
        for(Task t:items)rows.put(t.id(),String.join("\t",t.roomId(),t.title(),t.assignee(),t.priority().name(),t.status().name()));
        if(action==null) sqlite.replace("tasks",rows);
        else sqlite.replaceWithAudit("tasks",rows,"TASK",action,recordId,detail);
    }
    private void load() {
        if(!Files.exists(file))return;
        try {
            for(String line:Files.readAllLines(file)) {
                String[] p=line.split("\\t",-1);
                if(p.length!=6)throw new IOException("Invalid task data");
                items.add(new Task(p[0],p[1],p[2],p[3],Priority.valueOf(p[4]),Status.valueOf(p[5])));
            }
        } catch(Exception ex){throw new IllegalStateException("Cannot read tasks",ex);}
    }
    public List<Task> all(){return List.copyOf(items);}
    public Task create(String room,String title,String assignee,Priority priority) {
        room=clean(room);title=clean(title);assignee=clean(assignee);
        if(room.isBlank()||title.isBlank())throw new IllegalArgumentException("Room and title required");
        Task task=new Task(UUID.randomUUID().toString(),room,title,assignee,Objects.requireNonNull(priority),Status.OPEN);
        items.add(task);try{if(sqlite!=null)persistSqlite("CREATE",task.id(),task.roomId());else save();}catch(RuntimeException ex){items.remove(task);throw ex;}return task;
    }
    public void changeStatus(String id,Status next) {
        Objects.requireNonNull(next);
        for(int i=0;i<items.size();i++)if(items.get(i).id().equals(id)) {
            Task old=items.get(i);
            if(old.status()==Status.DONE && next!=Status.DONE)throw new IllegalStateException("Completed task cannot be reopened");
            if(old.status()==Status.OPEN && next==Status.DONE)throw new IllegalStateException("Start task before completing");
            items.set(i,new Task(old.id(),old.roomId(),old.title(),old.assignee(),old.priority(),next));
            try{if(sqlite!=null && old.status()!=next)persistSqlite("STATUS",id,old.status()+" -> "+next);else save();}catch(RuntimeException ex){items.set(i,old);throw ex;}return;
        }
        throw new IllegalArgumentException("Task not found");
    }
    private static String clean(String s){return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}
    private void save(){
        if(sqlite!=null){persistSqlite();return;}
        try{
            Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);
            Path tmp=Files.createTempFile(dir,"tasks-",".tmp");
            try{
                List<String> lines=new ArrayList<>();for(Task t:items)lines.add(String.join("\t",t.id(),t.roomId(),t.title(),t.assignee(),t.priority().name(),t.status().name()));
                Files.write(tmp,lines);
                try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(tmp);}
        }catch(IOException ex){throw new IllegalStateException("Cannot save tasks",ex);}
    }
}
