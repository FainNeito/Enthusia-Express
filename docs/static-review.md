# Museum static-review refinement

Codacy reported 100 new issues on the first candidate, 3d93ba6. The follow-up separates platform inventories, metadata input, command routing, review policy, and audit/notification SQL. It reduces long handlers, nested conditions and repeated literals, fixes Java fixture naming/imports/declarations, and gives the isolated probe a named package. No gate or global rule is disabled.

The following narrow exceptions retain intentional contracts:

- `LongParameterList` on the two Java-facing asynchronous museum commands and their atomic review adapter preserves their explicit actor/revision signatures, consistently with existing MapartStore/EventDeliveries methods.
- `LabeledExpression` only on callbacks that discard stale, disconnected or unauthorized work. These are local lambda returns, not recursive calls or player-controlled labels.
- `SpreadOperator` only on `Method.invoke`: Java reflection requires the unpacked argument array. The calls occur during menu construction, not every tick.
- ESLint's non-literal filesystem rule and line-local Semgrep annotations in the local SPEAR CLI/test tools cover deliberate developer-selected document/state paths and temporary test fixtures. No player, HTTP request or plugin input reaches these paths. CLI paths are intentionally configurable; tests exercise their locks, atomic replacement and isolation. The one array-index warning uses a locally computed, checked `findIndex` result. None of these tools is bundled in the plugin JAR.

The original red regressions remain unchanged. Full baseline/API tests, compiled call-cycle/layer rules, the actual-baseline-JAR final-26.3 run, isolated server probe and exact-head hosted analysis must be checked again after refinement. A broad local Detekt run also reports legacy complexity outside the added museum types; it is not represented as a clean whole-project audit.
