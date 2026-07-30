<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Highlight Companion Changelog

## [Unreleased]

## [0.1.2]

### Changed

- Added a strict local `verifyPlugin` gate (catches
  `@ApiStatus.OverrideOnly`/`Internal`/`Experimental` API usage and
  compatibility problems before Marketplace's own verifier would) — no
  user-visible change, confirmed passing clean against all 6 target IDEs.

## [0.1.1]

### Added

- Gap Hunter Labs brand icon (`pluginIcon.svg` / `pluginIcon_dark.svg`).

## [0.1.0]

### Added

- Cognitive-complexity gutter icon for Java and Kotlin methods/functions,
  using the real algorithm from SonarSource's "Cognitive Complexity"
  whitepaper: control-flow nesting (if/else, for/while/do-while,
  switch/when, catch), mixed boolean-operator sequences, labeled
  break/continue, and recursion.
- The scoring algorithm is a pure, PSI-free class
  (`CognitiveComplexityCalculator`) unit tested in isolation with exact,
  hand-derived expected numbers, not just "greater than zero" checks.
- The PSI walk always runs off the EDT, inside `collectSlowLineMarkers` -
  the platform's own background "slow line markers" pass - never inline
  with typing.
- Per-function cache keyed on the function's own PSI element and the
  containing file's modification stamp: editing one file never
  recomputes complexity for functions in another file.
- Every rule (if/else, loops, switch/when, catch, mixed logical
  operators, labeled jumps, recursion) is individually toggle-able from
  Settings > Tools > Highlight Companion, with no hardcoded "always on"
  rule.
- Configurable green/yellow/red thresholds and a minimum-complexity
  cutoff before an icon is shown at all.
- No telemetry, no license prompts, no network access.

[Unreleased]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.2...HEAD
[0.1.2]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/highlight-companion/commits/0.1.0
