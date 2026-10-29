# Báo cáo bàn giao — Giao diện Điện Máy Đỏ

Ngày: **30/10/2026**  
Đầu vào: `DienMay-Improved.rar`, ảnh nền gốc và bản yêu cầu chỉnh sửa giao diện.  
Đầu ra: dự án Java Swing đã sửa trực tiếp, tài nguyên ảnh, JAR mới, mã kiểm thử và ảnh chụp thực tế.

## 1. Kết quả triển khai

Tiêu đề nhận diện trên cửa sổ chính và thanh đầu ứng dụng:

**PHẦN MỀM QUẢN LÍ KINH DOANH NỘI BỘ CỦA CỬA HÀNG ĐIỆN MÁY ĐỎ**

Đã bỏ hai dòng thương hiệu cũ và hậu tố cửa sổ cũ ở các vị trí nhận diện.
Tên màn hình chức năng được đặt riêng trên thẻ nội dung; không xóa nhãn chức năng,
tên trường, tên cột hoặc nút nghiệp vụ.

Ảnh gốc nằm tại `client-swing/src/main/resources/images/red_mascot_background.png`.
`BackgroundPanel` đọc ảnh bằng `getResourceAsStream()`, dùng tỉ lệ
`max(viewWidth/imageWidth, viewHeight/imageHeight)`, giữ tỉ lệ, neo trái và cắt
phần dư thay vì kéo méo. Ảnh được vẽ trong `paintComponent()` phía sau component,
cache bản đã co giãn theo kích thước và giải phóng cache khi rời cây giao diện.

`WorkFrame` áp dụng lớp nền cho các màn hình kế thừa; các helper của `Ui` và
`EditDialog` áp dụng cho hộp thêm/sửa, xác nhận, thông báo và hộp chọn tệp.
Không thay JFrame bằng web hoặc thêm framework giao diện khác.

### Khả năng đọc và bố cục

Các thẻ nội dung dùng trắng khoảng 96,9% độ đục; bảng, viewport dữ liệu và ô nhập
dùng nền đục. Chữ nội dung là font logic `Dialog` 14, có hỗ trợ tiếng Việt trong
môi trường kiểm tra. Header dùng đỏ đậm/chữ trắng; bảng có hàng xen kẽ, màu chọn rõ,
xuống dòng nội dung dài và thanh cuộn ngang/dọc.

Các nút giữ callback gốc; nút thêm/lưu và hành động chính dùng đỏ, hành động xác nhận
ghi/thanh toán dùng xanh đậm, xóa dùng đỏ sẫm, hủy/thao tác phụ dùng nền sáng.
Trạng thái vô hiệu hóa vẫn có chữ đậm màu dễ đọc. Viền và focus được đặt riêng.

Đăng nhập và dashboard dành khoảng trống bên trái cho nhân vật.
Màn hình nhiều dữ liệu, đặc biệt bán hàng và kho, ưu tiên vùng nghiệp vụ; ảnh nền
vẫn hiện ở vùng bao quanh. Khi thu nhỏ, vùng làm việc có cuộn để không ép bảng
hoặc nút xuống chiều cao bằng không. Không cam kết mọi điều khiển cùng nằm trong
một khung nhìn 760×520; các điều khiển vẫn truy cập bằng thanh cuộn.

### Màu kiểm tra bằng công thức độ chói tương đối

| Cặp màu | Tỉ lệ |
|---|---:|
| Chữ #261C21 / trắng | 16,53:1 |
| Chữ phụ #5C4B53 / trắng | 8,12:1 |
| Trắng / nút chính #A41027 | 7,83:1 |
| Trắng / header #680A1C | 12,74:1 |
| Trắng / hàng chọn #770C22 | 11,29:1 |
| Trắng / nút xác nhận #175B41 | 8,05:1 |
| Chữ vô hiệu hóa #5C4E55 / #E7E1E4 | 6,09:1 |
| Chữ / nền hàng xen kẽ | 14,82:1 |
| Viền điều khiển / nền trắng | 5,23:1 |
| Viền điều khiển / nền vô hiệu hóa | 4,06:1 |

Đây là kết quả kiểm tra các cặp màu được định nghĩa và dùng bởi theme, không phải
chứng nhận khả năng tiếp cận toàn ứng dụng trên mọi hệ điều hành. Xem
[log kiểm tra](checks-1366x768.log) cho phép đo và kết quả cụ thể.

## 2. Danh sách Java thay đổi

### 11 tệp ứng dụng đã sửa

| Tệp | Nội dung |
|---|---|
| `App.java` | Cài theme, font và chống răng cưa trước khi tạo Swing; giữ nguyên khởi tạo API/ứng dụng. |
| `DashboardFrame.java` | Lưới chức năng tự đổi số cột; giữ nguyên đích điều hướng và lọc theo vai trò. |
| `EditDialog.java` | Hộp thêm/sửa có nền ảnh, nội dung đục, cuộn và nút thao tác dễ đọc. |
| `MainUI.java` | Bỏ hai dòng thương hiệu cũ, giữ form đăng nhập thật trên thẻ sáng lệch phải. |
| `MakeSaleUI.java` | Bố cục bán hàng thích ứng, form gọn, hộp chọn serial theo theme; giữ nguyên luồng giỏ hàng/thanh toán. |
| `Navigation.java` | Đồng bộ màu/font menu; không đổi danh mục hoặc callback. |
| `RowsFrame.java` | Dùng thanh công cụ chung theo theme. |
| `RowsPanel.java` | Bảng đục, renderer mới, độ rộng cột/cuộn; hộp xuất CSV và tên in theo nhận diện. |
| `StockFrame.java` | Bố cục nhập/chuyển kho và hộp nhập serial theo theme; giữ nguyên luồng ghi kho/đối soát. |
| `Ui.java` | Tiêu đề chính xác, bảng màu, font, nút, viền, form, panel, thông báo, xác nhận và hộp chọn tệp dùng chung. |
| `WorkFrame.java` | Gắn ảnh nền, thanh nhận diện, tiêu đề chức năng, footer; bố cục thích ứng và cuộn vùng làm việc khi cửa sổ nhỏ. |

Các tệp nằm trong `client-swing/src/main/java/vn/dienmay/client/`;
ngoại trừ `App.java`, các tệp còn lại thuộc package `ui`.

### 6 lớp UI mới

| Tệp | Nội dung |
|---|---|
| `BackgroundPanel.java` | Đọc PNG từ classpath, giữ tỉ lệ cover, neo trái, cache ảnh theo kích thước và vẽ ở paintComponent. |
| `BrandHeading.java` | Một dòng tiêu đề chính xác, tự giảm cỡ chữ theo chiều rộng, không rút gọn bằng dấu ba chấm. |
| `ScrollablePanel.java` | Container theo chiều rộng viewport cho dashboard thích ứng. |
| `SurfacePanel.java` | Vẽ lớp nền alpha đúng kỹ thuật để không làm mờ chữ hoặc chồng màu khi repaint. |
| `TableStyling.java` | Renderer chữ dài xuống dòng, nền hàng xen kẽ, hàng chọn rõ, chiều cao hàng và chiều rộng cột thích ứng. |
| `ThemeButton.java` | Nút có trạng thái thường/hover/nhấn/focus/vô hiệu hóa với màu dễ phân biệt. |

### Hai lớp kiểm thử mới và build

`tests/src/main/java/vn/dienmay/tests/RedUiChecks.java` bổ sung kiểm tra UI và
`PackagedAppSmoke.java` chạy entrypoint thật bằng `java -jar`.

`build.xml` chỉ bổ sung sao chép tài nguyên `client-swing/src/main/resources`
vào đầu ra biên dịch client. POM Maven giữ nguyên; quy trình Maven mặc định vẫn
đóng gói `src/main/resources`.

`scripts/verify-red-ui-linux.sh` chạy lại toàn bộ bằng chứng cuối cùng.
[changed-files.txt](changed-files.txt) có đường dẫn đầy đủ.
[ui-changes.patch](ui-changes.patch) là bản diff văn bản; ảnh nhị phân nằm sẵn
trong dự án, không nằm trong patch.

## 3. Build và kiểm thử đã thực hiện

Môi trường: Linux, OpenJDK 21.0.12.1, bytecode target Java 17, Ant 1.10.15,
Xvfb/Openbox, UI Swing thật. Openbox dùng cấu hình hệ thống để không ép mọi cửa
sổ thành toàn màn hình. Các bài UI dùng JAR trong `dist/`, không chỉ class nguồn.

| Nhóm | Kết quả | Bằng chứng |
|---|---:|---|
| Clean build + style | Thành công | [ant-clean-verify.log](ant-clean-verify.log) |
| Hồi quy offline | 66 đạt / 0 lỗi | [unit-tests.log](../evidence/unit-tests.log) |
| API/JSON/độ bền | 47 đạt / 0 lỗi | [api-tests.log](../evidence/api-tests.log) |
| Ranh giới phân quyền | 17 đạt / 0 lỗi | [security-tests.log](../evidence/security-tests.log) |
| UiSmoke 1366×768 | 23 đạt / 0 lỗi | [ui-1366x768.log](ui-1366x768.log) |
| UiSmoke 1920×1080 | 23 đạt / 0 lỗi | [ui-1920x1080.log](ui-1920x1080.log) |
| RedUiChecks 1366×768 | 54 đạt / 0 lỗi | [checks-1366x768.log](checks-1366x768.log) |
| RedUiChecks 1920×1080 | 54 đạt / 0 lỗi | [checks-1920x1080.log](checks-1920x1080.log) |
| Khởi động JAR độc lập 1366×768 | Đạt | [jar-1366x768.log](jar-1366x768.log) |
| Khởi động JAR độc lập 1920×1080 | Đạt | [jar-1920x1080.log](jar-1920x1080.log) |

**Tổng: 284 kiểm thử đạt, 0 thất bại; thêm 2 lượt khởi động JAR độc lập.**
Các ca UI được chạy lại ở hai độ phân giải; con số 284 tính cả hai lượt này,
không phải 284 kịch bản nghiệp vụ khác nhau.

Lệnh cuối cùng:

```sh
sh scripts/verify-red-ui-linux.sh
```

[full-verification.log](full-verification.log) xác nhận cả chuỗi đã kết thúc.
JDK phát cảnh báo `this-escape` ở một số constructor Swing, nhưng không có lỗi
compile. Các log vòng sửa trung gian nằm riêng ở `history/`; chúng không phải
kết quả bàn giao cuối cùng.

### Phạm vi UI đã kiểm tra

UiSmoke mở 16 màn hình chức năng của quản lý và dashboard thu ngân/nhân viên kho:
nhân viên, sản phẩm/tồn kho, khách hàng, nhà cung cấp, loại hàng, thương hiệu,
nhập kho, khuyến mãi, bán hàng, chuyển kho/quầy, hóa đơn, lịch sử kho, lịch sử
serial/IMEI, bảo hành, báo cáo và cảnh báo tồn kho. Có kiểm tra menu, giỏ hàng
qua HTTP và khả năng phản hồi của Swing EDT.

RedUiChecks bổ sung title chính xác/không bị cắt, font tiếng Việt, không có thương
hiệu cũ, ảnh phủ vùng nền, ảnh đọc qua URL `jar:`, login thật qua HTTP fixture và
logout bằng xác nhận. Kiểm tra hộp thêm/sửa sản phẩm, hộp thêm khách hàng/nhà
cung cấp/nhân viên/khuyến mãi/loại hàng/thương hiệu, đổi mật khẩu và thông báo lỗi.

Bảng thử có **500 hàng × 22 cột**, tiếng Việt dài, số tiền lớn, số điện thoại có
số 0 đầu, hàng chọn, resize cột, lọc về rỗng và phục hồi. Đã kiểm tra thanh cuộn
ngang/dọc và chiều cao hàng xuống dòng.

Dashboard, sản phẩm, bán hàng và nhập kho được kiểm tra thêm tại **1180×700,
960×640 và 760×520**. Các màn hình được phép của thu ngân và nhân viên kho cũng
được mở riêng.

PackagedAppSmoke tạo tiến trình `java -jar` dùng entrypoint sản xuất từ thư mục làm
việc khác, xác nhận cửa sổ đăng nhập hiển thị/không có lỗi khởi động rồi kết thúc
tiến trình. Nó không đăng nhập vào backend SQL thật.

### Maven — chưa được chạy thành công

Môi trường không có lệnh `mvn` và không tải được công cụ qua mạng.
[maven-build-attempt.log](maven-build-attempt.log) ghi lỗi command not found,
exit code 127. **Không có kết quả Maven PASS.** Ant là đường build đã thực sự
chạy. Trên máy có Maven, dùng `scripts/build-maven.bat` hoặc
`sh scripts/build-maven.sh` để chạy `mvn verify` và cập nhật `dist/`.

## 4. Giữ nguyên nghiệp vụ và tài nguyên gốc

[source-integrity.json](source-integrity.json) đối chiếu SHA-256 với bản giải nén
gốc: **54/54 tệp trong phạm vi bảo vệ giống byte**. Phạm vi gồm backend,
common-dto, API/controller/state của client, AppContext, database và config.
POM cũng giữ nguyên. Đây là chứng cứ không sửa nguồn ở các khu vực đó, không
thay thế kiểm thử giao dịch trên database thật.

Ảnh nguồn và ảnh resource có cùng SHA-256:

```text
c2cd0191ada4ab64539169ad67fac0774f56a18225b682158faa54ee3be656ad
```

Không đổi ảnh, màu ảnh, nhân vật hoặc biểu tượng gốc. Không tạo schema, migration,
tài khoản hoặc dữ liệu mới cho mục đích thay theme.

## 5. Ảnh thực tế

[gallery.html](gallery.html) gom **74 ảnh PNG**: 40 ảnh màn hình, 32 ảnh hộp thoại/
dữ liệu/resize và 2 ảnh khởi động `java -jar`, chia đều cho 1366×768 và 1920×1080.

Ảnh đăng nhập từ `standalone/` là entrypoint ứng dụng thật.
Ảnh màn hình sau đăng nhập dùng ứng dụng Swing thật với server HTTP fixture và
dữ liệu kiểm thử; dòng trạng thái có ghi rõ phạm vi. Không phải ảnh dựng,
không phải ứng dụng giả, nhưng **không phải dữ liệu cửa hàng hoặc SQL Server thật**.
Bộ fixture chỉ nằm trong mã kiểm thử, không thay luồng runtime của sản phẩm.

Ảnh resize được chụp toàn màn hình desktop nên có thể thấy cửa sổ đang kiểm tra
nằm trước cửa sổ ứng dụng khác. Đây là bằng chứng resize, không phải lỗi trùng
cửa sổ trong thiết kế.

## 6. Hướng dẫn sử dụng bản bàn giao

Đọc [README_RED_UI.md](../../README_RED_UI.md) ở gốc.
Giữ `dist/client-swing.jar` cạnh `dist/common-dto.jar`. Có Java/JDK 17+ là chạy
được client đã đóng gói; chỉ backend cần SQL Server/driver/cấu hình kết nối.

Windows PowerShell, backend đã chạy:

```powershell
$env:DIENMAY_API_URL = 'http://127.0.0.1:8080'
.\scripts\run-client.bat
```

Backend cùng máy, terminal riêng với cấu hình SQL đang dùng:

```powershell
.\scripts\run-backend.bat
```

Không chạy lại script tạo bảng hoặc nạp dữ liệu chỉ vì thay UI.
Cấu hình và tài khoản thật của người sử dụng vẫn cần được cung cấp như bản gốc.

## 7. Giới hạn nghiệm thu và nội dung gói ZIP

Chưa có SQL Server thật để kiểm chứng end-to-end thêm/sửa/xóa/thanh toán/báo cáo,
khóa giao dịch, tồn kho và đồng thời nhiều người dùng. Bộ kiểm thử hiện tại sử
dụng HTTP fixture và JDBC test doubles; không diễn giải chúng thành kiểm chứng
database sản xuất.

Chưa kiểm tra trực tiếp Windows, DPI 125%/150%/200%, máy in, thiết bị quét hoặc
hộp thoại in gốc của hệ điều hành. Màu thanh tiêu đề ngoài cùng do hệ điều hành/
window manager quyết định; phần header do ứng dụng vẽ đã dùng theme đỏ.
Phân quyền không bị đổi nguồn và các bài kiểm tra hiện có đạt, nhưng triển khai
thật vẫn cần nghiệm thu với tài khoản và database của cửa hàng.

ZIP chứa toàn bộ mã nguồn, resource, cấu hình và SQL gốc, script, JAR mới, báo cáo,
log và ảnh. Các thư mục sinh tự động `build/`, `target/` không được đưa vào ZIP
để tránh nhầm với binary cũ. `SHA256SUMS.txt` được tạo lại cho đúng tệp bàn giao.
Các tài liệu gốc như `ACCEPTANCE_REPORT.md`/`README_RUN.md` vẫn được giữ và không
phải bằng chứng của vòng chỉnh giao diện này; báo cáo hiện tại là điểm đọc chính.
