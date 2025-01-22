package com.example.distributedkeyvalue.model.commands;

import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

public class GetCommand implements Message {
    private final String key;

    public GetCommand(String key) {
        this.key = key;
    }

    @Override
    public ByteString getContent() {
        // Add "GET:" prefix
        return ByteString.copyFromUtf8("GET:" + key);
    }

    public static GetCommand from(ByteString byteString) {
        String content = byteString.toStringUtf8();
        String[] parts = content.split(":", 2); // Split into ["GET", "key"]
        return new GetCommand(parts[1]);
    }

    public String getKey() {
        return key;
    }
}