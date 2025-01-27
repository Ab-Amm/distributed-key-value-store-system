package com.example.distributedkeyvalue.config;

import lombok.RequiredArgsConstructor;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.client.RaftClientRpc;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.SizeInBytes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.MessageDigest;
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

    @Value("${SHARD_ID:default-shard}")
    private String shardId;

    @Value("${raft.cluster.group-id}")
    private String groupId;

    @Value("${raft.cluster.port}")
    private int raftPort;


    @Bean
    public RaftGroup raftGroup() {
        return RaftGroup.valueOf(
                RaftConfig.getRaftGroupId(shardId),
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


    private ByteString get16ByteHash(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        return ByteString.copyFrom(hash, 0, 16);
    }

    @Bean
    public RaftClient raftClient(RaftGroup raftGroup) throws Exception {
        // Initialize RaftProperties
        final RaftProperties properties = new RaftProperties();
        GrpcConfigKeys.setMessageSizeMax(properties, SizeInBytes.valueOf("64MB"));
        GrpcConfigKeys.setFlowControlWindow(properties, SizeInBytes.valueOf("4MB"));

        // Generate ClientId
        ByteString clientIdBytes = get16ByteHash(raftNodeId);
        ClientId clientId = ClientId.valueOf(clientIdBytes);

        // Initialize GrpcFactory with required parameters
        GrpcFactory grpcFactory = new GrpcFactory(new Parameters());

        // Initialize RaftClientRpc
        RaftClientRpc clientRpc = grpcFactory.newRaftClientRpc(clientId, properties);

        // Configure and build the RaftClient
        return RaftClient.newBuilder()
                .setProperties(properties)
                .setClientRpc(clientRpc)
                .setRaftGroup(raftGroup)
                .setClientId(clientId)
                .build();
    }




}