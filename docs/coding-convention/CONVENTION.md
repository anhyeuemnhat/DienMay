# Executable project convention

The delivered active source uses lowercase `vn.dienmay` packages, UpperCamelCase
classes/records, lowerCamelCase methods/variables, UPPER_SNAKE_CASE constants,
UTF-8, LF, two-space indentation, four-space continuation increments and a
120-column limit. Imports are explicit, sorted, with static imports first.
`SQL` was renamed `SqlDatabase`; `TransferfromWarehouse` became
`TransferFromWarehouse`. DTO records copy collections where mutability matters.
Javadoc concentrates on transaction, recovery, security and lifecycle boundaries.

## Tools actually run

`tools/.../SourceStyle.java` is a JDK-only formatter/checker, executed by Ant
`style`/`format`, and configured in optional Maven verify. `style` fails the build
on violations. It uses the Java compiler parser to inspect package, type, method
and variable names, and validates directory/package correspondence. Text checks
cover import wildcards, indentation, line length, formatting and the client SQL
boundary. A self-test rejects deliberately bad indentation/imports and checks
format idempotence, import sorting and preservation of a literal's spaces.

The formatter normalizes line endings/trailing whitespace, sorts import-only
blocks, and wraps existing source whitespace outside string/character literals.
It is deliberately limited, not a complete Java grammar pretty-printer. It does
not rearrange statements/braces, remove unused imports, rewrite logic or manage
NetBeans generated sections. Do not apply it blindly to future text blocks,
generated code or third-party source. Compile and test after applying it.

All 77 delivered Java files (67 production, 9 test, 1 tool) are in the check scope.
Logs: `docs/evidence/style-format.log`, `style-check.log`, `style-self-test.log`.
The build also invokes javac `-Xlint:all`. Three JDK 21 `this-escape` warnings
remain in Swing constructors. They were retained, not hidden by blanket warning
suppression; no failing tests were removed. Work windows are displayed after
construction and their context registration occurs in `setVisible`.

## What this does NOT prove

The width and checker differ from full Google Java Style. Neither Google Java
Format nor Checkstyle was downloaded/executed. This is a verified project subset,
not a claim of full Google-style conformance or whole-repository convention
completion. NetBeans `.form` and guarded generated sections were not acquired;
there is no claim that the original Designer round-trip still works. New views
are handwritten and have no generated sections.

`ant format` modifies files. Review the diff and run `ant clean verify` afterward.
