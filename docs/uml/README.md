# UML assets

Editable PlantUML sources describe the delivered classes, independent deployment,
checkout/cancellation sequences and view inheritance. PlantUML itself was not
available to render these files. SVG/PNG companions were rendered with Graphviz
from the same implementation names; they are not screenshots of a running system.

`classes-core.puml` is intentionally a core subset. `views.puml` inheritance edges
are extracted from the actual delivered view declarations. `source-inventory.csv`
lists every Java file, package and SHA256. The deployment drawing distinguishes
physical JVM/database nodes from logical packages. The network-to-SQL path is
implemented but has no executed SQL Server integration evidence here.
