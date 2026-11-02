package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Persistent single-workstation inventory ledger. Quantities are stored in each item's chosen unit. */
public final class InventoryService {
    public enum MovementType { RECEIPT, CONSUMPTION, ADJUSTMENT }
    public record Item(String sku, String name, String category, String unit, BigDecimal minimumStock,
                       BigDecimal unitCost, String supplier) {
        public Item {
            sku = required(sku, "SKU"); name = required(name, "Name"); category = clean(category);
            unit = required(unit, "Unit"); supplier = clean(supplier);
            minimumStock = nonNegative(minimumStock, "Minimum stock"); unitCost = nonNegative(unitCost, "Unit cost");
        }
    }
    public record Movement(long id, String sku, MovementType type, BigDecimal quantity, BigDecimal unitCost,
                           LocalDateTime occurredAt, String reference, String notes, String supplier) { }
    public record Stock(Item item, BigDecimal quantity, BigDecimal stockValue, boolean lowStock) { }
    private final Path file;
    private final Clock clock;
    private final Map<String, Item> items = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<Movement> movements = new ArrayList<>();
    private long nextId = 1;

    public InventoryService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "inventory.tsv"), Clock.systemDefaultZone()); }
    public InventoryService(Path file) { this(file, Clock.systemDefaultZone()); }
    public InventoryService(Path file, Clock clock) {
        this.file = Objects.requireNonNull(file); this.clock = Objects.requireNonNull(clock);
        try { Files.createDirectories(file.toAbsolutePath().getParent()); if (Files.exists(file)) load(); }
        catch (IOException ex) { throw new IllegalStateException("Cannot load inventory", ex); }
    }
    private static String required(String s, String label) { String v=clean(s); if(v.isBlank()) throw new IllegalArgumentException(label+" is required"); return v; }
    private static String clean(String s) { return s == null ? "" : s.strip(); }
    private static BigDecimal nonNegative(BigDecimal n,String label) { Objects.requireNonNull(n,label); if(n.signum()<0) throw new IllegalArgumentException(label+" cannot be negative"); return n.stripTrailingZeros(); }
    private static BigDecimal positive(BigDecimal n,String label) { Objects.requireNonNull(n,label); if(n.signum()<=0) throw new IllegalArgumentException(label+" must be positive"); return n.stripTrailingZeros(); }
    public synchronized void saveItem(Item item) throws IOException { Item previous=items.put(item.sku(),item); try { persist(); } catch(IOException ex) { if(previous==null) items.remove(item.sku()); else items.put(previous.sku(),previous); throw ex; } }
    public synchronized Optional<Item> findItem(String sku) { return Optional.ofNullable(items.get(clean(sku))); }
    public synchronized List<Item> items() { return List.copyOf(items.values()); }
    public synchronized List<Movement> movements() { return movements.stream().sorted(Comparator.comparing(Movement::occurredAt).reversed().thenComparing(Comparator.comparingLong(Movement::id).reversed())).toList(); }
    public synchronized Movement receive(String sku, BigDecimal quantity, BigDecimal unitCost, String supplier, String reference, String notes) throws IOException {
        Item item = requireItem(sku); return add(item, MovementType.RECEIPT, positive(quantity,"Quantity"), nonNegative(unitCost,"Unit cost"), supplier, reference, notes);
    }
    public synchronized Movement consume(String sku, BigDecimal quantity, String reference, String notes) throws IOException {
        Item item=requireItem(sku); BigDecimal q=positive(quantity,"Quantity"); if(stockOf(sku).compareTo(q)<0) throw new IllegalStateException("Insufficient stock for "+sku);
        return add(item,MovementType.CONSUMPTION,q,item.unitCost(),item.supplier(),reference,notes);
    }
    public synchronized Movement adjust(String sku, BigDecimal delta, String reference, String notes) throws IOException {
        Item item=requireItem(sku); Objects.requireNonNull(delta); if(delta.signum()==0) throw new IllegalArgumentException("Adjustment cannot be zero");
        if(stockOf(sku).add(delta).signum()<0) throw new IllegalStateException("Adjustment would make stock negative");
        return add(item,MovementType.ADJUSTMENT,delta.abs(),item.unitCost(),item.supplier(),reference,notes,delta.signum());
    }
    private Item requireItem(String sku) { return findItem(sku).orElseThrow(()->new IllegalArgumentException("Unknown SKU: "+sku)); }
    private Movement add(Item item,MovementType type,BigDecimal quantity,BigDecimal cost,String supplier,String ref,String notes) throws IOException { return add(item,type,quantity,cost,supplier,ref,notes,1); }
    private Movement add(Item item,MovementType type,BigDecimal quantity,BigDecimal cost,String supplier,String ref,String notes,int sign) throws IOException {
        Movement m=new Movement(nextId++,item.sku(),type,quantity.multiply(BigDecimal.valueOf(sign)),cost,LocalDateTime.now(clock),clean(ref),clean(notes),clean(supplier));
        movements.add(m); try { persist(); } catch(IOException ex) { movements.remove(m); nextId--; throw ex; } return m;
    }
    public synchronized BigDecimal stockOf(String sku) { BigDecimal total=BigDecimal.ZERO; for(Movement m:movements) if(m.sku().equalsIgnoreCase(sku)) { if(m.type()==MovementType.CONSUMPTION) total=total.subtract(m.quantity()); else total=total.add(m.quantity()); } return total; }
    public synchronized List<Stock> stock() { List<Stock> result=new ArrayList<>(); for(Item i:items.values()) { BigDecimal q=stockOf(i.sku()); result.add(new Stock(i,q,q.multiply(i.unitCost()).setScale(2,RoundingMode.HALF_UP),q.compareTo(i.minimumStock())<=0)); } return List.copyOf(result); }
    public synchronized List<Stock> lowStock() { return stock().stream().filter(Stock::lowStock).toList(); }
    public synchronized BigDecimal totalValue() { return stock().stream().map(Stock::stockValue).reduce(BigDecimal.ZERO,BigDecimal::add); }
    public static String stockCsv(List<Stock> rows) { StringBuilder b=new StringBuilder("SKU,Name,Category,Unit,Quantity,MinimumStock,UnitCost,StockValue,Supplier,LowStock\n"); for(Stock s:rows) { Item i=s.item(); b.append(csv(i.sku())).append(',').append(csv(i.name())).append(',').append(csv(i.category())).append(',').append(csv(i.unit())).append(',').append(s.quantity()).append(',').append(i.minimumStock()).append(',').append(i.unitCost()).append(',').append(s.stockValue()).append(',').append(csv(i.supplier())).append(',').append(s.lowStock()).append('\n'); } return b.toString(); }
    public synchronized String movementCsv() { StringBuilder b=new StringBuilder("ID,SKU,Type,Quantity,UnitCost,OccurredAt,Reference,Notes,Supplier\n"); for(Movement m:movements()) b.append(m.id()).append(',').append(csv(m.sku())).append(',').append(m.type()).append(',').append(m.quantity()).append(',').append(m.unitCost()).append(',').append(m.occurredAt()).append(',').append(csv(m.reference())).append(',').append(csv(m.notes())).append(',').append(csv(m.supplier())).append('\n'); return b.toString(); }
    private static String csv(String s) { String v=clean(s); if(!v.isEmpty() && "=+-@".indexOf(v.charAt(0))>=0) v="'"+v; return "\""+v.replace("\"","\"\"")+"\""; }
    private static String enc(String s) { return Base64.getEncoder().encodeToString(clean(s).getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8); }
    private void load() throws IOException { for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) { String[] f=line.split("\\t",-1); try { if(f.length==8 && f[0].equals("I")) { Item i=new Item(dec(f[1]),dec(f[2]),dec(f[3]),dec(f[4]),new BigDecimal(f[5]),new BigDecimal(f[6]),dec(f[7])); items.put(i.sku(),i); } else if(f.length==10 && f[0].equals("M")) { Movement m=new Movement(Long.parseLong(f[1]),dec(f[2]),MovementType.valueOf(f[3]),new BigDecimal(f[4]),new BigDecimal(f[5]),LocalDateTime.parse(f[6]),dec(f[7]),dec(f[8]),dec(f[9])); movements.add(m); nextId=Math.max(nextId,m.id()+1); } } catch(RuntimeException ignored) { } } }
    private void persist() throws IOException { Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"inventory-",".tmp"); try { List<String> lines=new ArrayList<>(); for(Item i:items.values()) lines.add(String.join("\t","I",enc(i.sku()),enc(i.name()),enc(i.category()),enc(i.unit()),i.minimumStock().toPlainString(),i.unitCost().toPlainString(),enc(i.supplier()))); for(Movement m:movements) lines.add(String.join("\t","M",Long.toString(m.id()),enc(m.sku()),m.type().name(),m.quantity().toPlainString(),m.unitCost().toPlainString(),m.occurredAt().toString(),enc(m.reference()),enc(m.notes()),enc(m.supplier()))); Files.write(tmp,lines,StandardCharsets.UTF_8); try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); } catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); } } finally { Files.deleteIfExists(tmp); } }
}
