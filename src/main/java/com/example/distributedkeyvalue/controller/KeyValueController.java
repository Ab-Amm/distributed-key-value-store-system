package com.example.distributedkeyvalue.controller;

import com.example.distributedkeyvalue.service.KeyValueService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Profile("node")
@RequestMapping("/internal/api/v1/keys")
public class KeyValueController {
    private final KeyValueService keyValueService;

    @PutMapping("/{key}")
    public void putKeyValue(@PathVariable String key,@RequestBody String value) throws Exception {
        keyValueService.put(key, value);
    }

    @GetMapping("/{key}")
    public String getKeyValue(@PathVariable String key) throws Exception {
        return keyValueService.get(key);
    }

    @DeleteMapping("/{key}")
    public void deleteKeyValue(@PathVariable String key) throws Exception {
        keyValueService.delete(key);
    }

}
