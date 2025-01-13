package com.nkd.event.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nkd.event.utils.CustomJSONSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder ->
                builder.serializationInclusion(JsonInclude.Include.USE_DEFAULTS)
                        .serializers(new CustomJSONSerializer());
    }
}
