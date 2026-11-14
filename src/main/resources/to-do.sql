Sau khi hoàn thành **Product Catalog** (và đã có nền tảng `Auth`, `RBAC`, `BaseJpaEntity` Auditing, `Cloudinary`), để `ShopFlow` thực sự trở thành một hệ thống E-Commerce chuẩn **Production** (vừa đủ tính năng người dùng, vừa ăn điểm tuyệt đối khi đi phỏng vấn Backend), bạn cần hoàn thiện theo **2 lớp**: **Lớp Chức năng nghiệp vụ (Business Modules)** và **Lớp Kỹ thuật chuyên sâu (Concurrency & Resilience Patterns)** mà bạn vừa nhắc tới (`Idempotency`, `Pessimistic Lock`, `Optimistic Lock`, `Redis Distributed Lock`, `Outbox/Event-driven`).

---

## 1. Bản đồ các chức năng còn lại (Sắp xếp theo thứ tự triển khai tối ưu)

Dưới đây là lộ trình 6 module tiếp theo gắn trực tiếp với từng "vũ khí kỹ thuật" trong hệ thống của bạn:

### Module 2: Shopping Cart & Wishlist (Giỏ hàng & Danh sách yêu thích)

* **Chức năng:** Thêm/sửa số lượng/xóa sản phẩm trong giỏ (`Cart` & `CartItem`), chọn sản phẩm để checkout, lưu danh sách yêu thích (`Wishlist`), tự động đồng bộ giá và kiểm tra tồn kho thực tế khi mở giỏ hàng.
* **Kỹ thuật áp dụng:**
* **Aggregate Pattern (DDD):** `Cart` là Aggregate Root quản lý toàn bộ `CartItem`, tự tính tổng tiền bằng `BigDecimal` và chặn thêm sản phẩm vượt quá `stockQuantity` hoặc sản phẩm đang ở trạng thái `DRAFT`/`ARCHIVED`.
* **Redis Cache (Tùy chọn):** Cache giỏ hàng theo `userId` để giảm tải truy vấn MySQL.



### Module 3: Order Management & Inventory Control (Đặt hàng & Quản lý Tồn kho)

* **Chức năng:** Chốt đơn từ giỏ hàng (`PlaceOrderCommand`), quản lý địa chỉ giao hàng, tính phí vận chuyển/mã giảm giá, theo dõi vòng đời đơn hàng (`PENDING_PAYMENT` $\rightarrow$ `PAID` $\rightarrow$ `PROCESSING` $\rightarrow$ `SHIPPING` $\rightarrow$ `DELIVERED` / `CANCELLED`), và hoàn kho tự động khi hủy đơn hoặc quá hạn thanh toán.
* **Kỹ thuật chuyên sâu bắt buộc có ở module này:**
* **Pessimistic Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)` — `SELECT ... FOR UPDATE`):** Khi khách bấm đặt hàng, khóa dòng sản phẩm (`ProductJpaEntity`) trong transaction MySQL trước khi trừ `stockQuantity` để **chống âm kho (Overselling)** khi 2 người mua chiếc cuối cùng cùng lúc.
* **Optimistic Locking (`@Version`):** Gắn cột `@Version` lên `OrderJpaEntity` và `ProductJpaEntity` để chống xung đột cập nhật trạng thái (ví dụ: Admin đang bấm "Xác nhận giao hàng" cùng mili-giây với khách bấm "Hủy đơn").
* **Idempotency Key (Chống tạo trùng đơn hàng):** Client gửi kèm header `Idempotency-Key` (UUID) khi gọi `POST /api/orders`; Backend lưu key này vào Redis/MySQL trong 24h — nếu mạng lag khiến khách bấm "Đặt hàng" 2 lần, hệ thống trả về đúng đơn hàng đã tạo trước đó thay vì trừ tiền/trừ kho 2 lần.



### Module 4: Payment Processing (Thanh toán VietQR payOS / VNPAY)



* **Chức năng:** Tạo link thanh toán / mã VietQR động cho đơn hàng (`POST /api/payments/create`), nhận thông báo tự động từ cổng thanh toán (`POST /api/payments/webhook`), đối soát giao dịch và chuyển trạng thái đơn sang `PAID`.


* **Kỹ thuật chuyên sâu bắt buộc có ở module này:**
* **Anti-Corruption Layer (ACL):** Cô lập SDK `payos-java` (hoặc thuật toán ký HMAC-SHA512 của VNPAY) hoàn toàn ở `infrastructure/api/payment/`.


* **Webhook Idempotency & Signature Verification:** Xác thực chữ ký checksum chống giả mạo request, đồng thời kiểm tra trạng thái giao dịch (`payment_transactions` table với `provider_transaction_id` UNIQUE) để đảm bảo nếu payOS/VNPAY gửi lại Webhook 3–5 lần thì đơn hàng chỉ được xử lý **đúng 1 lần**.





### Module 5: Flash-Sale Engine (Săn Sale tốc độ cao)

* **Chức năng:** Tạo chiến dịch Flash-sale theo khung giờ (`startTime`, `endTime`), giới hạn số lượng bán giá sốc và giới hạn mỗi user chỉ được mua tối đa N sản phẩm.
* **Kỹ thuật chuyên sâu bắt buộc có ở module này:**
* **Redis Atomic Operation (Lua Script / `DECR`) & Distributed Lock:** Đưa số lượng tồn kho Flash-sale lên RAM (Redis) trước giờ G; dùng Lua Script nguyên tử để kiểm tra khung giờ + kiểm tra giới hạn mỗi user + trừ kho trên Redis trong vài mili-giây, sau đó mới đẩy sang tạo đơn dưới MySQL (tránh sập MySQL khi hàng ngàn request ập vào cùng giây).





### Module 6: Real-time Notification System (Thông báo tức thì qua WebSocket)



* **Chức năng:**
* Gửi thông báo cá nhân tới từng User qua WebSocket (`/user/queue/notifications`) ngay khi thanh toán QR thành công hoặc đơn hàng đổi trạng thái.


* Gửi thông báo broadcast tới kênh quản trị (`/topic/admin/orders`) khi có đơn hàng mới.


* Lưu lịch sử thông báo vào DB (`notifications` table) để user xem lại danh sách thông báo đã đọc / chưa đọc.


* **Kỹ thuật áp dụng:**
* **Spring Application Events (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`):** Chỉ bắn thông báo WebSocket sau khi Transaction lưu đơn hàng/thanh toán đã commit thành công xuống MySQL, tránh tình trạng báo "Thanh toán thành công" nhưng DB lại bị rollback.



### Module 7: Reviews, User Profile nâng cao & Analytics Dashboard

* **Chức năng:**
* **Reviews & Ratings:** Cho phép khách hàng đã mua hàng thành công (`DELIVERED`) đánh giá 1–5 sao kèm bình luận và ảnh thực tế (Cloudinary), tự động tính lại `averageRating` và `reviewCount` của sản phẩm (khớp với `reviews.csv` trong dataset của bạn).
* **User Address Book:** Quản lý nhiều địa chỉ giao hàng (`user_addresses` với cờ `isDefault`), đổi mật khẩu (`ChangePasswordCommand`), quên mật khẩu qua OTP/Email.
* **Event Tracking & Admin Dashboard:** Ghi nhận hành vi người dùng (`events.csv`: `VIEW_PRODUCT`, `ADD_TO_CART`, `WISHLIST`, `PURCHASE`) và API thống kê doanh thu theo ngày/tháng, top sản phẩm bán chạy cho Admin (`CQRS Read Side`).



---

## 2. Bảng tổng hợp các kỹ thuật "Ăn điểm phỏng vấn" trong ShopFlow

| Kỹ thuật / Pattern | Áp dụng ở Module nào? | Giải quyết bài toán thực tế gì? |
| --- | --- | --- |
| **Pessimistic Lock** (`FOR UPDATE`) | `Order` & `Inventory` | Chống âm kho khi nhiều người mua cùng lúc trên MySQL. |
| **Optimistic Lock** (`@Version`) | `Order` & `Product` | Tránh ghi đè dữ liệu (Lost Update) khi 2 request cùng sửa 1 bản ghi. |
| **Idempotency Key** | `Order Creation` & `Payment Webhook` | Chống tạo trùng đơn khi mạng lag (client retry) và chống xử lý trùng Webhook thanh toán.

 |
| **Redis Lua Script / Atomic DECR** | `Flash-Sale` | Xử lý hàng ngàn lượt săn sale/giây trên RAM mà không làm nghẽn Connection Pool của MySQL.

 |
| **Transactional Event + WebSocket** | `Notification` | Đảm bảo thông báo real-time chỉ gửi đi sau khi dữ liệu DB đã lưu an toàn.

 |
| **Scheduled Job (Cron) + DLQ/Retry** | `Order` & `Payment` | Tự động hủy các đơn `PENDING_PAYMENT` quá 15 phút chưa quét QR và hoàn trả lại tồn kho. |

---

## 3. Bước tiếp theo ngay sau khi xong `Product Catalog`

Ngay khi Antigravity hoàn thành `Product Catalog` và `.\gradlew.bat check` báo xanh 100%, bạn chỉ cần dùng đúng quy trình Harness (`PLAN.md` $\rightarrow$ Implement $\rightarrow$ Verify) để triển khai tiếp **Module 2: `Shopping Cart & Wishlist**`, sau đó đến **Module 3: `Order & Inventory` (tích hợp sẵn `Idempotency-Key` + `Pessimistic Lock` + `Optimistic Lock`)**!