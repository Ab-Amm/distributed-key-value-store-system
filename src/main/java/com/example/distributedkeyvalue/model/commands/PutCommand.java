package com.example.distributedkeyvalue.model.commands;

import lombok.Getter;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

@Getter
public class PutCommand implements Message {
    private final String key;
    private final String value;

    public PutCommand(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public ByteString getContent() {
        // Add "PUT:" prefix
        return ByteString.copyFromUtf8("PUT:" + key + ":" + value);
    }

    public static PutCommand from(ByteString byteString) {
        String content = byteString.toStringUtf8();
        String[] parts = content.split(":", 3); // Split into ["PUT", "key", "value"]
        return new PutCommand(parts[1], parts[2]);
    }

}