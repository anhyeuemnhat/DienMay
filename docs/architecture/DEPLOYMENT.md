# Deployment / security / rollback

## Host allocation

| Host | Required files | Secrets | Network access |
|---|---|---|---|
| Desktop | client-swing.jar + common-dto.jar | In-memory API session only | Backend HTTPS |
| Application | backend-server.jar + common-dto.jar + JDBC JAR; backend config | SQL login, TLS private key/password | SQL Server TCP |
| Database | Reviewed schema v1 + additive v2; backups | SQL Server accounts/certificates | Accept application host, not desktops |

The delivery archive contains every module for development. Do not distribute the
whole folder, database config or backend lib to employee workstations. Desktop
runtime needs only its two JARs and a launch command/API URL.

## Backend variables

DIENMAY_DB_URL / DIENMAY_DB_USER / DIENMAY_DB_PASSWORD override the config file.
DIENMAY_CONFIG (or Java -Ddienmay.config) selects the UTF-8 properties file;
default `config/database.properties`. DIENMAY_BIND defaults 127.0.0.1,
DIENMAY_PORT defaults 8080. Nonlocal binding requires DIENMAY_TLS_KEYSTORE pointing
to a PKCS12 identity and DIENMAY_TLS_PASSWORD. Certificate SAN must match the API
hostname. Install the issuing chain into the appropriate JVM trust store using
your administrator's certificate procedure. Do not add trust-all code.

Client uses DIENMAY_API_URL only. A private CA may require JVM trustStore settings;
these are CA trust configuration, not SQL credentials. Pending files default to
user.home/.dienmay-api, namespaced by API profile; -Ddienmay.stateDir overrides this.
POSIX permissions are restricted; Windows profile ACLs require administrative
verification. Pending files contain business details, so treat them as sensitive
records even though they contain no password/token.

Backend logs emit correlation IDs and error classes, not raw SQL, tokens,
passwords or supplied bodies. They are intentionally minimal. Production needs
centralized retention, capacity monitoring, TLS lifecycle and backup/restore drills.
JDBC uses per-call connections, not a pool. Load/performance/HA were not accepted.

## Safe cutover from the reviewed dm v1 implementation

1. Verify the actual source/database version; take and test a complete backup.
2. Reconcile every legacy pending file while its original client can still operate.
   The new client profile is based on API URL, not DB URL; it will not automatically
   discover pending files under the old DB-derived profile. Do not copy/delete them
   casually or invent new UUIDs to replace uncertain requests.
3. Stop all legacy direct-JDBC clients; revoke/rotate their SQL credentials and
   restrict network access. A still-running old client bypasses the cancellation
   fence introduced in v2 and would invalidate the new safety model.
4. Apply migration 007 first on a restored test database, verify it and run SQL IT.
   Execute the production migration only after DBA approval and a cutover window.
5. Start backend, provision TLS/trust, deploy client-only files, and perform role,
   receive/transfer/sale/serial/warranty/report and peripheral acceptance.
6. Keep verified backups and reconciliation records. Never drop business tables
   or run the demo seed to repair a production error.

## Rollback

Application-only rollback to a legacy client is NOT safe once v2 cancellation
records exist: the old code does not honor them. Stop new traffic, preserve
pending files and logs, reconcile committed transactions, and restore an approved
matched application/database/pending snapshot only after evaluating lost writes.
The new migration does not delete legacy business data, but this alone does not
make downgrade safe. No destructive automated rollback script is supplied.
