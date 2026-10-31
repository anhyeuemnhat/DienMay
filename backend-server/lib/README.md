# Backend runtime JDBC driver

`mssql-jdbc-13.6.0.jre11.jar` is included. It was retained unchanged from the
user-uploaded project RAR, not downloaded during this build. The manifest
identifies Microsoft JDBC 13.6.0 for Java 11 and later. Ant and Maven dependency
metadata now name the same version. JDK 17+ is required for this application's code.

`LICENSE-mssql-jdbc.txt` contains the vendor MIT license read from the official
v13.6.0 tag. See `../../docs/REFERENCES.md` for official release, download, TLS
and license sources. The local JAR hash is in `../../SHA256SUMS.txt`.
**Its bytes have not been independently compared to a vendor-published checksum.**
A package checksum records identity; it is not supply-chain attestation.

The driver is backend-only; desktop clients do not need it. SQL password
authentication uses this JAR; Azure authentication, Always Encrypted and native
integrated-authentication options can require additional dependencies not bundled
here. Those configurations are not tested or advertised as ready.

Ant compiles against `java.sql` without downloading dependencies. Maven packaging
can resolve dependencies from its configured repository, but Maven was not run
in the delivery environment. Review vendor support/security advisories and obtain
an independently verified driver before production deployment.
