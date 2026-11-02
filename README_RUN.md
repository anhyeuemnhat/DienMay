# Build, cập nhật database và chạy Điện Máy Đỏ

## 1. Giải nén và build

Giải nén `DienMayDo_HoanThien.zip`, mở terminal trong thư mục `DienMayDo` chứa `build.xml`. Cài JDK 17+ và Apache Ant; Microsoft SQL Server cần cho backend chạy thật. Không cần SQL Server để build/offline test.

```bat
java -version
ant -version
ant clean verify
```

Ant kiểm tra quy ước mã, biên dịch ba module, chạy 5 bộ test offline và đóng gói `dist/common-dto.jar`, `dist/backend-server.jar`, `dist/client-swing.jar`. Giữ cả ba JAR cạnh nhau và giữ JDBC driver trong `backend-server/lib/`. Driver và ảnh nền đã có trong dự án; không cần tải lại để build Ant. Maven POM phục vụ IDE, chưa được thực thi trong đợt này.

## 2. Cấu hình bí mật chỉ trên máy backend

Bản bàn giao đã làm sạch `config/database.properties`. Cấp `DIENMAY_DB_URL`, `DIENMAY_DB_USER`, `DIENMAY_DB_PASSWORD` qua môi trường/chu trình bí mật nội bộ. Có thể sửa URL và user trong file theo `config/database.properties.example`; không lưu mật khẩu thật vào mã nguồn/log. `DIENMAY_CONFIG` chọn file cấu hình khác.

URL mẫu không chứa mật khẩu:

```text
jdbc:sqlserver://localhost:1433;databaseName=DienMayDo_Test;encrypt=true;trustServerCertificate=false
```

DBA phải chuẩn bị SQL login/user, chứng chỉ và quyền thích hợp. Client Swing không được cấp tài khoản SQL. Không dùng `sa`/`db_owner` làm tài khoản runtime.

## 3. Nâng cấp database bản sao/test, không tự động production

Dừng backend và client ghi dữ liệu. DBA sao lưu và thử khôi phục trước. Cấu hình kết nối vào đúng database đích, dùng tài khoản migration có quyền DDL.

Với **database hiện hữu của chính dự án**, ví dụ bản sao tên `DienMayDo_Test`:

```bat
scripts\database-tool.bat migrate DienMayDo_Test --backup-confirmed
```

Với **database test mới, trống** đã được DBA tạo, dùng lệnh sau THAY CHO lệnh trên:

```bat
scripts\database-tool.bat init-test DienMayDo_Test --confirm-test
```

Installer chạy theo thứ tự 001 → 007 → 010 → 011 → 012 → **014**, yêu cầu schema v5 trước khi chạy backend mới. Không tự nâng cấp khi backend khởi động. Migration 014 gồm các batch; nếu lỗi phải kiểm tra trạng thái/backup, không tự đánh dấu v5 bằng tay.

Sau nâng cấp, DBA chọn đúng database trong SSMS và chạy lần lượt:

```text
database/security/008_runtime_role.sql
database/security/015_controlled_operations.sql
```

Gán user runtime vào `dienmay_runtime_role`; không cấp DELETE trên schema. 015 cấp EXECUTE cho `dm.ControlledDelete`, siết quyền xóa trực tiếp và bảo vệ lịch sử. Script quyền không được installer tự áp dụng. Chuyển kết nối backend sang tài khoản runtime rồi chạy `scripts\database-tool.bat check`.

Database mới để sử dụng tương tác cần bootstrap quản lý trong terminal:

```bat
scripts\database-tool.bat bootstrap quanly
```

Nhập mật khẩu tự chọn, không có mật khẩu mặc định. Bộ `ant sql-it` tự tạo fixture test riêng và không cần bootstrap/seed trước. Dataset demo chỉ dành cho DB `_Demo`; xem `database/README.md`, không seed vào DB test có dữ liệu hoặc production.

## 4. Chạy phần mềm

```bat
scripts\run-backend.bat
REM Mo terminal khac, van tai thu muc DienMayDo:
scripts\run-client.bat
```

Backend/client mặc định dùng loopback `127.0.0.1:8080`. `DIENMAY_BIND`, `DIENMAY_PORT`, `DIENMAY_API_URL` điều chỉnh theo cấu hình triển khai; mạng nhiều máy phải nghiệm thu HTTPS/TLS theo `docs/architecture/DEPLOYMENT.md`. Linux dùng `sh scripts/<tên>.sh` thay các lệnh `.bat`.

F11 đổi fullscreen/cửa sổ; kéo thanh trên khi đã restore; ba nút –/□/✕ luôn có ở màn hình đăng nhập/đổi mật khẩu. Quay lại/Tiến giữ màn hình và bản nháp trong phiên. Không còn nút Nháp chưa lưu trên toolbar. Thoát/đăng xuất vẫn cảnh báo; dữ liệu yêu cầu chờ đối soát không bị xóa.

## 5. Nghiệp vụ mới

Xác nhận dưới vốn chỉ chấp nhận đúng giá và Revision vừa xem. Backend có thể từ chối nếu dữ liệu đổi; không tự sửa giá bán khi nhập. Khi nhập chờ bị báo giá thay đổi, dùng Hủy yêu cầu chưa ghi an toàn, tải lại rồi xác nhận lại; không tự sửa UUID của giao dịch chưa rõ kết quả.

Chọn dòng rồi bấm **Xóa bản ghi**, xem preview, nhập lý do; chỉ Manager được thực hiện. Với phiếu nhập đã ghi sổ, mở tab trong Nhập hàng: nút này dẫn tới **Xác nhận hủy/đảo phiếu**, không xóa vật lý chứng từ. Các phiếu thiếu snapshot hoặc phát sinh giao dịch tiếp theo có thể bị chặn.

Sau lỗi mạng khi xóa/đảo, dùng **Đối soát xóa/Đối soát đảo**; không xóa file pending. Chỉ đảo cho phép thử lại có chủ đích cùng mã, không tạo yêu cầu khác để thay thế yêu cầu chưa xác định.

## 6. Kiểm thử và nghiệm thu

```bat
ant clean verify
REM Tren Windows co desktop tuong tac:
ant ui
ant ui-modal
```

Linux có Xvfb/Openbox: `sh scripts/ui-smoke-linux.sh`, `sh scripts/ui-modal-linux.sh`; tổng hợp cả cold-start JAR: `sh scripts/verify-red-ui-linux.sh`.

SQL chỉ chạy trên DB `_Test` riêng với opt-in theo `docs/testing/SQL_SERVER_ACCEPTANCE.md`; `ant sql-it` báo SKIP không phải đạt. Chạy các kiểm tra chỉ đọc 009, 013 và 016, lưu kết quả. Windows/DPI/multiple-monitor và SQL thật vẫn là bước nghiệm thu bắt buộc trước triển khai.