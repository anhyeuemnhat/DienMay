# Hiện trạng và phát hiện từ mã nguồn

Base: tệp `DienMay-Do-UI.rar` do người dùng cung cấp.
Mã thật đã có ba module common/backend/client, API, JDBC, scripts, tests và UI đỏ;
không xây dựng lại một demo giao diện rời thay cho project.
Không có database vận hành thật được kết nối trong đợt này.

| Vấn đề có căn cứ trong bản gốc | Xử lý trong bản bàn giao |
|---|---|
| WorkFrame là JFrame; form sửa là dialog lớn | WorkFrame/EditDialog chuyển JPanel; AppShell duy nhất |
| Thiếu quy trình archive/restore thống nhất | LifecycleService + form + API + revision/audit + migration |
| Seed có DemoTech/mã vạch tự tạo | Thay seed đó trong bản sao project bằng reference hãng và DEMO tách biệt; không sửa DB thật |
| Không phân biệt đủ PENDING/VERIFIED/INVALID/DEMO | QualityService, field evidence, cổng chặn giao dịch, mode DB |
| Table formatting có thể dịch nhầm chuỗi/tên | Format theo column semantics; tests chuỗi SALE/ngày/SKU0 |
| Gợi ý nhập liệu không đồng bộ | GuidedInput, combo prefix, input type/evidence hints |
| Schema cũ thiếu field xác minh và audit | Migration additive v3/v4; không xóa bản ghi cũ |
| Báo cáo cũ không phải bằng chứng lần này | Thay tài liệu/trạng thái, loại log/ảnh/diagram cũ, chỉ giữ evidence mới |
| Cold packaged startup trắng trong lần test mới | Sửa native sizing/paint; chạy lại actual JAR đạt |

Phạm vi màn hình: nhân viên, sản phẩm/tồn, khách, vendor, category, brand, nhập,
khuyến mãi, bán, chuyển, hóa đơn, lịch sử kho, serial, bảo hành, báo cáo, cảnh báo tồn,
xác minh dữ liệu và màn hình con. Duyệt source/test không thay thế đọc mọi cell DB thật.

Rủi ro chưa giải quyết: SQL runtime chưa chạy, nghiệp vụ đảo chứng từ chưa có,
phân trang/tải/production UAT chưa đầy đủ. Xem KNOWN_LIMITATIONS.
