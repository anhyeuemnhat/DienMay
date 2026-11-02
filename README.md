# Điện Máy Đỏ — bản mã nguồn đã chỉnh sửa, schema v5

**Có mã triển khai và bằng chứng build/kiểm thử trong gói. Chưa nghiệm thu toàn bộ trên Windows và SQL Server; không coi đây là bản sẵn sàng production.**

Bản này được sửa trực tiếp từ `DienMayDo123.rar`, giữ các module `client-swing`, `backend-server`, `common-dto`, `database`, `tests`, `tools`; không thay dự án bằng mã mẫu. Các fixture mô phỏng chỉ nằm trong kiểm thử, không thay backend nghiệp vụ.

## Bắt đầu

```sh
ant clean verify
```

JDK 17+ và Apache Ant. Lệnh trên biên dịch riêng client/backend qua DTO chung, chạy **248 bài offline**, tạo ba JAR trong `dist/`; không ghi database. JDBC driver được giữ trong `backend-server/lib`. Maven POM được cập nhật bộ test mới nhưng chưa chạy Maven tại môi trường này.

Đọc `README_RUN.md` để cài đặt, `database/README.md` và `docs/CONTROLLED_OPERATIONS.md` để nâng cấp có kiểm soát. Không chạy migration production khi chưa backup, thử khôi phục và phê duyệt triển khai.

## Thay đổi thực tế

- Một `AppShell` không viền, toàn màn hình theo monitor, ba nút cửa sổ luôn tồn tại, F11/Alt+F4 và bảo vệ dữ liệu chưa lưu. Bỏ demo banner, nút Nháp chưa lưu trên toolbar và status bar; thay `message()` bằng toast có nút đóng.
- Dashboard dùng toàn chiều rộng, card trong suốt, lưới thích ứng; Kiểm chứng dữ liệu ở Hệ thống, không còn shortcut nhưng giữ quy trình/API.
- Xác nhận giá dưới vốn tại sửa sản phẩm và nhập hàng: BigDecimal, tuple giá/Revision, backend kiểm tra và audit. PendingStore giữ cả IMEI và metadata xác nhận qua khởi động lại.
- Xóa vật lý có điều kiện cho 7 loại thực thể, chỉ Manager. Nút trực tiếp tại màn hình, preview, lý do, kiểm tra lại transaction, audit và biên nhận đối soát. Không thay ARCHIVE/RESTORE bằng DELETE.
- Tab phiếu nhập đã ghi sổ: preview và đảo bằng movement bù trừ, không xóa chứng từ gốc; bảo vệ tồn/Serial/giá vốn, UUID/idempotency và đối soát bền vững.
- Migration `014` lên v5, quyền SQL giới hạn `015`, kiểm tra đối soát chỉ đọc `016`; không sửa migration lịch sử và không cấp DELETE toàn schema.

## Kết quả và giới hạn

248 bài offline đạt; tại mỗi độ phân giải 1366×768 và 1920×1080, 143 kiểm tra Swing và 19 kịch bản modal đạt. Swing chạy với HTTP fixture, không phải DB thật. **72 kịch bản SQL Server đã biên dịch nhưng chưa chạy**; quyền stored procedure, migration, transaction, rollback và tranh chấp thật cần nghiệm thu tiếp. Kết quả đầy đủ, lệnh và log ở `TEST_RESULTS.md`.

Đảo phiếu được chặn khi thiếu snapshot giá vốn hoặc có giao dịch kho về sau trên cùng sản phẩm. Các giới hạn cụ thể ở `IMPLEMENTATION_STATUS.md` và `docs/KNOWN_LIMITATIONS.md`. Không có chức năng hoàn tiền/đảo bán hàng ngoài phạm vi này.

## Tài liệu và toàn vẹn

| Nội dung | Tệp |
|---|---|
| Cài đặt và vận hành | `README_RUN.md`, `database/README.md` |
| Thay đổi, trạng thái từng yêu cầu | `CHANGELOG.md`, `IMPLEMENTATION_STATUS.md` |
| Bằng chứng và nghiệm thu còn thiếu | `TEST_RESULTS.md`, `docs/evidence/`, `docs/testing/` |
| API và an toàn giao dịch mới | `docs/architecture/API.md`, `docs/CONTROLLED_OPERATIONS.md` |
| Danh sách tệp thay đổi / loại bỏ | `DELIVERY_MANIFEST.md` |
| SHA-256 từng tệp / giấy phép | `SHA256SUMS.txt`, `THIRD_PARTY_NOTICES.md` |

Gói không chứa cache Eclipse/trình duyệt, mật khẩu SQL, token hay database khách hàng. `config/database.properties` là cấu hình đã làm sạch; người triển khai phải cấp kết nối và bí mật riêng trên máy backend.