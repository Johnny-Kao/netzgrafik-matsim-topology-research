# SBB Netzgrafik Converter — disconnected trainrun component loss

Research status: **verified source-to-output RED / candidate GREEN; no upstream Issue or PR**.
Baseline: upstream `SchweizerischeBundesbahnen/netzgrafik-editor-converter` `ff750392e4a36fcb3caf19cbd555003a42c2bcb2` (2026-11-18).

## Business model and reproduction

The Netzgrafik Editor documents interrupted trainruns such as A-B [gap] C-D and explicitly includes partial cancellation as one reason. Its own `TrainrunSectionService.deleteTrainrunSection` and `DataService.getNetzgrafikDto()` were run on the Editor's own test fixture under ChromeHeadless. Deleting the middle section of trainrun 2 leaves section IDs 3 and 5, both with `trainrunId=2`. This is an ordinary producer state, not necessarily malformed data.

Editor-native test: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45061964925 — ChromeHeadless 20/20 pass; log `EDITOR_EXPORT_RESEARCH`, before [3,4,5], after [3,5].

## Production converter path (upstream, unpatched) — RED

The converter's `NetworkGraphicConverter.addTrains()` groups all sections by `trainrunId`, then calls `SectionSequenceBuilder.build()` once per ID. `build()` begins with a single section, traverses only one connected component, and returns without checking that all input sections were visited. Existing `NetworkGraphicValidator` checks identifier hygiene; it does not validate component coverage. The converter runs to completion and emits a partial but syntactically valid supply schedule.

Reproduction is independent of any mocked final output:
- Official upstream `ng/cases/simple.json`, transformed by the analogous middle-section deletion A-B-C-D -> A-B / C-D.
- Full `JsonFileReader -> NetworkGraphicConverter -> GtfsSupplyBuilder -> GtfsScheduleWriter`: GTFS has **2 trips**, **4 stop_times**, stop IDs only **A,B**, losing C,D.
- Full `JsonFileReader -> NetworkGraphicConverter -> MatsimSupplyBuilder -> TransitScheduleXmlWriter`: MATSim has **1 transit route**, rather than two. XML writer completes.
- Continuous unmodified input passes.
- RED: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45060560891

## Candidate repair — GREEN

Research-only `research/disconnected/apply_candidate.py` updates just two upstream Java files:
- `SectionSequenceBuilder.buildComponents(direction)` enumerates each unvisited connected component rather than dropping it.
- `NetworkGraphicConverter.addTrains()` creates a distinct transit line for each resulting component.
- Scope: +24 / -1 Java source lines relative to the frozen baseline. Not yet cleaned into production-ready code.

Results on the same source-to-output pipelines:
- GTFS: **4 trips**, **8 stop_times**, stop IDs **A,B,C,D**.
- MATSim: **2 routes**, serialized XML produced.
- Round-trip with two fragments: four directional services, expected eight GTFS trips in two-hour window.
- Three isolated fragments: three routes, six trips in two-hour window.
- Reversing JSON section order: same coverage/trip count (with and without using names for route IDs).
- Full Maven reactor `mvn install`: **BUILD SUCCESS**. Test summaries: 159 lib unit + 73 lib integration + 20 app integration, plus test module checks.
- GREEN: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45062282958

## Severity and acceptance assessment

- Confirmed impact: conditional **silent omission of complete services** in GTFS and MATSim outputs for a producer-supported shape.
- Affected use: planning/simulation/supply exports, **not** evidence of SBB live signalling or live railway dispatch impact.
- Real-world frequency, production adoption and operational exposure are **unknown**. None can be inferred from a test fixture alone.
- The repair is feasible and minimally scoped, but acceptance requires maintainer confirmation of intended converter representation of multiple disconnected fragments.
- **Keep in private research until upstream contract is confirmed; do not post Issue/PR without explicit approval.**

## Research risks still worth considering

1. Candidate algorithm builds temporary sub-builders from all remaining sections every iteration. This is simple but can be quadratic in fragment count; a visited-set traversal would be cleaner before proposing a production PR.
2. Deterministic component order and stable output IDs should be explicitly specified; the implementation currently uses a `HashMap`.
3. Additional malformed graphs (branches, cycles, duplicated port mappings) are not proven supported; should fail explicitly rather than be silently truncated.
4. Compare exact serialized output for ordinary contiguous routes, not only regression PASS, if maintainers demand strict byte-level backward compatibility.
5. Editor-native test validates service calls and DTO serialization, not a manual mouse click. It does establish the real producer data state.

Unrelated PR #239 intentionally excluded from recommendations.
