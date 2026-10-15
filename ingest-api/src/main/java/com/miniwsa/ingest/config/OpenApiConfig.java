package com.miniwsa.ingest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves contract-first OpenAPI YAML and keeps a stable Swagger UI entry URL.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registry.addResourceHandler("/open-api/**")
                .addResourceLocations("classpath:/open-api/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // springdoc serves UI at /swagger-ui/index.html; keep the classic URL working
        registry.addRedirectViewController("/swagger-ui.html", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
    }
}

