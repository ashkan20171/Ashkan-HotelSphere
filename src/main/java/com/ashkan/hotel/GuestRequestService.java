package com.ashkan.hotel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Single-user local guest service requests. */
public final class GuestRequestService {
    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { OPEN, IN_PROGRESS, RESOLVED }
    public record Request(String id, String roomId, String category, String description, String assignee,
                          Priority priority, Status status, String createdAt) {}
    private final AuditService audit;
    private final Path file;
    private final OperationalSqliteStore sqlite;
    private final List<Request> requests = new ArrayList<>();
    public GuestRequestService() { this(OperationalSqliteStore.defaultStore()); }
    public GuestRequestService(Path file) { this.file=Objects.requireNonNull(file);this.sqlite=null;this.audit=null;load(); }
    public GuestRequestService(OperationalSqliteStore store) {
        this.file=null;this.sqlite=Objects.requireNonNull(store);this.audit=AuditService.defaultService();
        var rows=sqlite.read("requests");
        if(rows.isEmpty()) {
            Path legacy=Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","guest-requests.tsv");
            if(Files.exists(legacy)) {
                GuestRequestService previous=new GuestRequestService(legacy);
                requests.addAll(previous.requests);
                persistSqlite();
                return;
            }
        }
        for(var e:rows.entrySet()) {String[] a=e.getValue().split("\t",-1);if(a.length!=7)throw new IllegalStateException("Invalid request row");requests.add(new Request(e.getKey(),a[0],a[1],a[2],a[3],Priority.valueOf(a[4]),Status.valueOf(a[5]),a[6]));}
    }
    private void persistSqlite() { persistSqlite(null,null,null); }
    private void persistSqlite(String action,String recordId,String detail) {
        Map<String,String> rows=new LinkedHashMap<>();
        for(Request r:requests)rows.put(r.id(),String.join("\t",r.roomId(),r.category(),r.description(),r.assignee(),r.priority().name(),r.status().name(),r.createdAt()));
        if(action==null) sqlite.replace("requests",rows);
        else sqlite.replaceWithAudit("requests",rows,"REQUEST",action,recordId,detail);
    }
    public List<Request> all() { return List.copyOf(requests); }
    public Request create(String roomId,String category,String description,String assignee,Priority priority) {
        roomId=clean(roomId);category=clean(category);description=clean(description);assignee=clean(assignee);
        if(roomId.isBlank()||category.isBlank()||description.isBlank()) throw new IllegalArgumentException("Room, category and description required");
        if(description.length()>500) throw new IllegalArgumentException("Description too long");
        Request r=new Request(UUID.randomUUID().toString(),roomId,category,description,assignee,Objects.requireNonNull(priority),Status.OPEN,Instant.now().toString());
        requests.add(r);try { if(sqlite!=null)persistSqlite("CREATE",r.id(),r.roomId());else save(); }catch(RuntimeException e){requests.remove(r);throw e;}return r;
    }
    public void changeStatus(String id,Status next) {
        Objects.requireNonNull(next);
        for(int i=0;i<requests.size();i++) if(requests.get(i).id().equals(id)) {
            Request r=requests.get(i);
            if(r.status()==Status.RESOLVED && next!=Status.RESOLVED) throw new IllegalStateException("Resolved request cannot be reopened");
            if(r.status()==Status.OPEN && next==Status.RESOLVED) throw new IllegalStateException("Start request before resolving");
            Request updated=new Request(r.id(),r.roomId(),r.category(),r.description(),r.assignee(),r.priority(),next,r.createdAt());
            requests.set(i,updated);try {if(sqlite!=null && r.status()!=next)persistSqlite("STATUS",id,r.status()+" -> "+next);else save();}catch(RuntimeException e){requests.set(i,r);throw e;}return;
        }
        throw new IllegalArgumentException("Request not found");
    }
    private static String clean(String s) {return Objects.requireNonNullElse(s,"").replace('\t',' ').replace('\n',' ').replace('\r',' ').trim();}
    private void load() {
        if(!Files.exists(file))return;
        try {
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                String[] a=line.split("\t",-1);if(a.length!=8)throw new IOException("Invalid request data");
                requests.add(new Request(a[0],a[1],a[2],a[3],a[4],Priority.valueOf(a[5]),Status.valueOf(a[6]),a[7]));
            }
        }catch(Exception e){throw new IllegalStateException("Cannot load guest requests",e);}
    }
    private void save() {
        if(sqlite!=null){persistSqlite();return;}
        Path tmp=null;
        try {
            Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);
            tmp=Files.createTempFile(dir,"requests-",".tmp");
            List<String> lines=new ArrayList<>();
            for(Request r:requests)lines.add(String.join("\t",r.id(),r.roomId(),r.category(),r.description(),r.assignee(),r.priority().name(),r.status().name(),r.createdAt()));
            Files.write(tmp,lines,StandardCharsets.UTF_8);
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){throw new IllegalStateException("Cannot save guest requests",e);}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}
    }
}
