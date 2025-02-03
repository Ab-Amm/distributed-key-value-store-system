package com.example.distributedkeyvalue.controller;

import com.example.distributedkeyvalue.model.ShardInfo;
import com.example.distributedkeyvalue.model.ShardRegistrationRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.hash;

// ShardManagerController.java
@Profile("manager")
@RestController
@RequestMapping("/shard-manager")
public class ShardManagerController {
    private final TreeMap<Long, String> shardRing = new TreeMap<>(); // Consistent hashing ring
    private final Map<String, List<String>> shardToNodes = new ConcurrentHashMap<>(); // Shard ID → Raft nodes

    @GetMapping("/shard/{key}")
    public ResponseEntity<ShardInfo> getShardForKey(@PathVariable String key) {
        if (shardRing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ShardInfo("NO_SHARDS", List.of()));
        }

        long hash = hash(key); // e.g., SHA-256
        Map.Entry<Long, String> entry = shardRing.ceilingEntry(hash);
        String shardId = entry != null ? entry.getValue() : shardRing.firstEntry().getValue();
        List<String> nodes = shardToNodes.get(shardId);
        return ResponseEntity.ok(new ShardInfo(shardId, nodes));
    }


    @PostMapping("/register-shard")
    public void registerShard(@RequestBody ShardRegistrationRequest request) {
        shardToNodes.put(request.shardId(), request.raftNodes());
        for (String node : request.raftNodes()) {
            long hash = hash(node + request.shardId()); // Virtual node
            shardRing.put(hash, request.shardId());
        }
    }
}
