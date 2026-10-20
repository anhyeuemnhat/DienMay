# Báo cáo thay đổi

## Phát hiện từ mã nguồn gốc

- `ImplementationDB/SQL.java`: cấu hình gắn với máy tác giả, truy cập/đóng tài nguyên và ghi lỗi không đủ để bảo đảm persistence cho luồng UI; được thay bằng connection boundary cấu hình ngoài và helper JDBC.
- `GUI/MakeSaleUI.java`: thao tác trên trạng thái giỏ/kho và danh sách sale trong RAM không đủ chứng minh hóa đơn đã ghi DB; luồng mới gọi checkout transaction và chỉ hiển thị thành công sau commit.
- `Code/Sale.java`: `generateReceipt()` bỏ trống; nay receipt được sinh từ Sale đọc lại từ SQL.
- `GUI/MainUI.java` và mô hình LoginInformation: PIN/mật khẩu rõ và role do giao diện chọn không phải cơ chế xác thực/phân quyền an toàn; thay bằng hash, session và kiểm tra DB tại Service.
- `GUI/TransferfromWarehouse.java`: gán lại Quantity ở đích và ghi các bước rời rạc có thể ghi đè tồn; thay bằng additive transfer có ledger và rollback.
- `Code/Store.java`: hai nhánh xử lý dòng receipt có thể thêm lại dòng đầu; InvoiceDao mới gom theo detail ID, không nhân số lượng do JOIN Serial.
- SQL gốc có `float`, ngày dạng chuỗi, seed/thứ tự FK và phân tách batch cần xem lại. Bản mới không thực thi lại nguyên script đó để tạo schema điện máy; cài mới dùng dm và migration có vùng duyệt.

Các phát hiện trên là kết quả đọc source ZIP; không được mô tả là đã tái hiện bằng SQL Server trong môi trường này.

## Giữ, sửa, thêm, bỏ

Giữ và nối nghiệp vụ mới vào 6 cặp `.java/.form`: MainUI, MakeSaleUI, CashierMain, StoreManagerMain, WarehouseManagerMain, TransferfromWarehouse. Việt hóa, sửa nhãn bị cắt, bỏ role picker runtime, không điền sẵn mật khẩu. Giữ Images và tư liệu upstream.

Sửa `InventorySystem`, `SingletonPattern`, `Store`, `Warehouse`, `Item`, `ItemQuantity`, `Sale`, `SQL`, `sdaproject.sql`. Các điểm vào cũ không còn thực thi CRUD/RAM sale độc lập.

Thêm nhóm `Money`, `Validation`, `Role`, `Cart`, `SaleRequest`, `WarrantyRules`, `DatabaseTool`; `Jdbc`, `ProductDao`, `StockDao`, `InvoiceDao`; các Service xác thực/sản phẩm/khách-nhân viên/khuyến mãi/kho/bán/bảo hành/báo cáo/PendingStore; GUI helper và các màn hình nghiệp vụ phụ. Thêm Ant, Maven, script build/run, cấu hình mẫu, 7 script database, 4 file test và tài liệu bàn giao.

Bỏ khỏi source active: Perishable/PerishableBatch, Batch cũ, Silver/Gold/Platinum, các subclass nhân viên/khách cũ phụ thuộc PIN/RAM, UI CRUD/search cũ và test main cũ. Chức năng hợp phạm vi của các form CRUD bị bỏ đã được chuyển vào các màn hình mới; không để nút hoạt động trỏ tới DAO cũ. Không có xử lý tự hủy hàng hết hạn hoặc ưu đãi hạng khách cũ còn chạy.

Danh sách chính xác theo Git ở `FILE-CHANGES.txt`. Working branch: `upgrade/dienmay`, baseline commit `df3c2cb` từ ZIP người dùng. ZIP bàn giao không chứa `.git`; patch đối chiếu được bàn giao riêng. Tệp ZIP đầu vào không bị ghi đè.

## Rủi ro và giới hạn tương thích

Schema dm không tương thích trực tiếp với bảng dbo nguồn. Runtime mới không đọc receipt legacy để giả lập doanh thu. Cần backup/approval/cutover theo MIGRATION.md. Refactor có quy mô đáng kể ở backend vì mô hình dữ liệu thay đổi; cần regression test thực tế, không khẳng định chỉ đổi giao diện. Main forms còn metadata NetBeans, nhưng Designer/Maven/Windows chưa được chạy tại môi trường này.
