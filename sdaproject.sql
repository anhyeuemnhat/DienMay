-- Compatibility entry point for sqlcmd or SSMS with SQLCMD Mode enabled.
-- Run from the project root. The old dbo schema is not dropped or overwritten.
:ON ERROR EXIT
:r database/000_create_database.sql
:r database/001_schema.sql
-- Optional DEMO ONLY, execute separately after reviewing:
-- :r database/002_demo.sql
