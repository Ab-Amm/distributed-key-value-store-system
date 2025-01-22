package com.example.distributedkeyvalue.model.commands;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

public class DeleteCommand implements Message {
    private final String key;

    public DeleteCommand(String key) {
        this.key = key;
    }

    @Override
    public ByteString getContent() {
        // Keep "DELETE:" prefix (already correct)
        return ByteString.copyFromUtf8("DELETE:" + key);
    }

    public static DeleteCommand from(ByteString bytes) {
        String content = bytes.toStringUtf8();
        String[] parts = content.split(":", 2); // Split into ["DELETE", "key"]
        return new DeleteCommand(parts[1]);
    }

    public String getKey() {
        return key;
    }
}
