package com.example.distributedkeyvalue.model;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStateMachine extends BaseStateMachine {
    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    @Override
    public void initialize(RaftServer raftServer, RaftGroupId raftGroupId, RaftStorage raftStorage) throws IOException {
        super.initialize(raftServer, raftGroupId, raftStorage);
        storage.init(raftStorage);
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        try {
            // Access the log entry data directly from TransactionContext
            final ByteString data = trx.getStateMachineLogEntry().getLogData();
            final String content = data.toStringUtf8();
            final String[] parts = content.split(":", 3);

            switch (parts[0]) {
                case "PUT":
                    store.put(parts[1], parts[2]);
                    break;
                case "DELETE":
                    store.remove(parts[1]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command: " + parts[0]);
            }

            return CompletableFuture.completedFuture(Message.valueOf("SUCCESS"));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    @Override
    public CompletableFuture<Message> query(Message request) {
        try {
            // Extract the content from the request
            String content = request.getContent().toStringUtf8();
            // Split into ["GET", "key"]
            String[] parts = content.split(":", 2);

            if (parts.length < 2 || !"GET".equals(parts[0])) {
                throw new IllegalArgumentException("Invalid GET command: " + content);
            }

            String key = parts[1];
            String value = store.get(key);

            // Return the value or an empty string if not found
            return CompletableFuture.completedFuture(
                    Message.valueOf(value != null ? value : "")
            );
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public String get(String key) {
        return store.get(key);
    }
}