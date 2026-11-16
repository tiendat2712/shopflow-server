package org.shopflow.backend.infrastructure.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature

@Configuration
class JacksonValidationConfig {
    @Bean
    fun strictJsonTypeCoercionCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        }
}
