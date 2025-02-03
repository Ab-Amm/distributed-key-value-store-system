package com.example.distributedkeyvalue.loadBalancer;

import com.example.distributedkeyvalue.model.ShardInfo;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@RestController
@Profile("loadbalancer")
@RequestMapping("/api/v1/keys")
public class LoadBalancerController {
    private final LoadBalancer loadBalancer;
    private final WebClient webClient;
    private final RestTemplate restTemplate;

    public LoadBalancerController(LoadBalancer loadBalancer, RestTemplate restTemplate) {
        this.loadBalancer = loadBalancer;
        this.webClient = WebClient.create();
        this.restTemplate = restTemplate;
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
        // Retrieve the shard info from the shard manager
        ShardInfo shardInfo;
        try {
            shardInfo = webClient.get()
                    .uri("http://shard-manager:8080/shard-manager/shard/{key}", key)
                    .retrieve()
                    .bodyToMono(ShardInfo.class)
                    .block(Duration.ofSeconds(2));
            System.out.println("[LB] GET shardinfo lwla: " + shardInfo.shardId());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Shard manager is unavailable", e);
        }
        System.out.println("[LB] GET shardinfo ttanya: " + shardInfo.shardId());

        // Ensure shardInfo is not null.
        assert shardInfo != null;

        // Retrieve the target node using getShardAwareNode with false as the second parameter.
        String targetNode = loadBalancer.getShardAwareNode(shardInfo.shardId(), false);
        // Critical validation
        System.out.println("[LB] Target node wst getkey: " + targetNode);
        if (!isNodeInShard(targetNode, shardInfo.shardId())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Routing error: Node " + targetNode + " doesn't belong to shard " + shardInfo.shardId());
        }
        System.out.println("[LB] 3la slamtna, target node dyal getkey dwwzat test: " + targetNode);

        // Increment the connection count for the chosen node.
        loadBalancer.incrementConnections(targetNode);

        try {
            System.out.println("[LB] GET for shard " + shardInfo.shardId()
                    + " routed to " + targetNode +"(hadi dyal try f getkey)");
            // Call the GET API on the target node and return the result.
            return restTemplate.getForObject(
                    targetNode + "/internal/api/v1/keys/" + key,
                    String.class
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request on node: " + targetNode, e);
        } finally {
            // Always decrement the connection count.
            System.out.println("[LB] Target node (hadi dyal finally f getkey): " + targetNode);
            loadBalancer.decrementConnections(targetNode);
        }
    }
    public boolean isNodeInShard(String nodeUrl, String expectedShardId) {
        String actualShardId = loadBalancer.extractShardIdFromUrl(nodeUrl);
        return actualShardId.equals(expectedShardId);
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
                    .block(Duration.ofSeconds(2));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shard manager is unavailable", e);
        }

        assert shardInfo != null;
        String targetNode = loadBalancer.getShardAwareNode(shardInfo.shardId(), true);
        loadBalancer.incrementConnections(targetNode); // Moved inside try block

        try {
            return processor.process(targetNode);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request on node: " + targetNode, e);
        } finally {
            System.out.println("[LB] Target node dyal put w delete: " + targetNode);
            loadBalancer.decrementConnections(targetNode);
        }
    }


    @FunctionalInterface
    private interface RequestProcessor<T> {
        T process(String targetNode);
    }

    // In LoadBalancerController.java
    @GetMapping("/lb-status")
    public Map<String, LoadBalancer.NodeStatus> getLbStatus() {
        return loadBalancer.getNodes();
    }



}
