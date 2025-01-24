package com.example.distributedkeyvalue.controller;

import com.example.distributedkeyvalue.config.RaftConfig;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
@Profile("node")
@RestController
public class LeaderController {
    private final RaftServer raftServer;

    @Autowired(required = false)  // Makes dependency optional
    public LeaderController(RaftServer raftServer) {
        this.raftServer = raftServer;
    }

    @GetMapping("/leader/{shardId}")
    public String isLeader(@PathVariable String shardId) {
        if (raftServer == null) return "FOLLOWER";
        try {
            RaftGroupId raftGroupId = RaftConfig.getRaftGroupId(shardId);
            return raftServer.getDivision(raftGroupId).getInfo().isLeader()
                    ? "LEADER"
                    : "FOLLOWER";
        } catch (Exception e) {
            return "ERROR";
        }
    }
}