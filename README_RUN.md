# Hướng dẫn build và chạy

## 1. Chuẩn bị

Cần full JDK 17 trở lên và Apache Ant 1.10.x. Bản bàn giao đã chạy trên
OpenJDK 21.0.12.1, target bytecode Java 17. Cần màn hình để mở Swing.
Chỉ backend cần Microsoft SQL Server và JDBC driver.

```sh
java -version
javac -version
ant -version
ant clean verify
```

Lệnh trên chạy convention, compile độc lập ba module, 130 test không cần DB và tạo JAR trong `dist/`.
Có thể dùng `scripts/verify.bat` trên Windows hoặc `sh scripts/verify.sh` trên Linux.
Bộ kiểm thử có lỗi sẽ làm build thất bại, không bỏ qua test để tạo PASS.

Maven là lựa chọn bổ sung, **chưa được chạy kiểm chứng**:
`scripts/build-maven.bat` hoặc `sh scripts/build-maven.sh` gọi `mvn verify`, tải dependency và gom JAR vào `dist/`.
Không chạy song song Ant và Maven trong cùng thư mục.

## 2. Cài SQL Server cho môi trường thử nghiệm

Có thể dùng SQL Server 2022 với bản vá đã được quản trị phê duyệt.
Trong trình cài, chọn **Database Engine Services**. Cấu hình TCP/IP trong SQL Server Configuration Manager,
chọn cổng cố định và khởi động lại dịch vụ sau khi thay đổi.
Cấu hình hiện tại dùng SQL authentication; DBA phải cho phép kiểu đăng nhập này và tạo login riêng.
Không dùng `sa` cho backend. Chỉ cho máy backend kết nối cổng DB, không mở cho desktop client.

Nguồn cài đặt chính thức được liệt kê trong `docs/REFERENCES.md`.
Phần cài DB/TLS này là hướng dẫn, **không phải log đã triển khai**.

## 3. Database mới

Trong SSMS/SQL tool của DBA:

1. Chạy `database/schema/000_create_database.sql` từ `master` để tạo `DienMay` nếu chưa có.
2. Chuyển query context sang **DienMay**, chạy `database/schema/001_schema.sql`.
3. Vẫn ở DienMay, chạy `database/migration/007_api_transition.sql` để thêm version 2.
4. DBA tạo login/database user riêng; đọc `database/security/008_runtime_role.sql` để cấp quyền.

`001_schema.sql` cố ý từ chối chạy lại trên schema đã có.
Không chọn nhầm database thật. Không xóa bảng để vượt qua lỗi.

**Database dm v1 đang có:** backup và restore sang bản thử, đối soát yêu cầu chờ,
dừng client JDBC cũ, chỉ chạy migration `007`. Xem `database/README.md`.
Dữ liệu legacy trước schema dm v1 chưa có quy trình chuyển đổi đã kiểm chứng trong gói này.

## 4. Cấu hình backend và driver

Đặt `mssql-jdbc-13.4.0.jre11.jar` từ nguồn Microsoft/Maven chính thức vào `backend-server/lib/`.
Phiên bản này khớp POM gốc; không khẳng định là mới nhất.

Sao chép `config/database.properties.example` thành `config/database.properties` **chỉ trên backend**.
Đặt đúng server, cổng, tên database và tên user. Để `db.password` trống, truyền mật khẩu bằng environment.
Ví dụ PowerShell, không ghi mật khẩu vào lịch sử lệnh:

```powershell
$credential = Get-Credential -UserName 'dienmay_runtime' -Message 'SQL Server backend login'
$env:DIENMAY_DB_USER = $credential.UserName
$env:DIENMAY_DB_PASSWORD = $credential.GetNetworkCredential().Password
$env:DIENMAY_DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=DienMay;encrypt=true;trustServerCertificate=false'
.\scripts\database-tool.bat check
.\scripts\database-tool.bat bootstrap manager
```

Bootstrap hỏi mật khẩu ẩn trên terminal và chỉ thành công khi bảng Employees trống.
Mật khẩu quản lý là mật khẩu ứng dụng, khác mật khẩu SQL login.
Chạy trong terminal thật; IDE console có thể không cung cấp `System.console()`.

Trên Bash:

```bash
export DIENMAY_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=DienMay;encrypt=true;trustServerCertificate=false'
export DIENMAY_DB_USER=dienmay_runtime
read -r -s -p 'SQL password: ' DIENMAY_DB_PASSWORD; echo
export DIENMAY_DB_PASSWORD
sh scripts/database-tool.sh check
sh scripts/database-tool.sh bootstrap manager
```

Mặc định xác minh certificate SQL Server. Cần certificate/trust chain hợp lệ.
Chỉ ở lab local cách ly mới cân nhắc `trustServerCertificate=true`; không dùng thiết lập đó cho production.
Environment vẫn có thể được tiến trình đủ quyền đọc; cần bảo vệ tài khoản dịch vụ/host.

Tùy chọn: sau bootstrap, DBA chạy `database/demo/002_demo.sql` **chỉ trong lab với catalog trống**.
Script này không tạo tài khoản hay mật khẩu mặc định.

## 5. Chạy backend và desktop

Backend cùng máy (development):

```powershell
$env:DIENMAY_BIND = '127.0.0.1'
$env:DIENMAY_PORT = '8080'
.\scripts\run-backend.bat
```

Mở terminal desktop riêng, **không sao chép environment SQL sang terminal/máy client**:

```powershell
$env:DIENMAY_API_URL = 'http://127.0.0.1:8080'
.\scripts\run-client.bat
```

Linux dùng `sh scripts/run-backend.sh` và `sh scripts/run-client.sh`.
Đăng nhập bằng tài khoản vừa bootstrap, tạo tài khoản Thu ngân/Nhân viên kho trong Hệ thống.
Tài khoản được tạo/reset mật khẩu phải đổi mật khẩu ban đầu.

**Triển khai khác máy:** backend từ chối HTTP không TLS ngoài loopback.
Cấu hình `DIENMAY_BIND`, `DIENMAY_PORT`, `DIENMAY_TLS_KEYSTORE` (PKCS12), `DIENMAY_TLS_PASSWORD`.
Certificate phải khớp hostname và được JVM client tin cậy. Client dùng `https://...`.
Không vô hiệu hostname verification. Xem `docs/architecture/DEPLOYMENT.md`.

## 6. Kiểm thử bổ sung

```sh
ant test          # 130 offline tests; no real SQL Server
ant style         # check only
ant format        # intentionally rewrites Java whitespace/import order
ant ui            # requires desktop display and window manager
ant sql-it        # prints SKIP unless deliberately enabled
```

Linux có Xvfb/Openbox: `sh scripts/ui-smoke-linux.sh` chạy cả hai độ phân giải.
Windows: chạy `ant ui` tại từng độ phân giải/scaling và lưu kết quả riêng.
Xem `docs/testing/WINDOWS_ACCEPTANCE.md`; test layout tự động không thay thế xem chữ/nút trực quan.

SQL integration chỉ trên database throwaway có tên kết thúc `_Test`, schema v1+v2, **không demo**:

```powershell
$env:DIENMAY_IT_ALLOW_WRITES = 'YES'
$env:DIENMAY_IT_DATABASE = 'DienMay_Test'
# Configure DIENMAY_DB_URL / USER / PASSWORD for EXACTLY this isolated test database.
ant sql-it
```

Runner kiểm tra `DB_NAME()` khớp tên cho phép trước khi ghi fixture.
Dữ liệu kiểm thử được giữ lại để audit; không xóa DB tự động.

## 7. IDE và khắc phục

Mở POM gốc trong IDE hỗ trợ Maven, hoặc cấu hình `build.xml` là external Ant build.
Main class: `vn.dienmay.server.ServerMain` / `vn.dienmay.client.App`.
UI mới là Java viết tay; NetBeans GUI Designer không có `.form` để mở trong gói này.

- Lỗi driver: kiểm tra JAR ở backend-server/lib, không đặt ở client.
- Lỗi schema 2: chạy migration 007 trên bản DB đúng sau backup.
- Lỗi TLS: sửa certificate/trust/hostname; không bypass xác minh để che lỗi.
- Mất kết nối khi ghi: giữ yêu cầu chờ, dùng Đối soát hoặc Thử lại cùng mã.
  Không xóa tệp pending hoặc lập hóa đơn mới khi kết quả cũ chưa rõ.
