package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Validated coupon and campaign engine with guest-tier, date, spend and usage limits. */
public final class CampaignService {
    public enum DiscountType { PERCENT, FIXED }
    public record Campaign(String code,String name,DiscountType type,BigDecimal value,BigDecimal maxDiscount,
                           BigDecimal minimumSpend,LocalDate startsOn,LocalDate endsOn,int maxUses,
                           CrmService.Tier minimumTier,boolean active) {
        public Campaign {
            code=req(code).toUpperCase(Locale.ROOT); name=req(name); Objects.requireNonNull(type); Objects.requireNonNull(value);
            Objects.requireNonNull(maxDiscount); Objects.requireNonNull(minimumSpend); Objects.requireNonNull(startsOn); Objects.requireNonNull(endsOn); Objects.requireNonNull(minimumTier);
            if(value.signum()<=0||maxDiscount.signum()<0||minimumSpend.signum()<0||endsOn.isBefore(startsOn)||maxUses<0)throw new IllegalArgumentException("Invalid campaign limits or dates");
            if(type==DiscountType.PERCENT&&value.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Percentage cannot exceed 100");
        }
    }
    public record Redemption(long id,String code,String guestId,BigDecimal orderAmount,BigDecimal discount,LocalDateTime redeemedAt) {}
    private final Path file; private final Clock clock; private final Map<String,Campaign> campaigns=new TreeMap<>(); private final List<Redemption> redemptions=new ArrayList<>(); private long nextId=1;
    public CampaignService(){this(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","campaigns.tsv"),Clock.systemDefaultZone());}
    public CampaignService(Path file,Clock clock){this.file=Objects.requireNonNull(file);this.clock=Objects.requireNonNull(clock);try{Files.createDirectories(file.toAbsolutePath().getParent());if(Files.exists(file))load();}catch(IOException e){throw new IllegalStateException("Cannot load campaigns",e);}}
    private static String req(String s){String x=s==null?"":s.strip();if(x.isEmpty())throw new IllegalArgumentException("Required value is missing");return x;}
    private static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    public synchronized void save(Campaign c)throws IOException{Campaign old=campaigns.put(c.code(),c);try{persist();}catch(IOException e){if(old==null)campaigns.remove(c.code());else campaigns.put(old.code(),old);throw e;}}
    public synchronized List<Campaign> campaigns(){return List.copyOf(campaigns.values());} public synchronized List<Redemption> redemptions(){return redemptions.stream().sorted(Comparator.comparing(Redemption::redeemedAt).reversed()).toList();}
    public synchronized int usage(String code){return (int)redemptions.stream().filter(r->r.code().equalsIgnoreCase(code)).count();}
    public synchronized BigDecimal preview(String code,String guestId,BigDecimal orderAmount,CrmService crm){Campaign c=require(code);Objects.requireNonNull(orderAmount);if(orderAmount.signum()<0)throw new IllegalArgumentException("Order amount cannot be negative");validate(c,guestId,orderAmount,crm);BigDecimal d=c.type()==DiscountType.PERCENT?orderAmount.multiply(c.value()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP):c.value();if(c.maxDiscount().signum()>0)d=d.min(c.maxDiscount());return d.min(orderAmount).setScale(2,RoundingMode.HALF_UP);}
    public synchronized Redemption redeem(String code,String guestId,BigDecimal orderAmount,CrmService crm)throws IOException{BigDecimal discount=preview(code,guestId,orderAmount,crm);Redemption r=new Redemption(nextId++,code.toUpperCase(Locale.ROOT),guestId,orderAmount,discount,LocalDateTime.now(clock));redemptions.add(r);try{persist();}catch(IOException e){redemptions.remove(r);nextId--;throw e;}return r;}
    private void validate(Campaign c,String guestId,BigDecimal amount,CrmService crm){LocalDate today=LocalDate.now(clock);if(!c.active())throw new IllegalStateException("Campaign is inactive");if(today.isBefore(c.startsOn())||today.isAfter(c.endsOn()))throw new IllegalStateException("Campaign is outside its validity period");if(c.maxUses()>0&&usage(c.code())>=c.maxUses())throw new IllegalStateException("Campaign usage limit reached");if(amount.compareTo(c.minimumSpend())<0)throw new IllegalStateException("Minimum spend not reached");if(crm.tier(guestId).ordinal()<c.minimumTier().ordinal())throw new IllegalStateException("Guest does not meet the minimum loyalty tier");}
    private Campaign require(String code){Campaign c=campaigns.get(req(code).toUpperCase(Locale.ROOT));if(c==null)throw new IllegalArgumentException("Unknown campaign code");return c;}
    public synchronized BigDecimal totalDiscounts(){return redemptions.stream().map(Redemption::discount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public synchronized String csv(){StringBuilder b=new StringBuilder("Code,Name,Type,Value,Max Discount,Minimum Spend,Start,End,Max Uses,Used,Minimum Tier,Active\n");for(Campaign c:campaigns.values())b.append(q(c.code())).append(',').append(q(c.name())).append(',').append(c.type()).append(',').append(c.value()).append(',').append(c.maxDiscount()).append(',').append(c.minimumSpend()).append(',').append(c.startsOn()).append(',').append(c.endsOn()).append(',').append(c.maxUses()).append(',').append(usage(c.code())).append(',').append(c.minimumTier()).append(',').append(c.active()).append('\n');b.append("\nRedemption ID,Code,Guest,Order Amount,Discount,Date\n");for(Redemption r:redemptions())b.append(r.id()).append(',').append(q(r.code())).append(',').append(q(r.guestId())).append(',').append(r.orderAmount()).append(',').append(r.discount()).append(',').append(r.redeemedAt()).append('\n');return b.toString();}
    private static String q(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private void persist()throws IOException{Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"campaign-",".tmp");try{List<String> rows=new ArrayList<>();for(Campaign c:campaigns.values())rows.add(String.join("\t","C",enc(c.code()),enc(c.name()),c.type().name(),c.value().toPlainString(),c.maxDiscount().toPlainString(),c.minimumSpend().toPlainString(),c.startsOn().toString(),c.endsOn().toString(),Integer.toString(c.maxUses()),c.minimumTier().name(),Boolean.toString(c.active())));for(Redemption r:redemptions)rows.add(String.join("\t","R",Long.toString(r.id()),enc(r.code()),enc(r.guestId()),r.orderAmount().toPlainString(),r.discount().toPlainString(),r.redeemedAt().toString()));Files.write(tmp,rows,StandardCharsets.UTF_8);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private void load()throws IOException{for(String row:Files.readAllLines(file,StandardCharsets.UTF_8)){String[] f=row.split("\t",-1);try{if(f[0].equals("C")&&f.length==12){Campaign c=new Campaign(dec(f[1]),dec(f[2]),DiscountType.valueOf(f[3]),new BigDecimal(f[4]),new BigDecimal(f[5]),new BigDecimal(f[6]),LocalDate.parse(f[7]),LocalDate.parse(f[8]),Integer.parseInt(f[9]),CrmService.Tier.valueOf(f[10]),Boolean.parseBoolean(f[11]));campaigns.put(c.code(),c);}else if(f[0].equals("R")&&f.length==7){Redemption r=new Redemption(Long.parseLong(f[1]),dec(f[2]),dec(f[3]),new BigDecimal(f[4]),new BigDecimal(f[5]),LocalDateTime.parse(f[6]));redemptions.add(r);nextId=Math.max(nextId,r.id()+1);}}catch(RuntimeException ignored){}}}
}
