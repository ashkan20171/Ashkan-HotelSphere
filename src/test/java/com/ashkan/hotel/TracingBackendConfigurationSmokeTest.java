package com.ashkan.hotel;

import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies that the local tracing backend and Collector export pipeline are provisioned. */
public final class TracingBackendConfigurationSmokeTest {
    public static void main(String[] args) throws Exception {
        String compose = Files.readString(Path.of("infra/docker-compose.yml"));
        String collector = Files.readString(Path.of("infra/otel/collector-config.yaml"));
        String datasource = Files.readString(Path.of("infra/grafana/provisioning/datasources/tracing.yml"));
        if (!compose.contains("jaegertracing/all-in-one:1.62.0")) throw new AssertionError("Jaeger service missing");
        if (!compose.contains("127.0.0.1:16686:16686")) throw new AssertionError("Jaeger UI must bind locally");
        if (!collector.contains("endpoint: jaeger:4317")) throw new AssertionError("Collector Jaeger exporter missing");
        if (!collector.contains("exporters: [otlp/jaeger, debug]")) throw new AssertionError("Trace pipeline exporters missing");
        if (!datasource.contains("url: http://jaeger:16686")) throw new AssertionError("Grafana Jaeger datasource missing");
        System.out.println("TracingBackendConfigurationSmokeTest: PASS");
    }
}
