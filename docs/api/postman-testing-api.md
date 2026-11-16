# ShopFlow Backend — Tài Liệu Kiểm Thử REST API Bằng Postman

> Tài liệu tổng hợp toàn bộ REST API đang được triển khai trong **`shopflow-backend`**, được xác minh trực tiếp từ mã nguồn (`controller`, `dto`, `converter`, `usecase`, `domain`, `SecurityConfig`, `GlobalExceptionHandler`, `application.yaml`, `build.gradle.kts` và integration tests).
>
> **Nguyên tắc đọc tài liệu:**
> - Toàn bộ JSON body và response trong tài liệu là **ví dụ minh họa khớp DTO thực tế**, không phải log đã gọi API thật.
> - Tài liệu **không** chứa secret thực tế và **không** suy diễn endpoint từ roadmap tương lai (các module như `order`, `cart`, `payment`, `review`, `flash-sale` mới dừng ở tầng DB/schema, chưa có `@RestController`).

---

## 1. Tổng Quan Hệ Thống & Điểm Lưu Ý Thực Tế Từ Source

### 1.1. Cấu hình server & URL gốc
Xác minh từ [`application.yaml`](file:///D:/shopflow-backend/shopflow-backend/src/main/resources/application.yaml):
- `server.port`: `8080`
- `server.servlet.context-path`: **không cấu hình** (mặc định `/`)
- Giới hạn upload multipart: `max-file-size: 5MB`, `max-request-size: 6MB`
- Giá trị `{{baseUrl}}` chuẩn cho Postman: **`http://localhost:8080`** *(không chứa `/api` và không có `/` ở cuối)*.

### 1.2. Chuẩn Envelope `ApiResponse<T>`
Mọi REST API trả JSON đều đi qua [`ApiResponse.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/ApiResponse.kt) và danh mục mã chuẩn hóa tại [`ApiCode.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/ApiCode.kt):

- **Thành công (`200 OK`, `201 Created`):**
  ```json
  {
    "code": "SYSTEM_STATUS_SUCCESS",
    "message": "System status retrieved successfully.",
    "status": 200,
    "result": {}
  }
  ```
- **Lỗi (`4xx`, `5xx`):** luôn có `result: null`, mảng `errors` dạng `{ field, code, message }` và `timestamp` (ISO-8601 UTC). Hệ thống không bao giờ trả `rejectedValue` (mật khẩu/token), tên bảng/constraint nội bộ, SQL hay stack trace.
  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request data.",
    "status": 400,
    "result": null,
    "errors": [
      {
        "field": "email",
        "code": "INVALID_EMAIL",
        "message": "Invalid email format."
      }
    ],
    "timestamp": "2026-11-16T15:00:00Z"
  }
  ```
- **Các trường hợp KHÔNG bọc JSON `ApiResponse`:**
  1. `GET /`: trả `text/plain` (`"Everything cool!"`).
  2. `204 No Content` (`POST /api/auth/logout`, `DELETE /api/products/{id}`, `DELETE /api/media`): **không có response body** (`""`).
  3. Request có header `Accept` không tương thích `application/json` (ví dụ `Accept: application/xml`) hoặc `406 Not Acceptable`: giữ status HTTP tương ứng, không ép JSON body.

### 1.3. Những Điểm Khác Biệt / Mâu Thuẫn Phát Hiện Trong Source Hiện Tại
*(Phân biệt rõ **Hành vi hiện tại** để test đúng trong Postman và **Đề xuất cải tiến** nếu nâng cấp về sau)*

1. **Swagger / OpenAPI URL trong `SecurityConfig` vs `build.gradle.kts`:**
   - *Hành vi hiện tại:* [`SecurityConfig.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/config/SecurityConfig.kt#L46-L47) mở `permitAll()` cho `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` và có [`OpenApiConstants.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/openapi/OpenApiConstants.kt), nhưng [`build.gradle.kts`](file:///D:/shopflow-backend/shopflow-backend/build.gradle.kts) **chưa khai báo dependency `springdoc-openapi`**. Khi gọi các URL Swagger/OpenAPI thực tế sẽ nhận `404 ENDPOINT_NOT_FOUND`.
   - *Đề xuất cải tiến:* Thêm dependency `springdoc-openapi-starter-webmvc-ui` nếu muốn bật Swagger UI thực sự, hoặc dọn cấu hình chưa dùng.
2. **WebSocket dependency vs Controller:**
   - *Hành vi hiện tại:* [`build.gradle.kts`](file:///D:/shopflow-backend/shopflow-backend/build.gradle.kts#L30) có `spring-boot-starter-websocket`, nhưng source hiện chưa có WebSocket config hay STOMP/handler endpoint nào.
3. **CORS header cho `If-Match` / `ETag`:**
   - *Hành vi hiện tại:* [`SecurityConfig.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/config/SecurityConfig.kt#L74) chỉ cho phép `allowedHeaders = ["Authorization", "Content-Type"]` và chưa đặt `exposedHeaders = ["ETag"]`. **Trong Postman không bị CORS**, bạn có thể gửi `If-Match` và đọc `ETag` bình thường. Tuy nhiên trình duyệt Web SPA khác origin gửi `If-Match` qua CORS sẽ bị preflight chặn trừ khi gửi version qua query `?version=` hoặc body `version`.
   - *Đề xuất cải tiến:* Bổ sung `If-Match` vào `allowedHeaders` và `ETag` vào `exposedHeaders` trong cấu hình CORS.
4. **Hành vi `onlyActive=false` trên `GET /api/admin/categories`:**
   - *Hành vi hiện tại:* Tại [`AdminCatalogController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt#L102), `val resolvedIsActive = isActive ?: if (onlyActive == true) true else null`. Do đó nếu chỉ truyền `?onlyActive=false` (không truyền `isActive`), API trả **tất cả** category (cả active lẫn inactive), **không** lọc riêng category inactive. Muốn lọc đúng chỉ category inactive phải dùng `?isActive=false`.
5. **Danh mục không tồn tại hoặc inactive khi gọi endpoint lấy sản phẩm theo category:**
   - *Hành vi hiện tại:* `GET /api/categories/{idOrSlug}/products` và `GET /api/admin/categories/{idOrSlug}/products` không ném `404 CATEGORY_NOT_FOUND`, mà trả `200 OK` kèm trang rỗng (`items: []`, `totalElements: 0`) do xử lý trong [`ProductQueryServiceImpl.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/query/product/ProductQueryServiceImpl.kt#L38-L55).
6. **Tham số phân trang `page` và `size` tự động ép biên (clamp):**
   - *Hành vi hiện tại:* Mọi endpoint danh sách tự động ép `page < 0` thành `0` và `size` vào khoảng `1..100` (xem [`ListProductsUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListProductsUseCase.kt#L49-L50)), không trả lỗi `400` khi `page=-1` hoặc `size=500`.
7. **Khác biệt validation khi truyền `"password": ""` giữa Register và Login:**
   - *Hành vi hiện tại:* [`RegisterRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/RegisterRequest.kt#L12-L13) dùng `@ValidPassword` nên `"password": ""` trả `errors[0].code = "INVALID_PASSWORD"`; còn [`LoginRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/LoginRequest.kt#L10-L11) dùng `@NotBlank` nên `"password": ""` trả `errors[0].code = "REQUIRED_FIELD"`.
8. **Chặn số thực cho field số nguyên & `stagedCsvFiles` khi import dataset:**
   - [`JacksonValidationConfig.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/config/JacksonValidationConfig.kt) tắt `ACCEPT_FLOAT_AS_INT`, nên gửi `1.5` cho `stockQuantity`, `displayOrder`, `version`, `maxUsers`, `maxProducts` sẽ bị từ chối `400 VALIDATION_ERROR` (`TYPE_MISMATCH`).
   - Tại [`ImportKaggleDatasetUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/dataset/ImportKaggleDatasetUseCase.kt#L147), trường `stagedCsvFiles` trong response hiện luôn trả `{}` (object rỗng).

---

## 2. Thiết Lập Postman

### 2.1. Tạo Postman Environment
Tạo một Environment mới trong Postman (ví dụ: `ShopFlow Local`) với các biến sau:

| Variable | Type | Initial Value / Giá trị mẫu | Current Value / Ghi chú sử dụng |
|---|---|---|---|
| `baseUrl` | `default` | `http://localhost:8080` | Khớp `server.port=8080`, context-path `/` |
| `accessToken` | `secret` | *(để trống)* | Tự điền sau `POST /api/auth/login` hoặc `/refresh` (TTL **15 phút**) |
| `refreshToken` | `secret` | *(để trống)* | Tự điền sau `POST /api/auth/login` hoặc `/refresh` (TTL **30 ngày**) |
| `categoryId` | `default` | `62f784a2-2af1-43be-bbde-22a1b3413911` | UUID danh mục dùng cho test tạo/lọc sản phẩm |
| `categorySlug` | `default` | `electronics` | Slug danh mục dùng cho URL hoặc query lọc |
| `productId` | `default` | `b62df34a-04eb-4c96-9e77-8ef920233001` | UUID sản phẩm dùng cho detail/update/publish/restore/delete |
| `productSlug` | `default` | `wireless-mouse` | Slug sản phẩm dùng cho `GET /api/products/{idOrSlug}` |
| `productVersion` | `default` | `0` | Version lạc quan cho header `If-Match` hoặc `?version=` |
| `uploadedPublicId` | `default` | `shopflow/products/sample` | `publicId` trả về sau `POST /api/media/upload` để test `DELETE /api/media` |

### 2.2. Cấu hình Authorization → Bearer Token
1. Với endpoint cần đăng nhập, mở tab **Authorization** của Request (hoặc đặt ở cấp Collection rồi để Request kế thừa **Inherit auth from parent**).
2. Chọn **Auth Type**: `Bearer Token`.
3. Ô **Token**: nhập `{{accessToken}}`. Postman sẽ tự gắn header `Authorization: Bearer <accessToken>`.
4. Với endpoint công khai (`GET /`, `/api/status`, `/api/auth/register|login|refresh|logout`, `GET /api/categories*`, `GET /api/products*`), đặt **Auth Type**: `No Auth`.
   - *Lưu ý kỹ thuật:* Nếu vô tình gửi kèm `Authorization: Bearer <token-hết-hạn>` vào endpoint công khai (`permitAll()`), [`JwtAuthFilter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/jwt/JwtAuthFilter.kt) chỉ clear context rồi vẫn cho đi tiếp (endpoint `permitAll()` vẫn thành công bình thường); nhưng với endpoint yêu cầu đăng nhập sẽ bị chặn với `401 INVALID_TOKEN` hoặc `403 ACCOUNT_INACTIVE`.

### 2.3. Phân Tầng Quyền Truy Cập Thực Tế
Xác minh từ [`SecurityConfig.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/config/SecurityConfig.kt), `@PreAuthorize` trên từng Controller và [`RbacDataSeeder.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/config/RbacDataSeeder.kt):

- **Public (không cần token):**
  - `GET /`, `GET /api/status`
  - `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
  - `GET /api/categories`, `GET /api/categories/{idOrSlug}/products`
  - `GET /api/products`, `GET /api/products/{idOrSlug}`
- **Yêu cầu đăng nhập (bất kỳ tài khoản `ACTIVE`, gồm `ROLE_CUSTOMER`):**
  - `GET /api/auth/me`, `PUT /api/auth/me/profile`
  - `POST /api/media/upload` **khi `folder=avatars`**
- **Yêu cầu permission `PRODUCT_WRITE` (được gán sẵn cho `ROLE_STAFF` và `ROLE_ADMIN`):**
  - `POST /api/categories`
  - `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}`, `POST /api/products/{id}/publish`, `POST /api/products/{id}/restore`
  - Toàn bộ [`AdminCatalogController`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt): `GET /api/admin/products`, `GET /api/admin/products/{idOrSlug}`, `GET /api/admin/categories`, `GET /api/admin/categories/{idOrSlug}/products`
  - `POST /api/media/upload` **khi `folder=products` (mặc định) hoặc `folder=categories`**
  - `DELETE /api/media`
- **Yêu cầu role `ROLE_ADMIN` (`hasRole('ADMIN')`):**
  - `POST /api/admin/datasets/import` và `POST /api/admin/datasets/import-kaggle`

### 2.4. Cách Lấy Token & Chuẩn Bị Tài Khoản Staff / Admin Để Test
1. **Lấy token bằng Login:** Gọi `POST {{baseUrl}}/api/auth/login` với email/password của user đang `ACTIVE`. Trong tab **Scripts → Post-response** của request Login/Refresh, thêm script tự lưu token:
   ```javascript
   if (pm.response.code === 200) {
       const json = pm.response.json();
       if (json.result && json.result.accessToken) {
           pm.environment.set("accessToken", json.result.accessToken);
       }
       if (json.result && json.result.refreshToken) {
           pm.environment.set("refreshToken", json.result.refreshToken);
       }
   }
   ```
2. **Lưu ý quan trọng về tài khoản `ROLE_STAFF` và `ROLE_ADMIN`:**
   - Gọi `POST /api/auth/register` **chỉ tạo tài khoản với `ROLE_CUSTOMER`** (xem [`RegisterUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/RegisterUseCase.kt#L35)). Chức năng import Kaggle dataset cũng chỉ tạo user với `ROLE_CUSTOMER`.
   - `RbacDataSeeder.kt` chỉ seed 3 role (`ROLE_CUSTOMER`, `ROLE_STAFF`, `ROLE_ADMIN`) và 9 permission vào bảng `roles`/`permissions`/`role_permissions`, **không seed sẵn user mẫu**.
   - Hiện tại backend **chưa có REST API quản trị để tạo tài khoản Staff/Admin hoặc gán role/permission**.
   - **Cách chuẩn bị tài khoản test Staff/Admin trên môi trường local:**
     1. Người kiểm thử tự đăng ký 1 tài khoản mới qua `POST {{baseUrl}}/api/auth/register` (ví dụ `admin.test@shopflow.local`).
     2. Tự kết nối vào MySQL local (`shopflow_db`) và gán role tương ứng trong bảng `user_roles`:
        ```sql
        INSERT INTO user_roles (user_id, role_id)
        SELECT u.id, r.id
        FROM users u
        JOIN roles r ON r.name = 'ROLE_ADMIN'
        WHERE u.email = 'admin.test@shopflow.local';
        ```
        *(Hoặc thay `'ROLE_ADMIN'` bằng `'ROLE_STAFF'` nếu muốn test quyền staff không được gọi endpoint dataset import)*.
     3. Vì [`JwtAuthFilter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/jwt/JwtAuthFilter.kt) nạp lại `roles` và `permissions` trực tiếp từ DB qua [`JpaUserSecurityStateAdapter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/security/JpaUserSecurityStateAdapter.kt) ở mỗi request, quyền mới có hiệu lực ngay ở request tiếp theo (hoặc bạn có thể gọi lại `POST /api/auth/login`).

---

## 3. Bảng Tổng Hợp Toàn Bộ Endpoint (25 Routes / 7 Controllers)

| Nhóm chức năng | Method | URL đầy đủ | Mục đích | Xác thực | Role / Permission | Content-Type (Request) | HTTP thành công |
|---|---|---|---|---|---|---|---|
| System | `GET` | `{{baseUrl}}/` | Health check dạng text thuần | Public | Không yêu cầu | Không có body | `200` (`text/plain`) |
| System | `GET` | `{{baseUrl}}/api/status` | Kiểm tra trạng thái backend JSON | Public | Không yêu cầu | Không có body | `200` |
| Auth | `POST` | `{{baseUrl}}/api/auth/register` | Đăng ký tài khoản mới (`ROLE_CUSTOMER`) | Public | Không yêu cầu | `application/json` | `201` |
| Auth | `POST` | `{{baseUrl}}/api/auth/login` | Đăng nhập lấy access & refresh token | Public | Không yêu cầu | `application/json` | `200` |
| Auth | `POST` | `{{baseUrl}}/api/auth/refresh` | Xoay vòng refresh token lấy cặp token mới | Public | Không yêu cầu (dùng token trong body) | `application/json` | `200` |
| Auth | `POST` | `{{baseUrl}}/api/auth/logout` | Đăng xuất, thu hồi refresh token trong DB | Public | Không yêu cầu (dùng token trong body) | `application/json` | `204` *(rỗng)* |
| Auth | `GET` | `{{baseUrl}}/api/auth/me` | Xem hồ sơ & quyền hiện tại của user | Bearer | User `ACTIVE` | Không có body | `200` |
| Auth | `PUT` | `{{baseUrl}}/api/auth/me/profile` | Cập nhật toàn bộ hồ sơ cá nhân user | Bearer | User `ACTIVE` | `application/json` | `200` |
| Categories | `GET` | `{{baseUrl}}/api/categories` | Danh sách danh mục active cho storefront | Public | Không yêu cầu | Không có body | `200` |
| Categories | `GET` | `{{baseUrl}}/api/categories/{{categoryId}}/products` | Sản phẩm công khai thuộc 1 danh mục | Public | Không yêu cầu | Không có body | `200` |
| Categories | `POST` | `{{baseUrl}}/api/categories` | Tạo danh mục mới | Bearer | `PRODUCT_WRITE` | `application/json` | `201` |
| Products | `GET` | `{{baseUrl}}/api/products` | Tìm kiếm/lọc sản phẩm công khai storefront | Public | Không yêu cầu | Không có body | `200` |
| Products | `GET` | `{{baseUrl}}/api/products/{{productId}}` | Chi tiết sản phẩm công khai (UUID/slug) | Public | Không yêu cầu | Không có body | `200` (+`ETag`) |
| Products | `POST` | `{{baseUrl}}/api/products` | Tạo sản phẩm mới | Bearer | `PRODUCT_WRITE` | `application/json` | `201` (+`ETag`) |
| Products | `PUT` | `{{baseUrl}}/api/products/{{productId}}` | Cập nhật sản phẩm (optimistic locking) | Bearer | `PRODUCT_WRITE` | `application/json` | `200` (+`ETag`) |
| Products | `DELETE` | `{{baseUrl}}/api/products/{{productId}}` | Xóa mềm sản phẩm sang `ARCHIVED` | Bearer | `PRODUCT_WRITE` | Không có body | `204` *(rỗng)* |
| Products | `POST` | `{{baseUrl}}/api/products/{{productId}}/publish` | Xuất bản sản phẩm (`DRAFT`/`OUT_OF_STOCK` -> `ACTIVE`) | Bearer | `PRODUCT_WRITE` | Không có body | `200` (+`ETag`) |
| Products | `POST` | `{{baseUrl}}/api/products/{{productId}}/restore` | Khôi phục sản phẩm (`ARCHIVED` -> `DRAFT`) | Bearer | `PRODUCT_WRITE` | Không có body | `200` (+`ETag`) |
| Admin Catalog | `GET` | `{{baseUrl}}/api/admin/products` | Quản trị danh sách sản phẩm (mọi status) | Bearer | `PRODUCT_WRITE` | Không có body | `200` |
| Admin Catalog | `GET` | `{{baseUrl}}/api/admin/products/{{productId}}` | Quản trị chi tiết sản phẩm (mọi status) | Bearer | `PRODUCT_WRITE` | Không có body | `200` (+`ETag`) |
| Admin Catalog | `GET` | `{{baseUrl}}/api/admin/categories` | Quản trị danh sách danh mục (cả inactive) | Bearer | `PRODUCT_WRITE` | Không có body | `200` |
| Admin Catalog | `GET` | `{{baseUrl}}/api/admin/categories/{{categoryId}}/products` | Quản trị sản phẩm theo danh mục (mọi status) | Bearer | `PRODUCT_WRITE` | Không có body | `200` |
| Media | `POST` | `{{baseUrl}}/api/media/upload` | Upload ảnh JPG/PNG/WEBP lên Cloudinary | Bearer | User `ACTIVE` (`avatars`); `PRODUCT_WRITE` (`products`/`categories`) | `multipart/form-data` | `200` |
| Media | `DELETE` | `{{baseUrl}}/api/media?publicId={{uploadedPublicId}}` | Xóa ảnh trên Cloudinary theo `publicId` | Bearer | `PRODUCT_WRITE` | Không có body | `204` *(rỗng)* |
| Admin Dataset | `POST` | `{{baseUrl}}/api/admin/datasets/import` | Import bộ dataset CSV mẫu vào MySQL | Bearer | `ROLE_ADMIN` | `application/json` *(tùy chọn)* | `200` |
| Admin Dataset | `POST` | `{{baseUrl}}/api/admin/datasets/import-kaggle` | Alias URL của endpoint import dataset trên | Bearer | `ROLE_ADMIN` | `application/json` *(tùy chọn)* | `200` |

---

## 4. Chi Tiết Từng Endpoint

---

### 4.1. Root Health Check (`text/plain`)
- **Mục đích:** Kiểm tra nhanh ứng dụng đã chạy ở root path, trả văn bản thuần (`text/plain`), không bọc JSON `ApiResponse`.
- **Method & URL:** `GET {{baseUrl}}/`
- **Headers:** Không bắt buộc (`Accept: text/plain` hoặc `*/*`).
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body:** Không có request body.
- **Response thành công (`200 OK`, `Content-Type: text/plain;charset=UTF-8`, minh họa):**
  ```text
  Everything cool!
  ```
- **Lỗi đáng chú ý:**
  - `405 METHOD_NOT_ALLOWED`: sai HTTP method (ví dụ `POST {{baseUrl}}/`), kèm header `Allow: GET, HEAD`.
  - `406 Not Acceptable`: nếu gửi `Accept: application/json` vì endpoint chỉ `produces = [MediaType.TEXT_PLAIN_VALUE]`.
- **Source liên quan:** [`StatusController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/StatusController.kt), [`GetSystemStatusUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/GetSystemStatusUseCase.kt)

---

### 4.2. System API Status (`JSON`)
- **Mục đích:** Kiểm tra trạng thái hoạt động của API theo chuẩn JSON envelope `ApiResponse`.
- **Method & URL:** `GET {{baseUrl}}/api/status`
- **Headers:** `Accept: application/json` (khuyến nghị).
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<SystemStatusResponse>`):**
  ```json
  {
    "code": "SYSTEM_STATUS_SUCCESS",
    "message": "System status retrieved successfully.",
    "status": 200,
    "result": {
      "status": "UP",
      "message": "Everything cool!",
      "timestamp": "2026-11-16T15:00:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `405 METHOD_NOT_ALLOWED`: sai method (ví dụ `POST`), kèm header `Allow: GET, HEAD`.
- **Source liên quan:** [`StatusController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/StatusController.kt), [`SystemStatusResponse.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/SystemStatusResponse.kt), [`StatusPresentationConverter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/converter/StatusPresentationConverter.kt)

---

### 4.3. Đăng Ký Tài Khoản Mới
- **Mục đích:** Tạo người dùng mới ở trạng thái `ACTIVE` với role mặc định **`ROLE_CUSTOMER`**. Không tự cấp quyền staff/admin.
- **Method & URL:** `POST {{baseUrl}}/api/auth/register`
- **Headers:** `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`RegisterRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `email` | `String` | Có | — | `@NotBlank`, `@Email`; domain trim khoảng trắng và kiểm tra chứa `@`. |
| `password` | `String` | Có | — | `@ValidPassword`: tối thiểu **9 ký tự**, có ít nhất **1 chữ thường**, **1 chữ hoa**, **1 chữ số**. |
| `fullName` | `String?` | Không | `null` | `@Size(max = 150)`. Domain trim khoảng trắng hai đầu. |
| `city` | `String?` | Không | `null` | `@Size(max = 100)`. Domain trim khoảng trắng hai đầu. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - `email`, `password`: nếu bỏ qua hoặc `null` -> Jackson ném `400 VALIDATION_ERROR` (`errors[].code = "REQUIRED_FIELD"`). Nếu `email: ""` hoặc `"   "` -> `REQUIRED_FIELD`. Nếu `password: ""` hoặc `"   "` -> `INVALID_PASSWORD` (vì `@ValidPassword` đặt trực tiếp không kèm `@NotBlank`).
  - `fullName`, `city`: bỏ qua, gửi `null`, gửi `""` hoặc chuỗi chỉ toàn khoảng trắng `"   "` đều được chuẩn hóa qua `.trim().takeIf { it.isNotEmpty() }` thành `null`. Tuy nhiên nếu chuỗi khoảng trắng dài quá 150 (`fullName`) hoặc 100 (`city`) ký tự sẽ bị `@Size` chặn `400` (`INVALID_LENGTH`).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "email": "customer.test@shopflow.local",
    "password": "Password123",
    "fullName": "Nguyen Van A",
    "city": "Ho Chi Minh"
  }
  ```
- **Response thành công mẫu (`201 Created`, minh họa khớp `ApiResponse<UserProfileResponse>`):**
  ```json
  {
    "code": "AUTH_REGISTER_SUCCESS",
    "message": "User registered successfully.",
    "status": 201,
    "result": {
      "id": "a7f2b910-4c9a-4e20-8e4f-91a3f28c1100",
      "email": "customer.test@shopflow.local",
      "fullName": "Nguyen Van A",
      "phoneNumber": null,
      "city": "Ho Chi Minh",
      "address": null,
      "avatarUrl": null,
      "roles": [
        "ROLE_CUSTOMER"
      ],
      "permissions": [
        "ORDER_CREATE",
        "ORDER_READ_OWN",
        "PRODUCT_READ",
        "USER_READ"
      ],
      "status": "ACTIVE",
      "createdAt": "2026-11-16T15:01:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: email sai định dạng (`INVALID_EMAIL`), mật khẩu yếu (`INVALID_PASSWORD`), thiếu trường/null (`REQUIRED_FIELD`), hoặc quá độ dài (`INVALID_LENGTH`).
  - `409 USER_ALREADY_EXISTS`: email đã tồn tại trong DB (cả pre-check và unique constraint race translation).
  - `404 ROLE_NOT_FOUND`: chưa seed `ROLE_CUSTOMER` (hiếm khi xảy ra trừ khi DB rỗng RBAC).
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`RegisterRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/RegisterRequest.kt), [`ValidPassword.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/validation/ValidPassword.kt), [`RegisterUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/RegisterUseCase.kt), [`User.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/auth/User.kt)

---

### 4.4. Đăng Nhập
- **Mục đích:** Xác thực bằng email + password, phát hành JWT Access Token (TTL 15 phút) và Refresh Token (TTL 30 ngày, lưu SHA-256 hash vào DB).
- **Method & URL:** `POST {{baseUrl}}/api/auth/login`
- **Headers:** `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`LoginRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `email` | `String` | Có | — | `@NotBlank`, `@Email`; domain trim khoảng trắng và kiểm tra chứa `@`. |
| `password` | `String` | Có | — | `@NotBlank`. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - Bỏ qua hoặc `null` bất kỳ trường nào -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
  - Chuỗi rỗng `""` hoặc toàn khoảng trắng `"   "` ở `email` hoặc `password` -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "email": "customer.test@shopflow.local",
    "password": "Password123"
  }
  ```
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<AuthResponse>`):**
  ```json
  {
    "code": "AUTH_LOGIN_SUCCESS",
    "message": "Login successful.",
    "status": 200,
    "result": {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJhY2Nlc3MifQ.exampleAccessSignature",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJyZWZyZXNoIn0.exampleRefreshSignature"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: email sai định dạng, rỗng hoặc thiếu body (`MISSING_REQUEST_BODY`).
  - `401 INVALID_CREDENTIALS`: sai email hoặc sai mật khẩu.
  - `403 ACCOUNT_INACTIVE`: đúng email/mật khẩu nhưng tài khoản đang ở trạng thái `INACTIVE` hoặc `BANNED`.
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`LoginRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/LoginRequest.kt), [`AuthResponse.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/AuthResponse.kt), [`LoginUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/LoginUseCase.kt)

---

### 4.5. Xoay Vòng Refresh Token
- **Mục đích:** Đổi Refresh Token hợp lệ lấy cặp Access Token + Refresh Token mới; Refresh Token cũ bị tiêu thụ nguyên tử một lần duy nhất (anti-reuse).
- **Method & URL:** `POST {{baseUrl}}/api/auth/refresh`
- **Headers:** `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`RefreshTokenRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `refreshToken` | `String` | Có | — | `@NotBlank`. Phải là JWT type `"refresh"` hợp lệ chưa hết hạn và chưa bị dùng/thu hồi. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - Bỏ qua hoặc `null` hoặc `""` hoặc `"   "` -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJyZWZyZXNoIn0.exampleRefreshSignature"
  }
  ```
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<AuthResponse>`):**
  ```json
  {
    "code": "AUTH_REFRESH_SUCCESS",
    "message": "Token refreshed successfully.",
    "status": 200,
    "result": {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJhY2Nlc3MifQ.newAccessSignature",
      "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJyZWZyZXNoIn0.newRefreshSignature"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR` / `MISSING_REQUEST_BODY`: thiếu hoặc rỗng `refreshToken`.
  - `401 INVALID_TOKEN`: JWT sai chữ ký, hết hạn, truyền nhầm Access Token, user không tồn tại, token đã được dùng trước đó hoặc đã bị logout.
  - `403 ACCOUNT_INACTIVE`: tài khoản liên kết với refresh token đang ở trạng thái `INACTIVE` hoặc `BANNED`.
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`RefreshTokenRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/RefreshTokenRequest.kt), [`RefreshTokenUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/RefreshTokenUseCase.kt)

---

### 4.6. Đăng Xuất (Thu Hồi Refresh Token)
- **Mục đích:** Thu hồi Refresh Token hiện tại khỏi DB để không thể xoay vòng tiếp. Access Token đã phát hành vẫn giữ hiệu lực cho tới khi hết hạn (trừ trường hợp user bị khóa/gỡ quyền).
- **Method & URL:** `POST {{baseUrl}}/api/auth/logout`
- **Headers:** `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`RefreshTokenRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `refreshToken` | `String` | Có | — | `@NotBlank`. Phải là JWT type `"refresh"` hợp lệ. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - Bỏ qua, `null`, `""` hoặc `"   "` -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
  - Nếu gửi Refresh Token có chữ ký JWT hợp lệ nhưng đã bị xóa trước đó trong DB -> vẫn trả `204 No Content` (idempotent).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhN2YyYjkxMCIsInR5cGUiOiJyZWZyZXNoIn0.exampleRefreshSignature"
  }
  ```
- **Response thành công (`204 No Content`):**
  - Không có response body.
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: `refreshToken` trống/null/thiếu body.
  - `401 INVALID_TOKEN`: `refreshToken` sai chữ ký, hết hạn, sai format JWT hoặc truyền nhầm Access Token.
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`RefreshTokenRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/RefreshTokenRequest.kt), [`LogoutUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/LogoutUseCase.kt)

---

### 4.7. Lấy Thông Tin Tài Khoản Hiện Tại
- **Mục đích:** Trả hồ sơ người dùng, danh sách `roles`, danh sách `permissions` và `status` hiện hành từ Access Token đang đăng nhập.
- **Method & URL:** `GET {{baseUrl}}/api/auth/me`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<UserProfileResponse>`):**
  ```json
  {
    "code": "AUTH_ME_SUCCESS",
    "message": "Current user profile retrieved successfully.",
    "status": 200,
    "result": {
      "id": "a7f2b910-4c9a-4e20-8e4f-91a3f28c1100",
      "email": "customer.test@shopflow.local",
      "fullName": "Nguyen Van A",
      "phoneNumber": null,
      "city": "Ho Chi Minh",
      "address": null,
      "avatarUrl": null,
      "roles": [
        "ROLE_CUSTOMER"
      ],
      "permissions": [
        "ORDER_CREATE",
        "ORDER_READ_OWN",
        "PRODUCT_READ",
        "USER_READ"
      ],
      "status": "ACTIVE",
      "createdAt": "2026-11-16T15:01:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `401 UNAUTHORIZED`: thiếu header `Authorization: Bearer ...`.
  - `401 INVALID_TOKEN`: access token sai chữ ký, hết hạn, hoặc user đã bị xóa khỏi DB.
  - `403 ACCOUNT_INACTIVE`: user đang ở trạng thái `INACTIVE` hoặc `BANNED`.
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`UserProfileResponse.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/UserProfileResponse.kt), [`GetCurrentUserUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/GetCurrentUserUseCase.kt)

---

### 4.8. Cập Nhật Hồ Sơ Cá Nhân
- **Mục đích:** Cập nhật thông tin hồ sơ của user hiện tại.
- **Phương thức cập nhật (`PUT`):** Đây là **cập nhật thay thế toàn bộ các field hồ sơ (full replacement)**, **không phải PATCH**. Mọi trường tùy chọn đều có default `null` trong DTO và được ghi đè thẳng vào đối tượng `User`.
- **Method & URL:** `PUT {{baseUrl}}/api/auth/me/profile`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`UpdateUserProfileRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `fullName` | `String?` | Không | `null` | `@Size(max = 150)`. Trim khoảng trắng hai đầu. |
| `phoneNumber` | `String?` | Không | `null` | `@Size(max = 30)`. Trim khoảng trắng hai đầu. |
| `city` | `String?` | Không | `null` | `@Size(max = 100)`. Trim khoảng trắng hai đầu. |
| `address` | `String?` | Không | `null` | `@Size(max = 500)`. Trim khoảng trắng hai đầu. |
| `avatarUrl` | `String?` | Không | `null` | `@Size(max = 500)`. Trim khoảng trắng hai đầu. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""` (cảnh báo reset khi bỏ qua):**
  - **Bỏ qua trường** -> trường đó nhận giá trị mặc định `null` và **bị reset về `null` trong database** (`fullName`, `phoneNumber`, `city`, `address`, `avatarUrl`). Nếu gửi JSON `{}` thì toàn bộ 5 trường đều bị xóa về `null`.
  - **Truyền `null`, `""` hoặc `"   "`** -> đều được `normalizeOptional()` đổi thành `null` (miễn không vượt quá giới hạn ký tự `@Size` trước khi trim).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "fullName": "Nguyen Van B",
    "phoneNumber": "0909123456",
    "city": "Ha Noi",
    "address": "123 Kim Ma, Ba Dinh",
    "avatarUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/avatars/sample.jpg"
  }
  ```
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<UserProfileResponse>`):**
  ```json
  {
    "code": "AUTH_PROFILE_UPDATE_SUCCESS",
    "message": "User profile updated successfully.",
    "status": 200,
    "result": {
      "id": "a7f2b910-4c9a-4e20-8e4f-91a3f28c1100",
      "email": "customer.test@shopflow.local",
      "fullName": "Nguyen Van B",
      "phoneNumber": "0909123456",
      "city": "Ha Noi",
      "address": "123 Kim Ma, Ba Dinh",
      "avatarUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/avatars/sample.jpg",
      "roles": [
        "ROLE_CUSTOMER"
      ],
      "permissions": [
        "ORDER_CREATE",
        "ORDER_READ_OWN",
        "PRODUCT_READ",
        "USER_READ"
      ],
      "status": "ACTIVE",
      "createdAt": "2026-11-16T15:01:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: vượt quá giới hạn ký tự (`INVALID_LENGTH`), thiếu request body (`MISSING_REQUEST_BODY`), hoặc JSON field sai kiểu (`TYPE_MISMATCH`).
  - `401 UNAUTHORIZED` / `INVALID_TOKEN`: chưa đăng nhập hoặc token không hợp lệ.
  - `403 ACCOUNT_INACTIVE`: tài khoản đã bị khóa.
- **Source liên quan:** [`AuthController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/AuthController.kt), [`UpdateUserProfileRequest.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/auth/UpdateUserProfileRequest.kt), [`UpdateUserProfileUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/auth/UpdateUserProfileUseCase.kt), [`User.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/auth/User.kt)

---

### 4.9. Danh Sách Danh Mục Công Khai (Storefront)
- **Mục đích:** Lấy danh sách các danh mục đang hoạt động (`isActive = true`) có phân trang, lọc và sắp xếp.
- **Method & URL:** `GET {{baseUrl}}/api/categories`
- **URL mẫu có filter để copy:** `{{baseUrl}}/api/categories?page=0&size=20&sortBy=displayOrder&sortDir=asc`
- **Headers:** `Accept: application/json` (không cần `Authorization`).
- **Path parameters:** Không có path parameters.
- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn & Giá trị hợp lệ |
|---|---|---|---|---|
| `page` | `Int` | Không | `0` | Nếu `< 0`, tự clamp về `0`. |
| `size` | `Int` | Không | `20` | Tự clamp trong `1..100` (`MAX_CATALOG_PAGE_SIZE = 100`). |
| `parentId` | `String?` | Không | `null` | UUID chuẩn (`8-4-4-4-12`). Bỏ qua hoặc `""` / `"   "` -> không lọc theo parent. Sai định dạng UUID -> `400`. |
| `keyword` | `String?` | Không | `null` | Tìm không phân biệt hoa thường trên `name` hoặc `slug`. Chuỗi rỗng/khoảng trắng -> bỏ qua. |
| `sortBy` | `String` | Không | `"displayOrder"` | Field: `displayOrder` (`display_order`), `name`, `createdAt` (`created_at`); hoặc preset: `newest`, `oldest`, `name_asc`, `name_desc`; hoặc dạng `"field,dir"`. |
| `sortDir` | `String` | Không | `"asc"` | `asc` hoặc `desc` (không phân biệt hoa thường). Bị ghi đè nếu `sortBy` là preset hoặc dạng `"field,dir"`. |

- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<CategoryResponse>>`):**
  ```json
  {
    "code": "CATEGORY_LIST_SUCCESS",
    "message": "Categories retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "name": "Electronics",
          "slug": "electronics",
          "parentId": null,
          "description": "Devices and accessories",
          "displayOrder": 1,
          "isActive": true,
          "createdAt": "2026-11-16T12:00:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: `parentId` sai định dạng UUID (`"Invalid parentId format."`), `sortBy` hoặc `sortDir` ngoài danh sách hợp lệ.
  - `400 INVALID_PARAMETER_TYPE`: truyền chữ cho `page` hoặc `size`.
- **Source liên quan:** [`CategoryController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/CategoryController.kt), [`CategoryDtos.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/product/CategoryDtos.kt), [`ListCategoriesUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListCategoriesUseCase.kt), [`SortTypes.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/query/product/SortTypes.kt), [`CategoryQueryServiceImpl.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/query/product/CategoryQueryServiceImpl.kt)

---

### 4.10. Lấy Sản Phẩm Công Khai Theo Danh Mục
- **Mục đích:** Lấy danh sách sản phẩm công khai (`ACTIVE` và `OUT_OF_STOCK`) thuộc một danh mục đang active (nhận UUID hoặc slug của danh mục). Nếu danh mục không tồn tại hoặc inactive, hiện trả `200 OK` với trang rỗng.
- **Method & URL:** `GET {{baseUrl}}/api/categories/{{categoryId}}/products` (hoặc `{{baseUrl}}/api/categories/{{categorySlug}}/products`)
- **Headers:** `Accept: application/json` (không cần `Authorization`).
- **Path parameters:**

| Tham số | Kiểu | Bắt buộc | Quy tắc |
|---|---|---|---|
| `idOrSlug` | `String` | Có | UUID danh mục hoặc slug danh mục. Chuỗi toàn khoảng trắng -> `400 VALIDATION_ERROR`. |

- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn & Giá trị hợp lệ |
|---|---|---|---|---|
| `page` | `Int` | Không | `0` | Tự clamp `>= 0`. |
| `size` | `Int` | Không | `20` | Tự clamp `1..100`. |
| `keyword` | `String?` | Không | `null` | Tìm không phân biệt hoa thường trên `name` hoặc `sku` sản phẩm. `""`/`"   "` -> bỏ qua. |
| `minPrice` | `BigDecimal?` | Không | `null` | `0.00..9999999999999.99`, tối đa 2 chữ số thập phân, `minPrice <= maxPrice`. |
| `maxPrice` | `BigDecimal?` | Không | `null` | `0.00..9999999999999.99`, tối đa 2 chữ số thập phân. |
| `minRating` | `BigDecimal?` | Không | `null` | Trong khoảng `0..5`. |
| `status` | `String?` | Không | `null` | Storefront chỉ nhận **`ACTIVE`** hoặc **`OUT_OF_STOCK`**. Truyền `ALL`, `DRAFT`, `ARCHIVED` hoặc chuỗi lạ -> `400 VALIDATION_ERROR`. Bỏ qua / `""` -> lọc cả `ACTIVE` và `OUT_OF_STOCK`. |
| `sortBy` | `String` | Không | `"createdAt"` | `createdAt` (`created_at`), `price`, `name`, `averageRating` (`average_rating`, `rating`); preset: `newest`, `oldest`, `price_asc`, `price_desc`, `name_asc`, `name_desc`, `rating_desc`, `rating_asc`; hoặc `"field,dir"`. |
| `sortDir` | `String` | Không | `"desc"` | `asc` hoặc `desc`. |

- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<ProductSummaryResponse>>`):**
  ```json
  {
    "code": "CATEGORY_PRODUCT_LIST_SUCCESS",
    "message": "Category products retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
          "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "categoryName": "Electronics",
          "name": "Wireless Mouse",
          "slug": "wireless-mouse",
          "sku": "WM-001",
          "price": 29.99,
          "stockQuantity": 50,
          "averageRating": 0.00,
          "reviewCount": 0,
          "imageUrl": null,
          "status": "ACTIVE",
          "version": 0
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: `status=ALL|DRAFT|ARCHIVED`, `minPrice > maxPrice`, giá > 2 số thập phân, `minRating` ngoài `0..5`, hoặc `sortBy`/`sortDir` sai.
  - `400 INVALID_PARAMETER_TYPE`: sai kiểu số cho `page`, `size`, `minPrice`, `maxPrice`, `minRating`.
- **Source liên quan:** [`CategoryController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/CategoryController.kt), [`ProductPresentationConverter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/converter/ProductPresentationConverter.kt), [`CatalogQueryValidation.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/query/product/CatalogQueryValidation.kt), [`ListProductsUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListProductsUseCase.kt)

---

### 4.11. Tạo Danh Mục Mới
- **Mục đích:** Tạo danh mục sản phẩm mới. Yêu cầu quyền `PRODUCT_WRITE` (`ROLE_STAFF` hoặc `ROLE_ADMIN`).
- **Method & URL:** `POST {{baseUrl}}/api/categories`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`CreateCategoryRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `name` | `String` | Có | — | `@NotBlank`, `@Size(max = 120)`. Trim khoảng trắng hai đầu. |
| `slug` | `String?` | Không | `null` | `@Size(max = 140)`. Chuẩn hóa bỏ dấu tiếng Việt -> lowercase + `-`. Nếu bỏ qua/`null`/rỗng -> tự sinh từ `name`. |
| `parentId` | `String?` | Không | `null` | `@Pattern`: rỗng/khoảng trắng hoặc UUID chuẩn (`8-4-4-4-12`). Nếu có giá trị, danh mục cha phải tồn tại. |
| `description` | `String?` | Không | `null` | `@Size(max = 500)`. |
| `displayOrder` | `Int` | Không | `0` | `@Min(0)`. Không chấp nhận số thập phân. |
| `isActive` | `Boolean` | Không | `true` | `true` hoặc `false`. |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - `name`: bỏ qua, `null`, `""` hoặc `"   "` -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
  - `slug`: bỏ qua, `null`, `""` hoặc `"   "` -> tự động sinh slug từ `name` (lưu ý nếu truyền chuỗi chỉ toàn ký tự đặc biệt như `"---"` nhưng có độ dài > 0 và không blank thì sau khi chuẩn hóa sẽ rỗng -> `400 VALIDATION_ERROR`).
  - `parentId`: bỏ qua, `null`, `""` hoặc `"   "` -> lưu `null` (danh mục gốc).
  - `description`: bỏ qua, `null`, `""` hoặc `"   "` -> lưu `null`.
  - `displayOrder` / `isActive`: bỏ qua -> dùng mặc định (`0` / `true`); nhưng nếu truyền **`null`** rõ ràng trong JSON sẽ lỗi `400 VALIDATION_ERROR` (`REQUIRED_FIELD`) vì kiểu non-null trong Kotlin DTO.
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "name": "Wireless Audio",
    "slug": "wireless-audio",
    "parentId": null,
    "description": "Headphones and speakers",
    "displayOrder": 1,
    "isActive": true
  }
  ```
- **Response thành công mẫu (`201 Created`, minh họa khớp `ApiResponse<CategoryResponse>`):**
  ```json
  {
    "code": "CATEGORY_CREATE_SUCCESS",
    "message": "Category created successfully.",
    "status": 201,
    "result": {
      "id": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "name": "Wireless Audio",
      "slug": "wireless-audio",
      "parentId": null,
      "description": "Headphones and speakers",
      "displayOrder": 1,
      "isActive": true,
      "createdAt": "2026-11-16T15:05:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: tên rỗng, `parentId` sai UUID, `displayOrder < 0`, hoặc truyền `displayOrder: 1.5` / `null`.
  - `401 UNAUTHORIZED` / `INVALID_TOKEN`: chưa đăng nhập hoặc token hỏng.
  - `403 ACCESS_DENIED` / `ACCOUNT_INACTIVE`: tài khoản `ROLE_CUSTOMER` (thiếu `PRODUCT_WRITE`) hoặc bị khóa.
  - `404 CATEGORY_NOT_FOUND`: `parentId` hợp lệ UUID nhưng không tồn tại trong DB.
  - `409 DUPLICATE_CATEGORY_SLUG`: trùng slug danh mục (cả race condition ở DB).
- **Source liên quan:** [`CategoryController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/CategoryController.kt), [`CategoryDtos.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/product/CategoryDtos.kt), [`CreateCategoryUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/CreateCategoryUseCase.kt), [`Category.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/Category.kt)

---

### 4.12. Tìm Kiếm & Lọc Sản Phẩm Công Khai (Storefront)
- **Mục đích:** Danh sách sản phẩm công khai trên storefront (`ACTIVE` và `OUT_OF_STOCK`, bắt buộc thuộc category `isActive = true`). Không cho xem `DRAFT` hay `ARCHIVED`.
- **Method & URL:** `GET {{baseUrl}}/api/products`
- **URL mẫu có filter để copy:** `{{baseUrl}}/api/products?page=0&size=20&categorySlug=electronics&minPrice=10.00&maxPrice=200.00&minRating=4&status=ACTIVE&sortBy=price&sortDir=asc`
- **Headers:** `Accept: application/json` (không cần `Authorization`).
- **Path parameters:** Không có path parameters.
- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn & Giá trị hợp lệ |
|---|---|---|---|---|
| `page` | `Int` | Không | `0` | Tự clamp `>= 0`. |
| `size` | `Int` | Không | `20` | Tự clamp `1..100`. |
| `categoryId` | `String?` | Không | `null` | UUID chuẩn (`8-4-4-4-12`). **Không được truyền đồng thời với `categorySlug`** (sẽ lỗi `400`). |
| `categorySlug` | `String?` | Không | `null` | Slug danh mục (trim + lowercase). Không truyền đồng thời với `categoryId`. |
| `keyword` | `String?` | Không | `null` | Tìm không phân biệt hoa thường trên `name` hoặc `sku`. |
| `minPrice` | `BigDecimal?` | Không | `null` | `0.00..9999999999999.99`, tối đa 2 chữ số thập phân (`10.00` OK, `10.123` bị từ chối `400`), `minPrice <= maxPrice`. |
| `maxPrice` | `BigDecimal?` | Không | `null` | `0.00..9999999999999.99`, tối đa 2 chữ số thập phân. |
| `minRating` | `BigDecimal?` | Không | `null` | Khoảng `0..5`. |
| `status` | `String?` | Không | `null` | Chỉ nhận `ACTIVE` hoặc `OUT_OF_STOCK` (không phân biệt hoa thường). Truyền `ALL`, `DRAFT`, `ARCHIVED` -> `400 VALIDATION_ERROR`. Bỏ qua/`""` -> lọc cả 2 trạng thái công khai. |
| `sortBy` | `String` | Không | `"createdAt"` | Field: `createdAt` (`created_at`), `price`, `name`, `averageRating` (`average_rating`, `rating`); preset: `newest`, `oldest`, `price_asc`, `price_desc`, `name_asc`, `name_desc`, `rating_desc`, `rating_asc`; hoặc `"field,dir"`. |
| `sortDir` | `String` | Không | `"desc"` | `asc` hoặc `desc`. |

- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<ProductSummaryResponse>>`):**
  ```json
  {
    "code": "PRODUCT_LIST_SUCCESS",
    "message": "Products retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
          "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "categoryName": "Electronics",
          "name": "Wireless Mouse",
          "slug": "wireless-mouse",
          "sku": "WM-001",
          "price": 29.99,
          "stockQuantity": 50,
          "averageRating": 0.00,
          "reviewCount": 0,
          "imageUrl": null,
          "status": "ACTIVE",
          "version": 0
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: truyền cả `categoryId` lẫn `categorySlug`, `categoryId` sai UUID, `status=ALL|DRAFT|ARCHIVED`, `minPrice > maxPrice`, `minRating` ngoài `0..5`, quá 2 số lẻ thập phân ở giá.
  - `400 INVALID_PARAMETER_TYPE`: truyền chữ vào `page`/`size`/`minPrice`/`maxPrice`/`minRating`.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`ProductDtos.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/product/ProductDtos.kt), [`ListProductsUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListProductsUseCase.kt), [`CatalogQueryValidation.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/query/product/CatalogQueryValidation.kt), [`ProductQueryServiceImpl.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/query/product/ProductQueryServiceImpl.kt)

---

### 4.13. Xem Chi Tiết Sản Phẩm Công Khai (Storefront)
- **Mục đích:** Lấy chi tiết 1 sản phẩm công khai theo UUID hoặc slug, đồng thời trả header `ETag` chứa `version` hiện tại. Sản phẩm `DRAFT`, `ARCHIVED` hoặc thuộc category inactive đều bị ẩn (`404`).
- **Method & URL:** `GET {{baseUrl}}/api/products/{{productId}}` (hoặc `{{baseUrl}}/api/products/{{productSlug}}`)
- **Headers:** `Accept: application/json` (không cần `Authorization`).
- **Path parameters:**

| Tham số | Kiểu | Bắt buộc | Quy tắc |
|---|---|---|---|
| `idOrSlug` | `String` | Có | UUID hoặc slug sản phẩm (nếu chuỗi khớp UUID thì tìm theo ID trước, fallback tìm theo slug lowercase). |

- **Query parameters:** Không có query parameters.
- **Request body:** Không có request body.
- **Response headers đáng chú ý:** `ETag: "0"` (giá trị `version` của sản phẩm được bọc ngoặc kép theo chuẩn Spring `eTag`).
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "PRODUCT_DETAIL_SUCCESS",
    "message": "Product retrieved successfully.",
    "status": 200,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Wireless Mouse",
      "slug": "wireless-mouse",
      "sku": "WM-001",
      "description": "Ergonomic wireless mouse",
      "price": 29.99,
      "stockQuantity": 50,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": null,
      "status": "ACTIVE",
      "version": 0,
      "createdAt": "2026-11-16T12:00:00Z",
      "updatedAt": "2026-11-16T12:00:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `404 PRODUCT_NOT_FOUND`: sản phẩm không tồn tại, hoặc đang ở trạng thái `DRAFT`/`ARCHIVED`, hoặc thuộc category bị tắt `isActive = false`.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`GetProductDetailUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/GetProductDetailUseCase.kt), [`ProductQueryServiceImpl.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/query/product/ProductQueryServiceImpl.kt)

---

### 4.14. Tạo Sản Phẩm Mới
- **Mục đích:** Tạo sản phẩm mới thuộc 1 danh mục đã tồn tại. Yêu cầu permission `PRODUCT_WRITE` (`ROLE_STAFF` hoặc `ROLE_ADMIN`).
- **Method & URL:** `POST {{baseUrl}}/api/products`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Content-Type: application/json`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`CreateProductRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `categoryId` | `String` | Có | — | `@NotBlank`, UUID chuẩn (`8-4-4-4-12`). Danh mục phải tồn tại trong DB. |
| `name` | `String` | Có | — | `@NotBlank`, `@Size(max = 255)`. Domain trim khoảng trắng. |
| `slug` | `String?` | Không | `null` | `@Size(max = 255)`. Bỏ dấu tiếng Việt -> lowercase + `-`. Tự sinh từ `name` nếu bỏ qua/`null`/blank. |
| `sku` | `String` | Có | — | `@NotBlank`, `@Size(max = 100)`. Tự chuẩn hóa uppercase, thay khoảng trắng/ký tự lạ thành `-`, regex `^[A-Z0-9][A-Z0-9_-]*$`. |
| `description` | `String?` | Không | `null` | Tự do, domain trim và chuyển blank -> `null`. |
| `price` | `BigDecimal` | Có | — | `@NotNull`, `@ValidPrice`: từ `0.00` đến `9999999999999.99`, **tối đa 2 chữ số thập phân có nghĩa** (`29.900` OK, `29.999` -> `400 INVALID_PRICE`). |
| `stockQuantity` | `Int` | Không | `0` | `@Min(0)`. Không chấp nhận số thập phân (`ACCEPT_FLOAT_AS_INT=false`). |
| `imageUrl` | `String?` | Không | `null` | `@Size(max = 500)`. |
| `status` | `String` | Không | `"ACTIVE"` | `ACTIVE`, `DRAFT`, `OUT_OF_STOCK`, `ARCHIVED` (không phân biệt hoa thường). |

- **Hành vi bỏ qua / `null` / chuỗi rỗng `""`:**
  - `categoryId`, `name`, `sku`: bắt buộc khác blank (`""` hoặc `"   "` hoặc bỏ qua/`null` -> `400 VALIDATION_ERROR`).
  - `price`: bắt buộc, bỏ qua hoặc `null` -> `400 VALIDATION_ERROR`.
  - `slug`: bỏ qua, `null`, `""`, `"   "` -> tự sinh từ `name`.
  - `description`, `imageUrl`: bỏ qua, `null`, `""`, `"   "` -> lưu `null`.
  - `stockQuantity`, `status`: bỏ qua -> dùng mặc định (`0`, `"ACTIVE"`); nhưng truyền **`null`** rõ ràng trong JSON -> `400` (`REQUIRED_FIELD`) vì kiểu non-null; truyền `status: ""` -> `400` (`"Invalid product status ''."`).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
    "name": "Wireless Mouse",
    "slug": "wireless-mouse",
    "sku": "WM-001",
    "description": "Ergonomic wireless mouse",
    "price": 29.99,
    "stockQuantity": 50,
    "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse.jpg",
    "status": "ACTIVE"
  }
  ```
- **Response headers đáng chú ý:** `ETag: "0"`.
- **Response thành công mẫu (`201 Created`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "PRODUCT_CREATE_SUCCESS",
    "message": "Product created successfully.",
    "status": 201,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Wireless Mouse",
      "slug": "wireless-mouse",
      "sku": "WM-001",
      "description": "Ergonomic wireless mouse",
      "price": 29.99,
      "stockQuantity": 50,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse.jpg",
      "status": "ACTIVE",
      "version": 0,
      "createdAt": "2026-11-16T15:10:00Z",
      "updatedAt": "2026-11-16T15:10:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: giá vượt giới hạn / > 2 số thập phân (`INVALID_PRICE`), UUID sai (`INVALID_FORMAT`), `stockQuantity < 0` (`VALUE_OUT_OF_RANGE`), `status` không hợp lệ.
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu quyền `PRODUCT_WRITE`.
  - `404 CATEGORY_NOT_FOUND`: `categoryId` không tồn tại.
  - `409 DUPLICATE_PRODUCT_SKU` / `DUPLICATE_PRODUCT_SLUG`: trùng SKU hoặc slug sản phẩm (kể cả concurrent race).
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`ProductDtos.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/product/ProductDtos.kt), [`ValidPrice.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/validation/ValidPrice.kt), [`CreateProductUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/CreateProductUseCase.kt), [`Product.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/Product.kt)

---

### 4.15. Cập Nhật Sản Phẩm (Optimistic Concurrency)
- **Mục đích:** Cập nhật thông tin sản phẩm với kiểm soát xung đột đồng thời qua `version`. Yêu cầu `PRODUCT_WRITE`.
- **Phương thức cập nhật (`PUT`):** Đây là **cập nhật thay thế toàn bộ thông tin catalog (full replacement)**, không phải PATCH.
  - Các trường **bắt buộc** luôn phải gửi lại (`categoryId`, `name`, `sku`, `price`).
  - Các trường **tùy chọn sẽ bị reset nếu bỏ qua**:
    - Bỏ qua `description` -> **reset về `null`**.
    - Bỏ qua `imageUrl` -> **reset về `null`**.
    - Bỏ qua `stockQuantity` -> **reset về `0`**.
    - Bỏ qua `slug` -> **tự sinh lại slug mới từ `name`** (có thể làm thay đổi slug cũ nếu `name` khác slug hiện tại).
    - Riêng `status` khi bỏ qua, `null` hoặc `""` sẽ **giữ nguyên trạng thái hiện tại**.
- **Cơ chế gửi Expected Version (bắt buộc ít nhất 1 cách):**
  1. Header `If-Match`: `"0"` hoặc `0` hoặc `W/"0"` (không chấp nhận `*` hay danh sách nhiều giá trị có dấu `,`).
  2. Query parameter: `?version=0`.
  3. Trường trong JSON body: `"version": 0`.
  - *Lưu ý:* Nếu gửi nhiều hơn 1 nguồn (ví dụ cả `If-Match` và `body.version`) thì giá trị phải giống hệt nhau, nếu lệch sẽ trả `400 VALIDATION_ERROR`.
- **Method & URL:** `PUT {{baseUrl}}/api/products/{{productId}}`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Content-Type: application/json`, `Accept: application/json`, `If-Match: "{{productVersion}}"`.
- **Path parameters:**

| Tham số | Kiểu | Bắt buộc | Quy tắc |
|---|---|---|---|
| `id` | `String` | Có | UUID sản phẩm (`8-4-4-4-12`). Sai UUID -> `400 VALIDATION_ERROR`. |

- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Quy tắc |
|---|---|---|---|---|
| `version` | `Long?` | Không *(bắt buộc có version ở ít nhất 1 nguồn)* | `null` | Số nguyên `>= 0`. |

- **Request body (`UpdateProductRequest`):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `categoryId` | `String` | Có | — | `@NotBlank`, UUID chuẩn. |
| `name` | `String` | Có | — | `@NotBlank`, `@Size(max = 255)`. |
| `slug` | `String?` | Không | `null` | `@Size(max = 255)`. Bỏ qua/`null`/blank -> sinh lại từ `name`. |
| `sku` | `String` | Có | — | `@NotBlank`, `@Size(max = 100)`. |
| `description` | `String?` | Không | `null` | Bỏ qua/`null`/blank -> **reset về `null`**. |
| `price` | `BigDecimal` | Có | — | `@NotNull`, `@ValidPrice` (`0.00..9999999999999.99`, tối đa 2 số lẻ). |
| `stockQuantity` | `Int` | Không | `0` | `@Min(0)`. Bỏ qua -> **reset về `0`**; truyền `null` -> `400` (`REQUIRED_FIELD`). |
| `imageUrl` | `String?` | Không | `null` | `@Size(max = 500)`. Bỏ qua/`null`/blank -> **reset về `null`**. |
| `status` | `String?` | Không | `null` | Bỏ qua/`null`/blank -> giữ nguyên trạng thái. Cho phép chuyển đổi hợp lệ sang `ACTIVE` hoặc `OUT_OF_STOCK`. **Cấm** gửi `ARCHIVED` (phải dùng `DELETE`) hoặc `DRAFT` (`400`). Sản phẩm đang `ARCHIVED` không thể sửa trực tiếp (phải `/restore` trước). |
| `version` | `Long?` | Không *(bắt buộc có ở header/query/body)* | `null` | `@Min(0)`. |

- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
    "name": "Wireless Mouse Pro",
    "slug": "wireless-mouse-pro",
    "sku": "WM-001",
    "description": "Updated ergonomic wireless mouse",
    "price": 34.50,
    "stockQuantity": 40,
    "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse-pro.jpg",
    "status": "ACTIVE",
    "version": 0
  }
  ```
- **Response headers đáng chú ý:** `ETag: "1"` *(nếu không có thay đổi nào so với dữ liệu hiện tại, `version` và `ETag` giữ nguyên)*.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "PRODUCT_UPDATE_SUCCESS",
    "message": "Product updated successfully.",
    "status": 200,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Wireless Mouse Pro",
      "slug": "wireless-mouse-pro",
      "sku": "WM-001",
      "description": "Updated ergonomic wireless mouse",
      "price": 34.50,
      "stockQuantity": 40,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse-pro.jpg",
      "status": "ACTIVE",
      "version": 1,
      "createdAt": "2026-11-16T15:10:00Z",
      "updatedAt": "2026-11-16T15:12:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: thiếu cả `If-Match`/`version`, xung đột giá trị giữa `If-Match` và `body.version`/`?version=`, cập nhật sản phẩm đang `ARCHIVED`, gửi `status=ARCHIVED` hoặc `DRAFT`, lỗi validation trường.
  - `404 PRODUCT_NOT_FOUND` / `CATEGORY_NOT_FOUND`: không tìm thấy sản phẩm hoặc danh mục mới.
  - `409 PRODUCT_CONCURRENCY_CONFLICT`: `version` bạn gửi đã cũ so với DB (có người khác vừa cập nhật trước).
  - `409 DUPLICATE_PRODUCT_SKU` / `DUPLICATE_PRODUCT_SLUG`: trùng SKU hoặc slug với sản phẩm khác.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`ProductPresentationConverter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/converter/ProductPresentationConverter.kt), [`UpdateProductUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/UpdateProductUseCase.kt), [`Product.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/Product.kt)

---

### 4.16. Xóa Mềm Sản Phẩm (Archive)
- **Mục đích:** Chuyển sản phẩm từ `DRAFT`/`ACTIVE`/`OUT_OF_STOCK` sang `ARCHIVED` (soft delete). Nếu đã `ARCHIVED` với đúng `version`, giữ nguyên trạng thái idempotent. Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `DELETE {{baseUrl}}/api/products/{{productId}}` (hoặc `DELETE {{baseUrl}}/api/products/{{productId}}?version={{productVersion}}`)
- **Headers:** `Authorization: Bearer {{accessToken}}`, `If-Match: "{{productVersion}}"`, `Accept: application/json`.
- **Path parameters:**

| Tham số | Kiểu | Bắt buộc | Quy tắc |
|---|---|---|---|
| `id` | `String` | Có | UUID sản phẩm chuẩn (`8-4-4-4-12`). |

- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Quy tắc |
|---|---|---|---|---|
| `version` | `Long?` | Không *(bắt buộc nếu không gửi header `If-Match`)* | `null` | Số nguyên `>= 0`. |

- **Request body:** Không có request body.
- **Response thành công (`204 No Content`):**
  - Không có response body.
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: thiếu cả `If-Match` và `?version=`, hoặc sai định dạng UUID/version âm, hoặc `If-Match` khác `?version=`.
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu `PRODUCT_WRITE`.
  - `404 PRODUCT_NOT_FOUND`: sản phẩm không tồn tại.
  - `409 PRODUCT_CONCURRENCY_CONFLICT`: `version` không khớp với DB.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`DeleteProductUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/DeleteProductUseCase.kt), [`Product.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/Product.kt)

---

### 4.17. Xuất Bản Sản Phẩm (`Publish`)
- **Mục đích:** Chuyển trạng thái sản phẩm từ `DRAFT` hoặc `OUT_OF_STOCK` sang `ACTIVE`. Nếu đã `ACTIVE` (với đúng version) thì giữ nguyên (idempotent). Không thể publish sản phẩm đang `ARCHIVED` (phải gọi `/restore` trước). Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `POST {{baseUrl}}/api/products/{{productId}}/publish`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `If-Match: "{{productVersion}}"`, `Accept: application/json`.
- **Path parameters:** `id` (`String`, UUID sản phẩm, bắt buộc).
- **Query parameters:** `version` (`Long?`, `>= 0`, tùy chọn nếu đã có header `If-Match`).
- **Request body:** Không có request body.
- **Response headers đáng chú ý:** `ETag: "<newVersion>"`.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "PRODUCT_PUBLISH_SUCCESS",
    "message": "Product published successfully.",
    "status": 200,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Wireless Mouse Pro",
      "slug": "wireless-mouse-pro",
      "sku": "WM-001",
      "description": "Updated ergonomic wireless mouse",
      "price": 34.50,
      "stockQuantity": 40,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse-pro.jpg",
      "status": "ACTIVE",
      "version": 2,
      "createdAt": "2026-11-16T15:10:00Z",
      "updatedAt": "2026-11-16T15:14:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: thiếu `If-Match`/`version`, hoặc sản phẩm đang ở trạng thái `ARCHIVED`.
  - `404 PRODUCT_NOT_FOUND`: không tìm thấy sản phẩm.
  - `409 PRODUCT_CONCURRENCY_CONFLICT`: xung đột `version`.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`PublishProductUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/PublishProductUseCase.kt), [`ProductStatus.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/ProductStatus.kt)

---

### 4.18. Khôi Phục Sản Phẩm Đã Archive (`Restore`)
- **Mục đích:** Khôi phục sản phẩm đang `ARCHIVED` về trạng thái `DRAFT` (sau đó mới sửa thông tin hoặc gọi `/publish`). Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `POST {{baseUrl}}/api/products/{{productId}}/restore`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `If-Match: "{{productVersion}}"`, `Accept: application/json`.
- **Path parameters:** `id` (`String`, UUID sản phẩm, bắt buộc).
- **Query parameters:** `version` (`Long?`, `>= 0`, tùy chọn nếu đã có header `If-Match`).
- **Request body:** Không có request body.
- **Response headers đáng chú ý:** `ETag: "<newVersion>"`.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "PRODUCT_RESTORE_SUCCESS",
    "message": "Product restored successfully.",
    "status": 200,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Wireless Mouse Pro",
      "slug": "wireless-mouse-pro",
      "sku": "WM-001",
      "description": "Updated ergonomic wireless mouse",
      "price": 34.50,
      "stockQuantity": 40,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": "https://res.cloudinary.com/demo/image/upload/v1/shopflow/products/mouse-pro.jpg",
      "status": "DRAFT",
      "version": 3,
      "createdAt": "2026-11-16T15:10:00Z",
      "updatedAt": "2026-11-16T15:15:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: sản phẩm **không phải** trạng thái `ARCHIVED`, hoặc thiếu `If-Match`/`version`.
  - `404 PRODUCT_NOT_FOUND`: không tìm thấy sản phẩm.
  - `409 PRODUCT_CONCURRENCY_CONFLICT`: xung đột `version`.
- **Source liên quan:** [`ProductController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/ProductController.kt), [`RestoreProductUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/RestoreProductUseCase.kt), [`ProductStatus.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/domain/model/product/ProductStatus.kt)

---

### 4.19. Quản Trị Danh Sách Sản Phẩm (`Admin`)
- **Mục đích:** Lấy danh sách sản phẩm cho quản trị viên (xem được cả `DRAFT`, `ARCHIVED` và sản phẩm thuộc category inactive). Yêu cầu permission `PRODUCT_WRITE` (`ROLE_STAFF` hoặc `ROLE_ADMIN`).
- **Method & URL:** `GET {{baseUrl}}/api/admin/products`
- **URL mẫu có filter:** `{{baseUrl}}/api/admin/products?page=0&size=20&status=ALL&sortBy=createdAt&sortDir=desc`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Giống `GET /api/products`, **khác duy nhất ở `status`**:

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn & Giá trị hợp lệ |
|---|---|---|---|---|
| `status` | `String?` | Không | `null` | Bỏ qua, `""` hoặc **`ALL`** trả toàn bộ mọi trạng thái. Hoặc lọc cụ thể: `ACTIVE`, `DRAFT`, `OUT_OF_STOCK`, `ARCHIVED`. |
| Các tham số khác | — | Không | — | `page`, `size`, `categoryId`, `categorySlug` (không dùng đồng thời), `keyword`, `minPrice`, `maxPrice`, `minRating`, `sortBy`, `sortDir` tương tự mục 4.12. |

- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<ProductSummaryResponse>>`):**
  ```json
  {
    "code": "ADMIN_PRODUCT_LIST_SUCCESS",
    "message": "Admin products retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
          "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "categoryName": "Electronics",
          "name": "Draft Keyboard",
          "slug": "draft-keyboard",
          "sku": "DK-002",
          "price": 79.00,
          "stockQuantity": 10,
          "averageRating": 0.00,
          "reviewCount": 0,
          "imageUrl": null,
          "status": "DRAFT",
          "version": 0
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc tài khoản chỉ có `ROLE_CUSTOMER` (thiếu `PRODUCT_WRITE`).
  - `400 VALIDATION_ERROR`: `status` không thuộc (`ALL`, `ACTIVE`, `DRAFT`, `OUT_OF_STOCK`, `ARCHIVED`), hoặc lỗi range giá/rating/categoryId.
- **Source liên quan:** [`AdminCatalogController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt), [`ListAdminProductsUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListAdminProductsUseCase.kt)

---

### 4.20. Quản Trị Chi Tiết Sản Phẩm (`Admin`)
- **Mục đích:** Xem chi tiết sản phẩm bất kể trạng thái (`DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `ARCHIVED`) hoặc trạng thái active của category. Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `GET {{baseUrl}}/api/admin/products/{{productId}}` (hoặc `{{baseUrl}}/api/admin/products/{{productSlug}}`)
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** `idOrSlug` (`String`, UUID hoặc slug sản phẩm, bắt buộc).
- **Query parameters:** Không có query parameters.
- **Request body:** Không có request body.
- **Response headers đáng chú ý:** `ETag: "<version>"`.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<ProductDetailResponse>`):**
  ```json
  {
    "code": "ADMIN_PRODUCT_DETAIL_SUCCESS",
    "message": "Admin product retrieved successfully.",
    "status": 200,
    "result": {
      "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
      "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
      "categoryName": "Electronics",
      "categorySlug": "electronics",
      "name": "Draft Keyboard",
      "slug": "draft-keyboard",
      "sku": "DK-002",
      "description": null,
      "price": 79.00,
      "stockQuantity": 10,
      "averageRating": 0.00,
      "reviewCount": 0,
      "imageUrl": null,
      "status": "DRAFT",
      "version": 0,
      "createdAt": "2026-11-16T15:00:00Z",
      "updatedAt": "2026-11-16T15:00:00Z"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu `PRODUCT_WRITE`.
  - `404 PRODUCT_NOT_FOUND`: sản phẩm hoàn toàn không tồn tại trong DB.
- **Source liên quan:** [`AdminCatalogController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt), [`GetAdminProductDetailUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/GetAdminProductDetailUseCase.kt)

---

### 4.21. Quản Trị Danh Sách Danh Mục (`Admin`)
- **Mục đích:** Lấy danh sách danh mục cho quản trị viên, bao gồm cả danh mục inactive (`isActive = false`). Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `GET {{baseUrl}}/api/admin/categories`
- **URL mẫu có filter:** `{{baseUrl}}/api/admin/categories?page=0&size=20&isActive=false&sortBy=displayOrder&sortDir=asc`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn & Giá trị hợp lệ |
|---|---|---|---|---|
| `isActive` | `Boolean?` | Không | `null` | `true`: chỉ active; `false`: chỉ inactive; bỏ qua: xem tất cả. |
| `onlyActive` | `Boolean?` | Không | `null` | Tham số phụ: chỉ áp dụng khi không truyền `isActive`. `onlyActive=true` -> chỉ active; **`onlyActive=false` vẫn trả tất cả** (xem mục 1.3). |
| `page`, `size`, `parentId`, `keyword`, `sortBy`, `sortDir` | — | Không | — | Tương tự mục 4.9. |

- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<CategoryResponse>>`):**
  ```json
  {
    "code": "ADMIN_CATEGORY_LIST_SUCCESS",
    "message": "Admin categories retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "name": "Hidden Seasonal",
          "slug": "hidden-seasonal",
          "parentId": null,
          "description": "Inactive category",
          "displayOrder": 99,
          "isActive": false,
          "createdAt": "2026-11-16T12:00:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 INVALID_PARAMETER_TYPE`: truyền sai kiểu cho `isActive` / `onlyActive` / `page` / `size`.
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu `PRODUCT_WRITE`.
- **Source liên quan:** [`AdminCatalogController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt), [`ListAdminCategoriesUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListAdminCategoriesUseCase.kt), [`CategoryQueryServiceImpl.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/query/product/CategoryQueryServiceImpl.kt)

---

### 4.22. Quản Trị Sản Phẩm Theo Danh Mục (`Admin`)
- **Mục đích:** Xem toàn bộ sản phẩm thuộc 1 danh mục (kể cả khi danh mục inactive và sản phẩm ở trạng thái `DRAFT`/`ARCHIVED`). Yêu cầu `PRODUCT_WRITE`.
- **Method & URL:** `GET {{baseUrl}}/api/admin/categories/{{categoryId}}/products?status=ALL` (hoặc dùng `{{categorySlug}}`)
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** `idOrSlug` (`String`, UUID hoặc slug danh mục, bắt buộc).
- **Query parameters:** Giống mục 4.19 (`page`, `size`, `keyword`, `minPrice`, `maxPrice`, `minRating`, `status` nhận `ALL`/`DRAFT`/`ACTIVE`/`OUT_OF_STOCK`/`ARCHIVED`, `sortBy`, `sortDir`).
- **Request body:** Không có request body.
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<PageResponse<ProductSummaryResponse>>`):**
  ```json
  {
    "code": "ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS",
    "message": "Admin category products retrieved successfully.",
    "status": 200,
    "result": {
      "items": [
        {
          "id": "b62df34a-04eb-4c96-9e77-8ef920233001",
          "categoryId": "62f784a2-2af1-43be-bbde-22a1b3413911",
          "categoryName": "Electronics",
          "name": "Archived Mouse",
          "slug": "archived-mouse",
          "sku": "AM-003",
          "price": 19.99,
          "stockQuantity": 0,
          "averageRating": 0.00,
          "reviewCount": 0,
          "imageUrl": null,
          "status": "ARCHIVED",
          "version": 1
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu `PRODUCT_WRITE`.
  - `400 VALIDATION_ERROR`: `status` không hợp lệ hoặc lỗi range giá/rating.
- **Source liên quan:** [`AdminCatalogController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/product/AdminCatalogController.kt), [`ListAdminProductsUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/product/ListAdminProductsUseCase.kt)

---

### 4.23. Upload Ảnh Lên Cloudinary (`multipart/form-data`)
- **Mục đích:** Upload ảnh JPG, PNG hoặc WEBP lên Cloudinary.
  - **Phân quyền theo `folder`:** mọi tài khoản `ACTIVE` (kể cả `ROLE_CUSTOMER`) có thể upload vào `folder=avatars`; còn `folder=products` *(mặc định)* và `folder=categories` yêu cầu permission **`PRODUCT_WRITE`**.
- **Method & URL:** `POST {{baseUrl}}/api/media/upload`
- **Headers cần thiết trong Postman:**
  - `Authorization: Bearer {{accessToken}}`
  - `Accept: application/json`
  - **QUAN TRỌNG:** Ở tab **Body**, chọn **`form-data`**. **KHÔNG tự thêm header `Content-Type: multipart/form-data` hay tự điền `boundary=...`**, hãy để Postman tự sinh header kèm boundary đúng chuẩn.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters *(Spring `@RequestParam` đọc trực tiếp từ các part trong `multipart/form-data`)*.
- **Request Body (`form-data`):**

| Key | Type trong Postman | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `file` | `File` | Có | — | Dung lượng `> 0` và `<= 5 MB`. Kiểm tra magic bytes thật của file: chỉ nhận **JPG** (`FF D8 FF`), **PNG** (`89 50 4E 47 0D 0A 1A 0A`) hoặc **WEBP** (`RIFF....WEBP`). Đuôi file (nếu có) và `Content-Type` của part phải khớp định dạng thật. |
| `folder` | `Text` | Không | `products` | Chỉ chấp nhận **`avatars`**, **`products`**, **`categories`** (trim & lowercase). Nếu bỏ qua -> dùng mặc định `products` (khi đó `ROLE_CUSTOMER` sẽ bị `403 ACCESS_DENIED`). Truyền `folder: ""` -> `400 INVALID_MEDIA_FILE`. |

- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<UploadedImageResponse>`):**
  ```json
  {
    "code": "MEDIA_UPLOAD_SUCCESS",
    "message": "Image uploaded successfully.",
    "status": 200,
    "result": {
      "url": "https://res.cloudinary.com/demo/image/upload/v1700000000/shopflow/products/sample.jpg",
      "publicId": "shopflow/products/sample"
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 MISSING_MULTIPART_PART`: thiếu key `file`.
  - `400 INVALID_MEDIA_FILE`: file rỗng, sai magic bytes, giả mạo phần mở rộng (vd file `.png` ruột JPEG), sai `folder`, hoặc vượt `5 MB` ở tầng use case.
  - `413 PAYLOAD_TOO_LARGE`: file vượt ngưỡng `5MB` / `6MB` của Tomcat/Spring multipart resolver.
  - `415 UNSUPPORTED_MEDIA_TYPE`: gửi nhầm JSON/raw thay vì `multipart/form-data`.
  - `403 ACCESS_DENIED`: `ROLE_CUSTOMER` cố upload vào `products` (hoặc bỏ trống `folder`) hay `categories`.
  - `502 IMAGE_STORAGE_ERROR`: chưa cấu hình biến môi trường Cloudinary (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`) hoặc lỗi kết nối Cloudinary.
- **Source liên quan:** [`MediaController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/storage/MediaController.kt), [`UploadedImageResponse.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/storage/UploadedImageResponse.kt), [`UploadImageUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/storage/UploadImageUseCase.kt), [`CloudinaryImageStorageAdapter.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/infrastructure/storage/cloudinary/CloudinaryImageStorageAdapter.kt)

---

### 4.24. Xóa Ảnh Trên Cloudinary
- **Mục đích:** Xóa ảnh đã upload trên Cloudinary theo `publicId`. Yêu cầu permission `PRODUCT_WRITE`.
- **Method & URL:** `DELETE {{baseUrl}}/api/media?publicId={{uploadedPublicId}}`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json`.
- **Path parameters:** Không có path parameters.
- **Query parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Quy tắc & Hành vi khi rỗng |
|---|---|---|---|---|
| `publicId` | `String` | Có | — | Bỏ qua query param `publicId` -> `400 MISSING_PARAMETER`. Truyền `publicId=` hoặc toàn khoảng trắng -> `400 INVALID_MEDIA_FILE`. *(Nên URL-encode nếu có dấu `/`)* |

- **Request body:** Không có request body.
- **Response thành công (`204 No Content`):**
  - Không có response body.
- **Lỗi đáng chú ý:**
  - `400 MISSING_PARAMETER` / `INVALID_MEDIA_FILE`: thiếu hoặc rỗng `publicId`.
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc thiếu `PRODUCT_WRITE`.
  - `502 IMAGE_STORAGE_ERROR`: chưa cấu hình Cloudinary hoặc Cloudinary SDK báo lỗi.
- **Source liên quan:** [`MediaController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/storage/MediaController.kt), [`DeleteImageUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/storage/DeleteImageUseCase.kt)

---

### 4.25. Import Dataset Kaggle / CSV Mẫu (`Admin`)
- **Mục đích:** Nạp idempotent dữ liệu mẫu (`users`, `categories`, `products`, `orders`, `order_items`, `reviews`, `user_events`) từ thư mục local (`app.dataset.local-dir`, mặc định `data/ecommerce_dataset`) hoặc tải mới từ Kaggle vào MySQL. **Chỉ dành cho tài khoản có role `ROLE_ADMIN`** (`ROLE_STAFF` cũng bị chặn `403`).
- **Method & URL (2 route dùng chung 1 controller method):**
  - URL 1: `POST {{baseUrl}}/api/admin/datasets/import`
  - URL 2: `POST {{baseUrl}}/api/admin/datasets/import-kaggle`
- **Headers:** `Authorization: Bearer {{accessToken}}`, `Accept: application/json` *(và `Content-Type: application/json` nếu có gửi body JSON)*.
- **Path parameters:** Không có path parameters.
- **Query parameters:** Không có query parameters.
- **Request body (`ImportKaggleDatasetRequest?`, `required = false` — không gửi body cũng hợp lệ):**

| Trường | Kiểu | Bắt buộc | Mặc định | Quy tắc & Validation |
|---|---|---|---|---|
| `forceDownload` | `Boolean` | Không | `false` | Có tải lại zip dataset từ Kaggle API nếu có cấu hình `KAGGLE_USERNAME`/`KAGGLE_KEY` hay không. |
| `uploadCategoryPlaceholders` | `Boolean` | Không | `true` | Nếu Cloudinary đã cấu hình và category chưa có sản phẩm có ảnh, tự tạo/upload banner SVG/PNG làm ảnh đại diện. |
| `refreshExistingProductImages` | `Boolean` | Không | `true` | Cập nhật ảnh cho các sản phẩm đang null/rỗng trong DB. |
| `maxUsers` | `Int?` | Không | `null` | `@Min(0)`. `null` = import hết CSV; `0` = bỏ qua không import user; `< 0` -> `400`; số thực -> `400`. |
| `maxProducts` | `Int?` | Không | `null` | `@Min(0)`. `null` = import hết CSV; `0` = bỏ qua categories/products; `< 0` -> `400`; số thực -> `400`. |

- **Hành vi bỏ qua / `null` / body rỗng:**
  - Nếu gửi body hoàn toàn trống (chọn `none` trong Postman) hoặc `{}` -> backend dùng toàn bộ giá trị mặc định.
  - Nếu bỏ qua `forceDownload`, `uploadCategoryPlaceholders`, `refreshExistingProductImages` -> dùng giá trị mặc định tương ứng; nhưng truyền **`null`** cho 3 trường Boolean này -> `400 VALIDATION_ERROR` (`REQUIRED_FIELD`).
  - `maxUsers: null` và `maxProducts: null` -> hợp lệ (không giới hạn số dòng đọc từ CSV).
- **JSON body mẫu hợp lệ (minh họa):**
  ```json
  {
    "forceDownload": false,
    "uploadCategoryPlaceholders": true,
    "refreshExistingProductImages": true,
    "maxUsers": 100,
    "maxProducts": 200
  }
  ```
- **Response thành công mẫu (`200 OK`, minh họa khớp `ApiResponse<DatasetImportResponse>`):**
  ```json
  {
    "code": "DATASET_IMPORT_SUCCESS",
    "message": "Dataset imported successfully.",
    "status": 200,
    "result": {
      "sourceDirectory": "D:\\shopflow-backend\\shopflow-backend\\data\\ecommerce_dataset",
      "downloadedFromRemote": false,
      "cloudinaryLiveUploadUsed": false,
      "usersInserted": 100,
      "usersSkipped": 0,
      "categoriesInserted": 5,
      "categoriesExisting": 0,
      "productsInserted": 200,
      "productsSkipped": 0,
      "productsImageUpdated": 0,
      "ordersInserted": 500,
      "ordersSkipped": 0,
      "orderItemsInserted": 850,
      "orderItemsSkipped": 0,
      "reviewsInserted": 300,
      "reviewsSkipped": 0,
      "productReviewCountsUpdated": 120,
      "userEventsInserted": 1500,
      "userEventsSkipped": 0,
      "categoryImageUrls": {},
      "stagedCsvFiles": {},
      "durationMs": 2450
    }
  }
  ```
- **Lỗi đáng chú ý:**
  - `400 VALIDATION_ERROR`: `maxUsers < 0` hoặc `maxProducts < 0` (`VALUE_OUT_OF_RANGE`), hoặc JSON field sai kiểu (`TYPE_MISMATCH`).
  - `400 DATASET_IMPORT_ERROR`: thiếu thư mục CSV, thiếu `users.csv`/`products.csv`, hoặc lỗi cấu hình/tải Kaggle API khi bật `forceDownload: true`.
  - `401 UNAUTHORIZED` / `403 ACCESS_DENIED`: thiếu token hoặc user không có role **`ROLE_ADMIN`** (`ROLE_STAFF` cũng bị chặn `403`).
- **Source liên quan:** [`DatasetImportController.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/controller/dataset/DatasetImportController.kt), [`DatasetImportDtos.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/presentation/dto/dataset/DatasetImportDtos.kt), [`ImportKaggleDatasetUseCase.kt`](file:///D:/shopflow-backend/shopflow-backend/src/main/kotlin/org/shopflow/backend/application/usecase/dataset/ImportKaggleDatasetUseCase.kt)
