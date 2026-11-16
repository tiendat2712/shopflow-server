package org.shopflow.backend.infrastructure.security.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.service.auth.AuthFailureReason
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.domain.service.auth.UserSecurityStatePort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
class JwtAuthFilter(
    private val tokenProvider: TokenProvider,
    private val userSecurityStatePort: UserSecurityStatePort,
    @Qualifier("handlerExceptionResolver")
    private val handlerExceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            if (!tokenProvider.validateAccessToken(authHeader)) {
                SecurityContextHolder.clearContext()
                request.setAttribute(AuthFailureReason::class.java.name, AuthFailureReason.INVALID_TOKEN)
            } else {
                val userId =
                    try {
                        tokenProvider.extractUserId(authHeader)
                    } catch (_: InvalidTokenException) {
                        SecurityContextHolder.clearContext()
                        request.setAttribute(AuthFailureReason::class.java.name, AuthFailureReason.INVALID_TOKEN)
                        null
                    }

                if (userId != null) {
                    // Fail closed if account state cannot be verified; never fall back to JWT role/permission claims.
                    val snapshot =
                        try {
                            userSecurityStatePort.findByUserId(userId)
                        } catch (ex: RuntimeException) {
                            SecurityContextHolder.clearContext()
                            val resolved = handlerExceptionResolver.resolveException(request, response, null, ex)
                            if (resolved == null && !response.isCommitted) {
                                throw ex
                            }
                            return
                        }

                    when {
                        snapshot == null -> {
                            SecurityContextHolder.clearContext()
                            request.setAttribute(AuthFailureReason::class.java.name, AuthFailureReason.INVALID_TOKEN)
                        }
                        !snapshot.isActive() -> {
                            SecurityContextHolder.clearContext()
                            val reason =
                                when (snapshot.status) {
                                    UserStatus.BANNED -> AuthFailureReason.ACCOUNT_BANNED
                                    UserStatus.INACTIVE -> AuthFailureReason.ACCOUNT_INACTIVE
                                    UserStatus.ACTIVE -> AuthFailureReason.ACCOUNT_INACTIVE
                                }
                            request.setAttribute(AuthFailureReason::class.java.name, reason)
                        }
                        else -> {
                            val authorities =
                                linkedSetOf<SimpleGrantedAuthority>().apply {
                                    snapshot.roles.forEach { roleName ->
                                        val normalized = if (roleName.startsWith("ROLE_")) roleName else "ROLE_$roleName"
                                        add(SimpleGrantedAuthority(normalized))
                                    }
                                    snapshot.permissions.forEach { permissionCode ->
                                        add(SimpleGrantedAuthority(permissionCode))
                                    }
                                }
                            val auth =
                                UsernamePasswordAuthenticationToken(
                                    snapshot.userId.value.toString(),
                                    null,
                                    authorities.toList(),
                                )
                            SecurityContextHolder.getContext().authentication = auth
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
