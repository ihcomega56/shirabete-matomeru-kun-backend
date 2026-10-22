package jp.shirabete.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

import java.time.Clock;

@Configuration
public class ApplicationConfig {
    @Bean
    JsonMapperBuilderCustomizer strictStringInputs() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WebMvcConfigurer webMvcConfigurer(AppProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("Content-Type", "Accept")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    @Bean
    Object cloudAdapterGuard(AppProperties properties) {
        if (properties.storage().mode() != AppProperties.StorageMode.LOCAL
                || properties.search().mode() != AppProperties.SearchMode.LOCAL) {
            throw new IllegalStateException(
                    "Azure storage/search adapters are not implemented. Use LOCAL storage/search modes; "
                    + "implement ReportStore / ReportSearch before enabling those cloud modes.");
        }
        return new Object();
    }
}
