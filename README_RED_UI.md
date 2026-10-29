# Điện Máy Đỏ — Bản cập nhật giao diện Swing

**Ngày bàn giao: 30/10/2026.** Đây là dự án gốc đã được sửa trực tiếp mã Java,
không phải bản minh họa. Đọc tệp này trước; tài liệu gốc vẫn được giữ để tham khảo
về hệ thống, database và triển khai.

## Nội dung chính

Ảnh gốc được giữ nguyên byte và tích hợp tại
`client-swing/src/main/resources/images/red_mascot_background.png`.
Mọi cửa sổ ứng dụng dùng chung nền ảnh co giãn kiểu cover, ưu tiên nhân vật bên trái.
Các form sáng, bảng dữ liệu đục và thanh tiêu đề đỏ đậm được tách khỏi lớp ảnh nền.

Tiêu đề nhận diện mới, chính xác:

**PHẦN MỀM QUẢN LÍ KINH DOANH NỘI BỘ CỦA CỬA HÀNG ĐIỆN MÁY ĐỎ**

Tên chức năng như Đăng nhập, Sản phẩm, Nhân viên, Lưu, Hủy và các nhãn dữ liệu
vẫn được giữ. Không thay đổi API, database, nguồn xử lý nghiệp vụ hay phân quyền.
Đối chiếu SHA-256 xác nhận 54 tệp lõi được kiểm tra không thay đổi.

## Chạy ngay trên Windows

Cần Java/JDK 17 trở lên. Giữ các JAR trong `dist/` cạnh nhau, đặc biệt
`client-swing.jar` và `common-dto.jar`. Bản bàn giao đã có JAR biên dịch mới,
không cần Maven để chạy.

**Khi backend của bạn đã hoạt động:** mở PowerShell tại thư mục dự án và chạy:

```powershell
$env:DIENMAY_API_URL = 'http://127.0.0.1:8080'
.\scripts\run-client.bat
```

Thay địa chỉ bằng API đang sử dụng; cấu hình HTTPS/triển khai khác máy giữ nguyên
như dự án gốc. Không gửi thông tin đăng nhập SQL Server xuống client.

**Khi cần khởi động backend cùng máy:** giữ nguyên cấu hình database và biến môi
trường của hệ thống đang dùng, mở terminal riêng và chạy:

```powershell
.\scripts\run-backend.bat
```

Đăng nhập bằng tài khoản hiện có. Bản UI không tạo tài khoản hoặc mật khẩu mặc định.
**Không chạy lại schema, seed, bootstrap hoặc migration chỉ để cập nhật giao diện.**
Môi trường mới hoàn toàn cần thiết lập backend theo `README_RUN.md`; việc triển khai
SQL Server thật không được thực hiện trong lần sửa UI này.

Trên Linux:

```sh
export DIENMAY_API_URL='http://127.0.0.1:8080'
sh scripts/run-client.sh
```

Có thể chạy trực tiếp `java -jar dist/client-swing.jar`. Ảnh nền được đọc từ classpath,
không phụ thuộc thư mục làm việc hoặc tệp ảnh nằm ngoài JAR.

## Build lại

Cách đã chạy và kiểm chứng:

```sh
ant clean verify
```

Kết quả: build thành công, 130 kiểm thử hồi quy/API/phân quyền đạt, JAR được tạo tại
`dist/`. Có cảnh báo `this-escape` của trình biên dịch JDK 21 ở các lớp Swing;
không có lỗi biên dịch.

Maven **chưa chạy được trong môi trường bàn giao**: không có lệnh `mvn`, mạng tải
công cụ/phụ thuộc không khả dụng. POM gốc không bị thay đổi. Trên máy đã cài Maven:

```powershell
.\scripts\build-maven.bat
```

Script trên gọi `mvn verify` rồi chép JAR vào `dist/`. Không chạy Ant và Maven đồng thời
trong cùng thư mục. Không diễn giải kết quả Ant là kết quả Maven.

## Bằng chứng và phạm vi kiểm tra

- [Báo cáo chi tiết](docs/red-ui/BAO_CAO_UI.md): thay đổi, kết quả và giới hạn.
- [Danh sách tệp thay đổi](docs/red-ui/changed-files.txt).
- [Thư viện 74 ảnh chụp Swing thật](docs/red-ui/gallery.html): mở sau khi giải nén.
- [Đối chiếu mã nguồn và ảnh gốc](docs/red-ui/source-integrity.json).
- [Log xác minh cuối cùng](docs/red-ui/full-verification.log).

Tổng cộng **284 kiểm thử đạt, 0 thất bại**, cộng **2 lượt khởi động JAR độc lập**
ở 1366×768 và 1920×1080. Màn hình sau đăng nhập dùng HTTP fixture cục bộ với dữ liệu
kiểm thử; đây không phải kết quả nghiệm thu với SQL Server thật. Chưa kiểm chứng
Windows/DPI, máy in và thiết bị ngoại vi.

Chạy lại toàn bộ kiểm tra UI trên Linux có Ant, JDK, Xvfb, Openbox và `timeout`:

```sh
sh scripts/verify-red-ui-linux.sh
```

`SHA256SUMS.txt` là manifest mới của các tệp bàn giao. Các thư mục `build/` và
`target/` sinh tự động không nằm trong ZIP; `dist/` chứa bộ JAR mới đã được kiểm tra.
