# ShopFlow Backend — Chuẩn Response REST API (`ApiResponse`)

Tài liệu chuẩn hóa cấu trúc response thống nhất cho toàn bộ REST API của dự án **`shopflow-backend`**, phục vụ Frontend và Postman sử dụng nhất quán.
Xem thêm hướng dẫn cấu hình và script Postman tại [`docs/api/postman-guide.md`](./postman-guide.md).

---

## 1. Schema Response Chuẩn

Mọi REST API trả JSON (thành công hoặc lỗi) đều dùng chung cấu trúc `ApiResponse<T>`:

```ts
export interface ApiErrorDetail {
  field: string | null;
  code: string;
  message: string;
}

export interface ApiResponse<T> {
  code: string;
  message: string;
  status: number;
  result: T | null;
  errors?: ApiErrorDetail[]; // Chỉ xuất hiện khi lỗi
  timestamp?: string;        // ISO-8601 UTC, chỉ xuất hiện khi lỗi
}
```

### Quy tắc từng field
- `code`: Mã nghiệp vụ/hệ thống ổn định (UPPER_SNAKE_CASE). Frontend chỉ nên nhánh logic theo `code` và `status`, **không** so khớp theo `message`.
- `message`: Mô tả tiếng Anh ngắn gọn cho con người đọc/hiển thị fallback.
- `status`: Mã HTTP status thực tế (`200`, `201`, `400`, `401`, `403`, `404`, `405`, `409`, `413`, `415`, `500`, `502`). Giá trị `status` trong JSON luôn khớp tuyệt đối với HTTP status code của response.
- `result`:
  - Khi thành công (`200`, `201`): chứa toàn bộ payload kết quả của endpoint đó (object hoặc đối tượng phân trang). Không bao giờ bọc 2 lớp `result.result`.
  - Khi lỗi (`4xx`, `5xx`): luôn có giá trị `null`.
- `errors` *(chỉ có khi lỗi)*: Danh sách lỗi có cấu trúc `ApiErrorDetail[]`:
  - `field`: Tên trường/tham số gây lỗi (hoặc `null` với lỗi tổng quát / lỗi object-level không gắn vào 1 field đơn lẻ).
  - `code`: Mã chi tiết (ví dụ `REQUIRED_FIELD`, `INVALID_PRICE`, `TYPE_MISMATCH`, `INVALID_OBJECT`, hoặc mã nghiệp vụ tương ứng).
  - `message`: Thông báo chi tiết an toàn (không bao giờ trả `rejectedValue` của password, token, bí mật hay chi tiết SQL/stack trace nội bộ).
- `timestamp` *(chỉ có khi lỗi)*: Thời điểm phát sinh lỗi định dạng ISO-8601 UTC (`Instant`).

---

## 2. Các Ví Dụ Thực Tế

### 2.1. Thành công (`200 OK`) — `POST /api/auth/login`
```json
{
  "code": "AUTH_LOGIN_SUCCESS",
  "message": "Login successful.",
  "status": 200,
  "result": {
    "accessToken": "<jwt>",
    "refreshToken": "<jwt>"
  }
}
```

### 2.2. Tạo mới (`201 Created`) — `POST /api/products`
Header response đi kèm: `ETag: "0"`
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
    "description": "Ergonomic mouse",
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

### 2.3. Danh sách phân trang (`200 OK`) — `GET /api/products`
Metadata phân trang nằm trọn trong `result`:
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

### 2.4. Lỗi Validation (`400 Bad Request`)
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request data.",
  "status": 400,
  "result": null,
  "errors": [
    {
      "field": "price",
      "code": "INVALID_PRICE",
      "message": "Product price must be between 0.00 and 9999999999999.99."
    }
  ],
  "timestamp": "2026-11-16T12:05:00Z"
}
```

### 2.5. Lỗi Auth (`401 Unauthorized` & `403 Forbidden`)
**Thiếu access token (`401`):**
```json
{
  "code": "UNAUTHORIZED",
  "message": "Authentication is required to access this resource.",
  "status": 401,
  "result": null,
  "errors": [
    {
      "field": null,
      "code": "UNAUTHORIZED",
      "message": "Authentication is required to access this resource."
    }
  ],
  "timestamp": "2026-11-16T12:06:00Z"
}
```

**Access token không hợp lệ, hết hạn hoặc tài khoản đã bị xóa (`401`):**
```json
{
  "code": "INVALID_TOKEN",
  "message": "Invalid or expired access token.",
  "status": 401,
  "result": null,
  "errors": [
    {
      "field": null,
      "code": "INVALID_TOKEN",
      "message": "Invalid or expired access token."
    }
  ],
  "timestamp": "2026-11-16T12:06:10Z"
}
```

**Đã xác thực nhưng không đủ quyền (`403`):**
```json
{
  "code": "ACCESS_DENIED",
  "message": "You do not have permission to perform this action.",
  "status": 403,
  "result": null,
  "errors": [
    {
      "field": null,
      "code": "ACCESS_DENIED",
      "message": "You do not have permission to perform this action."
    }
  ],
  "timestamp": "2026-11-16T12:06:20Z"
}
```

**Tài khoản bị khóa (`INACTIVE` hoặc `BANNED`) (`403`):**
```json
{
  "code": "ACCOUNT_INACTIVE",
  "message": "User account is inactive.",
  "status": 403,
  "result": null,
  "errors": [
    {
      "field": null,
      "code": "ACCOUNT_INACTIVE",
      "message": "User account is inactive."
    }
  ],
  "timestamp": "2026-11-16T12:06:30Z"
}
```

### 2.6. Lỗi nghiệp vụ `404` / `409` và Lỗi hệ thống `500`
**Xung đột dữ liệu (`409`):**
```json
{
  "code": "DUPLICATE_PRODUCT_SKU",
  "message": "A product with that SKU already exists.",
  "status": 409,
  "result": null,
  "errors": [
    {
      "field": "sku",
      "code": "DUPLICATE_PRODUCT_SKU",
      "message": "A product with that SKU already exists."
    }
  ],
  "timestamp": "2026-11-16T12:07:00Z"
}
```

**Lỗi hệ thống chưa xử lý (`500`, không lộ SQL/stack trace):**
```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred.",
  "status": 500,
  "result": null,
  "errors": [
    {
      "field": null,
      "code": "INTERNAL_SERVER_ERROR",
      "message": "An unexpected error occurred."
    }
  ],
  "timestamp": "2026-11-16T12:08:00Z"
}
```

---

## 3. Danh Mục Mã Nghiệp Vụ Đang Triển Khai (`ApiCode`)

### 3.1. Success Codes
| Endpoint | HTTP Status | `code` | Kiểu dữ liệu trong `result` |
|---|---|---|---|
| `GET /api/status` | `200` | `SYSTEM_STATUS_SUCCESS` | `SystemStatusResponse` |
| `POST /api/auth/register` | `201` | `AUTH_REGISTER_SUCCESS` | `UserProfileResponse` |
| `POST /api/auth/login` | `200` | `AUTH_LOGIN_SUCCESS` | `AuthResponse` |
| `POST /api/auth/refresh` | `200` | `AUTH_REFRESH_SUCCESS` | `AuthResponse` |
| `GET /api/auth/me` | `200` | `AUTH_ME_SUCCESS` | `UserProfileResponse` |
| `PUT /api/auth/me/profile` | `200` | `AUTH_PROFILE_UPDATE_SUCCESS` | `UserProfileResponse` |
| `GET /api/categories` | `200` | `CATEGORY_LIST_SUCCESS` | `PageResponse<CategoryResponse>` |
| `GET /api/categories/{idOrSlug}/products` | `200` | `CATEGORY_PRODUCT_LIST_SUCCESS` | `PageResponse<ProductSummaryResponse>` |
| `POST /api/categories` | `201` | `CATEGORY_CREATE_SUCCESS` | `CategoryResponse` |
| `GET /api/products` | `200` | `PRODUCT_LIST_SUCCESS` | `PageResponse<ProductSummaryResponse>` |
| `GET /api/products/{idOrSlug}` | `200` (+`ETag`) | `PRODUCT_DETAIL_SUCCESS` | `ProductDetailResponse` |
| `POST /api/products` | `201` (+`ETag`) | `PRODUCT_CREATE_SUCCESS` | `ProductDetailResponse` |
| `PUT /api/products/{id}` | `200` (+`ETag`) | `PRODUCT_UPDATE_SUCCESS` | `ProductDetailResponse` |
| `POST /api/products/{id}/publish` | `200` (+`ETag`) | `PRODUCT_PUBLISH_SUCCESS` | `ProductDetailResponse` |
| `POST /api/products/{id}/restore` | `200` (+`ETag`) | `PRODUCT_RESTORE_SUCCESS` | `ProductDetailResponse` |
| `GET /api/admin/products` | `200` | `ADMIN_PRODUCT_LIST_SUCCESS` | `PageResponse<ProductSummaryResponse>` |
| `GET /api/admin/products/{idOrSlug}` | `200` (+`ETag`) | `ADMIN_PRODUCT_DETAIL_SUCCESS` | `ProductDetailResponse` |
| `GET /api/admin/categories` | `200` | `ADMIN_CATEGORY_LIST_SUCCESS` | `PageResponse<CategoryResponse>` |
| `GET /api/admin/categories/{idOrSlug}/products` | `200` | `ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS` | `PageResponse<ProductSummaryResponse>` |
| `POST /api/media/upload` | `200` | `MEDIA_UPLOAD_SUCCESS` | `UploadedImageResponse` |
| `POST /api/admin/datasets/import`, `/import-kaggle` | `200` | `DATASET_IMPORT_SUCCESS` | `DatasetImportResponse` |

### 3.2. Error Codes
| `code` | HTTP Status | Ngữ cảnh phát sinh |
|---|---|---|
| `VALIDATION_ERROR` | `400` | Lỗi Bean Validation (`@Valid`), domain validation (query/domain invariants), hoặc JSON field sai kiểu/null |
| `MALFORMED_JSON` | `400` | JSON body sai cú pháp hoặc không đọc được |
| `MISSING_REQUEST_BODY` | `400` | Thiếu request body bắt buộc |
| `INVALID_PARAMETER_TYPE` | `400` | Query/path parameter sai kiểu dữ liệu |
| `MISSING_PARAMETER` | `400` | Thiếu query parameter hoặc path variable bắt buộc |
| `MISSING_MULTIPART_PART` | `400` | Thiếu multipart part (ví dụ thiếu `file`) |
| `INVALID_MEDIA_FILE` | `400` | File ảnh upload sai định dạng, rỗng hoặc không hợp lệ |
| `DATASET_IMPORT_ERROR` | `400` | Lỗi cấu hình/nguồn dữ liệu khi import dataset |
| `APPLICATION_ERROR` | `400` | Lỗi nghiệp vụ tầng application |
| `UNAUTHORIZED` | `401` | Thiếu thông tin xác thực khi gọi API yêu cầu đăng nhập |
| `INVALID_CREDENTIALS` | `401` | Sai email hoặc mật khẩu khi đăng nhập |
| `INVALID_TOKEN` | `401` | Access/refresh token sai chữ ký, hết hạn, bị thu hồi hoặc user không còn tồn tại |
| `ACCOUNT_INACTIVE` | `403` | Tài khoản ở trạng thái `INACTIVE` hoặc `BANNED` |
| `ACCESS_DENIED` | `403` | Tài khoản `ACTIVE` nhưng không có role/permission cần thiết |
| `USER_NOT_FOUND` | `404` | Không tìm thấy user |
| `ROLE_NOT_FOUND` | `404` | Không tìm thấy role |
| `PERMISSION_NOT_FOUND` | `404` | Không tìm thấy permission |
| `CATEGORY_NOT_FOUND` | `404` | Không tìm thấy category |
| `PRODUCT_NOT_FOUND` | `404` | Không tìm thấy product (hoặc ẩn trên storefront public) |
| `ENDPOINT_NOT_FOUND` | `404` | Không tìm thấy URL endpoint trong hệ thống |
| `METHOD_NOT_ALLOWED` | `405` | Sai HTTP method (trả kèm header `Allow`) |
| `USER_ALREADY_EXISTS` | `409` | Trùng email khi đăng ký |
| `DUPLICATE_CATEGORY_SLUG` | `409` | Trùng slug category |
| `DUPLICATE_PRODUCT_SKU` | `409` | Trùng SKU product |
| `DUPLICATE_PRODUCT_SLUG` | `409` | Trùng slug product |
| `PRODUCT_CONCURRENCY_CONFLICT` | `409` | Xung đột `version`/`If-Match` khi cập nhật, publish, restore hoặc xóa product |
| `PAYLOAD_TOO_LARGE` | `413` | File upload vượt quá giới hạn tối đa (`5 MB`) |
| `UNSUPPORTED_MEDIA_TYPE` | `415` | `Content-Type` không được hỗ trợ |
| `INTERNAL_SERVER_ERROR` | `500` | Lỗi hệ thống/CSDL chưa xử lý (không lộ chi tiết nội bộ) |
| `IMAGE_STORAGE_ERROR` | `502` | Lỗi kết nối nhà cung cấp lưu trữ ảnh (Cloudinary) |

### 3.3. Detail Error Codes (trong `errors[].code`)
- `REQUIRED_FIELD`, `INVALID_EMAIL`, `INVALID_PASSWORD`, `INVALID_PRICE`, `INVALID_FORMAT`, `INVALID_LENGTH`, `VALUE_OUT_OF_RANGE`, `TYPE_MISMATCH`, `INVALID_VALUE`, `INVALID_OBJECT`, `FILE_TOO_LARGE`, hoặc các mã nghiệp vụ tương ứng (`USER_ALREADY_EXISTS`, `DUPLICATE_PRODUCT_SKU`, ...).

---

## 4. Các Trường Hợp Ngoại Lệ Không Bọc `ApiResponse` JSON

1. **HTTP `204 No Content`** (`POST /api/auth/logout`, `DELETE /api/products/{id}`, `DELETE /api/media`): response hoàn toàn **rỗng** (không có JSON body).
2. **`GET /`**: trả `text/plain` (`"Everything cool!"`) phục vụ health-check đơn giản ở root.
3. **Content Negotiation không hỗ trợ JSON (`406 Not Acceptable` hoặc request có `Accept` không tương thích `application/json`, ví dụ `Accept: application/xml`):** giữ nguyên HTTP status phù hợp (như `406`, `405` + `Allow`, `401`) và **không ép** trả JSON body.
4. **Swagger / OpenAPI & tài nguyên tĩnh / binary / streaming** (`/v3/api-docs/**`, `/swagger-ui/**`): không bị bọc envelope.

---

## 5. Hướng Dẫn Chuyển Từ Format Cũ Sang Format Mới (Frontend)

| Dữ liệu | Đường dẫn JSON cũ | Đường dẫn JSON mới |
|---|---|---|
| Access token | `data.accessToken` | `data.result.accessToken` |
| Refresh token | `data.refreshToken` | `data.result.refreshToken` |
| User profile (`/register`, `/me`, `/me/profile`) | `data.id`, `data.email`, `data.roles` | `data.result.id`, `data.result.email`, `data.result.roles` |
| Danh sách phân trang (`products`, `categories`) | `data.items`, `data.totalElements` | `data.result.items`, `data.result.totalElements` |
| Chi tiết/tạo/cập nhật product & category | `data.id`, `data.version` | `data.result.id`, `data.result.version` (+ header `ETag`) |
| Mã lỗi nghiệp vụ | `data.error` / `data.code` | `data.code` (`data.result === null`) |
| Chi tiết lỗi từng trường | `data.errors` (`string[]`) | `data.errors` (`Array<{ field, code, message }>`) |

### Ví dụ unwrap trong TypeScript (Frontend)
```ts
export async function unwrapApiResponse<T>(res: Response): Promise<T> {
  if (res.status === 204) {
    return undefined as T;
  }
  const payload = (await res.json()) as ApiResponse<T>;
  if (!res.ok || payload.result === null) {
    throw payload; // Kiểm tra payload.code và payload.errors trên UI
  }
  return payload.result;
}
```
