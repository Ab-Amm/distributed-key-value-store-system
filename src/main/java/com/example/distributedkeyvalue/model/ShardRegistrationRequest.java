package com.example.distributedkeyvalue.model;

import java.util.List;

public record ShardRegistrationRequest(
        String shardId,
        List<String> restNodes, // For leader checks (host:8080)
        List<String> raftNodes  // For Raft client (host:9870)
) {}