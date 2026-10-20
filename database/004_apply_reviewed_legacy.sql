-- Run ONLY on a backed-up test copy first; stop the old app during final cutover.
-- Fill review fields and Approved=1 explicitly in dm.Legacy*Stage before execution.
-- Example: map Categories/Brands, reconcile physical stock, set BOTH prices and warranty,
-- supply all Serial/IMEI for TrackSerial=1. Do not approve irrelevant food/medicine SKUs.
-- For account hashes use Code.DatabaseTool hash (does not echo the password).
SET XACT_ABORT ON;
DECLARE @ApproverUserName nvarchar(64)=N'CHANGE_ME'; -- Active MANAGER who approved reconciliation.
DECLARE @actor bigint=(SELECT Id FROM dm.Employees WHERE UserName=@ApproverUserName AND Role='MANAGER' AND Enabled=1);
IF @actor IS NULL THROW 51200,N'Điền tài khoản quản lý duyệt dữ liệu vào @ApproverUserName.',1;
IF OBJECT_ID(N'dm.LegacyProductStage',N'U') IS NULL THROW 51201,N'Chưa tạo vùng đối soát.',1;
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'dm:legacy-import',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
IF @lock<0 THROW 51202,N'Đang có phiên chuyển dữ liệu khác.',1;
IF EXISTS(SELECT 1 FROM dm.LegacyProductStage WHERE Approved=1 AND NewProductId IS NULL AND
    (NULLIF(LTRIM(RTRIM(TargetName)),N'') IS NULL OR CategoryId IS NULL OR BrandId IS NULL OR CostPrice IS NULL OR SellingPrice IS NULL
     OR CostPrice<0 OR SellingPrice<0 OR WarrantyMonths IS NULL OR WarrantyMonths NOT BETWEEN 0 AND 120 OR TrackSerial IS NULL
     OR SourceCounterQuantity IS NULL OR SourceWarehouseQuantity IS NULL OR SourceCounterQuantity NOT BETWEEN 0 AND 2147483647
     OR SourceWarehouseQuantity NOT BETWEEN 0 AND 2147483647 OR SourceBadBatchCount<>0 OR ReorderLevel<0 OR NULLIF(LTRIM(RTRIM(ReviewNote)),N'') IS NULL))
    THROW 51203,N'Sản phẩm đã duyệt còn thiếu ánh xạ/giá/bảo hành/ghi chú hoặc tồn nguồn không hợp lệ.',1;
IF EXISTS(SELECT 1 FROM dm.LegacyProductStage p WHERE p.Approved=1 AND p.NewProductId IS NULL AND p.TrackSerial=1 AND
    ((SELECT COUNT_BIG(*) FROM dm.LegacySerialStage s WHERE s.LegacySKU=p.LegacySKU AND s.Location='KHO')<>p.SourceWarehouseQuantity OR
     (SELECT COUNT_BIG(*) FROM dm.LegacySerialStage s WHERE s.LegacySKU=p.LegacySKU AND s.Location='QUAY')<>p.SourceCounterQuantity))
    THROW 51204,N'Số lượng Serial tại mỗi vị trí chưa khớp tồn đã đối soát.',1;
IF EXISTS(SELECT 1 FROM dm.LegacyProductStage p JOIN dm.LegacySerialStage s ON s.LegacySKU=p.LegacySKU WHERE p.Approved=1 AND p.TrackSerial=0)
    THROW 51205,N'Sản phẩm không theo Serial nhưng có Serial trong vùng đối soát.',1;
IF EXISTS(SELECT 1 FROM dm.LegacyAccountStage WHERE Approved=1 AND NewEmployeeId IS NULL AND
    (NewRole IS NULL OR NewRole NOT IN('MANAGER','CASHIER','WAREHOUSE') OR NewPasswordHash IS NULL OR NewPasswordHash NOT LIKE 'pbkdf2_sha256$600000$%'))
    THROW 51206,N'Tài khoản thiếu vai trò duyệt hoặc hash PBKDF2 mới. Không dùng PIN cũ.',1;
DECLARE @sku nvarchar(64),@id bigint,@request uniqueidentifier;
DECLARE products CURSOR LOCAL FAST_FORWARD FOR SELECT LegacySKU FROM dm.LegacyProductStage WHERE Approved=1 AND NewProductId IS NULL ORDER BY LegacySKU;
OPEN products;FETCH NEXT FROM products INTO @sku;
WHILE @@FETCH_STATUS=0
BEGIN
    INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,CostPrice,SellingPrice,WarrantyMonths,TrackSerial,ReorderLevel)
    SELECT LegacySKU,NULLIF(Barcode,N''),TargetName,CategoryId,BrandId,CostPrice,SellingPrice,WarrantyMonths,TrackSerial,ReorderLevel FROM dm.LegacyProductStage WHERE LegacySKU=@sku;
    SET @id=CONVERT(bigint,SCOPE_IDENTITY());
    INSERT dm.ProductCodes(Code,ProductId) VALUES(@sku,@id);
    INSERT dm.ProductCodes(Code,ProductId) SELECT Barcode,@id FROM dm.LegacyProductStage WHERE LegacySKU=@sku AND NULLIF(Barcode,N'') IS NOT NULL AND Barcode<>LegacySKU;
    INSERT dm.Stock(ProductId,Location,Quantity) SELECT @id,'KHO',CONVERT(int,SourceWarehouseQuantity) FROM dm.LegacyProductStage WHERE LegacySKU=@sku;
    INSERT dm.Stock(ProductId,Location,Quantity) SELECT @id,'QUAY',CONVERT(int,SourceCounterQuantity) FROM dm.LegacyProductStage WHERE LegacySKU=@sku;
    INSERT dm.ProductUnits(ProductId,Serial,Location) SELECT @id,UPPER(LTRIM(RTRIM(Serial))),Location FROM dm.LegacySerialStage WHERE LegacySKU=@sku;
    SET @request=NEWID();
    INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,EmployeeId)
    SELECT @request,@id,Location,Quantity,'OPENING',N'MIGRATION đã duyệt: '+@sku,@actor FROM dm.Stock WHERE ProductId=@id AND Quantity>0;
    INSERT dm.StockMovementUnits(MovementId,UnitId)
    SELECT m.Id,u.Id FROM dm.StockMovements m JOIN dm.ProductUnits u ON u.ProductId=m.ProductId AND u.Location=m.Location WHERE m.RequestId=@request;
    INSERT dm.LegacyImportAudit(EmployeeId,Entity,LegacyKey,NewId,Note) SELECT @actor,N'Product',LegacySKU,@id,ReviewNote FROM dm.LegacyProductStage WHERE LegacySKU=@sku;
    UPDATE dm.LegacyProductStage SET NewProductId=@id WHERE LegacySKU=@sku;
    FETCH NEXT FROM products INTO @sku;
END
CLOSE products;DEALLOCATE products;
DECLARE @key nvarchar(64);
DECLARE customers CURSOR LOCAL FAST_FORWARD FOR SELECT LegacyCNIC FROM dm.LegacyCustomerStage WHERE Approved=1 AND NewCustomerId IS NULL ORDER BY LegacyCNIC;
OPEN customers;FETCH NEXT FROM customers INTO @key;
WHILE @@FETCH_STATUS=0
BEGIN
    INSERT dm.Customers(FullName,Phone,Address) SELECT FullName,COALESCE(Phone,N''),COALESCE(Address,N'') FROM dm.LegacyCustomerStage WHERE LegacyCNIC=@key;
    SET @id=CONVERT(bigint,SCOPE_IDENTITY());UPDATE dm.LegacyCustomerStage SET NewCustomerId=@id WHERE LegacyCNIC=@key;
    INSERT dm.LegacyImportAudit(EmployeeId,Entity,LegacyKey,NewId,Note) VALUES(@actor,N'Customer',@key,@id,N'Đã duyệt thông tin khách hàng');
    FETCH NEXT FROM customers INTO @key;
END
CLOSE customers;DEALLOCATE customers;
DECLARE vendors CURSOR LOCAL FAST_FORWARD FOR SELECT LegacyCNIC FROM dm.LegacyVendorStage WHERE Approved=1 AND NewVendorId IS NULL ORDER BY LegacyCNIC;
OPEN vendors;FETCH NEXT FROM vendors INTO @key;
WHILE @@FETCH_STATUS=0
BEGIN
    INSERT dm.Vendors(Name,Phone,Address) SELECT TargetName,COALESCE(Phone,N''),Address FROM dm.LegacyVendorStage WHERE LegacyCNIC=@key;
    SET @id=CONVERT(bigint,SCOPE_IDENTITY());UPDATE dm.LegacyVendorStage SET NewVendorId=@id WHERE LegacyCNIC=@key;
    INSERT dm.LegacyImportAudit(EmployeeId,Entity,LegacyKey,NewId,Note) VALUES(@actor,N'Vendor',@key,@id,N'Đã duyệt nhà cung cấp');
    FETCH NEXT FROM vendors INTO @key;
END
CLOSE vendors;DEALLOCATE vendors;
DECLARE @legacyRole int;
DECLARE accounts CURSOR LOCAL FAST_FORWARD FOR SELECT LegacyCNIC,LegacyRole FROM dm.LegacyAccountStage WHERE Approved=1 AND NewEmployeeId IS NULL ORDER BY LegacyCNIC,LegacyRole;
OPEN accounts;FETCH NEXT FROM accounts INTO @key,@legacyRole;
WHILE @@FETCH_STATUS=0
BEGIN
    INSERT dm.Employees(UserName,FullName,PasswordHash,Role,Enabled,MustChange)
    SELECT UserName,FullName,NewPasswordHash,NewRole,Enabled,1 FROM dm.LegacyAccountStage WHERE LegacyCNIC=@key AND LegacyRole=@legacyRole;
    SET @id=CONVERT(bigint,SCOPE_IDENTITY());UPDATE dm.LegacyAccountStage SET NewEmployeeId=@id WHERE LegacyCNIC=@key AND LegacyRole=@legacyRole;
    INSERT dm.LegacyImportAudit(EmployeeId,Entity,LegacyKey,NewId,Note) VALUES(@actor,N'Employee',@key+N'/'+CONVERT(nvarchar(10),@legacyRole),@id,N'Mật khẩu mới có hash; bắt buộc đổi khi đăng nhập');
    FETCH NEXT FROM accounts INTO @key,@legacyRole;
END
CLOSE accounts;DEALLOCATE accounts;
COMMIT;
SELECT Entity,COUNT_BIG(*) AS ImportedCount FROM dm.LegacyImportAudit GROUP BY Entity;
-- dbo.Receipts/dbo.ReceiptItem are retained, not rewritten and not counted in dm revenue.
GO
