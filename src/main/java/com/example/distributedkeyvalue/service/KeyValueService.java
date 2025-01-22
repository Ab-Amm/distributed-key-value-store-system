package com.example.distributedkeyvalue.service;

import com.example.distributedkeyvalue.model.commands.DeleteCommand;
import com.example.distributedkeyvalue.model.commands.GetCommand;
import com.example.distributedkeyvalue.model.commands.PutCommand;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftClientReply;
import org.springframework.stereotype.Service;

@Service
public class KeyValueService {
    private final RaftClient raftClient;

    public KeyValueService(RaftClient raftClient) {
        this.raftClient = raftClient;
    }

    public void put(String key, String value) throws Exception {
        RaftClientReply reply = raftClient.io().send(
                new PutCommand(key, value)
        );
        if (!reply.isSuccess()) {
            throw new RuntimeException("Write failed");
        }
    }

    public String get(String key) throws Exception {
        RaftClientReply reply = raftClient.io().sendReadOnly(new GetCommand(key));
        if (!reply.isSuccess()) {
            throw new RuntimeException("Read failed");
        }
        String value = reply.getMessage().getContent().toStringUtf8();
        return value.isEmpty() ? null : value; // Return null if key not found
    }

    public void delete(String key) throws Exception {
        RaftClientReply reply = raftClient.io().send(
                new DeleteCommand(key)
        );
        if (!reply.isSuccess()) {
            throw new RuntimeException("Delete failed");
        }
    }

}