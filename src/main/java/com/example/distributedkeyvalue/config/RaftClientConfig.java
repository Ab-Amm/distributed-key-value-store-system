package com.example.distributedkeyvalue.config;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.grpc.GrpcTlsConfig;
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
public class RaftClientConfig {

    @Value("${RAFT_PEERS}")  // Now correctly reads from environment variable
    private String[] peers;

    @Value("${RAFT_NODE_ID}")
    private String raftNodeId;

    @Value("${raft.cluster.group-id}")
    private String groupId;

    @Bean
    public RaftGroup raftGroup() {
        return RaftGroup.valueOf(
                RaftGroupId.valueOf(UUID.nameUUIDFromBytes(groupId.getBytes())),
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

        ClientId id = ClientId.valueOf(ByteString.copyFromUtf8(raftNodeId));
        // Configure gRPC parameters
        GrpcConfigKeys.setMessageSizeMax(properties, SizeInBytes.valueOf("64MB"));
        GrpcConfigKeys.setFlowControlWindow(properties, SizeInBytes.valueOf("4MB"));

        GrpcFactory grpcFactory = new GrpcFactory((GrpcTlsConfig) null); // Pass null if TLS is not configured

        return RaftClient.newBuilder()
                .setClientId(id)
                .setProperties(properties)
                .setRaftGroup(raftGroup)
                .setClientRpc(grpcFactory.newRaftClientRpc(id, properties))
                .build();
    }
}
