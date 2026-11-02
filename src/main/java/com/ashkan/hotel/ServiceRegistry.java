package com.ashkan.hotel;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Small configurable service registry with active health checks and failure-aware lookup. */
public final class ServiceRegistry {
    public record Service(String name, URI baseUri, String healthPath) {}
    private final Map<String,Service> services = new ConcurrentHashMap<>();
    private final Map<String,Long> failures = new ConcurrentHashMap<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public ServiceRegistry register(String name, URI baseUri){
        if(name==null||!name.matches("[a-z][a-z0-9-]{1,40}"))throw new IllegalArgumentException("Invalid service name");
        if(baseUri==null||!("http".equalsIgnoreCase(baseUri.getScheme())||"https".equalsIgnoreCase(baseUri.getScheme())))throw new IllegalArgumentException("Invalid service URI");
        services.put(name,new Service(name,baseUri,"health"));return this;
    }
    public Optional<Service> find(String name){return Optional.ofNullable(services.get(name));}
    public Optional<Service> healthy(String name){Service s=services.get(name);if(s==null)return Optional.empty();try{HttpRequest req=HttpRequest.newBuilder(s.baseUri().resolve(s.healthPath())).timeout(Duration.ofSeconds(3)).GET().build();HttpResponse<Void> r=client.send(req,HttpResponse.BodyHandlers.discarding());if(r.statusCode()>=200&&r.statusCode()<300){failures.remove(name);return Optional.of(s);}}catch(Exception ignored){}failures.merge(name,1L,Long::sum);return Optional.empty();}
    public Map<String,String> snapshot(){Map<String,String> out=new TreeMap<>();services.forEach((n,s)->out.put(n,healthy(n).isPresent()?"UP":"DOWN"));return Collections.unmodifiableMap(out);}
    public long failureCount(String name){return failures.getOrDefault(name,0L);}
    public Map<String,Service> services(){return Collections.unmodifiableMap(new TreeMap<>(services));}
}
