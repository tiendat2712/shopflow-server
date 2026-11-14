# Quy Trình Phát Triển Module Mới (3 Pha Bắt Buộc) — `shopflow-backend`

> Tài liệu chuẩn hóa quy trình triển khai mọi Bounded Context mới (`product`, `cart`, `order`, `payment`, `inventory`, `promotion`, `notification`, `analytics`) tuân thủ tuyệt đối **Clean Architecture + DDD + CQRS** và các rào chắn tự động (**ktlint** + **ArchUnit** + **E2E Verification**).

---

## Tổng Quan Luồng 3 Pha

```text
[Pha 1: PLAN] ──(Duyệt PLAN.md)──▶ [Pha 2: IMPLEMENT Inside-Out] ──▶ [Pha 3: VERIFY & CLEANUP]
```

**Nguyên tắc bất di bất dịch:** Không viết bất kỳ dòng code implementation nào cho module mới trước khi người dùng duyệt `PLAN.md` ở Pha 1, và không báo cáo hoàn thành trước khi `./gradlew check` pass 100% ở Pha 3.

---

## Pha 1: Lập Kế Hoạch & Phê Duyệt (`PLAN.md`)

Khi nhận yêu cầu xây dựng một module E-Commerce mới (ví dụ: `Product`, `Cart`, `Order`, `Payment`, `FlashSale`, `Notification`):

1. **Tạo file `PLAN.md` tạm tại root `shopflow-backend/PLAN.md`** với đầy đủ 5 mục sau:
   - **Domain Model (Pure Kotlin):** Liệt kê Entities, Aggregate Roots, Value Objects (`@JvmInline value class`), Domain Events (`domain/event/<context>/`), Domain Exceptions (mở rộng `sealed class DomainException`), Repository Interfaces (`domain/repository/<context>/`).
   - **Thiết Kế Cơ Sở Dữ Liệu:** Mô tả bảng MySQL/collection tương ứng; mọi `@Entity` JPA bắt buộc dùng `class` thường (không `data class`), khóa chính UUID `VARCHAR(36)`, và **kế thừa `BaseJpaEntity`** để tự động audit (`created_at`, `updated_at`, `created_by`, `updated_by`).
   - **CQRS Application Design:** Phân tách rõ:
     - **Write Side:** `application/command/<context>/*Command` -> `*UseCase` (`@Transactional`).
     - **Read Side:** `application/query/<context>/*Query`, `*QueryService` -> `*UseCase` (`@Transactional(readOnly = true)`).
   - **REST API Endpoints & RBAC (`@PreAuthorize`):** Liệt kê Method + URL (`/api/<context>/...`), Request/Response DTOs, HTTP Status, và quyền `@PreAuthorize("hasAuthority('...')")` / `@PreAuthorize("hasRole('...')")` (hoặc ghi chú endpoint `permitAll()` nếu public).
   - **Kế Hoạch Kiểm Thử:** Liệt kê test cases cho Unit Test Pure Kotlin (`*UseCasesTest`) và Integration/E2E MockMvc Test.
2. **Cổng Phê Duyệt:** Dừng lại, trình bày tóm tắt `PLAN.md` và **chờ người dùng xác nhận** trước khi chuyển sang Pha 2.

---

## Pha 2: Triển Khai Theo Chiều Từ Trong Ra Ngoài (Inside-Out)

Triển khai tuần tự 4 tầng để tôn trọng Dependency Inversion (`presentation -> application -> domain <- infrastructure`):

1. **Tầng 1 — `domain/` (Pure Kotlin 100%, Cấm import Spring/Jakarta/Jackson/JWT):**
   - `domain/model/<context>/`: Entities, Aggregates, Value Objects.
   - `domain/event/<context>/`: Domain Events khi nghiệp vụ hoàn tất.
   - `domain/exception/DomainException.kt`: Thêm subclass mới vào `sealed class DomainException`.
   - `domain/repository/<context>/` & `domain/service/<context>/`: Interfaces (Ports).
2. **Tầng 2 — `application/` (Điều phối CQRS):**
   - `application/dto/<context>/`: Application DTOs (Write result / Read projection).
   - `application/command/<context>/` & `application/query/<context>/`: Input objects và `*QueryService` Read Port.
   - `application/usecase/<context>/`: Command UseCases (`@Transactional`) và Query UseCases (`@Transactional(readOnly = true)`).
3. **Tầng 3 — `infrastructure/` (Persistence, Query & External Adapters):**
   - `infrastructure/persistence/entity/<context>/*JpaEntity.kt` (**kế thừa `BaseJpaEntity`**, không khai báo lặp lại 4 cột audit, dùng `equals`/`hashCode` theo `getId()`).
   - `infrastructure/persistence/jpa/<context>/`: Spring Data JPA interfaces.
   - `infrastructure/persistence/mapper/<context>/`: `*PersistenceMapper` chuyển đổi 2 chiều `Domain <-> JPA Entity`.
   - `infrastructure/persistence/repository/<context>/`: `*RepositoryImpl` triển khai Repository Port.
   - `infrastructure/query/<context>/`: `*QueryServiceImpl` tối ưu truy vấn đọc sang DTO.
   - *(Nếu cần thêm Permission/Role mới)*: Bổ sung idempotent vào `RbacDataSeeder.kt`.
4. **Tầng 4 — `presentation/` (REST API & Exception Handling):**
   - `presentation/dto/<context>/`: `*Request` (kèm Jakarta Validation) và `*Response`.
   - `presentation/converter/<Context>PresentationConverter.kt`: Map `Request <-> Command/Query` và `Dto <-> Response`.
   - `presentation/controller/<Context>Controller.kt`: Gọi UseCase, gắn `@PreAuthorize(...)`, tuyệt đối không chạm Repository hay JPA Entity.
   - `presentation/exceptionhandler/GlobalExceptionHandler.kt`: Bổ sung ánh xạ cho mọi `DomainException` mới trong khối `when (ex)`.

---

## Pha 3: Kiểm Chứng Toàn Diện & Dọn Dẹp (`./gradlew check`)

1. **Viết Test Đầy Đủ:**
   - **Unit Test (Pure Kotlin + In-Memory Fakes):** `src/test/kotlin/org/shopflow/backend/application/usecase/<context>/*UseCasesTest.kt` kiểm tra mọi invariant nghiệp vụ và edge case.
   - **E2E / Integration Test (`@SpringBootTest` + `MockMvc`):** Kiểm tra luồng HTTP thực tế, serialization JSON, auditing `createdBy`/`updatedBy`, phân quyền `@PreAuthorize` (`401`/`403`/`200`), và dọn sạch bản ghi test trong `finally`.
2. **Chạy Vòng Lặp Kiểm Chứng Bắt Buộc (Guardrails):**
   - Format code chuẩn ktlint:
     ```powershell
     $env:PATHEXT = ".COM;.EXE;.BAT;.CMD;.VBS;.JS;.WS;.MSC"
     Set-Location "D:\shopflow-backend\shopflow-backend"
     .\gradlew.bat ktlintFormat --console=plain
     ```
   - Chạy toàn bộ cổng kiểm tra (`ktlintCheck` + `ArchitectureDependencyRuleTest` + Unit & Integration/E2E Tests):
     ```powershell
     .\gradlew.bat check --console=plain
     ```
   - **Điều kiện đạt:** `BUILD SUCCESSFUL`, **0** vi phạm ktlint, **0** vi phạm ArchUnit, **100%** test case pass (`0` failures, `0` errors, `0` skipped).
3. **Dọn dẹp & Báo cáo:**
   - Xóa file tạm `PLAN.md` sau khi toàn bộ tính năng đã xanh 100%.
   - Báo cáo ngắn gọn danh sách file theo 4 tầng, API endpoints mới, schema DB và kết quả `./gradlew check`.
