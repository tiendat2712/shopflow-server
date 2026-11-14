package org.shopflow.backend.infrastructure.security.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val tokenProvider: TokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            if (tokenProvider.validateAccessToken(authHeader)) {
                try {
                    val userId = tokenProvider.extractUserId(authHeader)
                    val roles = tokenProvider.extractRoles(authHeader)
                    val permissions = tokenProvider.extractPermissions(authHeader)
                    val authorities =
                        linkedSetOf<SimpleGrantedAuthority>().apply {
                            roles.forEach { roleName ->
                                val normalized = if (roleName.startsWith("ROLE_")) roleName else "ROLE_$roleName"
                                add(SimpleGrantedAuthority(normalized))
                            }
                            permissions.forEach { permissionCode ->
                                add(SimpleGrantedAuthority(permissionCode))
                            }
                        }
                    val auth =
                        UsernamePasswordAuthenticationToken(
                            userId.value.toString(),
                            null,
                            authorities.toList(),
                        )
                    SecurityContextHolder.getContext().authentication = auth
                } catch (_: InvalidTokenException) {
                    SecurityContextHolder.clearContext()
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
