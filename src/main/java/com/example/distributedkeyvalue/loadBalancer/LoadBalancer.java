package com.example.distributedkeyvalue.loadBalancer;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Component
public class LoadBalancer {
    public final Map<String, NodeStatus> nodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        initializeNodes();
        scheduleStaleNodeCleanup();
    } 

    private void initializeNodes() {
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


    public String getShardAwareNode(String shardId, boolean isWrite) {
        return nodes.entrySet().stream()
                .filter(entry -> {
                    if (!entry.getValue().healthy ||
                            !extractShardIdFromUrl(entry.getKey()).equals(shardId)) {
                        return false;
                    }
                    if (isWrite) {
                        // Call LeaderController to check leadership
                        String leaderStatus = restTemplate.getForObject(
                                entry.getKey() + "/leader/" + shardId,
                                String.class
                        );
                        System.out.println("Routing " + "WRITE"
                                + " for shard " + shardId);
                        return "LEADER".equals(leaderStatus);
                    }

                    System.out.println("Routing " + "READ"
                            + " for shard " + shardId);
                    return true; // Reads can go to any node
                })
                .min(Comparator.comparingInt(entry -> entry.getValue().activeConnections.get()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new RuntimeException("No healthy nodes available"));
    }



    public String extractShardIdFromUrl(String nodeUrl) {
        // Example: Extract "shard1" from "http://shard1-node1:8080"
        return nodeUrl.replace("http://", "")
                .split("-")[0];
    }


    public void incrementConnections(String node) {
        Optional.ofNullable(nodes.get(node))
                .ifPresent(status -> status.activeConnections.incrementAndGet());
    }

    public void decrementConnections(String node) {
        Optional.ofNullable(nodes.get(node))
                .ifPresent(status -> status.activeConnections.decrementAndGet());
    }

    // In LoadBalancer.java
    public void updateHealthStatus(String nodeUrl) {
        NodeStatus status = nodes.get(nodeUrl);
        if (status != null) {
            // Increase health check tolerance
            status.lastHeartbeat = System.currentTimeMillis();
            status.healthy = true; // Assume healthy until proven otherwise
        }
    }

    // Modify cleanup to be less aggressive
    private void removeStaleNodes() {
        long now = System.currentTimeMillis();
        nodes.entrySet().removeIf(entry ->
                (now - entry.getValue().lastHeartbeat) > 30_000 // 30s → from 15s
        );
    }
    private void scheduleStaleNodeCleanup() {
        healthCheckExecutor.scheduleAtFixedRate(
                this::removeStaleNodes,
                15, 15, TimeUnit.SECONDS // Adjust interval as needed
        );
    }

    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdownNow();
    }
}