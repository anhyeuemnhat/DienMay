# Test result ledger

Run environment: Linux; OpenJDK 21.0.12.1, javac --release 17; Ant;
Xvfb/Openbox standard system configuration for GUI. SQL Server, JDBC binary,
Windows and Maven were not available. No external DB was substituted.

| Suite | Cases executed | Passed | Failed | Main evidence |
|---|---:|---:|---:|---|
| UnitTests | 66 | 66 | 0 | unit-tests.log |
| ApiTests | 47 | 47 | 0 | api-tests.log |
| SecurityTests | 17 | 17 | 0 | security-tests.log |
| UiSmoke 1366x768 | 23 | 23 | 0 | ui-1366x768.log + ui/1366x768/*.png |
| UiSmoke 1920x1080 | 23 | 23 | 0 | ui-1920x1080.log + ui/1920x1080/*.png |
| SQL Server integration | 0 | n/a | n/a | sql-server-tests.log: SKIP/NOT RUN |

176 test executions = 130 offline/nonvisual + the same 23 GUI tests at two
resolutions. There are 153 distinct delivered scenarios in those executed suites,
not 176 independent scenarios. SourceStyle checks 77 Java files and has a separate
self-test; neither is included in the 176. Offline tests include actual local HTTP
but use explicit fixture handlers or JDBC doubles. They cannot prove SQL Server
constraints/locks/transaction races, SQL syntax, or client-to-real-database flow.

## Important coverage

Money precision/range/rounding; cart immutability and no mutation on invalid add;
serial normalization/duplication; request payload hashing; dates/warranty transitions;
PBKDF2 malformed hashes/salts; role matrix/opaque tokens/TTL/AuthVersion/MustChange;
transaction commit/rollback/resource-close control flow; movement ten-parameter
regression; pending restart/corruption/tampering/OS lock; stable retry/negative
resolve/cancel semantics; JSON malformed input/depth/duplicates/number precision;
real HTTP routes/content type/body size/authorization/error redaction/rate limiting;
complete response timeout and oversized streaming response; client dependency boundary.

## Retained failures and their fixes

`unit-tests-initial-display-failure.log`: one offline Swing date-input test lacked a
display. Headless mode was explicitly set and the same suite rerun: 66/66.

`ui-1366-forced-wm-failure.log` and `ui-1366-initial.log`: the user Openbox config
forced every application undecorated/maximized, interfering with Java WM geometry.
The final runner explicitly uses /etc/xdg/openbox/rc.xml, not forced maximization.

`ui-1366-dashboard-failure.log`: manager dashboard clipped/scrolled top actions.
The layout was changed, screenshots inspected, and both resolutions rerun. This
was a real detected UI issue, not a test removed to create PASS.

## Unexecuted gates

SqlServerIT is compiled and deliberately opt-in. It checks exact DB name ending
_Test before writes and uses random ephemeral fixture passwords. It covers real
SQL transactions, concurrent duplicate checkout, overselling, serial collisions,
rollback injection, stale prices/promotions, warranty history, authorization changes,
request cancellation/races, invoice reports, ledger reconciliation, and a real
HTTP client -> ApplicationApi -> SQL Server scenario. Running without prerequisites
prints SKIP, never PASS. See SQL_SERVER_ACCEPTANCE.md.

Windows scaling 100/125/150%, JDK17 runtime, TLS deployment, long-duration load,
NetBeans original form round-trip, physical scanner/printer and every legacy
function/document have no acceptance evidence in this delivery.

## Final runner interruption

A final Ant-wrapped GUI run stopped producing output before completion and was
terminated by the command deadline. Its incomplete log is retained as
ui-1366-ant-interrupted.log and is NOT counted as a passing run. A direct Java
run completed all 23 cases. The Linux runner now executes Java directly with a
120-second outer timeout; both resolution logs must have a final RESULT line.
The precise cause of that one runner stall was not established. This timeout is
a test-runner safeguard, not a claim that an intermittent production defect was
identified and fixed. Final complete bounded runs are the acceptance evidence.
