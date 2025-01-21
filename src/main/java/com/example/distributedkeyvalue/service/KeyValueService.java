package com.example.distributedkeyvalue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class KeyValueService {
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        log.info("Storing key: {} with value: {}", key, value);
        store.put(key, value);
    }

    public String get(String key) {
        log.info("Retrieving value for key: {}", key);
        return store.get(key);
    }

    public void delete(String key) {
        log.info("Deleting key: {}", key);
        store.remove(key);
    }
}
