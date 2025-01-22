package com.example.distributedkeyvalue.model;

import java.util.List;

public record ShardRegistrationRequest(String shardId, List<String> nodes) { }