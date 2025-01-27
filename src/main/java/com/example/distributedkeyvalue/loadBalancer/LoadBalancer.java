package com.example.distributedkeyvalue.loadBalancer;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class LoadBalancer {
    public final Map<String, NodeStatus> nodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1);
    private final WebClient healthCheckClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofMillis(1000))
            ))
            .build();

    @PostConstruct
    public void init() {
        initializeNodes();
        startHealthChecks();
    }

    private void initializeNodes() {
        List<String> initialNodes = List.of(
                "http://shard1-node1:8080",
                "http://shard1-node2:8080",
                "http://shard1-node3:8080",
                "http://shard2-node1:8080",
                "http://shard2-node2:8080",
                "http://shard2-node3:8080"
        );
        initialNodes.forEach(node ->
                nodes.put(node, new NodeStatus(true, new AtomicInteger(0)))
        );
    }

    private void startHealthChecks() {
        healthCheckExecutor.scheduleAtFixedRate(
                this::performHealthChecks,
                5, 10, TimeUnit.SECONDS
        );
        healthCheckExecutor.scheduleAtFixedRate(
                this::removeStaleNodes,
                15, 15, TimeUnit.SECONDS
        );
    }

    public static class NodeStatus {
        volatile boolean healthy;
        final AtomicInteger activeConnections;
        volatile long lastHeartbeat;

        NodeStatus(boolean healthy, AtomicInteger activeConnections) {
            this.healthy = healthy;
            this.activeConnections = activeConnections;
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }

    public synchronized String getLeastLoadedNode() {
        return nodes.entrySet().stream()
                .filter(entry -> entry.getValue().healthy)
                .min(Comparator.comparingInt(entry -> entry.getValue().activeConnections.get()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No healthy nodes available"));
    }

    public String getShardAwareNode(String shardId) {
        return nodes.entrySet().stream()
                .filter(entry -> entry.getValue().healthy && entry.getKey().contains(shardId))
                .min(Comparator.comparingInt(entry -> entry.getValue().activeConnections.get()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No healthy nodes available for shard: " + shardId));
    }

    private void performHealthChecks() {
        nodes.forEach((node, status) -> {
            healthCheckClient.get()
                    .uri(node + "/health")
                    .exchangeToMono(response -> {
                        status.healthy = response.statusCode().is2xxSuccessful();
                        return Mono.just(status.healthy);
                    })
                    .onErrorResume(e -> {
                        status.healthy = false;
                        return Mono.just(false);
                    })
                    .block();
        });
    }

    public void incrementConnections(String node) {
        Optional.ofNullable(nodes.get(node))
                .ifPresent(status -> status.activeConnections.incrementAndGet());
    }

    public void decrementConnections(String node) {
        Optional.ofNullable(nodes.get(node))
                .ifPresent(status -> status.activeConnections.decrementAndGet());
    }

    private void removeStaleNodes() {
        long now = System.currentTimeMillis();
        nodes.entrySet().removeIf(entry -> {
            NodeStatus status = entry.getValue();
            return (now - status.lastHeartbeat) > 15_000;
        });
    }

    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdownNow();
    }
}