-- DEMO ONLY. Synthetic prices, names and stock. Never run on a live shop database.
-- All 3 accounts must change their public demo password at first login.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM dm.Employees WHERE UserName IN(N'manager.demo',N'cashier.demo',N'warehouse.demo'))
    THROW 51001, N'Dữ liệu demo đã tồn tại. Không nạp lại tồn kho.', 1;
INSERT dm.Employees(UserName,FullName,PasswordHash,Role,MustChange) VALUES
(N'manager.demo',N'Quản lý mẫu','pbkdf2_sha256$600000$ShPhF4oz6YNpPKtN3e95SA==$t524+flRe6BMxDNvKLYJl3M8tlphTgDWBs0Daf5ZQXQ=','MANAGER',1),
(N'cashier.demo',N'Thu ngân mẫu','pbkdf2_sha256$600000$ARfVxu1pnRSkxQgz0P3RPg==$OuvHkxEu9oEjrDwxcYGrbR03/SnXEbwHhAjJF+OgzEU=','CASHIER',1),
(N'warehouse.demo',N'Nhân viên kho mẫu','pbkdf2_sha256$600000$sIEve/URwwMYXXU5ObpVGw==$2783nVsf0PVjXHvNszEzOxe2b8LLIvYCJNaeQUMgzPY=','WAREHOUSE',1);
INSERT dm.Categories(Name) VALUES(N'Điện thoại'),(N'Laptop'),(N'TV'),(N'Tủ lạnh'),(N'Máy giặt'),(N'Máy lạnh'),(N'Thiết bị gia dụng'),(N'Phụ kiện điện tử');
INSERT dm.Brands(Name) VALUES(N'DemoTech');
INSERT dm.Vendors(Name,Phone,Address) VALUES(N'Nhà cung cấp mẫu',N'0900000001',N'Địa chỉ mẫu — thay trước khi vận hành');
INSERT dm.Customers(FullName,Phone,Address) VALUES(N'Khách hàng mẫu',N'0900000000',N'Địa chỉ minh họa');
DECLARE @brand bigint=(SELECT Id FROM dm.Brands WHERE Name=N'DemoTech'),
        @phoneCategory bigint=(SELECT Id FROM dm.Categories WHERE Name=N'Điện thoại'),
        @accessoryCategory bigint=(SELECT Id FROM dm.Categories WHERE Name=N'Phụ kiện điện tử'),
        @manager bigint=(SELECT Id FROM dm.Employees WHERE UserName=N'manager.demo');
INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,CostPrice,SellingPrice,WarrantyMonths,TrackSerial,ReorderLevel)
VALUES(N'DT-DEMO-01',N'0001234567890',N'Điện thoại Demo 128 GB',@phoneCategory,@brand,N'Demo Phone',N'Sản phẩm minh họa, không phải thông số/giá thị trường.',5000000,6500000,12,1,2),
(N'PK-CAP-01',N'0000000000017',N'Cáp USB-C Demo',@accessoryCategory,@brand,N'Cable-1',N'Phụ kiện không theo dõi Serial.',60000,100000,6,0,5);
DECLARE @phone bigint=(SELECT Id FROM dm.Products WHERE SKU=N'DT-DEMO-01'),@cable bigint=(SELECT Id FROM dm.Products WHERE SKU=N'PK-CAP-01');
INSERT dm.ProductCodes(Code,ProductId) SELECT SKU,Id FROM dm.Products WHERE Id IN(@phone,@cable);
INSERT dm.ProductCodes(Code,ProductId) SELECT Barcode,Id FROM dm.Products WHERE Id IN(@phone,@cable);
INSERT dm.Stock(ProductId,Location,Quantity) VALUES(@phone,'KHO',2),(@phone,'QUAY',1),(@cable,'KHO',10),(@cable,'QUAY',5);
INSERT dm.ProductUnits(ProductId,Serial,Location) VALUES(@phone,N'PHONE-DEMO-0001','QUAY'),(@phone,N'PHONE-DEMO-0002','KHO'),(@phone,N'PHONE-DEMO-0003','KHO');
DECLARE @request uniqueidentifier=NEWID();
INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,EmployeeId)
SELECT @request,ProductId,Location,Quantity,'OPENING',N'DEMO: tồn đầu kỳ minh họa',@manager FROM dm.Stock WHERE ProductId IN(@phone,@cable);
INSERT dm.StockMovementUnits(MovementId,UnitId)
SELECT m.Id,u.Id FROM dm.StockMovements m JOIN dm.ProductUnits u ON u.ProductId=m.ProductId AND u.Location=m.Location WHERE m.RequestId=@request;
DECLARE @today date=CONVERT(date,DATEADD(hour,7,SYSUTCDATETIME()));
INSERT dm.Promotions(Code,Name,StartDate,EndDate,PercentOff) VALUES(N'DEMO5',N'Giảm giá demo 5%',@today,DATEADD(month,1,@today),5);
COMMIT;
GO
