package com.commerce.radar.config;

import com.commerce.radar.parser.HybrisLogParser;
import com.commerce.radar.parser.StackFingerprint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RadarConfiguration {

    @Bean
    HybrisLogParser hybrisLogParser(RadarProperties properties) {
        return new HybrisLogParser(properties.getCustomPackagePrefix());
    }

    @Bean
    StackFingerprint stackFingerprint(RadarProperties properties) {
        return new StackFingerprint(properties.getCustomPackagePrefix());
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:4200",
                                "http://127.0.0.1:4200",
                                "http://localhost:5173"
                        )
                        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false);
            }
        };
    }

}
