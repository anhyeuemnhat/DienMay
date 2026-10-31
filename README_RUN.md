# Cài đặt, build và chạy

## 1. Chuẩn bị

Giải nén toàn bộ thư mục `DienMayDo/`. Không chỉ sao chép một JAR.
Cài JDK 17 trở lên, Apache Ant, Microsoft SQL Server và công cụ DBA (ví dụ SSMS).
Mã được biên dịch với `--release 17`; môi trường đã chạy là JDK 21 trên Linux.
Windows và SQL Server chưa được chạy trong đợt này.

Driver JDBC `13.6.0.jre11` đã có ở `backend-server/lib/`, chỉ backend cần driver.
Tên `jre11` là biến thể Java 11 trở lên, không bắt buộc chạy Java 11.
Nguồn Microsoft và giấy phép: `docs/REFERENCES.md`, `THIRD_PARTY_NOTICES.md`.
POM Maven được giữ để tích hợp IDE; **Maven chưa được chạy kiểm chứng**. Ant là luồng build đã chạy.

## 2. Build và kiểm thử không cần DB

Mở terminal tại thư mục gốc:

```sh
java -version
ant -version
ant clean verify
```

Lệnh trên kiểm tra quy ước mã nguồn, biên dịch ba module,
chạy 4 bộ kiểm thử và tạo `dist/common-dto.jar`, `dist/backend-server.jar`,
`dist/client-swing.jar`. Nó **không** khởi tạo hay ghi vào SQL Server.

## 3. Thiết lập database trên máy backend

Làm theo `database/README.md` để tạo database DEMO riêng hoặc migration bản sao database cũ.
Tạo tài khoản quản lý ban đầu bằng lệnh `bootstrap` trong terminal tương tác;
mật khẩu 12-256 ký tự do người cài đặt tự chọn, không hiện trên màn hình.

Sao chép `config/database.properties.example` thành `config/database.properties` và sửa URL/tài khoản
**chỉ trên backend**. Ưu tiên cấp mật khẩu bằng biến môi trường `DIENMAY_DB_PASSWORD`,
không ghi mật khẩu thật vào kho mã hay log. Ba biến `DIENMAY_DB_URL`, `DIENMAY_DB_USER`,
`DIENMAY_DB_PASSWORD` ghi đè cấu hình file. `DIENMAY_CONFIG` chỉ định file khác.

Ví dụ URL (không chứa bí mật):

```text
jdbc:sqlserver://localhost:1433;databaseName=DienMayDo_Demo;encrypt=true;trustServerCertificate=false
```

Chứng chỉ SQL Server cần được JDK tin cậy và đúng tên máy chủ.
`trustServerCertificate=true` bỏ qua xác thực chứng chỉ; chỉ cân nhắc khi thử nghiệm cục bộ
với chứng chỉ tự ký. Không chuyển cấu hình này sang vận hành thật.

## 4. Chạy backend và giao diện

Linux:

```sh
sh scripts/database-tool.sh check
sh scripts/run-backend.sh
# Terminal khác:
sh scripts/run-client.sh
```

Windows (Command Prompt):

```bat
scripts\database-tool.bat check
scripts\run-backend.bat
REM Mo terminal khac:
scripts\run-client.bat
```

Backend mặc định `127.0.0.1:8080`. Giao diện mặc định `http://127.0.0.1:8080`;
đổi bằng `DIENMAY_API_URL`. `DIENMAY_BIND` và `DIENMAY_PORT` đổi địa chỉ backend.
Khi chạy qua mạng, cần HTTPS và tường lửa; xem `docs/architecture/DEPLOYMENT.md`.
Không mở SQL Server cho máy thu ngân; client không có tài khoản SQL.

Đăng nhập bằng tài khoản vừa bootstrap. Quản lý tạo nhân viên kho / thu ngân
trong màn hình Nhân viên; tài khoản mới phải đổi mật khẩu khi đăng nhập.
Mọi màn hình nằm trong cùng một cửa sổ. Quay lại không xóa bản nháp;
Tiến / Nháp chưa lưu giúp mở lại. Đăng xuất / thoát sẽ cảnh báo bản nháp.
Bản nháp form thông thường chỉ giữ trong phiên; yêu cầu giao dịch đang chờ đối soát
được lưu riêng trong hồ sơ người dùng.

## 5. Kiểm thử giao diện và SQL

Linux có Xvfb, Openbox và `timeout`:

```sh
sh scripts/ui-smoke-linux.sh
# Thêm kiểm tra JAR khởi động từ thư mục khác:
sh scripts/verify-red-ui-linux.sh
```

Đây là kiểm thử Swing thật với HTTP mô phỏng. Để kiểm thử SQL thật,
chỉ dùng database `_Test` riêng theo `docs/testing/SQL_SERVER_ACCEPTANCE.md`.
Bộ SQL test có ghi dữ liệu mô phỏng và cần opt-in rõ ràng; không dùng database thật.

## 6. Khi có lỗi

Lỗi schema: kiểm tra `dm.SchemaVersion` đã có v4, không chỉ v1/v2.
Lỗi chưa phân loại: xem quy trình `classify-production`, không sửa tên DEMO thành thật.
Lỗi chưa xác minh: quản lý kiểm tra nguồn / model / thương hiệu trong Xác minh dữ liệu.
Mất mạng sau khi gửi thanh toán: giữ nguyên yêu cầu và dùng Đối soát; không tạo yêu cầu mới để thử lại.
Chỉ gửi mã hỗ trợ / thông báo đã che thông tin nhạy cảm khi nhờ hỗ trợ.
