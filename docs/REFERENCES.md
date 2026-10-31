# Nguồn tham chiếu và giới hạn xác minh

Đợt đối chiếu: 31/10/2026. Nguồn dưới đây không phải bằng chứng về giao dịch hay hàng tồn thực tế của cửa hàng.

| Nguồn chính thức | Phạm vi đã đối chiếu / ứng dụng |
|---|---|
| [Sony WH-CH520 specifications](https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications) | Sony, model WH-CH520, tai nghe không dây, driver 30 mm, Bluetooth 5.2, khối lượng xấp xỉ 147 g. Dùng cho hai tệp reference SQL/JSON. |
| [LG 24MR400-B](https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/) | LG, model 24MR400-B, màn hình 23.8 inch, IPS, 1920 x 1080, tối đa 100 Hz; D-sub tối đa 75 Hz. Không đồng nhất 100 Hz cho mọi cổng. |
| [GS1 UK: check digit](https://www.gs1uk.org/knowledge-hub/standards/what-is-a-check-digit) | Quy tắc chữ số kiểm tra mã GS1. Ví dụ trong test là vector toán học, không gán cho sản phẩm demo. |
| [GS1 glossary](https://ref.gs1.org/glossary/) | Thuật ngữ GTIN. Cấu trúc hợp lệ không chứng minh quyền sử dụng hay quan hệ mã-sản phẩm. |
| [RFC 7254](https://www.rfc-editor.org/rfc/rfc7254.html) | Cấu trúc IMEI 15 chữ số và check digit. Kiểm tra Luhn trong code chỉ kiểm tra cấu trúc, không xác minh thiết bị/quyền sở hữu. |
| [Microsoft JDBC release notes](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver) | Thông tin phát hành và Java compatibility của driver 13.6.0. Dependency được pin, không tuyên bố luôn là bản mới nhất. |
| [Microsoft JDBC download](https://learn.microsoft.com/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server) | Nguồn để DBA tải/kiểm tra lại driver. JAR bàn giao có sẵn trong RAR của người dùng, chưa đối chiếu hash nhà phát hành. |
| [Microsoft: JDBC TLS](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption?view=sql-server-ver17) | `trustServerCertificate=true` bỏ xác minh certificate; chỉ cân nhắc trong môi trường lab có kiểm soát. Production dùng certificate tin cậy và xác minh hostname. |
| [Microsoft JDBC v13.6.0 LICENSE](https://github.com/microsoft/mssql-jdbc/blob/v13.6.0/LICENSE) | MIT license được đọc qua GitHub connector và lưu kèm JAR. |

## Phân biệt thông tin đã biết và dữ liệu cửa hàng

Hai model tham chiếu không xác nhận cửa hàng đang bán chúng. Tên hiển thị tiếng Việt
là mô tả sản phẩm, không phải cam kết trích nguyên văn mọi tên marketing của hãng.
Barcode không đủ căn cứ được để trống. Serial `DEMO-SN-*`, giá, số lượng,
chính sách bảo hành, nhà cung cấp, khách hàng và giao dịch seed là mô phỏng có nhãn.
Các giá trị này không lấy từ thông tin hãng và không được trình bày là dữ liệu thật.

## Mã nguồn và tài liệu đầu vào

Base chỉnh sửa là RAR đính kèm; main trên GitHub chỉ được kiểm tra metadata/tree.
Xem `provenance/ACQUISITION.md`. Không tuyên bố đã đọc giáo trình/PDF nào không có trong
đợt làm việc này. Các bảng trích giáo trình và báo cáo nghiệm thu cũ trong RAR
đã được thay bằng bằng chứng của bản sửa hiện tại.
