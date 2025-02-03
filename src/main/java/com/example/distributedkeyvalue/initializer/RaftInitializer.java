package com.example.distributedkeyvalue.initializer;

import com.example.distributedkeyvalue.config.RaftConfig;
import com.example.distributedkeyvalue.model.ShardRegistrationRequest;
import org.apache.ratis.server.RaftServer;
import org.apache.tomcat.util.http.fileupload.FileUtils;
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
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
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
        File storageDir = new File("raft-storage/" + nodeId);
        raftServer = RaftConfig.newRaftServer(shardId, nodeId, peerAddresses, storageDir).build();
        raftServer.start();
        System.out.println("Raft server started on node " + nodeId);
    }

    @Bean
    @ConditionalOnProperty(name = "shard.registration.enabled", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner registerWithShardManager() {
        return args -> {
            int attempts = 0;
            while (true) {
                try {

                    List<String> raftNodes = Arrays.asList(peers.split(","));
                    List<String> restNodes = Arrays.stream(peers.split(","))
                            .map(peer -> {
                                String[] parts = peer.split(":", 3);
                                return "http://" +parts[1] + ":8080"; // REST port
                            })
                            .collect(Collectors.toList());

                    ShardRegistrationRequest request = new ShardRegistrationRequest(shardId, restNodes, raftNodes);
                    try {
                        restTemplate.postForEntity("http://shard-manager:8080/shard-manager/register-shard", request, Void.class);
                    } catch (ResourceAccessException e) {
                        System.err.println("Warning: Failed to register with shard manager - " + e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    attempts++;
                    System.err.println("Registration failed, retrying in 5s...");
                    Thread.sleep(5000);
                }
            }

        };
    }




    @PreDestroy
    public void destroy() throws Exception {
        if (raftServer != null) {
            raftServer.close();
            cleanStorageDirectory();
        }
    }

    private void cleanStorageDirectory() throws IOException {
        Path path = Paths.get("raft-storage", nodeId);
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + p);
                    }
                });
    }

    @Bean
    public RaftServer raftServer() {
        return raftServer;
    }
}