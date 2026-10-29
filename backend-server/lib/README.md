# Backend runtime JDBC driver

This directory intentionally contains no downloaded driver or password.
Install Microsoft `mssql-jdbc-13.4.0.jre11.jar` here, obtained from the official
Microsoft download or Maven Central. Version matches the upstream pinned POM.
It is not claimed to be the latest release. The JAR is needed only at runtime;
compilation and the offline test suite use standard `java.sql` interfaces.

Maven backend packaging copies its runtime dependencies to this directory.
Do not distribute this directory to desktop clients. Review the driver's
license/security advisories and compatibility before production deployment.
