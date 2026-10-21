package com.wsa.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves contract-first OpenAPI YAML for Swagger UI.
 * Do not raise resource-handler order above controllers/actuator.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/open-api/**")
                .addResourceLocations("classpath:/open-api/");
    }
}
