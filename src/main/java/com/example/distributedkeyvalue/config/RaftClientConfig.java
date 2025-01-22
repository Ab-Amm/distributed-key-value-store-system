package com.example.distributedkeyvalue.config;

import lombok.RequiredArgsConstructor;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.SizeInBytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;


@Configuration
@RequiredArgsConstructor
public class RaftClientConfig {

    @Value("${RAFT_PEERS:node1:localhost:9870}")
    private String[] peers;

    @Value("${RAFT_NODE_ID:default-node}")
    private String raftNodeId;

    @Value("${raft.cluster.group-id}")
    private String groupId;

    @Value("${raft.cluster.port}")
    private int raftPort;


    @Bean
    public RaftGroup raftGroup() {
        return RaftGroup.valueOf(
                RaftConfig.getRaftGroupId(),
                Arrays.stream(peers)
                        .map(peer -> {
                            String[] parts = peer.split(":");
                            return RaftPeer.newBuilder()
                                    .setId(parts[0])
                                    .setAddress(parts[1] + ":" + parts[2])
                                    .build();
                        })
                        .collect(Collectors.toList())
        );
    }

    @Bean
    public RaftClient raftClient(RaftGroup raftGroup) {
        final RaftProperties properties = new RaftProperties();

        // Configure gRPC parameters
        GrpcConfigKeys.setMessageSizeMax(properties, SizeInBytes.valueOf("64MB"));
        GrpcConfigKeys.setFlowControlWindow(properties, SizeInBytes.valueOf("4MB"));

        // Ensure ClientId is 16 bytes
        ClientId clientId = ClientId.valueOf(UUID.nameUUIDFromBytes(raftNodeId.getBytes()));

        GrpcFactory grpcFactory = new GrpcFactory((Parameters) null);

        return RaftClient.newBuilder()
                .setClientId(clientId)
                .setProperties(properties)
                .setRaftGroup(raftGroup)
                .setClientRpc(grpcFactory.newRaftClientRpc(clientId, properties))
                .build();
    }

}