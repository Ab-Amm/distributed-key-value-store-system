package com.example.distributedkeyvalue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.distributedkeyvalue")

public class DistributedKeyValueApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedKeyValueApplication.class, args);
    }

}
