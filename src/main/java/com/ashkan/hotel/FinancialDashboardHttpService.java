package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.*;
import java.util.concurrent.Executors;

/** Admin-only financial dashboard/API backed by the payment PostgreSQL database. */
public final class FinancialDashboardHttpService implements AutoCloseable {
    private final HttpServer server;
    private final FinancialReportingRepository repository;
    private final JwtTokenService tokens;

    public FinancialDashboardHttpService(int port, String jdbcUrl, String user, String password,
                                         int poolSize, String jwtSecret) throws IOException {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        repository = new FinancialReportingRepository(jdbcUrl, user, password, poolSize);
        tokens = new JwtTokenService(jwtSecret, 3600);
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/health", x -> respond(x, 200, "application/json; charset=utf-8", "{\"service\":\"financial-dashboard\",\"status\":\"UP\"}"));
        server.createContext("/admin/finance", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "financial-dashboard"); t.setDaemon(true); return t; }));
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange x) throws IOException {
        if (!"GET".equals(x.getRequestMethod()) && !"POST".equals(x.getRequestMethod())) { respond(x,405,"application/json; charset=utf-8","{\"error\":\"method_not_allowed\"}"); return; }
        JwtTokenService.Claims claims = authorize(x);
        if (claims == null) return;
        if (!"Admin".equals(claims.role())) { respond(x,403,"application/json; charset=utf-8","{\"error\":\"admin_required\"}"); return; }
        String path = x.getRequestURI().getPath();
        try {
            if ("/admin/finance".equals(path) || "/admin/finance/".equals(path)) {
                if (!"GET".equals(x.getRequestMethod())) { respond(x,405,"application/json; charset=utf-8","{\"error\":\"method_not_allowed\"}"); return; }
                respond(x,200,"text/html; charset=utf-8", dashboard()); return;
            }
            if ("/admin/finance/api/expenses".equals(path) && "POST".equals(x.getRequestMethod())) { addExpense(x); return; }
            Map<String,String> q = query(x.getRequestURI().getRawQuery());
            LocalDate from = LocalDate.parse(required(q,"from"));
            LocalDate through = LocalDate.parse(required(q,"through"));
            BigDecimal tax = new BigDecimal(q.getOrDefault("taxRate","0"));
            if (through.isBefore(from) || from.isBefore(LocalDate.of(2000,1,1)) || through.isAfter(LocalDate.now().plusDays(1))) throw new IllegalArgumentException("Invalid reporting date range");
            var report = FinancialAnalyticsService.summarize(repository.payments(from,through), repository.refunds(from,through), repository.expenses(from,through), from, through, tax);
            if ("/admin/finance/export.csv".equals(path)) {
                x.getResponseHeaders().set("Content-Disposition", "attachment; filename=financial-report-"+from+"-"+through+".csv");
                respond(x,200,"text/csv; charset=utf-8",FinancialAnalyticsService.toCsv(report)); return;
            }
            if ("/admin/finance/api/report".equals(path)) { String group=q.getOrDefault("groupBy","day"); if(!Set.of("day","month","year").contains(group))throw new IllegalArgumentException("groupBy must be day, month, or year"); respond(x,200,"application/json; charset=utf-8",toJson(report,group)); return; }
            respond(x,404,"application/json; charset=utf-8","{\"error\":\"not_found\"}");
        } catch (DateTimeException | NumberFormatException e) { respond(x,400,"application/json; charset=utf-8","{\"error\":\"invalid_query\"}"); }
        catch (IllegalArgumentException e) { respond(x,400,"application/json; charset=utf-8","{\"error\":"+json(e.getMessage())+"}"); }
        catch (Exception e) { System.err.println("Financial dashboard request failed: "+e.getMessage()); respond(x,503,"application/json; charset=utf-8","{\"error\":\"financial_data_unavailable\"}"); }
    }

    private void addExpense(HttpExchange x) throws Exception {
        byte[] body=x.getRequestBody().readNBytes(8193);
        if(body.length>8192){respond(x,413,"application/json; charset=utf-8","{\"error\":\"payload_too_large\"}");return;}
        String raw=new String(body,StandardCharsets.UTF_8);
        String id=field(raw,"id"), date=field(raw,"date"), amount=field(raw,"amount"), currency=field(raw,"currency"), category=field(raw,"category");
        if(id==null||!id.matches("[A-Za-z0-9_.-]{3,80}")||date==null||amount==null||currency==null||category==null) throw new IllegalArgumentException("Required expense fields: id, date, amount, currency, category");
        if(category.length()>80)throw new IllegalArgumentException("Category is too long");
        repository.addExpense(new FinancialAnalyticsService.Expense(id,LocalDate.parse(date),new BigDecimal(amount),currency,category));
        respond(x,201,"application/json; charset=utf-8","{\"created\":true,\"id\":"+json(id)+"}");
    }

    private JwtTokenService.Claims authorize(HttpExchange x) throws IOException {
        String h=x.getRequestHeaders().getFirst("Authorization");
        if(h==null||!h.startsWith("Bearer ")){respond(x,401,"application/json; charset=utf-8","{\"error\":\"bearer_token_required\"}");return null;}
        JwtTokenService.Claims c=tokens.verify(h.substring(7));
        if(c==null){respond(x,401,"application/json; charset=utf-8","{\"error\":\"invalid_or_expired_token\"}");return null;}
        return c;
    }

    private static String toJson(FinancialAnalyticsService.Report r, String groupBy) {
        Map<String,Period> periods=new TreeMap<>();
        for(var d:r.daily()){String key=switch(groupBy){case "month"->d.date().toString().substring(0,7);case "year"->d.date().toString().substring(0,4);default->d.date().toString();};Period p=periods.computeIfAbsent(key+"|"+d.currency(),k->new Period(key,d.currency()));p.captured=p.captured.add(d.captured());p.refunded=p.refunded.add(d.refunded());p.expenses=p.expenses.add(d.expenses());}
        StringBuilder b=new StringBuilder("{\"groupBy\":").append(json(groupBy)).append(",\"from\":").append(json(r.from().toString())).append(",\"through\":").append(json(r.through().toString())).append(",\"currencies\":[");
        for(int i=0;i<r.currencies().size();i++){var c=r.currencies().get(i);if(i>0)b.append(',');b.append("{\"currency\":").append(json(c.currency())).append(",\"captured\":").append(c.captured()).append(",\"refunded\":").append(c.refunded()).append(",\"expenses\":").append(c.expenses()).append(",\"netBeforeTax\":").append(c.netBeforeTax()).append(",\"estimatedTax\":").append(c.estimatedTax()).append(",\"netAfterTax\":").append(c.netAfterTax()).append(",\"capturedCount\":").append(c.capturedCount()).append(",\"failedCount\":").append(c.failedCount()).append('}');}
        b.append("],\"daily\":[");for(int i=0;i<r.daily().size();i++){var d=r.daily().get(i);if(i>0)b.append(',');b.append("{\"date\":").append(json(d.date().toString())).append(",\"currency\":").append(json(d.currency())).append(",\"captured\":").append(d.captured()).append(",\"refunded\":").append(d.refunded()).append(",\"expenses\":").append(d.expenses()).append('}');}
        b.append("],\"periods\":[");int n=0;for(var e:periods.entrySet()){if(n++>0)b.append(',');Period p=e.getValue();b.append("{\"period\":").append(json(p.label)).append(",\"currency\":").append(json(p.currency)).append(",\"captured\":").append(p.captured).append(",\"refunded\":").append(p.refunded).append(",\"expenses\":").append(p.expenses).append('}');}return b.append("]}").toString();
    }
    private static final class Period{final String label,currency;BigDecimal captured=BigDecimal.ZERO,refunded=BigDecimal.ZERO,expenses=BigDecimal.ZERO;Period(String label,String currency){this.label=label;this.currency=currency;}}

    private static Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw!=null)for(String p:raw.split("&")){String[] a=p.split("=",2);m.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length==2?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}return m;}
    private static String required(Map<String,String> q,String k){String v=q.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing query parameter: "+k);return v;}
    private static String field(String raw,String name){var m=java.util.regex.Pattern.compile("\\\""+name+"\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([0-9]+(?:\\.[0-9]+)?))").matcher(raw);return m.find()?Objects.requireNonNullElse(m.group(1),m.group(2)):null;}
    private static String json(String s){return "\""+Objects.toString(s,"").replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private static void respond(HttpExchange x,int status,String type,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");x.sendResponseHeaders(status,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    private static String dashboard(){return """
<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Ashkan HotelSphere · Finance</title><style>
:root{color-scheme:dark;--bg:#0b1220;--panel:#131f32;--line:#26364e;--text:#edf4ff;--muted:#9eb0c8;--accent:#50c6b3;--blue:#6da9ff}*{box-sizing:border-box}body{margin:0;background:linear-gradient(135deg,#0b1220,#101d31);color:var(--text);font:15px Inter,Segoe UI,sans-serif}.wrap{max-width:1180px;margin:auto;padding:28px}.head{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap}.brand{font-size:22px;font-weight:750}.muted{color:var(--muted)}.panel,.metric{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:18px}.filters{display:flex;gap:10px;flex-wrap:wrap;margin:22px 0}.filters input,.filters button,.token{background:#0d1728;color:var(--text);border:1px solid var(--line);border-radius:9px;padding:11px}.filters button{background:var(--accent);color:#06211d;font-weight:700;cursor:pointer}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:13px}.metric small{color:var(--muted)}.metric strong{display:block;font-size:25px;margin-top:10px;overflow-wrap:anywhere}.grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}.bars{display:flex;align-items:end;gap:8px;height:150px;padding:14px 2px 25px}.currency-chart{display:flex;flex-direction:column;gap:6px;margin-bottom:12px}.bar{flex:1;min-width:10px;background:linear-gradient(180deg,var(--accent),#287e85);border-radius:6px 6px 0 0;position:relative}.bar span{position:absolute;top:100%;left:50%;transform:translateX(-50%);font-size:10px;color:var(--muted);white-space:nowrap}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:10px 7px;border-bottom:1px solid var(--line)}th{color:var(--muted);font-size:12px}a{color:var(--blue)}@media(max-width:700px){.grid{grid-template-columns:1fr}.wrap{padding:16px}}
</style></head><body><main class=\"wrap\"><header class=\"head\"><div><div class=\"brand\">ASHKAN <span style=\"color:var(--accent)\">HOTELSPHERE</span></div><div class=\"muted\">Financial intelligence · Admin workspace</div></div><input id=\"token\" class=\"token\" type=\"password\" placeholder=\"Admin JWT token\" style=\"width:min(420px,100%)\"></header>
<section class=\"filters\"><label>From <input id=\"from\" type=\"date\"></label><label>Through <input id=\"through\" type=\"date\"></label><label>Period <select id=\"groupBy\"><option value=\"day\">Daily</option><option value=\"month\">Monthly</option><option value=\"year\">Yearly</option></select></label><label>Tax % <input id=\"tax\" type=\"number\" min=\"0\" max=\"100\" step=\"0.1\" value=\"0\" style=\"width:90px\"></label><button onclick=\"loadReport()\">Refresh report</button><a id=\"csv\" href=\"#\" onclick=\"exportCsv(event)\">Export CSV</a></section>
<section id=\"metrics\" class=\"metrics\"><div class=\"panel muted\">Authenticate with an Admin JWT, then load a date range.</div></section><section class=\"grid\"><div class=\"panel\"><h3>Captured revenue by selected period</h3><div id=\"chart\"></div></div><div class=\"panel\"><h3>Currency summary</h3><div id=\"summary\" class=\"muted\">No report loaded</div></div></section><section class=\"panel\" style=\"margin-top:14px\"><h3>Daily activity</h3><div style=\"overflow:auto\"><table><thead><tr><th>Date</th><th>Currency</th><th>Captured</th><th>Refunded</th><th>Expenses</th></tr></thead><tbody id=\"daily\"></tbody></table></div></section><p id=\"error\" class=\"muted\" role=\"status\"></p></main>
<script>const $=id=>document.getElementById(id);const today=new Date();$('through').value=today.toISOString().slice(0,10);$('from').value=new Date(today.getFullYear(),today.getMonth(),1).toISOString().slice(0,10);let report=null;const money=(v,c)=>new Intl.NumberFormat(undefined,{style:'currency',currency:c}).format(Number(v));async function request(url){const r=await fetch(url,{headers:{Authorization:'Bearer '+$('token').value.trim()}});if(!r.ok)throw Error(r.status===401?'Invalid or expired token':r.status===403?'Admin role required':'Request failed ('+r.status+')');return r}async function loadReport(){try{$('error').textContent='';const p=new URLSearchParams({from:$('from').value,through:$('through').value,taxRate:$('tax').value||'0',groupBy:$('groupBy').value});report=await(await request('/admin/finance/api/report?'+p)).json();render()}catch(e){$('error').textContent=e.message}}function render(){const rows=report.currencies;$('metrics').innerHTML=rows.map(c=>'<article class="metric"><small>Net after estimated tax · '+c.currency+'</small><strong>'+money(c.netAfterTax,c.currency)+'</strong><small>Captured '+money(c.captured,c.currency)+'</small></article>').join('')||'<div class="panel">No financial activity in this range.</div>';$('summary').innerHTML='<table><tbody>'+rows.map(c=>'<tr><td>'+c.currency+'</td><td>Captured</td><td>'+money(c.captured,c.currency)+'</td></tr><tr><td></td><td>Refunds / expenses</td><td>'+money(c.refunded,c.currency)+' / '+money(c.expenses,c.currency)+'</td></tr><tr><td></td><td>Estimated tax</td><td>'+money(c.estimatedTax,c.currency)+'</td></tr>').join('')+'</tbody></table>';const daily=report.daily;$('daily').innerHTML=daily.map(d=>'<tr><td>'+d.date+'</td><td>'+d.currency+'</td><td>'+money(d.captured,d.currency)+'</td><td>'+money(d.refunded,d.currency)+'</td><td>'+money(d.expenses,d.currency)+'</td></tr>').join('');const groups=[...new Set(report.periods.map(d=>d.currency))];$('chart').innerHTML=groups.map(currency=>{const items=report.periods.filter(d=>d.currency===currency).slice(-28);const max=Math.max(1,...items.map(d=>Number(d.captured)));return '<div style="min-width:100%;height:100%"><b>'+currency+'</b><div class="bars">'+items.map(d=>'<div class="bar" title="'+d.period+' '+currency+' '+d.captured+'" style="height:'+Math.max(3,Number(d.captured)/max*100)+'%"><span>'+d.period+'</span></div>').join('')+'</div></div>'}).join('')||'<span class="muted">No captured revenue</span>'}async function exportCsv(e){e.preventDefault();try{const p=new URLSearchParams({from:$('from').value,through:$('through').value,taxRate:$('tax').value||'0'});const r=await request('/admin/finance/export.csv?'+p);const blob=await r.blob();const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='financial-report.csv';a.click();URL.revokeObjectURL(a.href)}catch(e){$('error').textContent=e.message}}</script></body></html>""";}
    @Override public void close(){server.stop(0);repository.close();}
    public static void main(String[] args)throws Exception{int port=Integer.parseInt(System.getenv().getOrDefault("FINANCE_PORT",args.length>0?args[0]:"8096"));String url=System.getenv("PAYMENT_JDBC_URL");if(url==null||url.isBlank())throw new IllegalStateException("PAYMENT_JDBC_URL is required");String secret=System.getenv("JWT_SECRET");FinancialDashboardHttpService app=new FinancialDashboardHttpService(port,url,System.getenv().getOrDefault("DB_USER","hotel"),System.getenv().getOrDefault("DB_PASSWORD","change-me"),Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","6")),secret);Runtime.getRuntime().addShutdownHook(new Thread(app::close));app.start();System.out.println("Admin financial dashboard listening on "+app.port());}
}
