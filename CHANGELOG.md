<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Highlight Companion Changelog

## [Unreleased]

## [0.1.5]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.4]

### Added

- Review/star CTA: after 10 distinct methods crossing the red
  (genuinely concerning) complexity threshold -- never counted for
  ordinary green/yellow markers, which show up on healthy code too --
  a one-time notification asks whether to rate the plugin on
  Marketplace, with a permanent "Don't ask again" option. Standard
  mechanism used catalog-wide since 2026-08-24, rolled out to this
  plugin now.

## [0.1.3]

### Added

- Buried control-flow constructs (an `if` passed as a function
  argument, or one inside a lambda body) are now scored structurally
  instead of being invisible to the calculator — `foo(if (x > 0) a()
  else b())` and `items.forEach { if (it > 0) flag() }` both count.
  Never bleeds across a nested named function or class/object
  boundary: a local function's or an anonymous object's own body is
  still scored on its own.

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

[Unreleased]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.5...HEAD
[0.1.5]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/highlight-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/highlight-companion/commits/0.1.0
