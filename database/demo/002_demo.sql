-- OPT-IN SYNTHETIC DATA ONLY; NEVER use a live shop database.
-- No demo passwords, accounts or hashes are bundled. Bootstrap a manager first.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM dm.Products) OR EXISTS(SELECT 1 FROM dm.Categories)
    THROW 51001,N'Demo requires an empty catalog. No existing stock will be changed.',1;
DECLARE @manager bigint=(SELECT MIN(Id) FROM dm.Employees WHERE Role='MANAGER' AND Enabled=1);
IF @manager IS NULL THROW 51002,N'Bootstrap a manager before loading demo data.',1;
INSERT dm.Categories(Name) VALUES(N'Dien thoai'),(N'Phu kien dien tu');
INSERT dm.Brands(Name) VALUES(N'DemoTech');
INSERT dm.Vendors(Name,Phone,Address) VALUES(N'Nha cung cap mau',N'0900000001',N'Dia chi minh hoa');
INSERT dm.Customers(FullName,Phone,Address) VALUES(N'Khach hang mau',N'0900000000',N'Dia chi minh hoa');
DECLARE @brand bigint=(SELECT Id FROM dm.Brands WHERE Name=N'DemoTech'),
    @phoneCategory bigint=(SELECT Id FROM dm.Categories WHERE Name=N'Dien thoai'),
    @accessoryCategory bigint=(SELECT Id FROM dm.Categories WHERE Name=N'Phu kien dien tu');
INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,CostPrice,SellingPrice,
    WarrantyMonths,TrackSerial,ReorderLevel) VALUES
(N'DT-DEMO-01',N'0001234567890',N'Dien thoai Demo 128 GB',@phoneCategory,@brand,N'Demo Phone',
    N'Synthetic data, not market specifications or prices',5000000,6500000,12,1,2),
(N'PK-CAP-01',N'0000000000017',N'Cap USB-C Demo',@accessoryCategory,@brand,N'Cable-1',
    N'Non-serialized accessory',60000,100000,6,0,5);
DECLARE @phone bigint=(SELECT Id FROM dm.Products WHERE SKU=N'DT-DEMO-01'),
    @cable bigint=(SELECT Id FROM dm.Products WHERE SKU=N'PK-CAP-01');
INSERT dm.ProductCodes(Code,ProductId) SELECT SKU,Id FROM dm.Products WHERE Id IN(@phone,@cable);
INSERT dm.ProductCodes(Code,ProductId) SELECT Barcode,Id FROM dm.Products WHERE Id IN(@phone,@cable);
INSERT dm.Stock(ProductId,Location,Quantity) VALUES
    (@phone,'KHO',2),(@phone,'QUAY',1),(@cable,'KHO',10),(@cable,'QUAY',5);
INSERT dm.ProductUnits(ProductId,Serial,Location) VALUES
    (@phone,N'PHONE-DEMO-0001','QUAY'),(@phone,N'PHONE-DEMO-0002','KHO'),(@phone,N'PHONE-DEMO-0003','KHO');
DECLARE @request uniqueidentifier=NEWID();
INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,EmployeeId)
SELECT @request,ProductId,Location,Quantity,'OPENING',N'DEMO opening stock',@manager
FROM dm.Stock WHERE ProductId IN(@phone,@cable);
INSERT dm.StockMovementUnits(MovementId,UnitId)
SELECT m.Id,u.Id FROM dm.StockMovements m JOIN dm.ProductUnits u
ON u.ProductId=m.ProductId AND u.Location=m.Location WHERE m.RequestId=@request;
DECLARE @today date=CONVERT(date,DATEADD(hour,7,SYSUTCDATETIME()));
INSERT dm.Promotions(Code,Name,StartDate,EndDate,PercentOff)
VALUES(N'DEMO5',N'Demo 5 percent',@today,DATEADD(month,1,@today),5);
COMMIT;
GO
