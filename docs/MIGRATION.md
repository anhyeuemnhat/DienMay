# Database và migration không phá dữ liệu nguồn

**Chưa chạy các script này trên SQL Server tại môi trường bàn giao.** Luôn thử trên bản khôi phục từ backup, không chạy trực tiếp lần đầu lên DB đang bán hàng. Không trộn script khởi tạo mới với migration đang có schema dm.

## Các file SQL

| File | Mục đích |
|---|---|
| 000_create_database.sql | Tạo/chọn DB DienMay khi cài mới. Không dùng file này để trỏ migration sang nhầm DB. |
| 001_schema.sql | Tạo schema dm phiên bản 1; không thay đổi bảng dbo cũ; từ chối chạy lại. |
| 002_demo.sql | Dữ liệu công khai cho môi trường demo riêng; không dùng cho production/migration. |
| 003_stage_legacy.sql | Đọc bảng dbo vào vùng đối soát; mặc định chưa duyệt, không sao chép PIN. |
| 004_apply_reviewed_legacy.sql | Nhập các bản ghi đã duyệt trong một transaction, giữ mapping và audit. |
| 005_reconciliation.sql | Các truy vấn tìm sai lệch kho/ledger/Serial/đơn/bảo hành, chỉ đọc. |
| 006_permissions.example.sql | Gán quyền runtime cho SQL login riêng đã tạo bằng công cụ DBA. |

Thực thể chính trong dm: Employees, Customers, Categories, Brands, Vendors, Products, ProductCodes, Stock, ProductUnits, Promotions, Invoices, InvoiceDetails, InvoiceUnits, StockMovements, StockMovementUnits, InventoryRequests, Warranties, WarrantyClaims, ClaimHistory và SchemaVersion. Staging chỉ xuất hiện nếu chạy 003. Giá DECIMAL(19,2), tiếng Việt NVARCHAR, thời gian DATETIME2(3), ngày DATE; PK/FK/UNIQUE/CHECK và index định nghĩa trong 001. Một số quan hệ chéo (sản phẩm của Serial phải khớp chi tiết) còn được kiểm tra bằng Service và truy vấn đối soát, không chỉ FK đơn.

## Vì sao không ALTER và ghi đè dbo

Database nguồn phục vụ hàng tiêu dùng, tồn kho theo StoreItems/Batch, có giá float và PIN rõ. Receipts/ReceiptItem không đủ ngày bán và đơn giá lịch sử để dựng doanh thu tin cậy. Tạo schema dm trong **cùng bản sao database** cho phép kiểm chứng/so sánh với dữ liệu nguồn, không xóa lịch sử hoặc âm thầm gán giá hiện tại vào hóa đơn cũ. Đây là biên di trú rõ ràng cho một đường chạy ứng dụng mới, không phải hai ứng dụng production chạy song song.

## Trình tự migration

1. Dừng ghi bằng ứng dụng cũ. Backup toàn bộ DB; thử restore sang DB tên rõ ràng, ví dụ `DienMayMigration_Test`. Ghi lại số lượng bảng/chứng từ và số tồn theo SKU/vị trí. Không chỉ sao chép vài bảng.
2. Trên **DB đã restore**, chọn đúng DB rồi chạy 001; không chạy 000 (vì 000 cố định chọn DienMay). Không seed demo.
3. Tạo quản lý ban đầu bằng DatabaseTool bootstrap với config trỏ bản sao này, hoặc quy trình DBA được duyệt. Tạo Categories/Brands cần dùng. Chỉ bootstrap khi Employees trống.
4. Chạy 003 một lần. LegacyProductStage giữ riêng tên/giá cửa hàng và kho, loại hàng cũ, tồn QUAY và tổng Number_of_Boxes × Items_Per_Box từ Batch; dữ liệu bất thường được đánh dấu để người phụ trách kiểm tra. Không tự đổi hàng thực phẩm thành điện máy.
5. Rà soát từng sản phẩm cần chuyển: TargetName, Barcode, CategoryId, BrandId, CostPrice, SellingPrice, WarrantyMonths, TrackSerial, ReorderLevel, số tồn và ReviewNote. Các giá/quy tắc nghiệp vụ mục tiêu không tự suy ra từ giá float cũ. Barcode phải do cửa hàng xác nhận; không thể khôi phục các số 0 đầu đã mất vì kiểu số ở dữ liệu nguồn.
6. Nếu TrackSerial=1, nhập đủ Serial thực tế vào LegacySerialStage, đúng vị trí KHO/QUAY; số Serial phải khớp số lượng. Không tạo Serial giả để đủ số tồn. Serial được chuẩn hóa khi nhập.
7. Khách hàng/nhà cung cấp phải duyệt tên và thông tin nguồn. Tài khoản cần UserName riêng, NewRole đúng, hash PBKDF2 mới từ `Code.DatabaseTool hash`, trạng thái Enabled phù hợp. PIN cũ **không được tái sử dụng**. Các tài khoản được import yêu cầu đổi mật khẩu ban đầu.
8. Chỉ đặt Approved=1 khi hoàn tất kiểm tra. Trong 004, điền `@ApproverUserName` bằng quản lý đang hoạt động. Chạy bằng tài khoản DBA, không runtime. Bản ghi không duyệt giữ nguyên ở staging; bản đã có New*Id không nhập lại.
9. Chạy 005, kiểm tra tất cả tập kết quả bất thường đều rỗng và đối chiếu số liệu với bảng nguồn/biên bản. Mỗi tồn đầu được ghi OPENING và audit; không dùng UPDATE trực tiếp để làm báo cáo trông khớp.
10. Chạy toàn bộ kịch bản A–E trên bản sao; sau khi được duyệt mới lập kế hoạch cutover, backup cuối, lặp migration có kiểm soát và thay config máy trạm. Không để ứng dụng cũ tiếp tục ghi vào dbo trong khi ứng dụng mới ghi dm.

## Dữ liệu lịch sử không được tự nhập

`dbo.Receipts` và `dbo.ReceiptItem` vẫn nằm nguyên trong DB nguồn. Do không có đủ timestamp/đơn giá bán tại thời điểm giao dịch, không tự nhập chúng thành hóa đơn dm và không cộng vào báo cáo doanh thu mới. Ứng dụng mới không có màn hình xem những receipt legacy này. DBA tra cứu bằng công cụ cũ/SQL và có thể xuất báo cáo riêng sau khi cửa hàng bổ sung được dữ liệu xác thực. Đây là giới hạn rõ ràng, không phải mất dữ liệu âm thầm.

## Rollback và quyền

003/004 dùng transaction + XACT_ABORT; nếu gặp lỗi không được kết luận đã migrate thành công. Đọc Messages, kiểm tra transaction còn mở trong session và rollback khi cần. Trước cutover có thể bỏ bản sao thử nghiệm và restore lại từ backup. Sau khi đã có giao dịch mới, không xóa dm hoặc restore đè mà không xử lý giao dịch phát sinh và có quyết định của người phụ trách.

Runtime không cần quyền tạo bảng/schema, sửa/xóa hóa đơn hoặc xóa lịch sử. Script 006 cần login đã có, không chứa mật khẩu. Quyền/nhận dạng DBA, tài khoản SQL runtime và tài khoản nhân viên trong Employees là ba khái niệm khác nhau.
