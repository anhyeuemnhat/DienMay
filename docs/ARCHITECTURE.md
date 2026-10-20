# Kiến trúc và các quyết định quan trọng

## Tận dụng mã cũ và phạm vi tái cấu trúc

Giữ sáu layout/form: `MainUI`, `MakeSaleUI`, `CashierMain`, `StoreManagerMain`, `WarehouseManagerMain`, `TransferfromWarehouse`. Giữ 55 ảnh, các tài liệu/sơ đồ upstream và các điểm vào `InventorySystem`, `SingletonPattern`, `Store`, `Warehouse`, `Item`, `ItemQuantity`, `Sale` (đã thay nội dung để bỏ trạng thái nghiệp vụ trong RAM).

Đây là tái cấu trúc đáng kể lớp dữ liệu và nghiệp vụ, không phải chỉ vá vài dòng. Mô hình cũ dùng số thực cho tiền, kế thừa hạng khách/hàng mau hỏng, trạng thái kho trong bộ nhớ và các thao tác ghi rời rạc. Giữ nguyên lớp đó sẽ tạo hai nguồn sự thật. Vì vậy chỉ có **một đường chạy nghiệp vụ** mới, giữ bố cục form chính nhưng không duy trì một ứng dụng legacy song song. Các form CRUD phụ được gom vào `RowsFrame`/`Ui` và các màn hình chuyên trách; các class perishable/tier và DAO cũ không còn trong source biên dịch.

## Luồng phụ thuộc

`Swing → Service (quyền, bất biến, transaction) → DAO/Jdbc → SQL Server dm`.

`Code` chứa các giá trị, request và quy tắc thuần. UI không trực tiếp JDBC. Dữ liệu đọc cho bảng/giỏ có thể nằm trong RAM; chứng từ chỉ được xem là thành công sau khi `SQL.transaction` commit. Không dùng database in-memory thay thế SQL Server trong triển khai.

`SQL` mở connection mới cho từng công việc, timeout hữu hạn, cấu hình ngoài code. `PreparedStatement`/`ResultSet` dùng try-with-resources. Tên bảng động chỉ lấy từ whitelist nội bộ. Các điều kiện lọc người dùng đi qua tham số và escape ký tự LIKE. Thời gian SQL là UTC `DATETIME2`; ngày bán/báo cáo/bảo hành hiển thị theo `Asia/Ho_Chi_Minh`. Khoảng báo cáo là `[đầu ngày, đầu ngày kế tiếp)`.

## Giao dịch và đồng thời

Một lần checkout khóa `sp_getapplock` theo UUID yêu cầu, kiểm tra chứng từ đã có và fingerprint/nhân viên. Nếu là retry hợp lệ, trả lại hóa đơn cũ, không trừ thêm kho. Các sản phẩm được khóa `UPDLOCK,HOLDLOCK` theo thứ tự ID để giảm vòng chờ khóa. Trừ kho bằng UPDATE có điều kiện `Quantity >= requested`, không phải read-modify-write ngoài transaction.

Cùng transaction ghi hóa đơn, chi tiết snapshot, biến động kho, trạng thái Serial, liên kết Serial–chi tiết và hồ sơ bảo hành. `XACT_ABORT ON`, rollback khi SQL hoặc validation thất bại. Khóa có timeout; không tự giả lập thành công hoặc retry vô hạn. Manual JDBC transaction có một SELECT từ bảng schema trước app-lock để bảo đảm transaction đã được mở, kể cả luồng quản trị gọi app-lock trước thao tác bảng khác. Hành vi khóa thực tế vẫn phải xác minh bằng bộ test SQL Server.

Nhập hàng cộng KHO, chuyển hàng trừ nguồn và cộng đích trong cùng transaction. Mỗi biến động có nhân viên, lý do/chứng từ, UUID, và nhà cung cấp/giá nhập khi phù hợp. Không có nút sửa trực tiếp số tồn. `CostPrice` là giá nhập gần nhất, không phải hệ thống kế toán giá vốn bình quân.

## Tiền và sản phẩm

Tiền dùng `BigDecimal`/`DECIMAL(19,2)`, scale 2, không dùng float/double. Giá âm và độ chính xác vượt 2 số lẻ bị từ chối. Giảm phần trăm làm tròn HALF_UP một lần tại hóa đơn. Không cộng chồng giảm tay và mã khuyến mãi. Backend so lại giá và mức giảm với giá trị nhân viên vừa xác nhận; việc sửa giá sau khi có giỏ không được âm thầm làm đổi số tiền phải thu.

Sản phẩm model và đơn vị thiết bị tách riêng. `ProductCodes` tạo một không gian mã dùng chung SKU/Barcode, tránh SKU sản phẩm A trùng barcode sản phẩm B. Các mã này, username và serial có collation CI_AS xác định trong schema, không phụ thuộc DB mặc định có phân biệt hoa/thường. Barcode không chuyển sang số. Serial được trim/uppercase, bắt buộc duy nhất toàn hệ thống. Thiết bị chỉ xuất bán nếu còn IN_STOCK ở QUAY và thuộc đúng sản phẩm. InvoiceUnits có UNIQUE trên UnitId để không bán lại cùng đơn vị.

Không đổi chế độ theo Serial sau khi đã có biến động kho; tạo SKU mới khi cần thay cách quản lý. Sản phẩm đã có lịch sử được ngừng kinh doanh, không xóa. Revision ngăn form quản lý ghi đè một chỉnh sửa mới hơn.

## Hóa đơn và báo cáo

ID do SQL Server IDENTITY cấp; mã HD được tính từ ID, không dùng `MAX(Id)+1`. Khoảng trống ID sau rollback là hợp lệ. Receipt đọc header xong mới đọc chi tiết, không giữ nhiều cursor mở lồng nhau. Các Serial được gom theo khóa chính chi tiết đúng một lần, không nhân đôi số lượng do JOIN.

Doanh thu/số hóa đơn tính trên header. Bán chạy tính số lượng và giá trị gộp từ chi tiết; cột giá trị gộp ghi rõ là trước giảm giá, không được cộng nó rồi gọi là doanh thu thuần. Giá trên hóa đơn và chính sách bảo hành là snapshot. Tên nhân viên hiển thị theo hồ sơ nhân viên hiện tại, mã nhân viên thực hiện vẫn là FK lưu tại giao dịch.

## Bảo hành

Bắt đầu từ ngày mua tại Việt Nam, cộng số tháng bằng `LocalDate.plusMonths`; ngày hết hạn được tính là còn hiệu lực. Nếu ngày đích không tồn tại (cuối tháng/năm nhuận), dùng ngày cuối tháng theo plusMonths. Thời hạn 0 không tạo hồ sơ; 1–120 tạo snapshot. Với Serial, một hồ sơ cho một thiết bị; không Serial, một hồ sơ cho dòng hàng với CoveredQuantity.

Trạng thái phiếu: RECEIVED → PROCESSING → COMPLETED hoặc REJECTED; có thể từ RECEIVED → REJECTED. Không mở lại trạng thái kết thúc trong phạm vi này. Mỗi thay đổi thêm ClaimHistory, không xóa lịch sử. Hết hạn không phải điều kiện tự từ chối tiếp nhận.

## Bảo mật và giới hạn tin cậy

PBKDF2-HMAC-SHA256, salt ngẫu nhiên 16 byte, khóa 32 byte, 600.000 vòng. Mật khẩu tối thiểu 12 ký tự; demo buộc đổi mật khẩu. Phiên chỉ được tạo và đăng ký sau xác thực; mọi Service kiểm tra lại Enabled, Role, AuthVersion, MustChange. UI ẩn chức năng không thay cho kiểm tra Service. Quản lý không tự hạ quyền/khóa bản thân và không được loại bỏ quản lý hoạt động cuối cùng.

Login SQL runtime chung không phải tài khoản nhân viên. Script quyền hạn chế DDL, xóa và sửa lịch sử nhưng vẫn cần quyền ghi các bảng nghiệp vụ. **Ứng dụng desktop không chống được người đã lấy cắp SQL credential, sửa executable hoặc có quyền DBA.** Phải kiểm soát máy trạm, quyền đọc config, mạng riêng, TLS, sao lưu và quyền SQL. Không tuyên bố role Java là hàng rào chống DBA hoặc client bị sửa. File pending hash phát hiện nội dung hỏng/thay đổi; nó không phải chữ ký chống người có quyền sửa cả file lẫn hash.

## Swing, phục hồi và in

DB và hash mật khẩu chạy qua SwingWorker, callback cập nhật UI trên EDT. Callback cũ không được áp dụng sau đổi phiên. Đăng xuất và đổi mật khẩu chủ động bị chặn khi còn worker để không đóng cửa sổ, nhả khóa pending trong lúc giao dịch đang ghi. Các thao tác thanh toán có busy guard; barcode chỉ một ActionListener cho Enter, không vừa KeyListener vừa ActionListener. Nhập bằng máy quét/nhập tay dùng cùng luồng tìm SQL.

PendingStore ghi UUID/payload trước khi ghi DB, fsync file, rename atomically khi hệ thống hỗ trợ, khóa file theo tài khoản/chức năng. Không tự xóa file hỏng. Đối soát dùng cùng app-lock với thao tác ghi. Thay đổi cấu hình URL sẽ tạo profile lưu chờ khác: phải xử lý xong pending trước khi đổi cấu hình. Không thực hiện hàng đợi bán offline.

Receipt là text Swing đọc từ invoice đã lưu; dùng `JTextArea.print`. Chưa có mẫu nhiệt riêng 58/80 mm và chưa kiểm tra máy in thật. Không sinh thêm transaction khi in lại.

## Tài liệu kỹ thuật đã tham khảo (nguồn ngoài mã dự án)

Các quyết định implement ở trên cần được nghiệm thu trên môi trường đích; tài liệu tham khảo không thay thế kết quả test:

- Microsoft JDBC download: `https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server`
- Microsoft JDBC setAutoCommit: `https://learn.microsoft.com/en-us/sql/connect/jdbc/reference/setautocommit-method-sqlserverconnection`
- SQL Server sp_getapplock: `https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-getapplock-transact-sql`
- SQL Server implicit transactions: `https://learn.microsoft.com/en-us/sql/t-sql/statements/set-implicit-transactions-transact-sql`
- OWASP Password Storage Cheat Sheet: `https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html`
