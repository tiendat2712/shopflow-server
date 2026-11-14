package org.shopflow.backend.application.usecase.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.auth.LoginCommand
import org.shopflow.backend.application.command.auth.LogoutCommand
import org.shopflow.backend.application.command.auth.RefreshTokenCommand
import org.shopflow.backend.application.command.auth.RegisterCommand
import org.shopflow.backend.application.command.auth.UpdateUserProfileCommand
import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.application.dto.auth.toProfileDto
import org.shopflow.backend.application.query.auth.GetCurrentUserQuery
import org.shopflow.backend.application.query.auth.UserQueryService
import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.event.auth.UserLoggedInEvent
import org.shopflow.backend.domain.event.auth.UserProfileUpdatedEvent
import org.shopflow.backend.domain.event.auth.UserRegisteredEvent
import org.shopflow.backend.domain.exception.AccountInactiveException
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.InvalidCredentialsException
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException
import org.shopflow.backend.domain.exception.UserNotFoundException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.RoleId
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.shopflow.backend.domain.service.auth.TokenProvider
import java.time.Instant

class AuthUseCasesTest {
    private lateinit var userRepository: InMemoryUserRepository
    private lateinit var roleRepository: InMemoryRoleRepository
    private lateinit var refreshTokenRepository: InMemoryRefreshTokenRepository
    private lateinit var passwordHasher: FakePasswordHasher
    private lateinit var tokenProvider: FakeTokenProvider
    private lateinit var eventPublisher: RecordingDomainEventPublisher
    private lateinit var clockTime: Instant
    private lateinit var timeProvider: TimeProvider

    private lateinit var registerUseCase: RegisterUseCase
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var refreshTokenUseCase: RefreshTokenUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var updateUserProfileUseCase: UpdateUserProfileUseCase

    private lateinit var customerPermissions: Set<Permission>

    @BeforeEach
    fun setUp() {
        userRepository = InMemoryUserRepository()
        roleRepository = InMemoryRoleRepository()
        refreshTokenRepository = InMemoryRefreshTokenRepository()
        passwordHasher = FakePasswordHasher()
        tokenProvider = FakeTokenProvider()
        eventPublisher = RecordingDomainEventPublisher()
        clockTime = Instant.parse("2026-11-14T15:00:00Z")
        timeProvider = TimeProvider { clockTime }

        customerPermissions =
            setOf(
                Permission.create(Permission.USER_READ, "Read profile", "USER"),
                Permission.create(Permission.PRODUCT_READ, "Read products", "PRODUCT"),
                Permission.create(Permission.ORDER_CREATE, "Create orders", "ORDER"),
                Permission.create(Permission.ORDER_READ_OWN, "Read own orders", "ORDER"),
            )
        roleRepository.save(
            Role.create(
                name = Role.ROLE_CUSTOMER,
                description = "Default customer",
                permissions = customerPermissions,
            ),
        )

        val issuer = AuthTokenIssuer(tokenProvider, refreshTokenRepository, timeProvider)
        registerUseCase = RegisterUseCase(userRepository, roleRepository, passwordHasher, timeProvider, eventPublisher)
        loginUseCase = LoginUseCase(userRepository, passwordHasher, issuer, timeProvider, eventPublisher)
        refreshTokenUseCase =
            RefreshTokenUseCase(
                tokenProvider,
                userRepository,
                refreshTokenRepository,
                issuer,
                timeProvider,
            )
        logoutUseCase = LogoutUseCase(tokenProvider, refreshTokenRepository)
        getCurrentUserUseCase = GetCurrentUserUseCase(userRepository)
        updateUserProfileUseCase = UpdateUserProfileUseCase(userRepository, timeProvider, eventPublisher)
    }

    @Test
    fun `register creates active customer with ROLE_CUSTOMER and publishes UserRegisteredEvent`() {
        val profile = registerUseCase.execute(RegisterCommand("  Alice@ShopFlow.org ", "Password123"))

        assertEquals("Alice@ShopFlow.org", profile.email)
        assertEquals(setOf(Role.ROLE_CUSTOMER), profile.roles)
        assertEquals(
            setOf(Permission.USER_READ, Permission.PRODUCT_READ, Permission.ORDER_CREATE, Permission.ORDER_READ_OWN),
            profile.permissions,
        )
        assertEquals("ACTIVE", profile.status)
        assertEquals(1, eventPublisher.events.size)
        val event = eventPublisher.events.single() as UserRegisteredEvent
        assertEquals(setOf(Role.ROLE_CUSTOMER), event.roles)
    }

    @Test
    fun `register throws RoleNotFoundException when default customer role is missing`() {
        val emptyRoleRepo = InMemoryRoleRepository()
        val failingRegisterUseCase =
            RegisterUseCase(
                userRepository,
                emptyRoleRepo,
                passwordHasher,
                timeProvider,
                eventPublisher,
            )

        assertThrows(RoleNotFoundException::class.java) {
            failingRegisterUseCase.execute(RegisterCommand("missingrole@shopflow.org", "Password123"))
        }
    }

    @Test
    fun `register rejects duplicate email`() {
        registerUseCase.execute(RegisterCommand("alice@shopflow.org", "Password123"))

        assertThrows(UserAlreadyExistsException::class.java) {
            registerUseCase.execute(RegisterCommand("alice@shopflow.org", "Password123"))
        }
    }

    @Test
    fun `login issues token pair and rejects invalid credentials or inactive accounts`() {
        val profile = registerUseCase.execute(RegisterCommand("bob@shopflow.org", "Password123"))

        val result = loginUseCase.execute(LoginCommand("bob@shopflow.org", "Password123"))
        assertEquals(true, tokenProvider.validateAccessToken(result.accessToken))
        assertEquals(true, tokenProvider.validateRefreshToken(result.refreshToken))
        assertEquals(UserLoggedInEvent::class, eventPublisher.events.last()::class)

        assertThrows(InvalidCredentialsException::class.java) {
            loginUseCase.execute(LoginCommand("bob@shopflow.org", "WrongPassword1"))
        }

        val saved = userRepository.findById(UserId.fromString(profile.id))!!
        userRepository.save(saved.copy(status = UserStatus.BANNED))
        assertThrows(AccountInactiveException::class.java) {
            loginUseCase.execute(LoginCommand("bob@shopflow.org", "Password123"))
        }
    }

    @Test
    fun `register accepts optional fullName and city matching dataset fields and trims whitespace`() {
        val profile =
            registerUseCase.execute(
                RegisterCommand(
                    email = "dataset.user@shopflow.org",
                    rawPassword = "Password123",
                    fullName = "  Nguyen Van An  ",
                    city = "   Ha Noi  ",
                ),
            )

        assertEquals("Nguyen Van An", profile.fullName)
        assertEquals("Ha Noi", profile.city)
        assertNull(profile.phoneNumber)
        assertNull(profile.address)
        assertNull(profile.avatarUrl)

        val fetched = getCurrentUserUseCase.execute(GetCurrentUserQuery(profile.id))
        assertEquals("Nguyen Van An", fetched.fullName)
        assertEquals("Ha Noi", fetched.city)
    }

    @Test
    fun `updateProfile updates profile fields, normalizes blanks, publishes event, and validates lengths`() {
        val registered = registerUseCase.execute(RegisterCommand("profile.user@shopflow.org", "Password123"))
        eventPublisher.events.clear()

        clockTime = clockTime.plusSeconds(30)
        val updated =
            updateUserProfileUseCase.execute(
                UpdateUserProfileCommand(
                    userId = registered.id,
                    fullName = "  Le Minh B ",
                    phoneNumber = " 0987654321 ",
                    city = "Ho Chi Minh",
                    address = "  99 Nguyen Hue ",
                    avatarUrl = "   ",
                ),
            )

        assertEquals("Le Minh B", updated.fullName)
        assertEquals("0987654321", updated.phoneNumber)
        assertEquals("Ho Chi Minh", updated.city)
        assertEquals("99 Nguyen Hue", updated.address)
        assertNull(updated.avatarUrl)

        val persisted = userRepository.findById(UserId.fromString(registered.id))!!
        assertEquals(clockTime, persisted.updatedAt)

        val event = eventPublisher.events.single() as UserProfileUpdatedEvent
        assertEquals(UserId.fromString(registered.id), event.userId)
        assertEquals("Le Minh B", event.fullName)
        assertEquals("Ho Chi Minh", event.city)
        assertEquals(clockTime, event.occurredAt)

        assertThrows(DomainValidationException::class.java) {
            updateUserProfileUseCase.execute(
                UpdateUserProfileCommand(
                    userId = registered.id,
                    fullName = "X".repeat(151),
                    phoneNumber = null,
                    city = null,
                    address = null,
                    avatarUrl = null,
                ),
            )
        }
    }

    @Test
    fun `refresh rotates token and invalidates the previous refresh token`() {
        registerUseCase.execute(RegisterCommand("carol@shopflow.org", "Password123"))
        val loginResult = loginUseCase.execute(LoginCommand("carol@shopflow.org", "Password123"))

        clockTime = clockTime.plusSeconds(10)
        val refreshed = refreshTokenUseCase.execute(RefreshTokenCommand(loginResult.refreshToken))

        assertNotEquals(loginResult.refreshToken, refreshed.refreshToken)
        assertThrows(InvalidTokenException::class.java) {
            refreshTokenUseCase.execute(RefreshTokenCommand(loginResult.refreshToken))
        }
        assertThrows(InvalidTokenException::class.java) {
            refreshTokenUseCase.execute(RefreshTokenCommand(loginResult.accessToken))
        }
    }

    @Test
    fun `logout revokes refresh token`() {
        registerUseCase.execute(RegisterCommand("dave@shopflow.org", "Password123"))
        val loginResult = loginUseCase.execute(LoginCommand("dave@shopflow.org", "Password123"))

        logoutUseCase.execute(LogoutCommand(loginResult.refreshToken))

        assertThrows(InvalidTokenException::class.java) {
            refreshTokenUseCase.execute(RefreshTokenCommand(loginResult.refreshToken))
        }
    }

    @Test
    fun `getCurrentUser returns profile or throws UserNotFoundException`() {
        val created = registerUseCase.execute(RegisterCommand("eve@shopflow.org", "Password123"))

        val profile = getCurrentUserUseCase.execute(GetCurrentUserQuery(created.id))
        assertEquals(created.email, profile.email)

        assertThrows(UserNotFoundException::class.java) {
            getCurrentUserUseCase.execute(GetCurrentUserQuery(UserId.generate().value.toString()))
        }
    }

    private class InMemoryUserRepository :
        UserRepository,
        UserQueryService {
        private val store = linkedMapOf<UserId, User>()

        override fun findByEmail(email: Email): User? = store.values.firstOrNull { it.email == email }

        override fun findById(id: UserId): User? = store[id]

        override fun existsByEmail(email: Email): Boolean = store.values.any { it.email == email }

        override fun findExistingEmails(emails: Collection<String>): Set<String> {
            val lookup = emails.mapTo(hashSetOf()) { it.trim().lowercase() }
            return store.values
                .map { it.email.value.lowercase() }
                .filterTo(hashSetOf()) { it in lookup }
        }

        override fun findExistingIds(ids: Collection<UserId>): Set<UserId> = ids.filterTo(hashSetOf()) { it in store }

        override fun save(user: User): User {
            store[user.id] = user
            return user
        }

        override fun saveAll(users: Collection<User>): List<User> = users.map(::save)

        override fun findUserProfileById(userId: UserId): UserProfileDto? = store[userId]?.toProfileDto()
    }

    private class InMemoryRoleRepository : RoleRepository {
        private val store = linkedMapOf<RoleId, Role>()

        override fun findByName(name: String): Role? = store.values.firstOrNull { it.name == name }

        override fun findById(id: RoleId): Role? = store[id]

        override fun findAll(): List<Role> = store.values.toList()

        override fun save(role: Role): Role {
            store[role.id] = role
            return role
        }
    }

    private class InMemoryRefreshTokenRepository : RefreshTokenRepository {
        private val tokens = mutableListOf<RefreshToken>()

        override fun findByUserIdAndHashedToken(
            userId: UserId,
            hashedToken: String,
        ): RefreshToken? =
            tokens.firstOrNull {
                it.userId == userId && it.hashedToken == hashedToken
            }

        override fun deleteByUserIdAndHashedToken(
            userId: UserId,
            hashedToken: String,
        ) {
            tokens.removeIf { it.userId == userId && it.hashedToken == hashedToken }
        }

        override fun deleteAllByUserId(userId: UserId) {
            tokens.removeIf { it.userId == userId }
        }

        override fun save(refreshToken: RefreshToken): RefreshToken {
            tokens += refreshToken
            return refreshToken
        }
    }

    private class FakePasswordHasher : PasswordHasher {
        override fun encode(rawPassword: String): HashedPassword = HashedPassword("hashed:$rawPassword")

        override fun matches(
            rawPassword: String,
            hashedPassword: HashedPassword,
        ): Boolean = hashedPassword.value == "hashed:$rawPassword"
    }

    private class FakeTokenProvider : TokenProvider {
        private var counter = 0
        override val refreshTokenValidityMs: Long = 3_600_000L

        override fun generateAccessToken(
            userId: UserId,
            roles: Set<String>,
            permissions: Set<String>,
        ): String {
            counter += 1
            return "access:${userId.value}:${roles.sorted().joinToString(",")}:${permissions.sorted().joinToString(",")}:$counter"
        }

        override fun generateRefreshToken(userId: UserId): String {
            counter += 1
            return "refresh:${userId.value}:$counter"
        }

        override fun validateAccessToken(token: String): Boolean = token.startsWith("access:")

        override fun validateRefreshToken(token: String): Boolean = token.startsWith("refresh:")

        override fun extractUserId(token: String): UserId {
            val parts = token.split(":")
            if (parts.size < 2) throw InvalidTokenException("Invalid token.")
            return UserId.fromString(parts[1])
        }

        override fun extractRoles(token: String): Set<String> =
            token
                .split(":")
                .getOrNull(2)
                ?.split(",")
                ?.filterTo(linkedSetOf()) { it.isNotBlank() }
                ?: emptySet()

        override fun extractPermissions(token: String): Set<String> =
            token
                .split(":")
                .getOrNull(3)
                ?.split(",")
                ?.filterTo(linkedSetOf()) { it.isNotBlank() }
                ?: emptySet()

        override fun hashRefreshToken(rawToken: String): String = "sha256:$rawToken"
    }

    private class RecordingDomainEventPublisher : DomainEventPublisher {
        val events = mutableListOf<DomainEvent>()

        override fun publish(event: DomainEvent) {
            events += event
        }
    }
}
