package com.example.distributedkeyvalue.model;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class KeyValue {
    private String key;
    private String value;
}
