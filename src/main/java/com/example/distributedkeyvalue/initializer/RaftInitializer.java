package com.example.distributedkeyvalue.initializer;

import com.example.distributedkeyvalue.config.RaftConfig;
import com.example.distributedkeyvalue.model.ShardRegistrationRequest;
import org.apache.ratis.server.RaftServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Profile("node")
public class RaftInitializer {

    private RaftServer raftServer; // Store the RaftServer instance

    @Value("${RAFT_NODE_ID:default-node}")
    private String nodeId;

    @Value("${RAFT_PEERS:default-node:localhost:9870}")
    private String peers;

    @Value("${SHARD_ID:default-shard}")
    private String shardId;

    @Autowired
    private RestTemplate restTemplate;


    @PostConstruct
    public void initRaftServer() throws Exception {
        // Only initialize Raft server here
        List<String> peerAddresses = Arrays.asList(peers.split(","));
        File storageDir = new File("./raft-storage/" + nodeId);
        raftServer = RaftConfig.newRaftServer(shardId, nodeId, peerAddresses, storageDir).build();
        raftServer.start();
        System.out.println("Raft server started on node " + nodeId);
    }

    @Bean
    @ConditionalOnProperty(name = "shard.registration.enabled", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner registerWithShardManager() {
        return args -> {

            List<String> raftNodes = Arrays.asList(peers.split(","));
            List<String> restNodes = Arrays.stream(peers.split(","))
                    .map(peer -> {
                        String[] parts = peer.split(":", 3);
                        return parts[1] + ":8080"; // REST port
                    })
                    .collect(Collectors.toList());

            ShardRegistrationRequest request = new ShardRegistrationRequest(shardId, restNodes, raftNodes);
            try {
                restTemplate.postForEntity("http://shard-manager:8080/shard-manager/register-shard", request, Void.class);
            } catch (ResourceAccessException e) {
                System.err.println("Warning: Failed to register with shard manager - " + e.getMessage());
            }
        };
    }

    @Bean
    public RaftServer raftServer() {
        return raftServer;
    }
}