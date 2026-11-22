# Disconnected trainrun: maintainer-style refinement

Status: research branch only; **no upstream issue, PR or comment**.
Upstream baseline: `ff750392e4a36fcb3caf19cbd555003a42c2bcb2`.
Candidate: `research/disconnected/apply_candidate.py`; applies two source-file edits to an unmodified upstream checkout.

## Upstream code and review conventions

Reviewed `CODING_STANDARDS.md`, `CONTRIBUTING.md`, and maintainer-authored merged PR #235. The project values an explicit domain contract, small package-private helpers, reuse of existing data structures and validation, descriptive regression tests, and an end-to-end observed result rather than speculation. The #235 review explicitly covered preservation of existing contracts when cached entities are reused.

## Candidate design

- `SectionSequenceBuilder.buildComponents()`: retain original two-pass traversal and `SectionAligner`, process every independent path instead of silently dropping all but one.
- Maintain a single remaining-sections map and visited set, avoiding repeated copies of all still-unprocessed sections. `LinkedHashMap<>(sections)` preserves the **existing HashMap iteration order** before consuming sections, so route direction/identity of ordinary connected input does not change.
- `NetworkGraphicConverter.addTrains()`: one transit line per independent part. Leave supply builder, GTFS/MATSim writer, validation settings, source JSON schema and service-day behavior unchanged.
- Changes against upstream: 2 Java files, 53 insertions and 54 deletions including reshaping the existing traversal method; no new dependency or framework.

## Strong regression evidence

- Editor native production code path: delete middle section; exported DTO retains two disconnected sections for the same trainrun, ChromeHeadless 20/20 PASS: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45061964925
- Upstream main end-to-end RED: GTFS `stop_times.txt` loses C,D; MATSim has one route instead of two: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45060560891
- Maintainable candidate GREEN: https://github.com/Johnny-Kao/netzgrafik-matsim-topology-research/actions/runs/45063569173 — Maven full reactor BUILD SUCCESS, 159 lib unit, 73 lib integration, 20 app integration, plus test module; all zero failures.
- GTFS repaired: 4 trips / 8 stop_times / stations A,B,C,D. MATSim repaired: 2 transit routes.
- Additional research tests: round-trip directions, 3 disconnected parts, input section order, names-vs-constructed route IDs. No new failures.

## Important review finding: preserving baseline orientation

Two intermediate candidate revisions failed existing regression tests even though disconnected-path tests were green:
- Using `TreeMap` reordered the first section and flipped the direction of a normal journey (GTFS/MATSim integration checks).
- Copying with `new HashMap<>(sections)` resized hash buckets and changed which section was visited first for a fractional-time fixture.
- Current `LinkedHashMap<>(sections)` snapshots **the existing iteration order** and removes visited segments without reshuffling; complete integration suite is GREEN.

**Do not replace that preservation with a generic sort** without explicitly resolving which route direction contract the maintainer wants.

## Scope and limits

Confirmed software issue: valid Editor DTO can silently lose an entire independent route fragment on GTFS and MATSim conversion. This is a correctness/supply issue, not evidence of live SBB dispatch/signal impact.

Still to decide with maintainer: whether independently disconnected parts should always be separate transit lines and how to name routes/line IDs in this situation. Branch/cycle/ambiguous-transition inputs are outside the demonstrated linear-path contract and need separate validation if supported. There is no production exposure frequency data.

Do not open upstream issues or PRs without explicit approval.
