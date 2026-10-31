-- DBA ONLY: creates a NEW isolated demo database; never adopts or overwrites an existing database.
-- Run this once in SSMS with a DBA login. Afterward configure DIENMAY_DB_URL to this exact name.
USE master;
GO
IF DB_ID(N'DienMayDo_Demo') IS NOT NULL
    THROW 51220,N'DienMayDo_Demo already exists. Inspect it; do not replace or drop it.',1;
CREATE DATABASE DienMayDo_Demo;
GO
