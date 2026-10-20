# Điện Máy — Java Swing + Microsoft SQL Server

Bản nâng cấp trực tiếp từ ZIP `Sales-And-Inventory-Management-System-main` do người dùng cung cấp, nguồn gốc `hassannaeem53/Sales-And-Inventory-Management-System`. Giữ nền Swing, sáu form NetBeans chính, tài nguyên ảnh và mô hình cửa hàng/kho; thay các luồng nghiệp vụ chỉ lưu RAM bằng Service/DAO/JDBC.

> **Trạng thái bàn giao 19/10/2026:** build Java và 63 kiểm thử offline thành công; sáu màn hình mở được trong Xvfb; JAR mở cửa sổ đăng nhập. **Chưa nghiệm thu SQL Server thực tế, NetBeans/Windows, máy quét hoặc máy in.** Microsoft JDBC Driver **chưa được đóng kèm** do môi trường build không phân giải được DNS Maven Central. Phải bổ sung driver và chạy bộ test SQL Server trước khi dùng dữ liệu thật. Chi tiết: `docs/TEST-RESULTS.md`.

## 1. Các chức năng đã có mã triển khai

| Phân hệ | Phạm vi |
|---|---|
| Đăng nhập | PBKDF2 có salt riêng, 600.000 vòng; vai trò từ SQL; kiểm tra quyền tại Service; đổi mật khẩu, đăng xuất, khóa tài khoản, thu hồi phiên khi đổi quyền. Sai 5 lần tạm khóa 5 phút. |
| Hàng hóa | SKU và Barcode dạng chuỗi, tên tiếng Việt, danh mục, thương hiệu, model, mô tả, giá nhập/bán, bảo hành mặc định, ngừng kinh doanh; bảo vệ sửa đồng thời bằng Revision. |
| Kho | KHO/QUAY riêng; nhập cộng tồn; chuyển trừ nguồn/cộng đích; lịch sử biến động và Serial; mã yêu cầu chống ghi lặp. |
| POS | Nhập SKU/Barcode hoặc USB HID + Enter; giỏ gộp cùng sản phẩm; chọn Serial cụ thể; giảm trực tiếp hoặc mã khuyến mãi; tiền mặt/chuyển khoản. |
| Hóa đơn | Ghi header, chi tiết, trừ kho, Serial, bảo hành trong một transaction; tìm kiếm, xem lại và in bằng Java Print; lưu đơn giá tại thời điểm bán. |
| Bảo hành | Snapshot thời hạn khi bán; từng Serial hoặc số lượng trên chi tiết hóa đơn; tìm mã BH/HD/IMEI/điện thoại; tiếp nhận, xử lý, hoàn tất/từ chối và lịch sử trạng thái. |
| Báo cáo | Ngày, tháng, khoảng ngày; số hóa đơn, doanh thu thuần, nhân viên, hàng bán chạy, tồn thấp. Nguồn dữ liệu là SQL Server. |

Ứng dụng không bán offline và không dùng một database giả thay SQL Server. Danh sách trong RAM chỉ là giỏ chưa thanh toán hoặc dữ liệu đọc để hiển thị. Không có cổng thanh toán, hóa đơn điện tử thuế, đổi trả hoặc hệ thống trung tâm sửa chữa.

## 2. Môi trường và cấu trúc

Biên dịch với `--release 17`; môi trường đã chạy là OpenJDK 21.0.12.1, Ant 1.10.15, Linux + Xvfb. Mục tiêu triển khai là JDK 17/21 và Microsoft SQL Server/Express với TCP/IP. Runtime JDK 17, Windows và SQL Server chưa được chạy tại môi trường bàn giao.

```text
Code/                 Model, tiền, giỏ, request, receipt, bootstrap CLI
GUI/                  Swing; 6 cặp .java/.form gốc được tiếp tục sử dụng
Service/              Xác thực, quyền, giao dịch, bảo hành, báo cáo, phục hồi request
ImplementationDB/     SQL connection boundary, JDBC helpers, DAO
Images/               55 tài nguyên ảnh từ dự án gốc
config/               Chỉ có database.properties.example, không có mật khẩu SQL thật
database/             Tạo mới, demo, staging/migration, đối soát, quyền runtime
tests/                UnitTests, SqlServerIT, UiSmoke, TestKit
scripts/              Build/run không cần Maven; tải JDBC Driver
lib/                  Đặt mssql-jdbc-13.4.0.jre11.jar tại đây
dist/DienMay.jar       JAR đã build, chưa có JDBC Driver
docs/                 Thiết kế, migration, kiểm thử, kết quả và bằng chứng
```

Các tài liệu `.docx` và sơ đồ cũ trong repository vẫn được giữ như tư liệu upstream, **không phải hướng dẫn của bản nâng cấp**. README cũ được giữ tại `docs/upstream/README-original.md`. Không tự gán giấy phép mới cho mã nguồn/tài nguyên gốc.

## 3. Cài mới database — không chạy migration trên dữ liệu thật lần đầu

Trong SSMS, dùng tài khoản DBA riêng:

1. Chạy `database/000_create_database.sql` để tạo/chọn `DienMay`.
2. Đảm bảo database đang chọn là `DienMay`, chạy `database/001_schema.sql` **một lần**. Script tạo schema `dm`, không sửa `dbo`.
3. Chỉ cho môi trường demo trống: chạy `database/002_demo.sql`. Sản xuất thì bỏ qua bước này và tạo quản lý ban đầu bằng `DatabaseTool bootstrap` ở mục 6.
4. Tạo SQL login riêng tên `dienmay_app` bằng SSMS, đặt mật khẩu mạnh riêng tư. Chạy `database/006_permissions.example.sql` trong đúng database. Không cấp `sysadmin`/`db_owner` cho login chạy ứng dụng.

Script `sdaproject.sql` ở gốc chỉ là entrypoint SQLCMD gọi 000 và 001. Khi dùng `sqlcmd`, đứng tại thư mục gốc dự án và dùng `-b` để dừng khi có lỗi. Khi dùng SSMS thông thường, mở và chạy từng file như trên; không chạy các lệnh `:r` ngoài SQLCMD mode. Không chạy lại 001 trên schema đã có.

Cấu hình mẫu dùng SQL Authentication: SQL Server phải cho phép SQL login, không chỉ Windows Authentication. Việc cấu hình chế độ xác thực thuộc trách nhiệm DBA.

Với Express, cấu hình TCP/IP và một cổng cố định là cách đơn giản để điền URL. File mẫu cũng có ví dụ named instance. Cần cho phép kết nối TCP đúng cổng trên firewall theo mạng nội bộ thực tế; không mở SQL Server công khai ra Internet.

## 4. Driver, cấu hình và khởi chạy

Dự án cố định dependency `com.microsoft.sqlserver:mssql-jdbc:13.4.0.jre11`. Đây là phiên bản được chọn cho build, không phải tuyên bố về phiên bản mới nhất.

### Windows, không bắt buộc Maven

Chạy từ thư mục gốc dự án trong PowerShell/Command Prompt, với `java`, `javac`, `jar` của JDK có trên PATH:

```powershell
powershell -NoProfile -File scripts\fetch-driver.ps1
Copy-Item config\database.properties.example config\database.properties
notepad config\database.properties
```

Nếu policy chặn PowerShell script, xem nội dung script trước và dùng phương thức tải driver được tổ chức cho phép, hoặc dùng Maven ở mục NetBeans. Không tải JAR từ nguồn lạ. File phải nằm tại `lib/mssql-jdbc-13.4.0.jre11.jar`.

Sửa `db.url`, `db.user`, tên/địa chỉ/số điện thoại cửa hàng. Không đưa `database.properties` thật vào Git. Có thể truyền mật khẩu SQL qua biến môi trường của phiên terminal:

```powershell
$secret = Read-Host "Mật khẩu SQL login dienmay_app" -AsSecureString
$env:DIENMAY_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secret).Password
.\scripts\build.bat
java -cp "dist\DienMay.jar;lib\*" Code.DatabaseTool check
.\scripts\run.bat
```

`DIENMAY_DB_URL`, `DIENMAY_DB_USER`, `DIENMAY_DB_PASSWORD` ghi đè từng trường trong cấu hình. Đường dẫn cấu hình riêng có thể đặt bằng `DIENMAY_CONFIG` hoặc `-Ddienmay.config=...`. Xóa biến mật khẩu khi kết thúc terminal dùng chung.

File mẫu bật mã hóa và xác thực chứng chỉ. Chỉ trong máy phát triển cục bộ dùng chứng chỉ tự ký mới cân nhắc `trustServerCertificate=true`; triển khai thật phải cấu hình chứng chỉ được tin cậy. Ứng dụng không tự hạ mức kiểm tra TLS.

### Linux / macOS có JDK

```sh
sh scripts/fetch-driver.sh
cp config/database.properties.example config/database.properties
# Sửa cấu hình, cung cấp DIENMAY_DB_PASSWORD qua môi trường riêng tư.
sh scripts/build.sh
java -cp 'dist/DienMay.jar:lib/*' Code.DatabaseTool check
sh scripts/run.sh
```

Có Ant thì dùng `ant clean dist`, `ant check-db`, `ant run`. Các script run tự chuyển về thư mục dự án. Không nên chỉ nhấp đúp JAR vì đường dẫn cấu hình tương đối có thể sai. `java -jar dist/DienMay.jar` cần driver trong `dist/lib/`; chạy build sau khi tải driver sẽ chép driver vào đó.

### NetBeans / Maven

Mở thư mục chứa `pom.xml` bằng **Open Project**, chọn JDK 17 hoặc 21. Source vẫn nằm tại `Code`, `GUI`, `ImplementationDB`, `Service`, không cần chuyển sang một ứng dụng khác. Build Maven sẽ tải driver và các plugin; main class là `GUI.App`.

```sh
mvn clean package
mvn exec:java -Dexec.mainClass=GUI.App
```

JAR Maven nằm trong `target/`, thư viện trong `target/lib/`. Sáu file `.form` đi cùng sáu Java form gốc. Menu, controller và phần footer bổ sung được nối bằng Java ngoài vùng layout generated. **Maven, thao tác Designer và việc lưu lại form trong NetBeans chưa được kiểm chứng ở đây**; chỉ Ant/javac và tính hợp lệ XML của `.form` được kiểm tra.

## 5. Tài khoản và dữ liệu demo

**Chỉ dùng trên database demo.** Mật khẩu dưới đây là công khai; SQL chỉ lưu hash riêng có salt, không lưu chuỗi mật khẩu này. Tất cả yêu cầu đổi mật khẩu ngay lần đăng nhập đầu.

| Vai trò | Tài khoản | Mật khẩu ban đầu |
|---|---|---|
| MANAGER | `manager.demo` | `DienMay!Manager2026` |
| CASHIER | `cashier.demo` | `DienMay!Cashier2026` |
| WAREHOUSE | `warehouse.demo` | `DienMay!Warehouse2026` |

Sản phẩm mẫu: `PK-CAP-01`, barcode `0000000000017`, giá bán 100.000 VND, bảo hành 6 tháng, KHO 10 / QUAY 5, không bắt buộc Serial. `DT-DEMO-01`, barcode `0001234567890`, giá bán 6.500.000 VND, bảo hành 12 tháng; `PHONE-DEMO-0001` ở QUAY, `PHONE-DEMO-0002` và `PHONE-DEMO-0003` ở KHO. Mã `DEMO5` giảm 5% trong thời gian hiệu lực được seed. Không có hóa đơn mẫu được tự tạo.

Không chạy seed demo trên dữ liệu sản xuất. Không giữ các tài khoản demo sau khi chuyển sang vận hành thật.

## 6. Database mới không dùng demo

Sau khi tạo schema và quyền kết nối, chạy trong terminal có Console:

```powershell
java -cp "dist\DienMay.jar;lib\*" Code.DatabaseTool bootstrap quanly
```

CLI hỏi và xác nhận mật khẩu, không nhận mật khẩu qua tham số dòng lệnh. Chỉ tạo quản lý khi bảng nhân viên đang trống. Sau đó đăng nhập quản lý, tạo danh mục/thương hiệu/nhà cung cấp, sản phẩm và nhân viên. Không có tài khoản mặc định ẩn. CLI `hash` phục vụ DBA tạo hash mới cho migration, cũng hỏi mật khẩu không hiển thị.

## 7. Luồng sử dụng chính

Nhân viên kho vào **Nghiệp vụ → Nhập hàng**, chọn SKU/Barcode, số lượng, nhà cung cấp, giá nhập, chứng từ/lý do; nhập đủ Serial nếu cấu hình theo từng chiếc. Hàng được cộng vào **KHO**. Chuyển KHO → QUAY trước khi bán; có thể chuyển ngược nếu cần. Không sửa số tồn trực tiếp trong form sản phẩm.

Thu ngân vào **Bán hàng**, đặt con trỏ vào **Mã hàng**. Enter luôn xử lý một chiếc như máy quét HID; nhập tay số lượng lớn thì chọn **SL** và bấm **Thêm**. Quét cùng mã hai lần sẽ gộp vào một dòng. Hàng có Serial phải chọn đúng thiết bị thực tế. Dùng **Tìm sản phẩm** để tra cứu; mã khách hàng có thể để trống cho khách lẻ. **Tìm KH** kiểm tra mã khách đã nhập; màn hình **Khách hàng** cho phép thêm/sửa thông tin cơ bản.

Thanh toán chọn tiền mặt/chuyển khoản, giảm trực tiếp hoặc mã khuyến mãi (không cộng chồng hai loại), xem lại tổng tiền rồi xác nhận. Nếu giá/khuyến mãi/tồn đã thay đổi, transaction bị từ chối. In và in lại từ hóa đơn đã lưu không tạo lượt bán mới. Việc ghi nhận phương thức thanh toán không xác minh tiền từ ngân hàng; khoản tiền đã nhận ngoài phần mềm phải được nhân viên đối soát nếu ghi hóa đơn thất bại.

Yêu cầu bán/nhập/chuyển chưa rõ kết quả được lưu dưới thư mục hồ sơ `~/.dienmay/<profile>/`. **Không xóa tệp chờ hoặc đổi cấu hình database để né lỗi.** Dùng **Đối soát** hoặc **Thử lại cùng yêu cầu**. Chỉ bỏ yêu cầu sau khi SQL Server xác nhận chưa ghi hoặc đã tìm được chứng từ tương ứng. Mỗi tài khoản/chức năng giữ một khóa file trên máy; không mở thêm POS để tránh xử lý yêu cầu cũ.

Bảo hành được tạo cùng transaction bán nếu thời hạn lớn hơn 0. Tra cứu theo BH/HD/Serial/IMEI/điện thoại, chọn hồ sơ để tiếp nhận. Thu ngân được tiếp nhận; quản lý đổi trạng thái. Hết hạn vẫn có thể tiếp nhận theo chính sách cửa hàng. Hàng không Serial dùng hồ sơ theo chi tiết hóa đơn và số lượng được bảo hành, không giả lập các serial không tồn tại.

## 8. Kiểm thử và triển khai an toàn

Đọc `docs/TESTING.md`, `docs/TEST-RESULTS.md`, `docs/MIGRATION.md`, `docs/ARCHITECTURE.md`. Trước vận hành: chạy bộ SQL Server integration trên database riêng có hậu tố `_Test`, đối soát kho/đơn/bảo hành bằng `005_reconciliation.sql`, kiểm tra hai máy bán đồng thời, đóng/mở ứng dụng, máy quét và máy in thật. Sao lưu và thử khôi phục database trước migration.

Đây là bản nguồn có kiểm thử offline và đường triển khai đầy đủ, **chưa phải bản đã đạt toàn bộ Definition of Done trên SQL Server thực tế**.
