package com.example.distributedkeyvalue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("test")
public class TestConfig {
    @Bean
    @Primary
    public RestTemplate testRestTemplate() {
        return new RestTemplate();
    }
}
