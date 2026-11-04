# MATSim network-route topology research

This is an **independent copy, not a GitHub fork**, of
[SchweizerischeBundesbahnen/netzgrafik-editor-converter](https://github.com/SchweizerischeBundesbahnen/netzgrafik-editor-converter).

Upstream baseline: `main` at `1899248624377f493d8952d82e9ea516c96b4a93` (reviewed 2026-11-05).
The upstream source code and history remain attributable to SBB under **GPL-3.0**.
This repository is for research only; it is not an official SBB project.

## Hypothesis and source evidence

In `InfrastructureBuilder.connect()`, each non-first `TrackSegmentInfo` currently
creates its own `<segmentId>_from_node`. The preceding segment uses a separate
`<previousSegmentId>_to_node`. Identical coordinates do not make these MATSim
network nodes identical.

A MATSim `NetworkRoute` requires each preceding link's `toNode` to equal
the following link's `fromNode`. MATSim provides
`TransitScheduleValidator.validateNetworkRoutes(schedule, network)` to check this.

`MatsimSupplyBuilderTest.testBuild()` exercises forward `B_C` (two segments) and
`C_D` (three segments), as well as reverse routes, but currently asserts only
counts and does not call that validator.

## Reproduction status

- **Source inspection:** the identity mismatch is present in upstream baseline.
- **Isolated Python topology model (handoff):** 3 broken boundaries on forward
  `B_C` and `C_D`; 0 after the proposed node-reuse change.
- **Actual upstream Java 25/MATSim red-green regression:** NOT YET RUN.
- **No production patch committed and no upstream PR submitted.**

The simplest candidate fix reuses the previous link's `toNode` as the next
new segment's `fromNode`. In the existing test fixture, the proposed
change predicts 16 -> 10 nodes and unchanged 16 links. Those are predictions,
not verified integration-test results.

## Validation plan

1. Add **only** the regression assertion calling
   `TransitScheduleValidator.validateNetworkRoutes(...).isValid()`.
   Leave the old node-count expectation intact and establish a failing
   baseline test in the real upstream build.
2. Inspect the exact validation failures; avoid mistaking unrelated
   route problems for confirmation of this hypothesis.
3. Apply a narrow production fix. Reconcile the test's node count and verify
   links, stop facilities, route ordering, and cached-segment reuse.
4. Run the focused JUnit test and full Maven suite with JDK 25 on GitHub Actions.
5. Re-check upstream issues/PRs and contribution instructions before proposing
   any upstream change.

Important independent question: reverse-direction `C_B`/`D_C` fixture
coordinates appear out of order. MATSim's topology validator does **not**
establish geographic plausibility. Do not silently fold geometry/order,
fractional-time, cache redesign, or performance issues into the topology fix.

## GitHub Actions safety

The copied Maven CI is **manual-only** (`workflow_dispatch`).
The upstream release/publishing workflow is removed from this research copy.
Do not publish Maven packages from this repository or enable automatic releases.
