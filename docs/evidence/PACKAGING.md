# Packaging verification - 2026-10-29

A preflight ZIP of the complete delivery directory was created, CRC-checked,
and extracted into a separate directory. From that extracted source directory,
`ant clean verify` completed successfully and all 130 nonvisual tests passed.
The actual output is retained in `package-rebuild.log`.

The delivery additionally retains successful independent Swing runs at
1366 x 768 and 1920 x 1080 (23 scenarios per resolution), with 40 PNG screenshots.
These Swing runs used synthetic HTTP API fixtures, not a real SQL Server.

Temporary build directories were removed from the final source package;
three independently built runtime JARs remain under `dist/`.
`SHA256SUMS.txt` covers every packaged file except the checksum file itself.
The ZIP is CRC-checked and its root prefix is checked after creation.

## Scope limitations

This packaging verification does not establish full acquisition of the upstream
repository, preservation of original NetBeans forms/resources, SQL Server
integration, Maven execution, Windows/DPI behavior, or production readiness.
See `../../ACCEPTANCE_REPORT.md` and `../provenance/ACQUISITION.md`.
No original repository was modified or pushed.
