package com.example.distributedkeyvalue.loadBalancer;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


@Profile("loadbalancer")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final LoadBalancer loadBalancer;

    public HealthController(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> handleHeartbeat(@RequestBody Map<String, String> payload) {
        String nodeId = payload.get("nodeId");
        String status = payload.get("status");
        boolean isHealthy = "healthy".equals(status);

        loadBalancer.nodes.compute(nodeId, (k, existingStatus) -> {
            if (existingStatus == null) {
                // New node: Initialize with status from payload
                return new LoadBalancer.NodeStatus(isHealthy, new AtomicInteger(0));
            } else {
                // Existing node: Update health and heartbeat
                existingStatus.healthy = isHealthy;
                existingStatus.lastHeartbeat = System.currentTimeMillis();
                return existingStatus;
            }
        });

        return ResponseEntity.ok().build();
    }
}