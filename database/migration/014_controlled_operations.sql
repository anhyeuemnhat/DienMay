-- Schema v5: additive controlled operations. Stop all clients/backend; verified backup required.
-- Run ONLY through the DBA installer against the explicitly selected target database.
-- Legacy receipts intentionally keep NULL snapshots and cannot be automatically reversed.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=4)
    THROW 51400,N'Apply schema version 4 first.',1;
IF COL_LENGTH(N'dm.Products',N'CostRevision') IS NULL
BEGIN
    ALTER TABLE dm.Products ADD CostRevision bigint NOT NULL CONSTRAINT DF_Product_CostRevision DEFAULT 0;
    ALTER TABLE dm.StockMovements ADD PreviousCostPrice decimal(19,2) NULL, CostRevisionAfter bigint NULL;
    ALTER TABLE dm.StockMovements DROP CONSTRAINT CK_Movement_Kind;
    ALTER TABLE dm.StockMovements ADD CONSTRAINT CK_Movement_Kind
        CHECK(Kind IN ('RECEIVE','TRANSFER','SALE','OPENING','RECEIVE_REVERSAL'));
    ALTER TABLE dm.ProductUnits DROP CONSTRAINT CK_Unit_Status;
    ALTER TABLE dm.ProductUnits ADD CONSTRAINT CK_Unit_Status CHECK(Status IN ('IN_STOCK','SOLD','REVERSED'));
    ALTER TABLE dm.RequestCancellations DROP CONSTRAINT CK_Cancellation_Kind;
    ALTER TABLE dm.RequestCancellations ADD CONSTRAINT CK_Cancellation_Kind
        CHECK(Kind IN ('SALE','STOCK','DELETE','REVERSAL'));
    CREATE TABLE dm.InventoryReversals(
        Id bigint IDENTITY PRIMARY KEY,
        RequestId uniqueidentifier NOT NULL UNIQUE,
        RequestHash char(64) NOT NULL,
        OriginalMovementId bigint NOT NULL UNIQUE REFERENCES dm.StockMovements(Id),
        ReversalMovementId bigint NOT NULL UNIQUE REFERENCES dm.StockMovements(Id),
        EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
        Reason nvarchar(1000) NOT NULL,
        PreviousCostPrice decimal(19,2) NOT NULL,
        RestoredCostPrice decimal(19,2) NOT NULL,
        CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_Reversal_Reason CHECK(LEN(LTRIM(RTRIM(Reason)))>0),
        CONSTRAINT CK_Reversal_Movement CHECK(OriginalMovementId<>ReversalMovementId),
        CONSTRAINT CK_Reversal_Cost CHECK(PreviousCostPrice>=0 AND RestoredCostPrice>=0)
    );
    CREATE TABLE dm.DeletionRequests(
        RequestId uniqueidentifier PRIMARY KEY,
        RequestHash char(64) NOT NULL,
        EntityType varchar(24) NOT NULL,
        EntityId bigint NOT NULL,
        Revision int NOT NULL,
        EntityCode nvarchar(128) NOT NULL,
        EntityName nvarchar(180) NOT NULL,
        EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
        Reason nvarchar(1000) NOT NULL,
        Snapshot nvarchar(1000) NOT NULL,
        CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_Deletion_Reason CHECK(LEN(LTRIM(RTRIM(Reason)))>0),
        CONSTRAINT CK_Deletion_Type CHECK(EntityType IN
            ('product','customer','vendor','employee','promotion','category','brand'))
    );
    CREATE INDEX IX_Deletion_Entity ON dm.DeletionRequests(EntityType,EntityId);
    CREATE INDEX IX_Invoice_PromotionCode ON dm.Invoices(PromotionCode) WHERE PromotionCode IS NOT NULL;
END;
COMMIT;
GO
CREATE OR ALTER PROCEDURE dm.ControlledDelete
    @Type varchar(24), @EntityId bigint, @Revision int, @ActorId bigint,
    @Execute bit=0, @RequestId uniqueidentifier=NULL, @RequestHash char(64)=NULL, @Reason nvarchar(1000)=N''
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @Own bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
    IF @Own=1 BEGIN TRANSACTION;
    BEGIN TRY
        IF @Type NOT IN ('product','customer','vendor','employee','promotion','category','brand')
            OR @Type IS NULL OR @EntityId IS NULL OR @EntityId<=0 OR @Revision IS NULL OR @Revision<=0
            OR @Execute IS NULL
            THROW 51504,N'Invalid delete target.',1;
        DECLARE @Lock int,@Resource nvarchar(255);
        IF @Type='employee'
        BEGIN
            EXEC @Lock=sys.sp_getapplock @Resource=N'dm:employee-admin',@LockMode='Exclusive',
                @LockOwner='Transaction',@LockTimeout=10000;
            IF @Lock<0 THROW 51506,N'Employee administration is busy; reconcile before retry.',1;
        END;
        IF NOT EXISTS(SELECT 1 FROM dm.Employees WITH(HOLDLOCK)
            WHERE Id=@ActorId AND Role='MANAGER' AND Enabled=1 AND MustChange=0)
            THROW 51500,N'Manager authorization required.',1;
        IF @Execute=1
        BEGIN
            IF @RequestId IS NULL OR @RequestHash IS NULL OR LEN(@RequestHash)<>64
                OR LEN(LTRIM(RTRIM(COALESCE(@Reason,N''))))=0
                THROW 51504,N'Request and reason are required.',1;
            SET @Resource=N'dm:delete:'+LOWER(CONVERT(nvarchar(36),@RequestId));
            EXEC @Lock=sys.sp_getapplock @Resource=@Resource,@LockMode='Exclusive',
                @LockOwner='Transaction',@LockTimeout=10000;
            IF @Lock<0 THROW 51506,N'Deletion request busy; reconcile before retry.',1;
            IF EXISTS(SELECT 1 FROM dm.DeletionRequests WHERE RequestId=@RequestId)
            BEGIN
                IF NOT EXISTS(SELECT 1 FROM dm.DeletionRequests WHERE RequestId=@RequestId
                    AND RequestHash=@RequestHash AND EmployeeId=@ActorId AND EntityType=@Type
                    AND EntityId=@EntityId AND Revision=@Revision)
                    THROW 51505,N'Request already belongs to different content or actor.',1;
                SELECT EntityType,EntityId,Revision,EntityCode,EntityName,CAST(1 AS bit) AS Allowed,
                    N'' AS BlockReason FROM dm.DeletionRequests WHERE RequestId=@RequestId;
                IF @Own=1 COMMIT;
                RETURN;
            END;
            IF EXISTS(SELECT 1 FROM dm.RequestCancellations WHERE Kind='DELETE' AND RequestId=@RequestId)
                THROW 51505,N'This deletion request has been cancelled.',1;
        END;
        DECLARE @Current int,@Code nvarchar(128),@Name nvarchar(180),@Table sysname,@Blocked nvarchar(2000)=N'';
        IF @Type='product'
        BEGIN
            SET @Table=N'Products';
            SELECT @Current=Revision,@Code=SKU,@Name=Name FROM dm.Products WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='customer'
        BEGIN
            SET @Table=N'Customers';
            SELECT @Current=Revision,@Code=CONVERT(nvarchar(128),Id),@Name=FullName FROM dm.Customers WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='vendor'
        BEGIN
            SET @Table=N'Vendors';
            SELECT @Current=Revision,@Code=CONVERT(nvarchar(128),Id),@Name=Name FROM dm.Vendors WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='employee'
        BEGIN
            SET @Table=N'Employees';
            SELECT @Current=Revision,@Code=UserName,@Name=FullName FROM dm.Employees WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='promotion'
        BEGIN
            SET @Table=N'Promotions';
            SELECT @Current=Revision,@Code=Code,@Name=Name FROM dm.Promotions WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='category'
        BEGIN
            SET @Table=N'Categories';
            SELECT @Current=Revision,@Code=CONVERT(nvarchar(128),Id),@Name=Name FROM dm.Categories WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Type='brand'
        BEGIN
            SET @Table=N'Brands';
            SELECT @Current=Revision,@Code=CONVERT(nvarchar(128),Id),@Name=Name FROM dm.Brands WITH(UPDLOCK,HOLDLOCK)
                WHERE Id=@EntityId;
        END;
        IF @Current IS NULL THROW 51501,N'Record not found.',1;
        IF @Current<>@Revision THROW 51502,N'Record revision changed; reload.',1;
        -- Preserve all audit rows. Basic CREATE/UPDATE snapshots are retained, not treated as a business link.
        -- Reviews, price acknowledgements, lifecycle and other business audit prohibit deletion.
        IF EXISTS(SELECT 1 FROM dm.AuditLog WITH(HOLDLOCK) WHERE EntityType=@Type AND EntityId=@EntityId
            AND Action NOT IN ('CREATE','UPDATE'))
            SET @Blocked+=N'Ban ghi co lich su kiem chung/nghiep vu can bao toan. '+CHAR(10);
        IF @Type='product'
        BEGIN
            IF EXISTS(SELECT 1 FROM dm.Stock WITH(UPDLOCK,HOLDLOCK) WHERE ProductId=@EntityId AND Quantity<>0)
                SET @Blocked+=N'San pham con ton kho thuc. '+CHAR(10);
            IF EXISTS(SELECT 1 FROM dm.Products WHERE Id=@EntityId AND
                (VerifiedBy IS NOT NULL OR BarcodeVerifiedBy IS NOT NULL OR VerificationStatus='VERIFIED'))
                SET @Blocked+=N'San pham da co du lieu kiem chung can giu. '+CHAR(10);
        END;
        IF @Type='promotion' AND EXISTS(SELECT 1 FROM dm.Invoices WITH(HOLDLOCK) WHERE PromotionCode=@Code)
            SET @Blocked+=N'Khuyen mai da duoc su dung tren hoa don. '+CHAR(10);
        -- Before v5, promotion UPDATE audits did not preserve old codes. Never guess a legacy linkage.
        IF @Type='promotion' AND EXISTS(SELECT 1 FROM dm.AuditLog a WITH(HOLDLOCK)
            WHERE a.EntityType='promotion' AND a.EntityId=@EntityId AND a.Action='UPDATE'
              AND a.CreatedAt<=(SELECT AppliedAt FROM dm.SchemaVersion WHERE Version=5))
            SET @Blocked+=N'Khuyen mai tung sua truoc v5; khong du lich su ma de chung minh an toan. '+CHAR(10);
        IF @Type='employee'
        BEGIN
            IF @EntityId=@ActorId SET @Blocked+=N'Khong tu xoa tai khoan dang dang nhap. '+CHAR(10);
            IF EXISTS(SELECT 1 FROM dm.Employees WHERE Id=@EntityId AND Role='MANAGER')
                AND NOT EXISTS(SELECT 1 FROM dm.Employees WITH(UPDLOCK,HOLDLOCK)
                    WHERE Role='MANAGER' AND Enabled=1 AND Id<>@EntityId)
                SET @Blocked+=N'Phai giu it nhat mot Quan ly hoat dong. '+CHAR(10);
            IF EXISTS(SELECT 1 FROM dm.AuditLog WITH(HOLDLOCK) WHERE EntityType='employee' AND EntityId=@EntityId)
                SET @Blocked+=N'Tai khoan co lich su audit. '+CHAR(10);
        END;
        IF @Type='brand' AND EXISTS(SELECT 1 FROM dm.Brands WHERE Id=@EntityId AND VerifiedBy IS NOT NULL)
            SET @Blocked+=N'Thuong hieu co lich su kiem chung. '+CHAR(10);
        IF @Type='category' AND EXISTS(SELECT 1 FROM dm.Categories WHERE Id=@EntityId AND VerifiedBy IS NOT NULL)
            SET @Blocked+=N'Loai san pham co lich su kiem chung. '+CHAR(10);
        -- Inspect every real FK, including later additions, not just a hardcoded list.
        -- SQL identifiers originate ONLY from sys metadata and are QUOTENAME-escaped; values stay parameters.
        -- SELECT permission on these tables is already required by the runtime role; no elevated dynamic DML.
        DECLARE @Fk int,@Child nvarchar(517),@Join nvarchar(max),@Query nvarchar(max),@Has bit,@Label sysname;
        DECLARE Dependencies CURSOR LOCAL FAST_FORWARD FOR
            SELECT f.object_id,QUOTENAME(OBJECT_SCHEMA_NAME(f.parent_object_id))+N'.'
                +QUOTENAME(OBJECT_NAME(f.parent_object_id)),OBJECT_NAME(f.parent_object_id)
            FROM sys.foreign_keys f WHERE f.referenced_object_id=OBJECT_ID(N'dm.'+@Table)
              AND NOT(@Type='product' AND f.parent_object_id IN(OBJECT_ID(N'dm.Stock'),OBJECT_ID(N'dm.ProductCodes')));
        OPEN Dependencies;
        FETCH NEXT FROM Dependencies INTO @Fk,@Child,@Label;
        WHILE @@FETCH_STATUS=0
        BEGIN
            SELECT @Join=STUFF((SELECT N' AND child.'+QUOTENAME(COL_NAME(k.parent_object_id,k.parent_column_id))
                +N'=parent.'+QUOTENAME(COL_NAME(k.referenced_object_id,k.referenced_column_id))
                FROM sys.foreign_key_columns k WHERE k.constraint_object_id=@Fk
                ORDER BY k.constraint_column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)'),1,5,N'');
            SET @Query=N'SELECT @Found=CASE WHEN EXISTS(SELECT 1 FROM '+@Child+N' child WITH(HOLDLOCK)'
                +N' JOIN dm.'+QUOTENAME(@Table)+N' parent ON '+@Join+N' WHERE parent.Id=@Id) THEN 1 ELSE 0 END';
            SET @Has=0;
            EXEC sys.sp_executesql @Query,N'@Id bigint,@Found bit OUTPUT',@Id=@EntityId,@Found=@Has OUTPUT;
            IF @Has=1 SET @Blocked+=N'Co du lieu lien ket: '+@Label+N'. '+CHAR(10);
            FETCH NEXT FROM Dependencies INTO @Fk,@Child,@Label;
        END;
        CLOSE Dependencies;
        DEALLOCATE Dependencies;
        DECLARE @Snapshot nvarchar(max)=(SELECT @Type AS entityType,@EntityId AS id,@Current AS revision,
            @Code AS code,@Name AS name FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
        IF DATALENGTH(@Snapshot)>2000
            SET @Blocked+=N'Snapshot vuot gioi han audit; khong xoa khi khong luu du thong tin. '+CHAR(10);
        IF @Execute=0
        BEGIN
            SELECT @Type AS EntityType,@EntityId AS EntityId,@Current AS Revision,COALESCE(@Code,N'') AS EntityCode,
                @Name AS EntityName,CAST(CASE WHEN LEN(@Blocked)=0 THEN 1 ELSE 0 END AS bit) AS Allowed,
                @Blocked AS BlockReason;
            IF @Own=1 COMMIT;
            RETURN;
        END;
        IF LEN(@Blocked)>0 THROW 51503,@Blocked,1;
        INSERT dm.AuditLog(EntityType,EntityId,Action,OldValue,NewValue,Reason,EmployeeId)
            VALUES(@Type,@EntityId,'HARD_DELETE',@Snapshot,N'DELETED',@Reason,@ActorId);
        -- Only zero-stock technical rows are removed; every business dependency was checked above.
        IF @Type='product'
        BEGIN
            DELETE FROM dm.ProductCodes WHERE ProductId=@EntityId;
            DELETE FROM dm.Stock WHERE ProductId=@EntityId AND Quantity=0;
            DELETE FROM dm.Products WHERE Id=@EntityId AND Revision=@Revision;
        END;
        IF @Type='customer' DELETE FROM dm.Customers WHERE Id=@EntityId AND Revision=@Revision;
        IF @Type='vendor' DELETE FROM dm.Vendors WHERE Id=@EntityId AND Revision=@Revision;
        IF @Type='employee' DELETE FROM dm.Employees WHERE Id=@EntityId AND Revision=@Revision;
        IF @Type='promotion' DELETE FROM dm.Promotions WHERE Id=@EntityId AND Revision=@Revision;
        IF @Type='category' DELETE FROM dm.Categories WHERE Id=@EntityId AND Revision=@Revision;
        IF @Type='brand' DELETE FROM dm.Brands WHERE Id=@EntityId AND Revision=@Revision;
        INSERT dm.DeletionRequests(RequestId,RequestHash,EntityType,EntityId,Revision,EntityCode,EntityName,
            EmployeeId,Reason,Snapshot) VALUES(@RequestId,@RequestHash,@Type,@EntityId,@Revision,
            COALESCE(@Code,N''),@Name,@ActorId,@Reason,@Snapshot);
        SELECT @Type AS EntityType,@EntityId AS EntityId,@Revision AS Revision,COALESCE(@Code,N'') AS EntityCode,
            @Name AS EntityName,CAST(1 AS bit) AS Allowed,N'' AS BlockReason;
        IF @Own=1 COMMIT;
    END TRY
    BEGIN CATCH
        IF @Own=1 AND XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
-- Fail closed on a partially/manual altered database instead of marking an incomplete upgrade ready.
IF COL_LENGTH(N'dm.Products',N'CostRevision') IS NULL
    OR COL_LENGTH(N'dm.StockMovements',N'PreviousCostPrice') IS NULL
    OR COL_LENGTH(N'dm.StockMovements',N'CostRevisionAfter') IS NULL
    OR OBJECT_ID(N'dm.DeletionRequests',N'U') IS NULL
    OR OBJECT_ID(N'dm.InventoryReversals',N'U') IS NULL
    OR OBJECT_ID(N'dm.ControlledDelete',N'P') IS NULL
    THROW 51401,N'Incomplete v5 objects. Stop; inspect the migration and restore plan.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes i JOIN sys.index_columns ic
    ON ic.object_id=i.object_id AND ic.index_id=i.index_id
    WHERE i.object_id=OBJECT_ID(N'dm.InventoryReversals') AND i.is_unique=1
      AND COL_NAME(ic.object_id,ic.column_id)=N'OriginalMovementId' AND ic.key_ordinal=1)
    THROW 51402,N'Missing unique original receipt guard.',1;
-- Version marker is written only after procedure creation succeeds. Installer serializes concurrent runs.
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=5) INSERT dm.SchemaVersion(Version) VALUES(5);
GO
