package com.mmtq.memora.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 3 configuration for Spring Boot 4.x.
 *
 * Use JsonMapperBuilderCustomizer to layer configuration onto Boot's auto-configured
 * JsonMapper without overriding it entirely.
 *
 * ISO-8601 date formatting is already the default in Jackson 3 — no extra config needed.
 * NEVER use new ObjectMapper() or new JsonMapper().
 */
@Configuration( proxyBeanMethods = false )
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.changeDefaultPropertyInclusion(
                inc -> inc.withValueInclusion( JsonInclude.Include.NON_NULL )
        );
    }
}
