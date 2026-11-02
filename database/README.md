# Cập nhật bắt buộc cho bản bàn giao schema v5

**SQL trong đợt này chưa chạy trên SQL Server.** Các hướng dẫn bên dưới phải được DBA thử trên database riêng trước. Backend mới yêu cầu v5; không chỉ chạy các bước v4 của tài liệu cũ.

| Tệp mới | Vai trò |
|---|---|
| `migration/014_controlled_operations.sql` | CostRevision; snapshot phiếu nhập; InventoryReversals; DeletionRequests; constraint movement/Serial/cancel; procedure ControlledDelete; marker v5 |
| `security/015_controlled_operations.sql` | Chạy sau 008; EXECUTE procedure, DENY xóa danh mục trực tiếp/lịch sử, không cấp DELETE schema |
| `checks/016_controlled_reconciliation.sql` | Chỉ đọc: gốc/bù trừ, tồn tại Serial đã đảo, liên kết/audit/cancel, không sửa dữ liệu |

Thứ tự cấu trúc qua installer: **001 → 007 → 010 → 011 → 012 → 014**. Sau đó DBA chạy **008 → 015** để cấp quyền; chạy checks **009, 013, 016** khi nghiệm thu. Không sửa migration lịch sử. Các bước cấp quyền và checks không tự chạy khi backend khởi động.

Snapshot của phiếu cũ giữ NULL: phiếu đó bị chặn đảo tự động, không tự backfill giá đoán. CostRevision theo dõi cập nhật giá vốn trong catalog/receive/reverse; một số cập nhật SQL trực tiếp ngoài ứng dụng không có cùng kiểm soát, nên không cấp cho client quyền truy cập DB.

Procedure xóa dùng SQL ownership chaining cho DML tĩnh; giữ chung owner của procedure/tables, không cấp ALTER cho runtime. Phải xác nhận thực tế direct DELETE bị DENY trong khi procedure thực hiện đúng các điều kiện. Chưa có kết quả chạy permission test trong môi trường này.

Có thể cần restore từ backup nếu migration lỗi; marker v5 chỉ ghi sau khi procedure và các đối tượng cần thiết đã tạo. Các batch không phải một transaction duy nhất bao toàn bộ bộ cài. Không đánh dấu version thủ công để bỏ qua lỗi.

Xem `../README_RUN.md`, `../IMPLEMENTATION_STATUS.md`, `../docs/CONTROLLED_OPERATIONS.md` để biết phạm vi và hạn chế mới. Nội dung nền bên dưới được giữ để tham khảo quy trình demo/production; bước cấp quyền phải bao gồm 015 như trên.

---

# Bộ database SQL Server - schema v5

**Có mã SQL và dataset, nhưng chưa chạy trên SQL Server trong môi trường bàn giao.**
Không có file `.bak` giả lập. Cài đặt từ các script kèm theo; kiểm chứng theo checklist trước vận hành.
Bộ SQL dùng schema `dm`, không tạo hệ thống bảng song song với ứng dụng.

## Cấu trúc

| Tệp | Mục đích |
|---|---|
| `schema/000_create_database.sql` | DBA tạo mới `DienMayDo_Demo`; từ chối nếu đã tồn tại |
| `schema/001_schema.sql` | Bảng, khóa, ràng buộc v1 |
| `migration/007_api_transition.sql` | v2: chốt hủy yêu cầu và đối soát API |
| `migration/010_quality_lifecycle.sql` | v3: xác minh, revision, lưu vết, phân loại môi trường |
| `migration/011_identifiers.sql` | v4: GTIN / IMEI, loại mã thiết bị |
| `reference/012_reference_catalog.sql`, `reference/catalog.json` | Hai sản phẩm có nguồn hãng; chỉ thêm, không ghi đè danh mục cũ |
| `demo/002_demo.sql` | Dataset mô phỏng riêng, có giao dịch liên kết |
| `security/008_runtime_role.sql` | Quyền SQL tối thiểu cho backend; không tạo login/mật khẩu |
| `checks/009_reconciliation.sql` | Đối chiếu tồn, sổ kho, Serial, hóa đơn, bảo hành |
| `checks/013_quality_audit.sql` | Phân loại dữ liệu chờ duyệt / cần kiểm tra; không sửa dữ liệu |

## A. Tạo database DEMO riêng từ môi trường trống

1. DBA cài SQL Server, bật kết nối TCP và cấp tài khoản cài đặt.
   Chạy `000_create_database.sql` trong SSMS. Script này chỉ tạo DB DEMO mới.
2. Tại thư mục gốc dự án, build và cấu hình kết nối backend tới chính xác `DienMayDo_Demo`.
   Tài khoản cài đặt cần quyền DDL; không dùng nó làm tài khoản vận hành.
3. Chạy lần lượt:

```sh
ant clean verify
sh scripts/database-tool.sh init-demo DienMayDo_Demo --confirm-demo
sh scripts/database-tool.sh bootstrap quanly
# Nhập mật khẩu tự chọn trong terminal, không ghi vào lệnh.
sh scripts/database-tool.sh seed-demo DienMayDo_Demo --confirm-demo
sh scripts/database-tool.sh check
```

Windows thay `sh scripts/database-tool.sh` bằng `scripts\database-tool.bat`.
`init-demo` yêu cầu DB trống hoặc bản khởi tạo DEMO do công cụ này tạo,
không tự nhận database cũ là DEMO. `seed-demo` yêu cầu tên kết thúc `_Demo`,
mode DEMO, schema v5, quản lý ban đầu và chưa có dữ liệu nghiệp vụ.
Chạy lại đúng dataset version sẽ không chèn trùng; dataset khác / mode thật bị chặn.

4. DBA chạy `security/008_runtime_role.sql` trong DB này, tạo login và user riêng bằng quy trình bí mật nội bộ.
   Gán user vào `dienmay_runtime_role`, không gán `db_owner`.
5. Đổi kết nối backend sang user runtime; chạy `check`, backend, client.
6. Chạy hai script `checks/` bằng tài khoản phù hợp; lưu kết quả.
   Trong reconciliation, bảng thông tin môi trường có một dòng; các truy vấn **vi phạm** phải không có dòng.

### Dữ liệu DEMO mong đợi sau seed

| Sản phẩm | Nhập | Chuyển quầy | Bán | Tồn kho | Tồn quầy |
|---|---:|---:|---:|---:|---:|
| Sony WH-CH520 | 4 | 2 | 1 | 2 | 1 |
| LG 24MR400-B | 8 | 3 | 1 | 5 | 2 |

Một hóa đơn hai dòng: tạm tính 3.400.000, giảm 170.000, thành tiền 3.230.000 VND.
Có nhà cung cấp/khách DEMO, khuyến mãi DEMO, hai bảo hành và một lần tiếp nhận bảo hành.
Giá, số lượng, chính sách bảo hành, khách/nhà cung cấp và giao dịch đều **mô phỏng**,
không phải dữ liệu cửa hàng hay chính sách hãng đã xác minh.
Mã thiết bị bắt đầu `DEMO-SN-`; không phải Serial thật. Không tạo IMEI hay GTIN giả.
Barcode hai sản phẩm để trống. `TrackSerial` là chính sách theo dõi của dataset, không khẳng định thiết bị có/không có serial nhà sản xuất.

## B. Database hiện hữu

Không chạy seed, không chạy script tạo database lên môi trường thật.
DBA sao lưu và **thử khôi phục** sang bản sao; đối chiếu schema v1/v2 thực tế với mã gốc.
Công cụ chỉ hỗ trợ schema `dm` của dự án, không tự chuyển đổi schema khác.
Cấu hình URL và chỉ định chính xác tên bản sao:

```sh
sh scripts/database-tool.sh migrate DienMayDo_Staging --backup-confirmed
# Chỉ sau khi đã xác nhận đây là dữ liệu cửa hàng hợp lệ:
sh scripts/database-tool.sh classify-production DienMayDo_Staging --acknowledge-unverified
```

`classify-production` xác nhận loại môi trường, **không** duyệt sản phẩm hay chứng minh dữ liệu là thật.
Không cho đổi `_Demo` / `_Test` / dataset DEMO thành production.
Dữ liệu cũ được giữ lại; danh tính và mã vạch chuyển về chờ duyệt,
barcode cũ gán loại OTHER thay vì đoán GTIN; mã thiết bị cũ giữ loại SERIAL.
Các giao dịch mới bị chặn cho đến khi dữ liệu liên quan được duyệt hợp lệ.
Phải lập kế hoạch chuyển đổi và thời gian dừng nghiệp vụ, không bật chặn trên hệ thống đang bán hàng khi chưa nghiệm thu.

Muốn tạo database production **mới, trống**, DBA tự tạo DB không mang hậu tố `_Demo` / `_Test`,
chọn DB đó trong SSMS, lần lượt chạy 001, 007, 010, 011, 012, 014; sau đó classify-production,
bootstrap và cấp quyền runtime. Không chạy 002. Cách này chưa được kiểm chứng tại đây.

## C. An toàn, lặp lại và khôi phục

Installer so khớp `DB_NAME()` với tên truyền vào, từ chối database hệ thống,
khóa một phiên cài đặt, chỉ đọc các script đi kèm và bắt buộc cờ xác nhận.
Không có lệnh DROP DATABASE, không nạp seed khi backend khởi động.
Migration được commit theo từng phiên bản; nếu bước sau lỗi, các bước trước đã commit vẫn còn.
Kiểm tra `dm.SchemaVersion` và lỗi trước khi chạy lại. Không có down-migration phá hủy;
khởi phục bằng backup đã kiểm chứng khi cần.
Các script có chốt an toàn không thay thế quyền phê duyệt và trách nhiệm của DBA.
