-- v4: structural checks are NOT evidence of product ownership or device authenticity.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=3)
    THROW 51120,N'Apply quality/lifecycle migration 010 first.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=4)
BEGIN
    EXEC(N'CREATE OR ALTER FUNCTION dm.ValidGtin(@code nvarchar(128)) RETURNS bit WITH SCHEMABINDING AS
BEGIN
    DECLARE @n int=LEN(@code), @i int, @weight int=3, @total int=0;
    IF @code IS NULL OR @n NOT IN (8,12,13,14) OR DATALENGTH(@code)<>@n*2
       OR @code COLLATE Latin1_General_100_BIN2 LIKE N''%[^0-9]%'' RETURN 0;
    SET @i=@n-1;
    WHILE @i>0 BEGIN
        SET @total=@total+CONVERT(int,SUBSTRING(@code,@i,1))*@weight;
        SET @weight=4-@weight; SET @i=@i-1;
    END;
    RETURN CASE WHEN (@total+CONVERT(int,RIGHT(@code,1)))%10=0 THEN 1 ELSE 0 END;
END');
    EXEC(N'CREATE OR ALTER FUNCTION dm.ValidImei(@code nvarchar(128)) RETURNS bit WITH SCHEMABINDING AS
BEGIN
    IF @code IS NULL OR LEN(@code)<>15 OR DATALENGTH(@code)<>30
       OR @code COLLATE Latin1_General_100_BIN2 LIKE N''%[^0-9]%'' RETURN 0;
    DECLARE @i int=1, @digit int, @total int=0;
    WHILE @i<=15 BEGIN
        SET @digit=CONVERT(int,SUBSTRING(@code,@i,1));
        IF @i%2=0 BEGIN SET @digit=@digit*2; IF @digit>9 SET @digit=@digit-9; END;
        SET @total=@total+@digit; SET @i=@i+1;
    END;
    RETURN CASE WHEN @total%10=0 THEN 1 ELSE 0 END;
END');
    ALTER TABLE dm.ProductUnits ADD IdentifierType varchar(8) NOT NULL
        CONSTRAINT DF_Unit_IdentifierType DEFAULT 'SERIAL',
        IdentifierSource nvarchar(200) NOT NULL CONSTRAINT DF_Unit_IdentifierSource DEFAULT N'';
    EXEC(N'ALTER TABLE dm.ProductUnits ADD CONSTRAINT CK_Unit_IdentifierType CHECK(IdentifierType IN (''SERIAL'',''IMEI'')),
        CONSTRAINT CK_Unit_Imei CHECK(IdentifierType<>''IMEI'' OR (dm.ValidImei(Serial)=1 AND LEN(IdentifierSource)>0))');
    EXEC(N'ALTER TABLE dm.Products ADD CONSTRAINT CK_Product_TypedBarcode CHECK(
        (Barcode IS NULL AND BarcodeType IN (''NONE'',''GTIN8'',''UPC12'',''EAN13'',''GTIN14'',''OTHER'')) OR
        (Barcode IS NOT NULL AND ((BarcodeType=''OTHER'' AND LEN(Barcode)>0) OR
        (dm.ValidGtin(Barcode)=1 AND
        ((BarcodeType=''GTIN8'' AND LEN(Barcode)=8) OR (BarcodeType=''UPC12'' AND LEN(Barcode)=12) OR
        (BarcodeType=''EAN13'' AND LEN(Barcode)=13) OR (BarcodeType=''GTIN14'' AND LEN(Barcode)=14))))))');
    INSERT dm.SchemaVersion(Version) VALUES(4);
END;
COMMIT;
GO
