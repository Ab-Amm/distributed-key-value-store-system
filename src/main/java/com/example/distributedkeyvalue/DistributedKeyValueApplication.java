package com.example.distributedkeyvalue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DistributedKeyValueApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistributedKeyValueApplication.class, args);
    }
}
