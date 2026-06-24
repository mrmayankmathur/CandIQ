package com.redrob.discovery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Single shared {@link HttpClient} for the OpenAI calls in {@link com.redrob.discovery.service.LlmService}.
 * A singleton (rather than one client per request) reuses connections and its internal thread pool.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient openAiHttpClient(@Value("${openai.connect-timeout-ms:10000}") long connectTimeoutMs) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }
}
