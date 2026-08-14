package com.commerce.radar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI radarOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Commerce Error Radar")
                .version("0.1.0")
                .description("""
                        Local collector API for the Hybris console inbox.

                        Fingerprints contain `@` (for example `NullPointerException@com.yourcompany.facades…`).
                        Use the `fingerprint` **query** parameter — never put it in the path.

                        Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`
                        """));
    }
}
