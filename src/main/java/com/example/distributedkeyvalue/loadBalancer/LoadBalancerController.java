package com.example.distributedkeyvalue.loadBalancer;

import com.example.distributedkeyvalue.model.ShardInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;




@RestController
@Profile("loadbalancer")
@RequestMapping("/api/v1/keys")
public class LoadBalancerController {
    private final LoadBalancer loadBalancer;
    private final WebClient webClient;
    private final RestTemplate restTemplate;

    public LoadBalancerController(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        this.webClient = WebClient.create();
        this.restTemplate = new RestTemplate();
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> putKey(@PathVariable String key, @RequestBody String value) {
        return processRequest(key, targetNode -> {
            restTemplate.put(
                    targetNode + "/internal/api/v1/keys/" + key,
                    value
            );
            return ResponseEntity.ok().build();
        });
    }

    @GetMapping("/{key}")
    public String getKey(@PathVariable String key) {
        return processRequest(key, targetNode ->
                restTemplate.getForObject(
                        targetNode + "/internal/api/v1/keys/" + key,
                        String.class
                )
        );
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteKey(@PathVariable String key) {
        return processRequest(key, targetNode -> {
            restTemplate.delete(targetNode + "/internal/api/v1/keys/" + key);
            return ResponseEntity.ok().build();
        });
    }

    private <T> T processRequest(String key, RequestProcessor<T> processor) {
        ShardInfo shardInfo;
        try {
            shardInfo = webClient.get()
                    .uri("http://shard-manager:8080/shard-manager/shard/{key}", key)
                    .retrieve()
                    .bodyToMono(ShardInfo.class)
                    .block(Duration.ofSeconds(2)); // Timeout for shard-manager response
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shard manager is unavailable", e);
        }

        String targetNode = loadBalancer.getShardAwareNode(shardInfo.shardId());
        loadBalancer.incrementConnections(targetNode);

        try {
            return processor.process(targetNode);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request on node: " + targetNode, e);
        } finally {
            loadBalancer.decrementConnections(targetNode);
        }
    }


    @FunctionalInterface
    private interface RequestProcessor<T> {
        T process(String targetNode);
    }
}
