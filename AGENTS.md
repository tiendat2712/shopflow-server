# AGENTS.md — Hiến Pháp Kỹ Thuật Dự Án `shopflow-backend`

> Tài liệu ngữ cảnh gốc bắt buộc tuân thủ cho mọi phiên làm việc, refactor và mở rộng tính năng trong hệ thống E-Commerce Backend `shopflow-backend`.
> Tham khảo blueprint chi tiết và cây thư mục mẫu (`auth` module) tại [`docs/architecture/overview.md`](./docs/architecture/overview.md).
> Tham khảo thiết kế CSDL tổng thể (MySQL + MongoDB) khớp bộ dataset mẫu tại [`docs/architecture/database-schema.md`](./docs/architecture/database-schema.md).
> Mọi module E-Commerce mới phải đi qua quy trình 3 pha tại [`docs/workflows/new-feature-workflow.md`](./docs/workflows/new-feature-workflow.md).

---

## 1. Tổng Quan Dự Án & Tech Stack Thực Tế

- **Project:** `shopflow-backend` (root package: `org.shopflow.backend`) — Hệ thống Backend Thương mại Điện tử (E-Commerce).
- **Ngôn ngữ & Runtime:** Kotlin `2.3.21`, Java `21` Toolchain, Gradle `9.8.2` Kotlin DSL (`build.gradle.kts`).
  - Compiler options đang bật: `-Xjsr305=strict`, `-Xannotation-default-target=param-property`, `-Xemit-jvm-type-annotations`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")` + `allOpen` (`@Entity`, `@MappedSuperclass`, `@Embeddable`).
  - Quality & Guardrails: `org.jlleitschuh.gradle.ktlint` (`14.2.0`) và `com.tngtech.archunit:archunit-junit5` (`1.5.1`).
- **Framework & Thư viện cốt lõi (từ `build.gradle.kts`):**
  - **Web & Validation:** Spring Boot `4.1.2` (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`), Jackson 3 Kotlin (`tools.jackson.module:jackson-module-kotlin`), WebSocket (`spring-boot-starter-websocket`).
  - **Security & Auth:** `spring-boot-starter-security` (Stateless Bearer JWT), `io.jsonwebtoken:jjwt-api:0.12.6` (`HS256` cho access/refresh tokens + SHA-256 hash cho refresh token rotation), `BCryptPasswordEncoder`.
  - **Persistence & Caching:**
    - **MySQL + Spring Data JPA:** `spring-boot-starter-data-jpa`, `com.mysql:mysql-connector-j`, Hibernate ORM (`spring.jpa.hibernate.ddl-auto=update`).
    - **MongoDB:** `spring-boot-starter-data-mongodb` (lưu ý chuẩn Spring Boot 4 dùng `spring.mongodb.uri` và `spring.data.mongodb.auto-index-creation`).
    - **Redis:** `spring-boot-starter-data-redis` (dành cho Cache, Cart, Flash-sale / Distributed Locks khi mở rộng).

---

## 2. Luật Bất Di Bất Dịch Của Kiến Trúc 4 Tầng (Strict Dependency Rules)

Chiều phụ thuộc duy nhất hợp lệ: **`presentation` -> `application` -> `domain` <- `infrastructure`** (tuân thủ Dependency Inversion Principle và được kiểm tra tự động bởi `ArchitectureDependencyRuleTest.kt`).

### 2.1. `domain/` — Trái Tim Nghiệp Vụ (Pure Kotlin 100%)
- **CẤM TUYỆT ĐỐI** mọi import từ framework/thư viện hạ tầng:
  - `org.springframework.*`
  - `jakarta.*` (kể cả `jakarta.persistence.*`, `jakarta.validation.*`)
  - `io.jsonwebtoken.*`
  - `com.fasterxml.jackson.*`, `tools.jackson.*`
  - `org.bson.*`
  - Hoặc bất kỳ package nào của `application.*`, `infrastructure.*`, `presentation.*`.
- **Chỉ được phép dùng:** Kotlin stdlib (`kotlin.*`), JDK chuẩn (`java.time.*`, `java.util.UUID`), và nội bộ `org.shopflow.backend.domain.*`.
- **Thành phần cho phép:** Aggregate Roots & Entities (`domain/model/<context>/`), Value Objects (`@JvmInline value class`), Domain Events & `DomainEventPublisher` (`domain/event/`), `sealed class DomainException` (`domain/exception/`), Repository Interfaces (`domain/repository/<context>/`), và Domain Service Ports (`TimeProvider`, `PasswordHasher`, `TokenProvider` trong `domain/service/`).

### 2.2. `application/` — Điều Phối Use Case & CQRS
- **Chỉ phụ thuộc vào `domain/`** (và annotation điều phối `@Service`, `@Transactional` của Spring).
- **CẤM** import `org.shopflow.backend.infrastructure.*`, `org.shopflow.backend.presentation.*`, `jakarta.persistence.*`, `io.jsonwebtoken.*`.
- **Thành phần:** Commands (`application/command/<context>/`), Queries & Read Ports `*QueryService` (`application/query/<context>/`), Application DTOs (`application/dto/`), `ApplicationException`, và Use Cases (`application/usecase/<context>/`).

### 2.3. `infrastructure/` — Hạ Tầng & Adapters
- Triển khai (implement) các interface của `domain/` và `application/`.
- **Bắt buộc tách biệt JPA Entity và Domain Model:**
  - JPA Entity đặt tại `infrastructure/persistence/entity/<context>/*JpaEntity.kt` và **bắt buộc kế thừa `BaseJpaEntity`** (`infrastructure/persistence/entity/common/BaseJpaEntity.kt`, implement `Persistable<String>`) để tự động audit `created_at`, `updated_at`, `created_by`, `updated_by` qua `JpaAuditingConfig.kt` (`auditorProvider`, fallback `"SYSTEM"`). Không khai báo lặp lại `createdAt`/`updatedAt` trên Entity con.
  - **KHÔNG** dùng `data class` cho `@Entity`; luôn dùng `class` thường kết hợp `equals`/`hashCode` theo `id`.
  - Lưu khóa chính/ngoại UUID dưới dạng `String` (`VARCHAR(36)`) để tương thích ổn định với MySQL.
  - Mọi chuyển đổi 2 chiều `Domain Model <-> JPA Entity` bắt buộc đi qua `*PersistenceMapper` tại `infrastructure/persistence/mapper/<context>/`.
- Lưu trữ Repositories tại `infrastructure/persistence/jpa/<context>/` (Spring Data JPA) và `infrastructure/persistence/repository/<context>/` (`*RepositoryImpl`).
- Lưu trữ CQRS Read Adapters tại `infrastructure/query/<context>/` (`*QueryServiceImpl`).
- Lưu trữ Security (`jwt/`, `encoder/`, `config/`), RBAC seeding (`RbacDataSeeder`), `time/` (`SystemTimeProvider`), và `config/` (`SpringDomainEventPublisher`, `JpaAuditingConfig`).

### 2.4. `presentation/` — Giao Tiếp Bên Ngoài (REST API)
- **Chỉ giao tiếp với `application/`** (gọi `*UseCase` qua `Command` / `Query` và nhận Application DTO) và bắt `DomainException` / `ApplicationException` / `AccessDeniedException` tại `presentation/exceptionhandler/GlobalExceptionHandler.kt`.
- **CẤM** import hoặc gọi trực tiếp `org.shopflow.backend.infrastructure.*` hay `org.shopflow.backend.domain.repository.*`.
- **Phân quyền RBAC:** `@EnableMethodSecurity` đã bật trong `SecurityConfig.kt`; `JwtAuthFilter` nạp cả `roles` (`ROLE_*`) và `permissions` (`PRODUCT_WRITE`, `ORDER_MANAGE_ALL`, ...) vào `SecurityContext`. Sử dụng `@PreAuthorize("hasAuthority('...')")` hoặc `@PreAuthorize("hasRole('...')")` trên Controller endpoint thay vì hardcode role enum.
- **Thành phần:** Controllers (`presentation/controller/`), Request/Response DTOs (`presentation/dto/`), Presentation Converters (`presentation/converter/`), Custom Validators (`presentation/validation/`), `GlobalExceptionHandler` (`@RestControllerAdvice`), và `openapi/` constants.

---

## 3. Quy Ước CQRS Pattern & Mở Rộng Bounded Contexts

### 3.1. Phân Tách CQRS (Command vs. Query)
- **Write Side (Thay đổi trạng thái):**
  `Controller` -> `PresentationConverter` -> `application/command/<context>/*Command` -> `application/usecase/<context>/*UseCase` (`@Transactional`) -> `domain/model/<context>/*` -> `domain/repository/<context>/*Repository` -> `infrastructure/persistence/repository/<context>/*RepositoryImpl`.
  - Nên phát `DomainEvent` qua `DomainEventPublisher` khi nghiệp vụ quan trọng hoàn tất (ví dụ `UserRegisteredEvent`, `OrderPlacedEvent`).
- **Read Side (Truy vấn dữ liệu):**
  `Controller` -> `application/query/<context>/*Query` -> `application/usecase/<context>/*UseCase` (`@Transactional(readOnly = true)`) -> `application/query/<context>/*QueryService` -> `infrastructure/query/<context>/*QueryServiceImpl`.
  - Trả thẳng Read DTO (`*Dto`) tối ưu cho việc đọc/tìm kiếm/lọc mà không cần hydrate toàn bộ Write Aggregate.

### 3.2. Quy Tắc Chia Package Theo Bounded Context
Khi phát triển các module E-Commerce tiếp theo (`product`, `cart`, `order`, `payment`, `inventory`, `promotion`, `notification`, `analytics`):
- Bắt buộc tạo sub-package `<context>` **đồng bộ xuyên suốt 4 tầng**:
  - `domain/model/<context>/`, `domain/repository/<context>/`, `domain/event/<context>/`, `domain/service/<context>/` (nếu có)
  - `application/command/<context>/`, `application/query/<context>/`, `application/dto/<context>/`, `application/usecase/<context>/`
  - `infrastructure/persistence/entity/<context>/`, `infrastructure/persistence/jpa/<context>/`, `infrastructure/persistence/mapper/<context>/`, `infrastructure/persistence/repository/<context>/`, `infrastructure/query/<context>/`
  - `presentation/dto/<context>/`, `presentation/controller/`, `presentation/converter/`
- Mọi subclass mới thêm vào `sealed class DomainException` phải được xử lý đầy đủ trong nhánh `when (ex)` tại `GlobalExceptionHandler.kt` để compiler đảm bảo exhaustive mapping sang HTTP Status.

---

## 4. Rào Chắn An Toàn & Vòng Lặp Kiểm Chứng (Guardrails & Verification Loop)

### 4.1. Rào Chắn Bảo Mật & File Nhạy Cảm
- **Cấm sửa tự ý:** Không tự ý chỉnh sửa `SecurityConfig.kt`, `JwtAuthFilter.kt`, `JwtService.kt`, `build.gradle.kts`, hoặc `src/main/resources/application.yaml` nếu không nằm trong yêu cầu rõ ràng của người dùng.
- **Bảo mật dữ liệu cấu hình:** Tuyệt đối không hardcode, commit hoặc in ra log/test các biến môi trường nhạy cảm (`DB_PASSWORD`, `MONGODB_URI`, `JWT_SECRET_BASE64`). Luôn giữ giá trị fallback chỉ trong `src/test/resources/application.yaml` phục vụ test local.
- **Chuẩn endpoint & HTTP contract:** Các API mới đặt dưới prefix `/api/<context>` và bảo toàn contract hiện tại của `/`, `/api/status`, `/api/auth/*`.

### 4.2. Rào Chắn Đóng Băng Schema & Bảo Toàn Dữ Liệu (Schema Freeze & Data Preservation Rules)
- **Đóng băng tuyệt đối 7 bảng dữ liệu thật trong `shopflow_db`:** `users`, `categories`, `products`, `orders`, `order_items`, `reviews`, `user_events`.
  - **CẤM TUYỆT ĐỐI** đổi tên bảng (`@Table`), đổi tên cột (`@Column`), xóa cột, thay đổi kiểu dữ liệu khóa chính/khóa ngoại (`VARCHAR(36)` / `DECIMAL(15, 2)`), hoặc thay đổi thuật toán sinh UUID trong `DatasetUuidConverter` (`"shopflow:<namespace>:<raw_id>"`).
- **Cấm phá hủy dữ liệu & DDL nguy hiểm:**
  - **CẤM TUYỆT ĐỐI** đặt `spring.jpa.hibernate.ddl-auto` thành `create` hoặc `create-drop` trong `application.yaml` (main hoặc test) — chỉ được phép dùng `update` hoặc `validate`.
  - **CẤM** chạy `TRUNCATE`, `DROP TABLE`, hoặc xóa hàng loạt dữ liệu đã nạp trong `shopflow_db`.
- **Tái sử dụng nguyên trạng & mở rộng tương thích ngược:**
  - Khi phát triển các module `order`, `review`, `analytics`/`event` ở các phiên tiếp theo, **BẮT BUỘC tái sử dụng nguyên trạng** các JPA Entity đã tạo (`OrderJpaEntity`, `OrderItemJpaEntity`, `ReviewJpaEntity`, `UserEventJpaEntity`) và ánh xạ chúng sang Domain Model qua `*PersistenceMapper`.
  - Nếu nghiệp vụ mới bắt buộc cần thêm cột vào 1 trong 7 bảng trên, chỉ được phép thêm cột mới dạng **nullable** (`@Column(..., nullable = true)`) hoặc có giá trị mặc định an toàn (`DEFAULT`), tuyệt đối không phá vỡ tính tương thích với dữ liệu dataset hiện có.

### 4.3. Vòng Lặp Kiểm Chứng Bắt Buộc Trước Khi Kết Thúc Task
Sau mọi thao tác tạo mới hoặc chỉnh sửa code:
1. Chạy unit test cho UseCase bằng Pure Kotlin (in-memory fakes) và integration/E2E tests cho module mới.
2. **BẮT BUỘC chạy `./gradlew check`** (tự động chạy `ktlintCheck` + `ArchitectureDependencyRuleTest` gồm source-import & ArchUnit guardrails + Unit Tests + `RbacAndAuditingIntegrationTest` + `AuthEndToEndFlowTest` + `ShopflowBackendApplicationTests`). Nếu có lỗi format, chạy `.\gradlew.bat ktlintFormat --console=plain` trước khi chạy lại `check`.
   - Trên shell Windows PowerShell của máy hiện tại, nếu `PATHEXT` bị thiếu `.BAT`/`.EXE`, luôn chạy theo mẫu:
     ```powershell
     $env:PATHEXT = ".COM;.EXE;.BAT;.CMD;.VBS;.JS;.WS;.MSC"
     Set-Location "D:\shopflow-backend\shopflow-backend"
     .\gradlew.bat ktlintFormat --console=plain
     .\gradlew.bat check --console=plain
     ```
3. **Chỉ được phép báo cáo hoàn thành** khi `./gradlew check` thành công 100% (`0` lỗi ktlint, `0` vi phạm ArchUnit, `0` failures, `0` errors, `0` skipped).
