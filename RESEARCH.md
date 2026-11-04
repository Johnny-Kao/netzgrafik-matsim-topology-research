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

## Verified results (2026-11-05)

All runs used the actual Java 25/MATSim version in the upstream Maven build,
executed through manual GitHub Actions in **this independent research repo**.
None ran on the local Mac.

| Run | Code version | Result | Evidence |
| --- | --- | --- | --- |
| [Original + validator](https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/41858661615) | `6b45390` | **RED**: 89 tests, 1 failure; six disconnected route boundaries across forward/reverse routes | Real MATSim validator output |
| [Simple previous-node reuse](https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/41859291450) | `8555120` | **GREEN**: normal full Maven CI | Existing fixture: 16 -> 10 nodes, 16 links |
| [Cached shared-segment junction](https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/41860097074) | `4302ae6` | **RED**: 90 tests, 1 failure | Two routes converge at identical geometric junction J, but the cached J->D link uses a different start Node |
| [Junction-aware next-link reuse](https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/41860682605) | `f4b8ae8` | **GREEN**: Maven build and tests pass | Reuse the cached next link's `fromNode` when creating its predecessor |

The first minimal patch **was not sufficient** for shared cached segments. This
research branch therefore includes two complementary local connection rules:

1. For a new segment following an existing or newly-created link, use the
   preceding link's `toNode` as `fromNode`.
2. For a new segment preceding a previously cached next segment, use the
   next link's `fromNode` as `toNode`.

Both regressions must remain in the candidate fix. This is evidence of the two
tested topologies, **not** exhaustive proof for arbitrary infrastructure or
shared route geometry. No upstream PR has been opened.

## Exposure and follow-up

- The included `NoInfrastructureRepository` and `CsvInfrastructureRepository`
  currently return exactly one `TrackSegmentInfo` per `getTrack()` call, so
  the reproduced multi-segment defect does not affect those default paths.
- The public `InfrastructureRepository` contract returns a list, and the
  existing tests explicitly exercise multi-segment routes: custom repository
  consumers can encounter this bug.
- Reused segment IDs with inconsistent directions, stop nodes coincident with
  cached internal junctions, and coordinate-order/geometric correctness need
  separate investigation before claiming universal validity.
- Compare upstream history/issues/PRs before preparing an optional small PR;
  a related but distinct upstream fix is [#4](https://github.com/SchweizerischeBundesbahnen/netzgrafik-editor-converter/pull/4).

Important independent question: reverse-direction `C_B`/`D_C` fixture
coordinates appear out of order. MATSim's topology validator does **not**
establish geographic plausibility. Do not silently fold geometry/order,
fractional-time, cache redesign, or performance issues into the topology fix.

## GitHub Actions safety

The copied Maven CI is **manual-only** (`workflow_dispatch`).
The upstream release/publishing workflow is removed from this research copy.
Do not publish Maven packages from this repository or enable automatic releases.
