-- ADDITIVE migration. Review on a restored test database first. Never removes business data.
-- Stop legacy direct-JDBC clients and reconcile their pending files BEFORE cutover.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dm.SchemaVersion',N'U') IS NULL
    THROW 51007,N'Missing dm schema. Apply schema/001 only to a fresh database.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=1)
    THROW 51008,N'Unsupported schema version. Stop and review migration.',1;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=2)
BEGIN
    IF OBJECT_ID(N'dm.RequestCancellations',N'U') IS NOT NULL
        THROW 51009,N'Unexpected cancellation table. Review before marking version 2.',1;
    CREATE TABLE dm.RequestCancellations(
        Kind varchar(8) NOT NULL,
        RequestId uniqueidentifier NOT NULL,
        RequestHash char(64) NOT NULL,
        EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
        CancelledAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_RequestCancellations PRIMARY KEY(Kind,RequestId),
        CONSTRAINT CK_Cancellation_Kind CHECK(Kind IN ('SALE','STOCK'))
    );
    INSERT dm.SchemaVersion(Version) VALUES(2);
END;
COMMIT;
GO
