-- Run with a DBA account in SSMS/sqlcmd. No credentials are embedded here.
USE master;
GO
IF DB_ID(N'DienMay') IS NULL
    CREATE DATABASE DienMay;
GO
USE DienMay;
GO
