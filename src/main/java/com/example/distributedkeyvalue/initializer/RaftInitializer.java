package com.example.distributedkeyvalue.initializer;

import com.example.distributedkeyvalue.config.RaftConfig;
import org.apache.ratis.server.RaftServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.List;

@Component
public class RaftInitializer {

    private RaftServer raftServer; // Store the RaftServer instance

    @Value("${RAFT_NODE_ID:default-node}")
    private String nodeId;

    @Value("${RAFT_PEERS:default-node:localhost:9870}")
    private String peers;

    @PostConstruct
    public void init() throws Exception {
        // Parse peer addresses
        List<String> peerAddresses = Arrays.asList(peers.split(","));

        // Create Raft server
        File storageDir = new File("./raft-storage/" + nodeId);
        raftServer = RaftConfig.newRaftServer(nodeId, peerAddresses, storageDir).build();
        raftServer.start();

        System.out.println("Raft server started on node " + nodeId);
    }

    @Bean
    public RaftServer raftServer() {
        return raftServer; // Return the initialized RaftServer instance
    }
}
