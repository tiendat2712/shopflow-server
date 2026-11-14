package org.shopflow.backend.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class JpaAuditingConfig {
    @Bean
    fun auditorProvider(): AuditorAware<String> =
        AuditorAware {
            val authentication = SecurityContextHolder.getContext().authentication
            if (
                authentication == null ||
                !authentication.isAuthenticated ||
                authentication is AnonymousAuthenticationToken
            ) {
                return@AuditorAware Optional.of(SYSTEM_AUDITOR)
            }

            val principalName =
                authentication.name
                    ?.takeIf { it.isNotBlank() && it != "anonymousUser" }
            Optional.of(principalName ?: SYSTEM_AUDITOR)
        }

    companion object {
        const val SYSTEM_AUDITOR = "SYSTEM"
    }
}
