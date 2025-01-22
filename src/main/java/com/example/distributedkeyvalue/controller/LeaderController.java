package com.example.distributedkeyvalue.controller;

import com.example.distributedkeyvalue.config.RaftConfig;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class LeaderController {

    private final RaftServer raftServer;

    @Autowired
    public LeaderController(RaftServer raftServer) {
        this.raftServer = raftServer;
    }

    @GetMapping("/leader")
    public String isLeader() throws IOException {
        // Get the RaftGroupId from your RaftConfig
        RaftGroupId raftGroupId = RaftConfig.getRaftGroupId();

        return raftServer.getDivision(raftGroupId).getInfo().isLeader()
                ? "LEADER"
                : "FOLLOWER";
    }
}
