# ShopFlow Backend — Hướng Dẫn Postman

Hướng dẫn sử dụng Postman với **`shopflow-backend`**. Toàn bộ endpoint JSON tuân thủ schema `ApiResponse<T>` định nghĩa tại [`docs/api/response-contract.md`](./response-contract.md).

---

## 1. Biến Environment Đề Xuất

| Biến | Giá trị mẫu local | Mô tả |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | URL gốc của backend |
| `accessToken` | *(tự động điền từ script login/refresh)* | JWT Access Token (TTL 15 phút) |
| `refreshToken` | *(tự động điền từ script register/login/refresh)* | JWT Refresh Token |
| `categoryId` | *(tự động điền sau `POST /api/categories`)* | UUID danh mục vừa tạo |
| `productId` | *(tự động điền sau `POST /api/products`)* | UUID sản phẩm vừa tạo |
| `productVersion` | `0` | Version lạc quan cho `If-Match` / `PUT` / `DELETE` |

> **Auth ở cấp Collection:** Chọn **Bearer Token** với giá trị `{{accessToken}}` cho các API yêu cầu xác thực.

---

## 2. Danh Sách Endpoint Thực Tế

### 2.1. System Health
| Method | Path | Auth | Status thành công | Ghi chú |
|---|---|---|---|---|
| `GET` | `/` | Public | `200` | Trả `text/plain` (`Everything cool!`), không bọc JSON |
| `GET` | `/api/status` | Public | `200` (`SYSTEM_STATUS_SUCCESS`) | Thông tin trạng thái hệ thống trong `result` |

### 2.2. Authentication (`/api/auth`)
| Method | Path | Auth | Status thành công | Ghi chú |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | `201` (`AUTH_REGISTER_SUCCESS`) | Trả `UserProfileResponse` trong `result` |
| `POST` | `/api/auth/login` | Public | `200` (`AUTH_LOGIN_SUCCESS`) | Trả `{ accessToken, refreshToken }` trong `result` |
| `POST` | `/api/auth/refresh` | Public | `200` (`AUTH_REFRESH_SUCCESS`) | Xoay vòng refresh token, trả cặp token mới trong `result` |
| `POST` | `/api/auth/logout` | Public | `204` | **Body rỗng**, thu hồi refresh token trong DB |
| `GET` | `/api/auth/me` | Bearer | `200` (`AUTH_ME_SUCCESS`) | Lấy thông tin & quyền hiện hành của user |
| `PUT` | `/api/auth/me/profile` | Bearer | `200` (`AUTH_PROFILE_UPDATE_SUCCESS`) | Cập nhật hồ sơ user |

### 2.3. Storefront Catalog (`/api/categories`, `/api/products`)
| Method | Path | Auth | Status thành công | Ghi chú |
|---|---|---|---|---|
| `GET` | `/api/categories` | Public | `200` (`CATEGORY_LIST_SUCCESS`) | Chỉ trả category active, phân trang trong `result` |
| `GET` | `/api/categories/{idOrSlug}/products` | Public | `200` (`CATEGORY_PRODUCT_LIST_SUCCESS`) | Chỉ trả `ACTIVE` & `OUT_OF_STOCK` thuộc category active |
| `POST` | `/api/categories` | Bearer (`PRODUCT_WRITE`) | `201` (`CATEGORY_CREATE_SUCCESS`) | Tạo danh mục mới |
| `GET` | `/api/products` | Public | `200` (`PRODUCT_LIST_SUCCESS`) | Storefront list phân trang trong `result` |
| `GET` | `/api/products/{idOrSlug}` | Public | `200` (`PRODUCT_DETAIL_SUCCESS`) | Kèm header `ETag` (`"<version>"`) |
| `POST` | `/api/products` | Bearer (`PRODUCT_WRITE`) | `201` (`PRODUCT_CREATE_SUCCESS`) | Kèm header `ETag` (`"<version>"`) |
| `PUT` | `/api/products/{id}` | Bearer (`PRODUCT_WRITE`) | `200` (`PRODUCT_UPDATE_SUCCESS`) | Yêu cầu `If-Match`, `?version=` hoặc `body.version` |
| `DELETE` | `/api/products/{id}` | Bearer (`PRODUCT_WRITE`) | `204` | Soft-delete sang `ARCHIVED`; yêu cầu `If-Match` hoặc `?version=`; **body rỗng** |
| `POST` | `/api/products/{id}/publish` | Bearer (`PRODUCT_WRITE`) | `200` (`PRODUCT_PUBLISH_SUCCESS`) | Chuyển `DRAFT`/`OUT_OF_STOCK` -> `ACTIVE`; yêu cầu `If-Match` hoặc `?version=` |
| `POST` | `/api/products/{id}/restore` | Bearer (`PRODUCT_WRITE`) | `200` (`PRODUCT_RESTORE_SUCCESS`) | Chuyển `ARCHIVED` -> `DRAFT`; yêu cầu `If-Match` hoặc `?version=` |

### 2.4. Admin Catalog (`/api/admin`)
| Method | Path | Auth | Status thành công | Ghi chú |
|---|---|---|---|---|
| `GET` | `/api/admin/products` | Bearer (`PRODUCT_WRITE`) | `200` (`ADMIN_PRODUCT_LIST_SUCCESS`) | Xem được mọi trạng thái (`DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `ARCHIVED`) |
| `GET` | `/api/admin/products/{idOrSlug}` | Bearer (`PRODUCT_WRITE`) | `200` (`ADMIN_PRODUCT_DETAIL_SUCCESS`) | Kèm header `ETag` |
| `GET` | `/api/admin/categories` | Bearer (`PRODUCT_WRITE`) | `200` (`ADMIN_CATEGORY_LIST_SUCCESS`) | Lọc được cả category inactive (`?isActive=`) |
| `GET` | `/api/admin/categories/{idOrSlug}/products` | Bearer (`PRODUCT_WRITE`) | `200` (`ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS`) | Xem tất cả sản phẩm theo category |

### 2.5. Media & Admin Dataset (`/api/media`, `/api/admin/datasets`)
| Method | Path | Auth | Status thành công | Ghi chú |
|---|---|---|---|---|
| `POST` | `/api/media/upload` | Bearer (`isAuthenticated`, riêng `products`/`categories` cần `PRODUCT_WRITE`) | `200` (`MEDIA_UPLOAD_SUCCESS`) | `multipart/form-data` với field `file` và `folder` |
| `DELETE` | `/api/media?publicId=...` | Bearer (`PRODUCT_WRITE`) | `204` | **Body rỗng** |
| `POST` | `/api/admin/datasets/import`, `/import-kaggle` | Bearer (`ROLE_ADMIN`) | `200` (`DATASET_IMPORT_SUCCESS`) | Import bộ CSV mẫu vào MySQL |

---

## 3. Postman Post-response Scripts (Tab *Scripts -> Post-response*)

> **Lưu ý khi nâng cấp:** Các script cũ dùng `pm.response.json().accessToken` hoặc `pm.response.json().id` sẽ trả về `undefined`. Luôn đọc dữ liệu từ `json.result`.

### 3.1. `POST /api/auth/login` và `POST /api/auth/refresh`
```javascript
const json = pm.response.json();

pm.test("Status matches HTTP status", function () {
    pm.response.to.have.status(200);
    pm.expect(json.status).to.eql(pm.response.code);
    pm.expect(json.result).to.be.an("object");
});

if (json.result && json.result.accessToken) {
    pm.environment.set("accessToken", json.result.accessToken);
}
if (json.result && json.result.refreshToken) {
    pm.environment.set("refreshToken", json.result.refreshToken);
}
```

### 3.2. `POST /api/categories`
```javascript
const json = pm.response.json();

pm.test("Category created with envelope", function () {
    pm.response.to.have.status(201);
    pm.expect(json.code).to.eql("CATEGORY_CREATE_SUCCESS");
    pm.expect(json.status).to.eql(201);
    pm.expect(json.result.id).to.be.a("string");
});

if (json.result && json.result.id) {
    pm.environment.set("categoryId", json.result.id);
}
```

### 3.3. `POST /api/products`, `PUT /api/products/{id}`, `POST /api/products/{id}/publish|restore`
```javascript
const json = pm.response.json();

pm.test("Product envelope and version available", function () {
    pm.expect(json.status).to.eql(pm.response.code);
    pm.expect(json.result.id).to.be.a("string");
    pm.expect(json.result.version).to.be.a("number");
});

if (json.result) {
    pm.environment.set("productId", json.result.id);
    pm.environment.set("productVersion", String(json.result.version));
}
```

### 3.4. `GET /api/products` (Phân trang)
```javascript
const json = pm.response.json();

pm.test("Paged result matches contract", function () {
    pm.response.to.have.status(200);
    pm.expect(json.code).to.eql("PRODUCT_LIST_SUCCESS");
    pm.expect(json.result.items).to.be.an("array");
    pm.expect(json.result.page).to.be.a("number");
    pm.expect(json.result.totalElements).to.be.a("number");
});
```

### 3.5. Endpoint `204 No Content` (`POST /api/auth/logout`, `DELETE /api/products/{id}`, `DELETE /api/media`)
```javascript
pm.test("204 response has empty body", function () {
    pm.response.to.have.status(204);
    pm.expect(pm.response.text()).to.eql("");
});
```
> Không gọi `pm.response.json()` trên response `204` vì body rỗng sẽ gây lỗi parse JSON trong Postman.
