# ShopFlow Backend — Architecture Blueprint (`overview.md`)

Tài liệu tham chiếu kiến trúc chuẩn (Few-Shot Reference Blueprint) cho dự án **`org.shopflow.backend`**, được tổng hợp từ mô hình nghiệp vụ E-Commerce cốt lõi và thiết kế **Clean Architecture + DDD + CQRS Pattern** hiện hữu.
Xem thêm đặc tả schema cơ sở dữ liệu tổng thể (MySQL + MongoDB) tại [`database-schema.md`](./database-schema.md) và chuẩn response REST API thống nhất tại [`docs/api/response-contract.md`](../api/response-contract.md).

---

## 1. Bản Đồ Domain E-Commerce & Bounded Contexts

Dựa trên 8 thành phần chức năng cốt lõi của hệ thống E-Commerce, các module trong `org.shopflow.backend` được quy hoạch theo các Bounded Context như sau:

| # | Thành phần nghiệp vụ E-Commerce | Bounded Context (`<context>`) | Trách nhiệm chính | Chiến lược lưu trữ / Hạ tầng |
|---|---|---|---|---|
| 1 | **User & Authentication System** | `auth` / `user` | Đăng ký, đăng nhập, xoay vòng JWT refresh token, hồ sơ & phân quyền người dùng | **MySQL (JPA)** *(Đã triển khai)* |
| 2 | **Product Catalog System** | `product` | Danh mục, sản phẩm, biến thể, hình ảnh, giá gốc, mô tả, tìm kiếm & bộ lọc | **MySQL (JPA)** / MongoDB (Catalog mở rộng) |
| 3 | **Shopping Cart & Checkout** | `cart` | Thêm/xóa/cập nhật giỏ hàng, tính tạm tính, thuế, phí vận chuyển, checkout | **Redis** (giỏ hàng tốc độ cao) + MySQL |
| 4 | **Payment Processing System** | `payment` | Giao dịch thanh toán (thẻ, ví điện tử, bank), xác thực & chống gian lận | **MySQL (JPA)** (ACID Ledger) |
| 5 | **Order Management System** | `order` | Vòng đời đơn hàng, hóa đơn, lịch sử đơn, trạng thái giao vận & đổi trả | **MySQL (JPA)** + Domain Events |
| 6 | **Inventory Management System** | `inventory` | Theo dõi tồn kho thời gian thực, giữ chỗ khi checkout, chống overselling | **MySQL (JPA)** + **Redis** (Flash-sale Lock) |
| 7 | **Pricing & Promotions Engine** | `promotion` | Mã giảm giá (coupons), chiến dịch giảm giá, định giá linh hoạt | **MySQL (JPA)** + **Redis** Cache |
| 8 | **Analytics, Notification & Monitoring** | `analytics` / `notification` | Hành vi khách hàng, xu hướng doanh số, thông báo đơn hàng real-time | **MongoDB** / WebSocket |

---

## 2. Kiến Trúc 4 Tầng & Chiều Phụ Thuộc CQRS

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER (REST Controllers, Request/Response DTOs, Converters) │
│   • Chỉ gọi Application UseCases (Command / Query)                      │
│   • Chuyển DomainException -> HTTP Status tại GlobalExceptionHandler    │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ APPLICATION LAYER (Commands, Queries, UseCases, Application DTOs)       │
│   • Write Side: Command -> UseCase (@Transactional) -> Domain Model     │
│   • Read Side:  Query   -> UseCase (readOnly) -> *QueryService (Port)   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ DOMAIN LAYER — PURE KOTLIN 100% (NO Spring / JPA / Jackson / JWT)       │
│   • Entities, Aggregates, Value Objects, Domain Events & Exceptions     │
│   • Repository Interfaces & Domain Service Ports (Dependency Inversion) │
└───────────────────────────────────▲─────────────────────────────────────┘
                                    │ implements
┌───────────────────────────────────┴─────────────────────────────────────┐
│ INFRASTRUCTURE LAYER (JPA Entities, Mappers, RepoImpl, JWT, Security)   │
│   • Persistence: *JpaEntity <-> *PersistenceMapper <-> *RepositoryImpl  │
│   • CQRS Read:   *QueryServiceImpl (truy vấn đọc trực tiếp sang DTO)    │
│   • Security & Tech: JwtService, JwtAuthFilter, HashEncoder, Time       │
└─────────────────────────────────────────────────────────────────────────┘
```

### Luồng CQRS chuẩn
1. **Write Side (Command):** `AuthController` -> `AuthPresentationConverter` -> `LoginCommand` -> `LoginUseCase` -> `User` + `UserRepository` / `PasswordHasher` / `AuthTokenIssuer` -> `UserRepositoryImpl` / `RefreshTokenRepositoryImpl` -> MySQL (`users`, `refresh_tokens`).
2. **Read Side (Query):** `JwtAuthFilter` -> `AuthController` -> `GetCurrentUserQuery` -> `GetCurrentUserUseCase` -> `UserQueryService` -> `UserQueryServiceImpl` -> `UserJpaRepository` -> trả thẳng `UserProfileDto` (không cần dựng Aggregate ghi).

---

## 3. Cây Thư Mục Thực Tế Chuẩn (Few-Shot Reference: Module `auth`)

Đây là cấu trúc thực tế hiện có (65 file Kotlin trong `src/main`, 3 file test + cấu hình trong `src/test`) làm mẫu chuẩn cho toàn bộ dự án:

```text
shopflow-backend/
├── AGENTS.md                                           # Hiến pháp kỹ thuật & quy tắc ràng buộc cho AI Agents
├── docs/architecture/overview.md                       # Tài liệu blueprint kiến trúc (file này)
├── build.gradle.kts                                    # Kotlin 2.3.21, Java 21, Spring Boot 4.1.2, JPA, Security, Redis, Mongo, JJWT
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yaml                        # Port 8080, MySQL JPA, Spring Boot 4 MongoDB (`spring.mongodb.uri`), JWT, CORS
    │   └── kotlin/org/shopflow/backend/
    │       ├── ShopflowBackendApplication.kt           # Entry point (@SpringBootApplication)
    │       │
    │       ├── domain/                                 # [TẦNG 1] PURE KOTLIN - Cấm import Spring/JPA/Jackson/JWT
    │       │   ├── event/
    │       │   │   ├── DomainEvent.kt                  # Marker interface cho Domain Event (occurredAt)
    │       │   │   ├── DomainEventPublisher.kt         # Port phát sự kiện nghiệp vụ
    │       │   │   └── auth/
    │       │   │       ├── UserLoggedInEvent.kt        # Event khi user đăng nhập thành công
    │       │   │       └── UserRegisteredEvent.kt      # Event khi đăng ký user mới
    │       │   ├── exception/
    │       │   │   └── DomainException.kt              # Sealed hierarchy: InvalidCredentials, UserAlreadyExists, InvalidToken...
    │       │   ├── model/auth/
    │       │   │   ├── Email.kt                        # @JvmInline value class + validate format
    │       │   │   ├── HashedPassword.kt               # @JvmInline value class bảo vệ mật khẩu đã hash
    │       │   │   ├── RefreshToken.kt                 # Domain Entity kiểm tra expiry
    │       │   │   ├── Role.kt                         # Enum: CUSTOMER, SELLER, ADMIN
    │       │   │   ├── User.kt                         # Aggregate Root: register(), isActive(), ensureCanAuthenticate()
    │       │   │   ├── UserId.kt                       # @JvmInline value class bọc UUID
    │       │   │   └── UserStatus.kt                   # Enum: ACTIVE, INACTIVE, BANNED
    │       │   ├── repository/auth/
    │       │   │   ├── RefreshTokenRepository.kt       # Repository Interface (Port)
    │       │   │   └── UserRepository.kt               # Repository Interface (Port)
    │       │   └── service/
    │       │       ├── TimeProvider.kt                 # Port trừu tượng hóa thời gian (dễ unit test)
    │       │       └── auth/
    │       │           ├── PasswordHasher.kt           # Port hash & đối chiếu mật khẩu
    │       │           └── TokenProvider.kt            # Port sinh/xác thực JWT & hash refresh token
    │       │
    │       ├── application/                            # [TẦNG 2] USE CASES & CQRS
    │       │   ├── command/auth/
    │       │   │   ├── LoginCommand.kt                 # Write input: đăng nhập
    │       │   │   ├── LogoutCommand.kt                # Write input: thu hồi refresh token
    │       │   │   ├── RefreshTokenCommand.kt          # Write input: xoay vòng token
    │       │   │   └── RegisterCommand.kt              # Write input: đăng ký tài khoản
    │       │   ├── dto/
    │       │   │   ├── SystemStatusDto.kt              # Read output: trạng thái hệ thống
    │       │   │   └── auth/
    │       │   │       ├── AuthResult.kt               # Output cặp accessToken + refreshToken
    │       │   │       └── UserProfileDto.kt           # Output hồ sơ người dùng
    │       │   ├── exception/
    │       │   │   └── ApplicationException.kt         # Base exception tầng Application
    │       │   ├── query/auth/
    │       │   │   ├── GetCurrentUserQuery.kt          # Read input: lấy thông tin user hiện tại
    │       │   │   ├── GetSystemStatusQuery.kt         # Read input: kiểm tra health status
    │       │   │   └── UserQueryService.kt             # CQRS Read Port (truy vấn thẳng ra UserProfileDto)
    │       │   └── usecase/auth/
    │       │       ├── AuthTokenIssuer.kt              # Helper dùng chung phát hành & lưu refresh token
    │       │       ├── GetCurrentUserUseCase.kt        # Query UseCase (@Transactional(readOnly = true))
    │       │       ├── GetSystemStatusUseCase.kt       # Query UseCase cho status
    │       │       ├── LoginUseCase.kt                 # Command UseCase đăng nhập
    │       │       ├── LogoutUseCase.kt                # Command UseCase đăng xuất
    │       │       ├── RefreshTokenUseCase.kt          # Command UseCase xoay vòng token
    │       │       └── RegisterUseCase.kt              # Command UseCase đăng ký
    │       │
    │       ├── infrastructure/                         # [TẦNG 3] ADAPTERS & FRAMEWORKS
    │       │   ├── config/
    │       │   │   └── SpringDomainEventPublisher.kt   # Adapter triển khai DomainEventPublisher qua Spring Events
    │       │   ├── persistence/
    │       │   │   ├── entity/auth/
    │       │   │   │   ├── RefreshTokenJpaEntity.kt    # @Entity bảng refresh_tokens (non-data class, VARCHAR(36))
    │       │   │   │   └── UserJpaEntity.kt            # @Entity bảng users (non-data class, VARCHAR(36))
    │       │   │   ├── jpa/auth/
    │       │   │   │   ├── RefreshTokenJpaRepository.kt # Spring Data JPA + @Modifying @Query DELETE
    │       │   │   │   └── UserJpaRepository.kt        # Spring Data JPA
    │       │   │   ├── mapper/auth/
    │       │   │   │   ├── RefreshTokenPersistenceMapper.kt # RefreshToken <-> RefreshTokenJpaEntity
    │       │   │   │   └── UserPersistenceMapper.kt    # User <-> UserJpaEntity
    │       │   │   └── repository/auth/
    │       │   │       ├── RefreshTokenRepositoryImpl.kt # Triển khai RefreshTokenRepository
    │       │   │       └── UserRepositoryImpl.kt       # Triển khai UserRepository
    │       │   ├── query/auth/
    │       │   │   └── UserQueryServiceImpl.kt         # Triển khai CQRS Read Port UserQueryService
    │       │   ├── security/
    │       │   │   ├── config/SecurityConfig.kt        # Stateless filterChain, CORS, public/private endpoints
    │       │   │   ├── encoder/HashEncoder.kt          # Triển khai PasswordHasher bằng BCrypt
    │       │   │   └── jwt/
    │       │   │       ├── JwtAuthFilter.kt            # Đọc Bearer JWT, gán SecurityContextHolder
    │       │   │       └── JwtService.kt               # Triển khai TokenProvider bằng jjwt 0.12.6 + SHA-256
    │       │   └── time/
    │       │       └── SystemTimeProvider.kt           # Triển khai TimeProvider bằng Instant.now()
    │       │
    │       └── presentation/                           # [TẦNG 4] REST API & VALIDATION
    │           ├── controller/
    │           │   ├── AuthController.kt               # /api/auth/{register,login,refresh,logout,me}
    │           │   └── StatusController.kt             # GET / (text/plain) và GET /api/status (ApiResponse)
    │           ├── converter/
    │           │   ├── AuthPresentationConverter.kt    # Request <-> Command & Dto <-> Response
    │           │   └── StatusPresentationConverter.kt  # SystemStatusDto -> SystemStatusResponse
    │           ├── dto/
    │           │   ├── ApiCode.kt                      # Tập trung mã nghiệp vụ & lỗi chuẩn hóa
    │           │   ├── ApiResponse.kt                  # Chuẩn envelope ApiResponse<T> & ApiErrorDetail
    │           │   ├── SystemStatusResponse.kt
    │           │   └── auth/
    │           │       ├── AuthResponse.kt
    │           │       ├── LoginRequest.kt
    │           │       ├── RefreshTokenRequest.kt
    │           │       ├── RegisterRequest.kt
    │           │       └── UserProfileResponse.kt
    │           ├── exceptionhandler/
    │           │   ├── ApiErrorResponseWriter.kt       # Ghi chuẩn lỗi JSON & tôn trọng content negotiation
    │           │   ├── GlobalExceptionHandler.kt       # @RestControllerAdvice ánh xạ DomainException -> ApiResponse
    │           │   └── SecurityExceptionHandlers.kt    # ApiAuthenticationEntryPoint & ApiAccessDeniedHandler
    │           ├── openapi/
    │           │   └── OpenApiConstants.kt             # Constants cho Swagger/OpenAPI
    │           └── validation/
    │               └── ValidPassword.kt                # Custom Jakarta Validation (@ValidPassword)
    │
    └── test/
        ├── resources/
        │   └── application.yaml                        # Fallback env vars cho ./gradlew test
        └── kotlin/org/shopflow/backend/
            ├── ArchitectureDependencyRuleTest.kt       # Kiểm chứng tự động Dependency Rule 4 tầng
            ├── ShopflowBackendApplicationTests.kt      # Smoke test Spring Boot + MySQL JPA contextLoads()
            └── application/usecase/auth/
                └── AuthUseCasesTest.kt                 # Unit test Pure Kotlin cho các UseCase của auth
```

---

## 4. Checklist 5 Bước Tạo Module Mới (Ví Dụ Minh Họa: `product`)

Khi thêm bất kỳ Bounded Context mới nào (ví dụ `product`), thực hiện đúng theo thứ tự từ lõi `domain` ra ngoài:

### Bước 1: Tạo `domain/` (Pure Kotlin)
- [ ] `domain/model/product/`: `ProductId.kt`, `Money.kt`, `Sku.kt`, `ProductStatus.kt`, `Product.kt` (chứa business invariant như kiểm tra giá >= 0, tồn kho).
- [ ] `domain/event/product/`: `ProductCreatedEvent.kt`, `ProductPriceUpdatedEvent.kt`.
- [ ] `domain/repository/product/`: `ProductRepository.kt` (chỉ nhận và trả Domain Model).
- [ ] `domain/exception/DomainException.kt`: Thêm subclass mới (ví dụ `ProductNotFoundException`, `DuplicateSkuException`).

### Bước 2: Tạo `application/` (CQRS Write & Read)
- [ ] `application/command/product/`: `CreateProductCommand.kt`, `UpdateProductPriceCommand.kt`.
- [ ] `application/query/product/`: `GetProductDetailQuery.kt`, `ListProductsQuery.kt`, `ProductQueryService.kt` (CQRS Read Port).
- [ ] `application/dto/product/`: `ProductDetailDto.kt`, `ProductSummaryDto.kt`.
- [ ] `application/usecase/product/`: `CreateProductUseCase.kt`, `UpdateProductPriceUseCase.kt`, `GetProductDetailUseCase.kt`, `ListProductsUseCase.kt`.

### Bước 3: Tạo `infrastructure/` (Persistence & Read Adapters)
- [ ] `infrastructure/persistence/entity/product/ProductJpaEntity.kt` (dùng `class` thường, không dùng `data class`, ID `VARCHAR(36)`).
- [ ] `infrastructure/persistence/jpa/product/ProductJpaRepository.kt` (`JpaRepository<ProductJpaEntity, String>`).
- [ ] `infrastructure/persistence/mapper/product/ProductPersistenceMapper.kt` (`toDomain` / `toEntity`).
- [ ] `infrastructure/persistence/repository/product/ProductRepositoryImpl.kt` (implements `ProductRepository`).
- [ ] `infrastructure/query/product/ProductQueryServiceImpl.kt` (implements `ProductQueryService` tối ưu truy vấn đọc/phân trang).

### Bước 4: Tạo `presentation/` (REST API & Exception Mapping)
- [ ] `presentation/dto/product/`: `CreateProductRequest.kt`, `UpdateProductPriceRequest.kt`, `ProductResponse.kt`.
- [ ] `presentation/converter/ProductPresentationConverter.kt`.
- [ ] `presentation/controller/ProductController.kt` (`@RequestMapping("/api/products")`).
- [ ] Cập nhật `GlobalExceptionHandler.kt` cho các `DomainException` mới (compiler Kotlin tự kiểm tra vì `DomainException` là `sealed class`).
- [ ] Nếu có endpoint public (như `GET /api/products/**`), cập nhật rõ ràng trong `SecurityConfig.kt` (khi được yêu cầu).

### Bước 5: Viết Test & Chạy Vòng Lặp Kiểm Chứng
- [ ] Viết Pure Kotlin unit tests tại `src/test/kotlin/org/shopflow/backend/application/usecase/product/ProductUseCasesTest.kt` dùng in-memory fakes.
- [ ] Viết E2E/Integration tests (kế thừa chuẩn từ `AuthEndToEndFlowTest` / `RbacAndAuditingIntegrationTest`).
- [ ] Chạy `./gradlew check` (gồm `ktlintCheck` + `ArchitectureDependencyRuleTest` + toàn bộ Unit/Integration/E2E Tests) theo quy trình 3 pha tại [`docs/workflows/new-feature-workflow.md`](../workflows/new-feature-workflow.md) và đảm bảo pass 100%.


---

## Nâng Cấp Nền Tảng: `BaseJpaEntity` Auditing & Hệ Thống Phân Quyền RBAC

- **Auditing (`BaseJpaEntity` + `JpaAuditingConfig`):**
  - Mọi JPA Entity kế thừa `infrastructure/persistence/entity/common/BaseJpaEntity.kt` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener::class)`, `Persistable<String>`).
  - Các cột chuẩn tự động ghi nhận: `created_at`, `updated_at`, `created_by`, `updated_by` (mặc định `"SYSTEM"` khi chạy seeder hoặc đăng ký chưa xác thực).
- **RBAC (`User` - `Role` - `Permission` Many-to-Many):**
  - Tách thực thể `Role` và `Permission` tại `domain/model/auth/`, bảng MySQL `roles`, `permissions`, `user_roles`, `role_permissions`.
  - `RbacDataSeeder` seed idempotent 9 quyền chuẩn (`USER_READ`, `USER_MANAGE`, `PRODUCT_READ`, `PRODUCT_WRITE`, `ORDER_CREATE`, `ORDER_READ_OWN`, `ORDER_MANAGE_ALL`, `PAYMENT_VERIFY`, `FLASH_SALE_MANAGE`) và 3 vai trò (`ROLE_CUSTOMER`, `ROLE_STAFF`, `ROLE_ADMIN`).
  - Access Token JWT mang cả claim `roles` và `permissions`; `SecurityConfig` bật `@EnableMethodSecurity` hỗ trợ `@PreAuthorize` trên Controller.
