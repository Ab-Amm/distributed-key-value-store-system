package com.example.distributedkeyvalue.loadBalancer;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoadBalancer {
    public final Map<String, NodeStatus> nodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        initializeNodes();
        scheduleStaleNodeCleanup();
    }

    private void initializeNodes() {
//        List<String> initialNodes = List.of(
//                "http://shard1-node1:8080",
//                "http://shard1-node2:8080",
//                "http://shard1-node3:8080",
//                "http://shard2-node1:8080",
//                "http://shard2-node2:8080",
//                "http://shard2-node3:8080"
//        );
//        initialNodes.forEach(node ->
//                nodes.put(node, new NodeStatus(true, new AtomicInteger(0)))
//        );
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


    public String getShardAwareNode(String shardId) {
        return nodes.entrySet().stream()
                .filter(entry -> {
                    String nodeUrl = entry.getKey();
                    String nodeShardId = extractShardIdFromUrl(nodeUrl);
                    return entry.getValue().healthy && nodeShardId.equals(shardId);
                })
                .min(Comparator.comparingInt(entry -> entry.getValue().activeConnections.get()))
                .map(Map.Entry::getKey) // Extract node URL from the entry
                .orElseThrow(() -> new IllegalStateException("No healthy nodes for shard: " + shardId));
    }

    private String extractShardIdFromUrl(String nodeUrl) {
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

    private void removeStaleNodes() {
        long now = System.currentTimeMillis();
        nodes.entrySet().removeIf(entry -> {
            NodeStatus status = entry.getValue();
            return (now - status.lastHeartbeat) > 15_000;
        });
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