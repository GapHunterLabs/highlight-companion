# Highlight Companion

IntelliJ-family plugin. A cognitive-complexity gutter icon for Java and
Kotlin methods/functions — a colored badge with the real number, computed
with SonarSource's documented Cognitive Complexity algorithm, never blocking
the UI thread.

## Why it exists

Born from real, independent, recent (2026-02) evidence in JetBrains
Marketplace reviews of Better Highlights (1.03M downloads, FREEMIUM) — not
the raw aggregate rating, which is contaminated by unrelated products
sharing the word "better", but the actual review text once filtered:

- Multiple independent reports of IDE freezes on macOS, GoLand, and
  PyCharm: "IntelliJ freezes while analyzing source code. Disabling the
  plugin restores normal functionality" and "PyCharm application may
  become unresponsive, and the only solution is to force quit it."
- The competitor's own changelog admits the same root cause three releases
  in a row without fully fixing it: "(Bug Fix) Move slow checks out of EDT
  thread to decrease amount of freezes" (2025.3.2, 2025.3.3, 2025.3.4).
- A Freemium-tier complaint: "Many settings are silently disabled...
  blocker to me... features as simple as moving inlay hints to the RIGHT"
  and "I hate that I cannot disable Cognitive Complexity. Even after
  unchecking all checkboxes all code is underlined."

## Why built this way

- **The scoring algorithm is PSI-free.** `CognitiveComplexityCalculator`
  scores a small sealed-class tree (`ControlNode`) that has never heard of
  `PsiElement`. Java and Kotlin each get their own walker
  (`JavaCognitiveWalker`, `KotlinCognitiveWalker`) that translate a real
  method/function body into that tree. This is what makes the algorithm
  unit-testable with exact, hand-derived numbers instead of "greater than
  zero" — see `CognitiveComplexityCalculatorTest`.
- **The PSI walk itself never touches the EDT.** It only ever runs inside
  `LineMarkerProviderDescriptor.collectSlowLineMarkers`, the platform's own
  background pass for exactly this kind of per-method heavy computation.
  `getLineMarkerInfo` (the fast, far-more-frequent pass) is a hard no-op —
  the direct fix for "freezes while analyzing source code" and the
  competitor's own repeated, half-finished changelog entry about moving
  slow checks off the EDT.
- **Per-function cache, not per-keystroke recompute.** `ComplexityCache`
  stores each function's score as user data on its own PSI element, keyed
  by that file's modification stamp (per-file, not global) plus a settings
  epoch. Editing file A never recomputes anything for file B; in the common
  case of an incremental block reparse, editing inside one function doesn't
  even recompute its unchanged siblings in the same file.
- **Every rule is a real, independent, unchecked-by-default-nothing
  toggle.** `RuleConfig` has one boolean per rule (if/else, loops,
  switch/when, catch, mixed logical operators, labeled jumps, recursion),
  all wired to real checkboxes in Settings > Tools > Highlight Companion.
  Disabling a rule doesn't just hide its own contribution — it stops it
  from adding a nesting level to whatever it contains, so a disabled rule
  never leaves a "ghost" penalty behind. Direct fix for "I hate that I
  cannot disable Cognitive Complexity... all code is underlined."
- **No fixed icon set.** The gutter badge is rendered on demand
  (`ComplexityGutterIcon`) because the number is arbitrary and the
  green/yellow/red thresholds are user-configurable, not fixed tiers.
- **Explicit K1/K2 Kotlin plugin mode support.** `plugin.xml` declares
  `<supportsKotlinPluginMode supportsK1="true" supportsK2="true"/>` — without
  it, the whole plugin silently fails to load under the Kotlin plugin's K2
  mode (the default since 2024.x), caught by a real `buildSearchableOptions`
  run logging "incompatible with Kotlin in K2 mode", not by inspection.
  Safe to support both: neither walker calls a resolve/analysis API that
  differs between K1 and K2, only syntactic PSI navigation.

## What "Cognitive Complexity" means here

Based on SonarSource's "Cognitive Complexity: A new way of measuring
understandability" whitepaper (G. Ann Campbell), restricted to the subset
of constructs this plugin recognizes:

- if / else-if / else, for / while / do-while, switch / when, and catch
  each add 1, plus their current nesting depth (an else-if chain doesn't
  compound nesting across its own rungs; a plain else is +1 flat).
- A sequence of mixed `&&`/`||` operators in one condition adds 1 per run
  of the same operator (so `a && b && c` costs 1, but `a && b || c` costs
  2).
- A labeled `break`/`continue` adds a flat 1, regardless of depth.
- A recursive call adds a flat 1 per call, detected by simple-name match
  against the enclosing function (no full resolve, by design — see the
  walkers' own doc comments for the tradeoff).

A control-flow expression buried inside a larger expression (an `if`
passed as a function argument, or one inside a lambda body) is scored
structurally, not just statements/return values/branch bodies/property
initializers — `foo(if (x > 0) a() else b())` and
`items.forEach { if (it > 0) flag() }` both count the inner `if`. A
buried construct never bleeds across a nested named function or
class/object boundary, though: a local function's or an anonymous
object's own body is scored on its own, never folded into the
enclosing function's number.

Known v0.1 limitation, still open: nested functions/lambdas adding
their own ambient nesting level to the *code inside them* (a real rule
in the whitepaper) isn't modeled — a buried construct is scored as if
it were a direct statement, without extra nesting credit for being
inside a lambda. Documented gap, not a silent one.

## Usage

Open any Java or Kotlin file. Every method/function gets a colored gutter
badge with its cognitive complexity number. Hover for the exact thresholds;
open Settings > Tools > Highlight Companion to toggle any rule or change
the green/yellow/red cutoffs.

## Enterprise / Team Licensing

Need enterprise features, custom complexity rules, or team licensing?
Contact us at **gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
