package com.example.distributedkeyvalue.service;

import com.example.distributedkeyvalue.config.RaftConfig;
import com.example.distributedkeyvalue.model.commands.DeleteCommand;
import com.example.distributedkeyvalue.model.commands.GetCommand;
import com.example.distributedkeyvalue.model.commands.PutCommand;
import com.example.distributedkeyvalue.model.ShardInfo;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KeyValueService {
    @Autowired
    private RestTemplate restTemplate;

    public void put(String key, String value) throws Exception {
        // 1. Get shard info from Shard Manager
        ShardInfo shardInfo = restTemplate.getForObject(
                "http://shard-manager:8080/shard-manager/shard/{key}",
                ShardInfo.class,
                key
        );


        // 2. Extract shardId and nodes
        assert shardInfo != null;
        String shardId = shardInfo.shardId();
        List<String> nodes = shardInfo.nodes();

        // 3. Create RaftClient with the entire Raft group (no manual leader selection)
        List<RaftPeer> peers = nodes.stream()
                .map(addr -> {
                    String[] parts = addr.split(":", 3);
                    String id = parts[0];
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    return RaftPeer.newBuilder()
                            .setId(id)
                            .setAddress(host + ":" + port)
                            .build();
                })
                .collect(Collectors.toList());

        RaftClient raftClient = RaftClient.newBuilder()
                .setRaftGroup(RaftConfig.getRaftGroup(shardId, peers))
                .build(); // No need to set leader ID!

        // 4. Send PUT request (client auto-discovers leader)
        RaftClientReply reply = raftClient.io().send(new PutCommand(key, value));
        if (!reply.isSuccess()) {
            throw new RuntimeException("Write failed");
        }
    }

    public String get(String key) throws Exception {
        // 1. Get shard info from Shard Manager
        ShardInfo shardInfo = restTemplate.getForObject(
                "http://shard-manager:8080/shard-manager/shard/{key}",
                ShardInfo.class,
                key
        );

        // 2. Extract shardId and nodes
        assert shardInfo != null;
        String shardId = shardInfo.shardId();
        List<String> nodes = shardInfo.nodes();

        // 3. Create RaftClient with the entire Raft group (no manual leader selection)
        List<RaftPeer> peers = nodes.stream()
                .map(addr -> {
                    String[] parts = addr.split(":", 3);
                    String id = parts[0];
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    return RaftPeer.newBuilder()
                            .setId(id)
                            .setAddress(host + ":" + port)
                            .build();
                })
                .collect(Collectors.toList());

        RaftClient raftClient = RaftClient.newBuilder()
                .setRaftGroup(RaftConfig.getRaftGroup(shardId, peers))
                .build(); // No need to set leader ID!

        // 5. Send GET request to the leader
        RaftClientReply reply = raftClient.io().sendReadOnly(new GetCommand(key));
        if (!reply.isSuccess()) {
            throw new RuntimeException("Read failed");
        }
        return reply.getMessage().getContent().toStringUtf8();
    }

    public void delete(String key) throws Exception {
        // 1. Get shard info from Shard Manager
        ShardInfo shardInfo = restTemplate.getForObject(
                "http://shard-manager:8080/shard-manager/shard/{key}",
                ShardInfo.class,
                key
        );

        // 2. Extract shardId and nodes
        assert shardInfo != null;
        String shardId = shardInfo.shardId();
        List<String> nodes = shardInfo.nodes();

        // 3. Create RaftClient with the entire Raft group (no manual leader selection)
        List<RaftPeer> peers = nodes.stream()
                .map(addr -> {
                    String[] parts = addr.split(":", 3);
                    String id = parts[0];
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    return RaftPeer.newBuilder()
                            .setId(id)
                            .setAddress(host + ":" + port)
                            .build();
                })
                .collect(Collectors.toList());

        RaftClient raftClient = RaftClient.newBuilder()
                .setRaftGroup(RaftConfig.getRaftGroup(shardId, peers))
                .build(); // No need to set leader ID!

        // 5. Send DELETE request to the leader
        RaftClientReply reply = raftClient.io().send(new DeleteCommand(key));
        if (!reply.isSuccess()) {
            throw new RuntimeException("Delete failed");
        }
    }


}