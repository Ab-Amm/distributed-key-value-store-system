package com.example.distributedkeyvalue.config;

import com.example.distributedkeyvalue.model.KeyValueStateMachine;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.util.TimeDuration;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RaftConfig {
    private static final String RAFT_GROUP_ID = "kv-store-raft-group";

    // In RaftConfig.java
    public static RaftGroup getRaftGroup(String shardId, List<RaftPeer> peers) {
        // Use shardId to create unique group ID
        UUID clusterId = UUID.nameUUIDFromBytes(shardId.getBytes(StandardCharsets.UTF_8));
        System.out.println("Raft group ID: " + clusterId + " for shard: " + shardId);
        return RaftGroup.valueOf(RaftGroupId.valueOf(clusterId), peers);
    }

    public static RaftServer.Builder newRaftServer(String shardId, String nodeId, List<String> peerAddresses, File storageDir) {
        final RaftProperties props = new RaftProperties();

        // Set longer timeouts for Docker environments
        RaftServerConfigKeys.Rpc.setTimeoutMin(props, TimeDuration.valueOf(10000, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.Rpc.setTimeoutMax(props, TimeDuration.valueOf(15000, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.LeaderElection.setLeaderStepDownWaitTime(props, TimeDuration.valueOf(2, TimeUnit.MINUTES));


        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(props, true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(props, 50);
        RaftServerConfigKeys.Snapshot.setCreationGap(props, 50);

        props.set("raft.server.leader.election.heartbeat.interval", "2000");



        RaftServerConfigKeys.Rpc.setRequestTimeout(props, TimeDuration.valueOf(30, TimeUnit.SECONDS));



        // Parse peers into id, host, port
        List<RaftPeer> peers = peerAddresses.stream()
                .map(addr -> {
                    String[] parts = addr.split(":", 3);
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Invalid peer format: " + addr);
                    }
                    String id = parts[0];
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    return RaftPeer.newBuilder()
                            .setId(id)
                            .setAddress(host + ":" + port)
                            .build();
                })
                .collect(Collectors.toList());

        // Set the bind address to 0.0.0.0
        GrpcConfigKeys.Server.setHost(props, "0.0.0.0");
        // Use the first peer's port for this server
        int port = peers.stream()
                .filter(peer -> peer.getId().toString().equals(nodeId))
                .findFirst()
                .map(peer -> NetUtils.createSocketAddr(peer.getAddress()).getPort())
                .orElseThrow(() -> new IllegalArgumentException("Node ID not found in peers"));

        GrpcConfigKeys.Server.setPort(props, port);
        RaftServerConfigKeys.setStorageDir(props, Collections.singletonList(storageDir));

        return RaftServer.newBuilder()
                .setServerId(RaftPeerId.valueOf(nodeId))
                .setProperties(props)
                .setGroup(getRaftGroup(shardId, peers))
                .setStateMachine(new KeyValueStateMachine());
    }

    public static RaftGroupId getRaftGroupId(String shardId) {
        UUID clusterId = UUID.nameUUIDFromBytes(shardId.getBytes());
        return RaftGroupId.valueOf(clusterId);
    }
}