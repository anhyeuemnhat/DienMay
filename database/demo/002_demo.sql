-- OPT-IN DEMO ONLY. All prices, quantities, transactions, contacts and warranty policy are SIMULATED.
-- Official product identities are separately evidenced in database/reference/catalog.json.
-- No default password, genuine customer data, guessed GTIN or realistic IMEI is included.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF RIGHT(DB_NAME(),5)<>N'_Demo' OR NOT EXISTS(SELECT 1 FROM dm.DeploymentInfo WHERE Id=1 AND Mode='DEMO')
    THROW 51210,N'Demo seeding requires an explicitly classified *_Demo database.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=4)
    THROW 51211,N'Schema version 4 is required.',1;
IF EXISTS(SELECT 1 FROM dm.DeploymentInfo WHERE DatasetVersion=N'sony-lg-demo-2026-10-31-v1')
BEGIN
    COMMIT;
    PRINT N'DEMO already installed; no records changed.';
    RETURN;
END;
IF EXISTS(SELECT 1 FROM dm.Products) OR EXISTS(SELECT 1 FROM dm.Categories)
   OR EXISTS(SELECT 1 FROM dm.Brands) OR EXISTS(SELECT 1 FROM dm.Customers)
   OR EXISTS(SELECT 1 FROM dm.Vendors) OR EXISTS(SELECT 1 FROM dm.Promotions)
   OR EXISTS(SELECT 1 FROM dm.StockMovements) OR EXISTS(SELECT 1 FROM dm.Invoices)
   OR EXISTS(SELECT 1 FROM dm.InventoryRequests) OR EXISTS(SELECT 1 FROM dm.RequestCancellations)
   OR EXISTS(SELECT 1 FROM dm.DeploymentInfo WHERE DatasetVersion<>N'')
    THROW 51212,N'Nonempty business data. Demo import refused; nothing is replaced.',1;
DECLARE @manager bigint=(SELECT MIN(Id) FROM dm.Employees WHERE Role='MANAGER' AND Enabled=1);
IF @manager IS NULL THROW 51213,N'Bootstrap your manager account before seeding. No default password exists.',1;
DECLARE @now datetime2(3)=SYSUTCDATETIME(), @day date=CONVERT(date,DATEADD(hour,7,SYSUTCDATETIME()));
DECLARE @evidence nvarchar(1000)=N'Official page checked by bundle author 2026-10-31. DBA explicitly imports this reference into DEMO; bootstrap manager is import custodian, not an independent factual reviewer.';
IF NOT EXISTS(SELECT 1 FROM dm.ReferenceProducts WHERE BrandName=N'Sony' AND Model=N'WH-CH520'
    AND ProductName=N'Tai nghe không dây Sony WH-CH520' AND CategoryName=N'Tai nghe'
    AND Specifications=N'Driver 30 mm; Bluetooth 5.2; khối lượng khoảng 147 g.' AND SourceUrl=N'https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications')
    THROW 51214,N'Reference catalog is missing or differs. Review rather than overwrite.',1;
INSERT dm.Brands(Name,VerificationStatus,VerificationSource,VerificationNote,VerifiedBy,VerifiedAt)
VALUES(N'Sony','VERIFIED',N'https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications',@evidence,@manager,@now);
DECLARE @brand1 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Categories(Name,VerificationStatus,VerificationSource,VerificationNote,VerifiedBy,VerifiedAt)
VALUES(N'Tai nghe','VERIFIED',N'https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications',@evidence,@manager,@now);
DECLARE @category1 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,CostPrice,SellingPrice,
    WarrantyMonths,TrackSerial,ReorderLevel,VerificationStatus,VerificationSource,VerificationNote,
    VerifiedBy,VerifiedAt,BarcodeType,BarcodeStatus,IsDemo)
VALUES(N'DEMO-WH-CH520',NULL,N'Tai nghe không dây Sony WH-CH520',@category1,@brand1,N'WH-CH520',N'Driver 30 mm; Bluetooth 5.2; khối lượng khoảng 147 g.',
    700000,1000000,12,1,3,
    'VERIFIED',N'https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications',@evidence,@manager,@now,'NONE','PENDING',1);
DECLARE @p1 bigint=CONVERT(bigint,SCOPE_IDENTITY());
IF NOT EXISTS(SELECT 1 FROM dm.ReferenceProducts WHERE BrandName=N'LG' AND Model=N'24MR400-B'
    AND ProductName=N'Màn hình LG 24MR400-B' AND CategoryName=N'Màn hình máy tính'
    AND Specifications=N'23,8 inch; IPS; 1920 x 1080; tần số quét tối đa 100 Hz (D-sub tối đa 75 Hz).' AND SourceUrl=N'https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/')
    THROW 51214,N'Reference catalog is missing or differs. Review rather than overwrite.',1;
INSERT dm.Brands(Name,VerificationStatus,VerificationSource,VerificationNote,VerifiedBy,VerifiedAt)
VALUES(N'LG','VERIFIED',N'https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/',@evidence,@manager,@now);
DECLARE @brand2 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Categories(Name,VerificationStatus,VerificationSource,VerificationNote,VerifiedBy,VerifiedAt)
VALUES(N'Màn hình máy tính','VERIFIED',N'https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/',@evidence,@manager,@now);
DECLARE @category2 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,CostPrice,SellingPrice,
    WarrantyMonths,TrackSerial,ReorderLevel,VerificationStatus,VerificationSource,VerificationNote,
    VerifiedBy,VerifiedAt,BarcodeType,BarcodeStatus,IsDemo)
VALUES(N'DEMO-24MR400-B',NULL,N'Màn hình LG 24MR400-B',@category2,@brand2,N'24MR400-B',N'23,8 inch; IPS; 1920 x 1080; tần số quét tối đa 100 Hz (D-sub tối đa 75 Hz).',
    1800000,2400000,24,0,5,
    'VERIFIED',N'https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/',@evidence,@manager,@now,'NONE','PENDING',1);
DECLARE @p2 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.ProductCodes(Code,ProductId) SELECT SKU,Id FROM dm.Products WHERE Id IN(@p1,@p2);
INSERT dm.Vendors(Name,Phone,Address) VALUES(N'NCC DEMO - mo phong, khong phai doanh nghiep that',N'',N'DEMO - khong co dia chi that');
DECLARE @vendor bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Customers(FullName,Phone,Address) VALUES(N'Khach hang DEMO - nhan vat mo phong',N'',N'DEMO - khong co thong tin ca nhan that');
DECLARE @customer bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Promotions(Code,Name,StartDate,EndDate,PercentOff)
VALUES(N'DEMO5',N'DEMO - giam 5 phan tram, khong phai uu dai cua cua hang',@day,DATEADD(month,1,@day),5);
-- Receipt 4 serialized headphones + 8 monitors, transfer 2 + 3, sell 1 + 1.
INSERT dm.Stock(ProductId,Location,Quantity) VALUES(@p1,'KHO',2),(@p1,'QUAY',1),(@p2,'KHO',5),(@p2,'QUAY',2);
INSERT dm.ProductUnits(ProductId,Serial,Location,Status,IdentifierType,IdentifierSource) VALUES
(@p1,N'DEMO-SN-SONY-0001','QUAY','SOLD','SERIAL',N'DEMO-RECEIVE-SONY'),
(@p1,N'DEMO-SN-SONY-0002','QUAY','IN_STOCK','SERIAL',N'DEMO-RECEIVE-SONY'),
(@p1,N'DEMO-SN-SONY-0003','KHO','IN_STOCK','SERIAL',N'DEMO-RECEIVE-SONY'),
(@p1,N'DEMO-SN-SONY-0004','KHO','IN_STOCK','SERIAL',N'DEMO-RECEIVE-SONY');
DECLARE @s1 bigint=(SELECT Id FROM dm.ProductUnits WHERE Serial=N'DEMO-SN-SONY-0001');
DECLARE @s2 bigint=(SELECT Id FROM dm.ProductUnits WHERE Serial=N'DEMO-SN-SONY-0002');
DECLARE @r1 uniqueidentifier='00000000-0000-4000-8000-000000000001',
    @r2 uniqueidentifier='00000000-0000-4000-8000-000000000002',
    @t1 uniqueidentifier='00000000-0000-4000-8000-000000000003',
    @t2 uniqueidentifier='00000000-0000-4000-8000-000000000004',
    @sale uniqueidentifier='00000000-0000-4000-8000-000000000005';
INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId,CreatedAt)
VALUES(@r1,LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONCAT(CONCAT(LEN(CONVERT(varchar(max),@p1)),':',CONVERT(varchar(max),@p1)),CONCAT(LEN(CONVERT(varchar(max),'4')),':',CONVERT(varchar(max),'4')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-RECEIVE-SONY')),':',CONVERT(varchar(max),'DEMO-RECEIVE-SONY')),CONCAT(LEN(CONVERT(varchar(max),@vendor)),':',CONVERT(varchar(max),@vendor)),CONCAT(LEN(CONVERT(varchar(max),'700000.00')),':',CONVERT(varchar(max),'700000.00')),CONCAT(LEN(CONVERT(varchar(max),'null')),':',CONVERT(varchar(max),'null')),CONCAT(LEN(CONVERT(varchar(max),'KHO')),':',CONVERT(varchar(max),'KHO')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0001')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0001')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0002')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0002')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0003')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0003')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0004')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0004')))),2)),@manager,DATEADD(hour,-3,@now));
INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId,CreatedAt)
VALUES(@r2,LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONCAT(CONCAT(LEN(CONVERT(varchar(max),@p2)),':',CONVERT(varchar(max),@p2)),CONCAT(LEN(CONVERT(varchar(max),'8')),':',CONVERT(varchar(max),'8')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-RECEIVE-LG')),':',CONVERT(varchar(max),'DEMO-RECEIVE-LG')),CONCAT(LEN(CONVERT(varchar(max),@vendor)),':',CONVERT(varchar(max),@vendor)),CONCAT(LEN(CONVERT(varchar(max),'1800000.00')),':',CONVERT(varchar(max),'1800000.00')),CONCAT(LEN(CONVERT(varchar(max),'null')),':',CONVERT(varchar(max),'null')),CONCAT(LEN(CONVERT(varchar(max),'KHO')),':',CONVERT(varchar(max),'KHO')))),2)),@manager,DATEADD(hour,-3,@now));
INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId,CreatedAt)
VALUES(@t1,LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONCAT(CONCAT(LEN(CONVERT(varchar(max),@p1)),':',CONVERT(varchar(max),@p1)),CONCAT(LEN(CONVERT(varchar(max),'2')),':',CONVERT(varchar(max),'2')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-TRANSFER-SONY')),':',CONVERT(varchar(max),'DEMO-TRANSFER-SONY')),CONCAT(LEN(CONVERT(varchar(max),'null')),':',CONVERT(varchar(max),'null')),CONCAT(LEN(CONVERT(varchar(max),'0.00')),':',CONVERT(varchar(max),'0.00')),CONCAT(LEN(CONVERT(varchar(max),'KHO')),':',CONVERT(varchar(max),'KHO')),CONCAT(LEN(CONVERT(varchar(max),'QUAY')),':',CONVERT(varchar(max),'QUAY')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0001')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0001')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0002')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0002')))),2)),@manager,DATEADD(hour,-3,@now));
INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId,CreatedAt)
VALUES(@t2,LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONCAT(CONCAT(LEN(CONVERT(varchar(max),@p2)),':',CONVERT(varchar(max),@p2)),CONCAT(LEN(CONVERT(varchar(max),'3')),':',CONVERT(varchar(max),'3')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-TRANSFER-LG')),':',CONVERT(varchar(max),'DEMO-TRANSFER-LG')),CONCAT(LEN(CONVERT(varchar(max),'null')),':',CONVERT(varchar(max),'null')),CONCAT(LEN(CONVERT(varchar(max),'0.00')),':',CONVERT(varchar(max),'0.00')),CONCAT(LEN(CONVERT(varchar(max),'KHO')),':',CONVERT(varchar(max),'KHO')),CONCAT(LEN(CONVERT(varchar(max),'QUAY')),':',CONVERT(varchar(max),'QUAY')))),2)),@manager,DATEADD(hour,-3,@now));
DECLARE @saleHash char(64)=LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONCAT(CONCAT(LEN(CONVERT(varchar(max),@customer)),':',CONVERT(varchar(max),@customer)),CONCAT(LEN(CONVERT(varchar(max),'CASH')),':',CONVERT(varchar(max),'CASH')),CONCAT(LEN(CONVERT(varchar(max),'0.00')),':',CONVERT(varchar(max),'0.00')),CONCAT(LEN(CONVERT(varchar(max),'DEMO5')),':',CONVERT(varchar(max),'DEMO5')),CONCAT(LEN(CONVERT(varchar(max),'170000.00')),':',CONVERT(varchar(max),'170000.00')),CONCAT(LEN(CONVERT(varchar(max),@p1)),':',CONVERT(varchar(max),@p1)),CONCAT(LEN(CONVERT(varchar(max),'1')),':',CONVERT(varchar(max),'1')),CONCAT(LEN(CONVERT(varchar(max),'1000000.00')),':',CONVERT(varchar(max),'1000000.00')),CONCAT(LEN(CONVERT(varchar(max),'DEMO-SN-SONY-0001')),':',CONVERT(varchar(max),'DEMO-SN-SONY-0001')),CONCAT(LEN(CONVERT(varchar(max),'END')),':',CONVERT(varchar(max),'END')),CONCAT(LEN(CONVERT(varchar(max),@p2)),':',CONVERT(varchar(max),@p2)),CONCAT(LEN(CONVERT(varchar(max),'1')),':',CONVERT(varchar(max),'1')),CONCAT(LEN(CONVERT(varchar(max),'2400000.00')),':',CONVERT(varchar(max),'2400000.00')),CONCAT(LEN(CONVERT(varchar(max),'END')),':',CONVERT(varchar(max),'END')))),2));
INSERT dm.Invoices(RequestId,RequestHash,SoldAt,EmployeeId,CustomerId,CustomerName,CustomerPhone,
    Subtotal,Discount,Total,PaymentMethod,PromotionCode)
VALUES(@sale,@saleHash,@now,@manager,@customer,N'Khach hang DEMO - nhan vat mo phong',N'',3400000,170000,3230000,'CASH',N'DEMO5');
DECLARE @invoice bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.InvoiceDetails(InvoiceId,ProductId,SKU,ProductName,UnitPrice,Quantity,WarrantyMonths)
SELECT @invoice,Id,SKU,Name,SellingPrice,1,WarrantyMonths FROM dm.Products WHERE Id=@p1;
DECLARE @d1 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.InvoiceDetails(InvoiceId,ProductId,SKU,ProductName,UnitPrice,Quantity,WarrantyMonths)
SELECT @invoice,Id,SKU,Name,SellingPrice,1,WarrantyMonths FROM dm.Products WHERE Id=@p2;
DECLARE @d2 bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.InvoiceUnits(DetailId,UnitId) VALUES(@d1,@s1);
INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,EmployeeId,InvoiceId,VendorId,UnitCost,CreatedAt)
VALUES
(@r1,@p1,'KHO',4,'RECEIVE',N'DEMO-RECEIVE-SONY',@manager,NULL,@vendor,700000,DATEADD(hour,-3,@now)),
(@r2,@p2,'KHO',8,'RECEIVE',N'DEMO-RECEIVE-LG',@manager,NULL,@vendor,1800000,DATEADD(hour,-3,@now)),
(@t1,@p1,'KHO',-2,'TRANSFER',N'DEMO-TRANSFER-SONY',@manager,NULL,NULL,NULL,DATEADD(hour,-2,@now)),
(@t1,@p1,'QUAY',2,'TRANSFER',N'DEMO-TRANSFER-SONY',@manager,NULL,NULL,NULL,DATEADD(hour,-2,@now)),
(@t2,@p2,'KHO',-3,'TRANSFER',N'DEMO-TRANSFER-LG',@manager,NULL,NULL,NULL,DATEADD(hour,-2,@now)),
(@t2,@p2,'QUAY',3,'TRANSFER',N'DEMO-TRANSFER-LG',@manager,NULL,NULL,NULL,DATEADD(hour,-2,@now)),
(@sale,@p1,'QUAY',-1,'SALE',N'DEMO-SALE',@manager,@invoice,NULL,NULL,@now),
(@sale,@p2,'QUAY',-1,'SALE',N'DEMO-SALE',@manager,@invoice,NULL,NULL,@now);
INSERT dm.StockMovementUnits(MovementId,UnitId)
SELECT m.Id,u.Id FROM dm.StockMovements m CROSS JOIN dm.ProductUnits u
WHERE m.ProductId=@p1 AND u.ProductId=@p1 AND
(m.RequestId=@r1 OR (m.RequestId=@t1 AND u.Id IN(@s1,@s2)) OR (m.RequestId=@sale AND u.Id=@s1));
INSERT dm.Warranties(DetailId,UnitId,StartDate,EndDate,WarrantyMonths,CoveredQuantity)
VALUES(@d1,@s1,@day,DATEADD(month,12,@day),12,1);
DECLARE @warranty bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.Warranties(DetailId,UnitId,StartDate,EndDate,WarrantyMonths,CoveredQuantity)
VALUES(@d2,NULL,@day,DATEADD(month,24,@day),24,1);
INSERT dm.WarrantyClaims(WarrantyId,ReceivedBy,Fault,Status,Notes)
VALUES(@warranty,@manager,N'DEMO - tinh huong bao hanh mo phong, khong phai loi thiet bi that',
    'RECEIVED',N'DEMO - tap thao tac tiep nhan va chuyen trang thai');
DECLARE @claim bigint=CONVERT(bigint,SCOPE_IDENTITY());
INSERT dm.ClaimHistory(ClaimId,ChangedBy,OldStatus,NewStatus,Notes)
VALUES(@claim,@manager,NULL,'RECEIVED',N'DEMO - tiep nhan mo phong');
INSERT dm.AuditLog(EntityType,EntityId,Action,OldValue,NewValue,Reason,EmployeeId)
VALUES('deployment',1,'DEMO_IMPORT',N'',N'sony-lg-demo-2026-10-31-v1',
    N'Explicit DBA seed; all transactions/prices/quantity/warranty policy are simulated; no genuine personal/device data.',@manager);
UPDATE dm.DeploymentInfo SET DatasetVersion=N'sony-lg-demo-2026-10-31-v1' WHERE Id=1;
-- Reject inconsistent seed inside this same transaction, rather than publishing partial data.
IF EXISTS(SELECT 1 FROM dm.Stock s OUTER APPLY(SELECT SUM(m.Delta) q FROM dm.StockMovements m
    WHERE m.ProductId=s.ProductId AND m.Location=s.Location) x WHERE s.Quantity<>COALESCE(x.q,0))
    THROW 51215,N'Demo ledger reconciliation failed; transaction rolled back.',1;
IF EXISTS(SELECT 1 FROM dm.Stock s JOIN dm.Products p ON p.Id=s.ProductId AND p.TrackSerial=1
    OUTER APPLY(SELECT COUNT(*) q FROM dm.ProductUnits u WHERE u.ProductId=s.ProductId
    AND u.Location=s.Location AND u.Status='IN_STOCK') x WHERE s.Quantity<>x.q)
    THROW 51216,N'Demo unit reconciliation failed; transaction rolled back.',1;
COMMIT;
PRINT N'DEMO installed. Run database/checks/009_reconciliation.sql and the application before acceptance.';
GO
