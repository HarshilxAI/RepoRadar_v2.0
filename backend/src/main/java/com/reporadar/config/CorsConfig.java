package com.reporadar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String origins) {
        List<String> exact = new ArrayList<>();
        List<String> patterns = new ArrayList<>();
        for (String value : sanitize(origins)) {
            if (value.contains("*")) {
                patterns.add(value);
            } else {
                exact.add(value);
            }
        }
        this.allowedOrigins = exact.toArray(String[]::new);
        this.allowedOriginPatterns = patterns.toArray(String[]::new);
    }

    @Override
    @SuppressWarnings("null")
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Accept")
                .maxAge(3600);
    }

    private List<String> sanitize(String origins) {
        List<String> values = new ArrayList<>();
        for (String value : origins.split(",")) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
