package com.example.distributedkeyvalue.loadBalancer;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


@Profile("loadBalancer")
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

        LoadBalancer.NodeStatus nodeStatus = loadBalancer.nodes.getOrDefault(nodeId,
                new LoadBalancer.NodeStatus(true, new AtomicInteger(0)));
        if ("healthy".equals(status)) {
            nodeStatus.healthy = true;
        } else {
            nodeStatus.healthy = false;
        }
        nodeStatus.lastHeartbeat = System.currentTimeMillis();
        loadBalancer.nodes.put(nodeId, nodeStatus);

        return ResponseEntity.ok().build();
    }
}