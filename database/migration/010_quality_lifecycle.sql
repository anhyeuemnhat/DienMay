-- Additive schema v3. Review a restored copy first. Does NOT certify or delete any legacy data.
-- Close old clients and reconcile pending requests before deploying the new client/backend together.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dm.SchemaVersion',N'U') IS NULL
    THROW 51100,N'Apply the initial dm schema and migration 007 first.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=2)
    THROW 51101,N'Schema version 2 is required.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=3)
BEGIN
    ALTER TABLE dm.Customers ADD Enabled bit NOT NULL CONSTRAINT DF_Customer_Enabled DEFAULT 1,
        Revision int NOT NULL CONSTRAINT DF_Customer_Revision DEFAULT 1;
    ALTER TABLE dm.Vendors ADD Revision int NOT NULL CONSTRAINT DF_Vendor_Revision DEFAULT 1;
    ALTER TABLE dm.Promotions ADD Revision int NOT NULL CONSTRAINT DF_Promotion_Revision DEFAULT 1;
    ALTER TABLE dm.Employees ADD Revision int NOT NULL CONSTRAINT DF_Employee_Revision DEFAULT 1;
    ALTER TABLE dm.Categories ADD Enabled bit NOT NULL CONSTRAINT DF_Category_Enabled DEFAULT 1,
        Revision int NOT NULL CONSTRAINT DF_Category_Revision DEFAULT 1,
        VerificationStatus varchar(16) NOT NULL CONSTRAINT DF_Category_Verification DEFAULT 'PENDING',
        VerificationSource nvarchar(1000) NOT NULL CONSTRAINT DF_Category_Source DEFAULT N'',
        VerificationNote nvarchar(1000) NOT NULL CONSTRAINT DF_Category_Note DEFAULT N'',
        VerifiedBy bigint NULL REFERENCES dm.Employees(Id), VerifiedAt datetime2(3) NULL;
    ALTER TABLE dm.Brands ADD Enabled bit NOT NULL CONSTRAINT DF_Brand_Enabled DEFAULT 1,
        Revision int NOT NULL CONSTRAINT DF_Brand_Revision DEFAULT 1,
        VerificationStatus varchar(16) NOT NULL CONSTRAINT DF_Brand_Verification DEFAULT 'PENDING',
        VerificationSource nvarchar(1000) NOT NULL CONSTRAINT DF_Brand_Source DEFAULT N'',
        VerificationNote nvarchar(1000) NOT NULL CONSTRAINT DF_Brand_Note DEFAULT N'',
        VerifiedBy bigint NULL REFERENCES dm.Employees(Id), VerifiedAt datetime2(3) NULL;
    ALTER TABLE dm.Products ADD
        VerificationStatus varchar(16) NOT NULL CONSTRAINT DF_Product_Verification DEFAULT 'PENDING',
        VerificationSource nvarchar(1000) NOT NULL CONSTRAINT DF_Product_Source DEFAULT N'',
        VerificationNote nvarchar(1000) NOT NULL CONSTRAINT DF_Product_Note DEFAULT N'',
        VerifiedBy bigint NULL REFERENCES dm.Employees(Id), VerifiedAt datetime2(3) NULL,
        BarcodeType varchar(16) NOT NULL CONSTRAINT DF_Product_BarcodeType DEFAULT 'NONE',
        BarcodeStatus varchar(16) NOT NULL CONSTRAINT DF_Product_BarcodeStatus DEFAULT 'PENDING',
        BarcodeSource nvarchar(1000) NOT NULL CONSTRAINT DF_Product_BarcodeSource DEFAULT N'',
        BarcodeNote nvarchar(1000) NOT NULL CONSTRAINT DF_Product_BarcodeNote DEFAULT N'',
        BarcodeVerifiedBy bigint NULL REFERENCES dm.Employees(Id), BarcodeVerifiedAt datetime2(3) NULL,
        IsDemo bit NOT NULL CONSTRAINT DF_Product_IsDemo DEFAULT 0;
    -- Existing barcodes are retained exactly and marked pending, never silently repaired or certified.
    EXEC(N'UPDATE dm.Products SET BarcodeType=CASE WHEN Barcode IS NULL THEN ''NONE'' ELSE ''OTHER'' END');
    EXEC(N'ALTER TABLE dm.Brands ADD CONSTRAINT CK_Brand_Verification
        CHECK(VerificationStatus IN (''PENDING'',''VERIFIED'',''INVALID'',''DEMO''));
    ALTER TABLE dm.Categories ADD CONSTRAINT CK_Category_Verification
        CHECK(VerificationStatus IN (''PENDING'',''VERIFIED'',''INVALID'',''DEMO''));
    ALTER TABLE dm.Products ADD CONSTRAINT CK_Product_Verification
        CHECK(VerificationStatus IN (''PENDING'',''VERIFIED'',''INVALID'',''DEMO'')),
        CONSTRAINT CK_Product_BarcodeStatus CHECK(BarcodeStatus IN (''PENDING'',''VERIFIED'',''INVALID'',''DEMO'')),
        CONSTRAINT CK_Product_BarcodeType CHECK(BarcodeType IN (''NONE'',''GTIN8'',''UPC12'',''EAN13'',''GTIN14'',''OTHER''));');
    -- CHECKs below are compiled only after new columns exist in the batch.
    EXEC(N'ALTER TABLE dm.Brands ADD CONSTRAINT CK_Brand_Evidence CHECK(VerificationStatus<>''VERIFIED'' OR
        (VerificationSource LIKE N''https://%'' AND LEN(VerificationNote)>=10 AND VerifiedBy IS NOT NULL AND VerifiedAt IS NOT NULL))');
    EXEC(N'ALTER TABLE dm.Categories ADD CONSTRAINT CK_Category_Evidence CHECK(VerificationStatus<>''VERIFIED'' OR
        (VerificationSource LIKE N''https://%'' AND LEN(VerificationNote)>=10 AND VerifiedBy IS NOT NULL AND VerifiedAt IS NOT NULL))');
    EXEC(N'ALTER TABLE dm.Products ADD CONSTRAINT CK_Product_Evidence CHECK(VerificationStatus<>''VERIFIED'' OR
        (VerificationSource LIKE N''https://%'' AND LEN(VerificationNote)>=10 AND VerifiedBy IS NOT NULL AND VerifiedAt IS NOT NULL)),
        CONSTRAINT CK_Product_BarcodeEvidence CHECK(BarcodeStatus<>''VERIFIED'' OR
        (Barcode IS NOT NULL AND BarcodeType<>''NONE'' AND BarcodeSource LIKE N''https://%''
        AND LEN(BarcodeNote)>=10 AND BarcodeVerifiedBy IS NOT NULL AND BarcodeVerifiedAt IS NOT NULL))');
    CREATE TABLE dm.DeploymentInfo(
        Id int NOT NULL PRIMARY KEY CONSTRAINT CK_Deployment_Id CHECK(Id=1),
        Mode varchar(16) NOT NULL CONSTRAINT CK_Deployment_Mode CHECK(Mode IN ('UNCLASSIFIED','DEMO','PRODUCTION')),
        DatasetVersion nvarchar(80) NOT NULL DEFAULT N'',
        CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    INSERT dm.DeploymentInfo(Id,Mode) VALUES(1,'UNCLASSIFIED');
    CREATE TABLE dm.AuditLog(
        Id bigint IDENTITY PRIMARY KEY,
        EntityType varchar(24) NOT NULL,
        EntityId bigint NOT NULL,
        Action varchar(40) NOT NULL,
        OldValue nvarchar(1000) NOT NULL,
        NewValue nvarchar(1000) NOT NULL,
        Reason nvarchar(1000) NOT NULL,
        EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
        CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX IX_Audit_Entity ON dm.AuditLog(EntityType,EntityId,Id);
    CREATE TABLE dm.ReferenceProducts(
        Id bigint IDENTITY PRIMARY KEY,
        BrandName nvarchar(100) NOT NULL,
        CategoryName nvarchar(100) NOT NULL,
        ProductName nvarchar(180) NOT NULL,
        Model nvarchar(100) NOT NULL,
        Specifications nvarchar(1000) NOT NULL,
        SourceUrl nvarchar(1000) NOT NULL,
        CheckedOn date NOT NULL,
        CONSTRAINT UX_Reference_Model UNIQUE(BrandName,Model),
        CONSTRAINT CK_Reference_Source CHECK(SourceUrl LIKE N'https://%')
    );
    INSERT dm.SchemaVersion(Version) VALUES(3);
END;
COMMIT;
GO
