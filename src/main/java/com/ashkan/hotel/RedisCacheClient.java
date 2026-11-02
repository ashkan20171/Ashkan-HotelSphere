package com.ashkan.hotel;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.LongAdder;

/** Minimal Redis RESP2 client. Each command uses a short-lived socket so failures never stall request threads indefinitely. */
public final class RedisCacheClient {
    private static final LongAdder HITS=new LongAdder(), MISSES=new LongAdder(), ERRORS=new LongAdder();
    private final String host,password; private final int port,timeoutMillis,database; private final boolean enabled;
    public RedisCacheClient(){this(System.getenv().getOrDefault("REDIS_HOST","redis"),integer("REDIS_PORT",6379),System.getenv().getOrDefault("REDIS_PASSWORD",""),integer("REDIS_DATABASE",0),integer("REDIS_TIMEOUT_MS",250));}
    RedisCacheClient(String host,int port,String password,int database,int timeoutMillis){this.host=host;this.port=port;this.password=password;this.database=database;this.timeoutMillis=timeoutMillis;this.enabled=Boolean.parseBoolean(System.getenv().getOrDefault("REDIS_CACHE_ENABLED","true"));}
    public String get(String key){if(!enabled)return null;try{Object v=command("GET",key);if(v==null){MISSES.increment();return null;}HITS.increment();return String.valueOf(v);}catch(Exception e){ERRORS.increment();return null;}}
    public boolean set(String key,String value,int ttlSeconds){if(!enabled)return false;try{return "OK".equals(command("SET",key,value,"EX",Integer.toString(Math.max(1,ttlSeconds))));}catch(Exception e){ERRORS.increment();return false;}}
    public void delete(String key){if(!enabled)return;try{command("DEL",key);}catch(Exception e){ERRORS.increment();}}
    /** Atomic lease acquisition: SET key token NX PX ttl. False means busy or Redis unavailable. */
    public boolean acquireLock(String key,String token,int leaseMillis){if(!enabled)return false;try{return "OK".equals(command("SET",key,token,"NX","PX",Integer.toString(Math.max(100,leaseMillis))));}catch(Exception e){ERRORS.increment();return false;}}
    /** Releases only the lease owned by this token; never deletes another worker's lock. */
    public boolean releaseLock(String key,String token){if(!enabled)return false;String script="if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";try{return Long.valueOf(1).equals(command("EVAL",script,"1",key,token));}catch(Exception e){ERRORS.increment();return false;}}
    private Object command(String... args)throws IOException{
        try(Socket s=new Socket()){s.connect(new InetSocketAddress(host,port),timeoutMillis);s.setSoTimeout(timeoutMillis);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
            if(password!=null&&!password.isBlank()){write(out,"AUTH",password);read(in);}
            if(database!=0){write(out,"SELECT",Integer.toString(database));read(in);}
            write(out,args);return read(in);
        }
    }
    private static void write(OutputStream out,String... args)throws IOException{StringBuilder b=new StringBuilder("*").append(args.length).append("\r\n");for(String a:args){byte[] bytes=a.getBytes(StandardCharsets.UTF_8);b.append('$').append(bytes.length).append("\r\n");out.write(b.toString().getBytes(StandardCharsets.UTF_8));b.setLength(0);out.write(bytes);out.write('\r');out.write('\n');}out.flush();}
    private static Object read(InputStream in)throws IOException{int type=in.read();if(type<0)throw new EOFException("Redis closed connection");String line=line(in);return switch(type){case '+'->line;case '-'->throw new IOException("Redis: "+line);case ':'->Long.parseLong(line);case '$'->{int n=Integer.parseInt(line);if(n<0)yield null;byte[] data=in.readNBytes(n);if(data.length!=n)throw new EOFException();expectCrLf(in);yield new String(data,StandardCharsets.UTF_8);}default->throw new IOException("Unsupported Redis RESP response: "+(char)type);};}
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int prev=-1,c;while((c=in.read())!=-1){if(prev=='\r'&&c=='\n'){byte[] x=b.toByteArray();return new String(x,0,Math.max(0,x.length-1),StandardCharsets.UTF_8);}b.write(c);prev=c;}throw new EOFException();}
    private static void expectCrLf(InputStream in)throws IOException{if(in.read()!='\r'||in.read()!='\n')throw new IOException("Malformed Redis response");}
    private static int integer(String key,int fallback){try{return Integer.parseInt(System.getenv().getOrDefault(key,Integer.toString(fallback)));}catch(Exception e){return fallback;}}
    public static long hits(){return HITS.sum();}public static long misses(){return MISSES.sum();}public static long errors(){return ERRORS.sum();}
}
