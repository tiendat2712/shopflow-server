package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.event.auth.UserProfileUpdatedEvent
import org.shopflow.backend.domain.event.auth.UserRegisteredEvent
import org.shopflow.backend.domain.exception.AccountInactiveException
import org.shopflow.backend.domain.exception.DomainValidationException
import java.time.Instant

/**
 * Aggregate root representing a registered ShopFlow user.
 */
data class User(
    val id: UserId,
    val email: Email,
    val hashedPassword: HashedPassword,
    val roles: Set<Role> = emptySet(),
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
    val fullName: String? = null,
    val phoneNumber: String? = null,
    val city: String? = null,
    val address: String? = null,
    val avatarUrl: String? = null,
) {
    init {
        validateMaxLength("Full name", fullName, MAX_FULL_NAME_LENGTH)
        validateMaxLength("Phone number", phoneNumber, MAX_PHONE_NUMBER_LENGTH)
        validateMaxLength("City", city, MAX_CITY_LENGTH)
        validateMaxLength("Address", address, MAX_ADDRESS_LENGTH)
        validateMaxLength("Avatar URL", avatarUrl, MAX_AVATAR_URL_LENGTH)
    }

    fun isActive(): Boolean = status == UserStatus.ACTIVE

    fun ensureCanAuthenticate() {
        if (!isActive()) {
            throw AccountInactiveException("User account is ${status.name.lowercase()}.")
        }
    }

    fun hasRole(roleName: String): Boolean = roles.any { it.name == roleName }

    fun hasPermission(permissionCode: String): Boolean = roles.any { it.hasPermission(permissionCode) }

    fun getAllPermissions(): Set<String> = roles.flatMapTo(linkedSetOf()) { it.getPermissionCodes() }

    fun getRoleNames(): Set<String> = roles.mapTo(linkedSetOf()) { it.name }

    fun updateProfile(
        fullName: String?,
        phoneNumber: String?,
        city: String?,
        address: String?,
        avatarUrl: String?,
        now: Instant,
    ): Pair<User, UserProfileUpdatedEvent> {
        ensureCanAuthenticate()
        val updatedUser =
            copy(
                fullName = fullName.normalizeOptional(),
                phoneNumber = phoneNumber.normalizeOptional(),
                city = city.normalizeOptional(),
                address = address.normalizeOptional(),
                avatarUrl = avatarUrl.normalizeOptional(),
                updatedAt = now,
            )
        val event =
            UserProfileUpdatedEvent(
                userId = updatedUser.id,
                email = updatedUser.email,
                fullName = updatedUser.fullName,
                city = updatedUser.city,
                occurredAt = now,
            )
        return updatedUser to event
    }

    private fun validateMaxLength(
        fieldLabel: String,
        value: String?,
        maxLength: Int,
    ) {
        if (value != null && value.length > maxLength) {
            throw DomainValidationException("$fieldLabel must not exceed $maxLength characters.")
        }
    }

    companion object {
        const val MAX_FULL_NAME_LENGTH = 150
        const val MAX_PHONE_NUMBER_LENGTH = 30
        const val MAX_CITY_LENGTH = 100
        const val MAX_ADDRESS_LENGTH = 500
        const val MAX_AVATAR_URL_LENGTH = 500

        fun register(
            email: Email,
            hashedPassword: HashedPassword,
            defaultRole: Role,
            now: Instant,
            fullName: String? = null,
            city: String? = null,
        ): Pair<User, UserRegisteredEvent> {
            val user =
                User(
                    id = UserId.generate(),
                    email = email,
                    hashedPassword = hashedPassword,
                    roles = setOf(defaultRole),
                    status = UserStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                    fullName = fullName.normalizeOptional(),
                    city = city.normalizeOptional(),
                )
            val event =
                UserRegisteredEvent(
                    userId = user.id,
                    email = user.email,
                    roles = user.getRoleNames(),
                    occurredAt = now,
                )
            return user to event
        }
    }
}

private fun String?.normalizeOptional(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
