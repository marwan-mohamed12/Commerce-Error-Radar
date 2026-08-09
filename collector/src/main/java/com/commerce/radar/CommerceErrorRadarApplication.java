package com.commerce.radar;

import com.commerce.radar.config.RadarProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RadarProperties.class)
public class CommerceErrorRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceErrorRadarApplication.class, args);
    }
}
