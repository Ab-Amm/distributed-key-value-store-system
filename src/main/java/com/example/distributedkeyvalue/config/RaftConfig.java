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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RaftConfig {
    private static final UUID CLUSTER_ID = UUID.fromString("02511d47-d67c-49a3-9011-abb310949a45");
    private static final String RAFT_GROUP_ID = "kv-store-raft-group";

    public static RaftGroup getRaftGroup(List<RaftPeer> peers) {
        return RaftGroup.valueOf(RaftGroupId.valueOf(CLUSTER_ID), peers);
    }

    public static RaftServer.Builder newRaftServer(String nodeId, List<String> peerAddresses, File storageDir) {
        final RaftProperties props = new RaftProperties();

        // Set the port for the Raft server
        GrpcConfigKeys.Server.setPort(props, NetUtils.createSocketAddr(peerAddresses.get(0)).getPort());

        // Configure peers using RaftPeer.newBuilder()
        List<RaftPeer> peers = peerAddresses.stream()
                .map(addr -> {
                    String[] parts = addr.split(":");
                    return RaftPeer.newBuilder()
                            .setId(RaftPeerId.valueOf(parts[0])) // Use the first part as the peer ID
                            .setAddress(addr) // Use the full address
                            .build();
                })
                .collect(Collectors.toList());

        // Set the storage directory
        RaftServerConfigKeys.setStorageDir(props, Collections.singletonList(storageDir));

        return RaftServer.newBuilder()
                .setServerId(RaftPeerId.valueOf(nodeId))
                .setProperties(props)
                .setGroup(getRaftGroup(peers))
                .setStateMachine(new KeyValueStateMachine());
    }
}