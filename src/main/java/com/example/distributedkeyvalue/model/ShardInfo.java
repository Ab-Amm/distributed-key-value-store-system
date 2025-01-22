package com.example.distributedkeyvalue.model;

import java.util.List;

public record ShardInfo(String shardId, List<String> nodes) { }