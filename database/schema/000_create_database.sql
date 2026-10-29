-- DBA only. Does not modify an existing database.
USE master;
GO
IF DB_ID(N'DienMay') IS NULL CREATE DATABASE DienMay;
GO
USE DienMay;
GO
