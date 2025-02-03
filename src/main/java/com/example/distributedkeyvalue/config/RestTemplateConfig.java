package com.example.distributedkeyvalue.config;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(RestTemplateConfig.HttpPoolProperties.class)
public class RestTemplateConfig {

    private final HttpPoolProperties poolProps;

    public RestTemplateConfig(HttpPoolProperties poolProps) {
        this.poolProps = poolProps;
    }

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(poolProps.getMaxTotal());
        cm.setDefaultMaxPerRoute(poolProps.getMaxPerRoute());

        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .evictIdleConnections(TimeValue.of(poolProps.getIdleTimeout(), TimeUnit.SECONDS))
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @ConfigurationProperties(prefix = "http.pool")
    @Getter
    @Setter
    public static class HttpPoolProperties {
        private int maxTotal = 1000;
        private int maxPerRoute = 200;
        private int idleTimeout = 30;
    }
}