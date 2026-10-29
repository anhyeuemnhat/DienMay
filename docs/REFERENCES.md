# Sources and application traceability

## User-supplied textbooks (actual sections reviewed)

Page below means **PDF file page, one-based**, not an assumed chapter page.
`ptud.pdf` has one printed slide per page. The Nhap mon Cong nghe phan mem PDFs
usually place two printed slides on each PDF page. Twelve chapter PDFs were
extracted from the supplied archive; only the relevant sections listed here were
reviewed. This is NOT a claim to have read every page of all twelve chapters.
No unread content is cited as supporting a design decision.

| Material / location | What that source actually presents | Application to this delivery |
|---|---|---|
| ptud, chapter 4, p.19, slide 19 | Data, Business, Client in the 3-Tiers model; no tier skipping | Independent Swing/API/SQL deployment and no desktop SQL link |
| ptud pp.113-114 | UI independence from processing; usability/efficiency | Transport/controller separation, asynchronous UI work |
| ptud pp.121-124 | Screen list, navigation, components and events as design outputs | SCREENS.md and navigation diagram |
| ptud p.128 | Consistent presentation/naming; short route to main work | One menu selection or home shortcut opens a work screen |
| ptud p.136 | Functional organization: system/storage/search/calculation/reports | Six menu groups matched to DienMay functions |
| ptud pp.139-142 | Lookup/input/notification/report screen concerns | Search filters, validation, confirmation/status, table/receipt output |
| ptud p.155 | Context-relevant information and effective screen space | Resizable tables and maximized work windows |
| ptud p.159 | Functional-menu example: System/Catalog/Update/Processing/Search/Reports | Direct basis for Navigation functional groups |
| ptud p.175 | Main menu organization | Account/system actions separated from work/report actions |
| Chuong2.pdf p.9, slides 17-18 | Interface implementation example and encapsulation | Private dependencies and ApiGateway/ApiHandler contracts |
| Chuong2.pdf p.12, slides 23-24 | Messages and polymorphism discussion | Interface-dispatched gateways and reusable view hierarchy |
| Chuong5.pdf p.6, slides 11-12 | Design class diagrams describe cooperating classes | Current-source class diagram rather than an old schematic |
| Chuong5.pdf p.9, slide 18 | Deployment diagram describes nodes/network organization | Three deployable tiers and their actual connections |
| Chuong7.pdf p.4, slides 7-8 | Boundary, entity, control analysis classes | UI, immutable models and workflow controllers (implementation interpretation) |
| Chuong8.pdf p.6, slides 11-12 | Architecture patterns; MVC responsibilities | Deliberate UI/model/controller boundaries |
| Chuong8.pdf p.8, slides 15-16 | MVC trade-offs; layered architecture | Avoid framework excess, distinguish logical layers from physical deployment |
| Chuong8.pdf p.11, slide 22; p.12, slides 23-24 | Client/server roles and network trade-offs | Explicit remote errors, timeouts, deployment and restart behavior |
| Chuong12.pdf p.5, slides 9-10 | Build plans and implementation workflow | Ordered implementation plan and repeatable Ant verify |
| Chuong12.pdf p.6, slides 11-12 | Executable components mapped to nodes, integration | Separate client/backend executables; integration gate kept distinct |
| Chuong12.pdf p.9, slide 17 | White-/black-box unit testing; other testing levels remain | Boundary/state tests do not replace SQL/system acceptance |

Some course examples are C#; this delivery applies the design concepts in Java,
not a claim that the course prescribes Java syntax, HTTP, TLS, JSON or PBKDF2.
The concrete session lifetime, cancellation fence, API paths, Java records, DI
boundaries and SQL permissions are implementation decisions based on reviewed
source/engineering analysis, not quotations from the textbooks. The textbook's
Repository Architecture is not silently equated to a Java Repository interface.

The screenshot/diagram sections were read as rendered pages where available;
text from chapter PDFs was extracted without OCR. Uploaded textbooks are not
repackaged in the ZIP; the user already has them. See reference-inputs.json for
hashes identifying the inspected source files.

## Reviewed project source

Repository: https://github.com/anhyeuemnhat/DienMay
Pinned source base: https://github.com/anhyeuemnhat/DienMay/tree/83bec84a21714275296552a2f201962cc19089ba
`docs/provenance/upstream-files.csv` lists the reviewed files. Source claims in
the audit refer to that commit, not to uninspected newer commits. Missing source
coverage is documented explicitly in ACQUISITION.md.

## External implementation documentation (not substitutes for uploaded textbooks)

Official references checked during this implementation:

- Java HTTP server module: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/module-summary.html
- Google Java Style: https://google.github.io/styleguide/javaguide.html
- Microsoft JDBC release notes: https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver
- SQL Server graphical installation: https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-installation-wizard-setup
- Remote SQL Server TCP setup: https://learn.microsoft.com/en-us/sql/relational-databases/lesson-2-connecting-from-another-computer

JDBC 13.4.0.jre11 is pinned to the reviewed upstream dependency and its documented
JDK17/21 support, not advertised as the latest version. Google style is a reference;
the actually executed checker enforces the narrower rules documented separately.
