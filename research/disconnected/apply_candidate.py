#!/usr/bin/env python3
"""Research-only candidate; applies to a fresh unmodified upstream checkout on GitHub Actions."""
from pathlib import Path

def replace_once(path, old, new):
    p=Path(path)
    before=p.read_text()
    assert before.count(old)==1, f"{path}: expected exactly one insertion point, got {before.count(old)}"
    p.write_text(before.replace(old,new,1))

s="lib/src/main/java/ch/sbb/pfi/netzgrafikeditor/converter/core/SectionSequenceBuilder.java"
replace_once(s,
"    void add(TrainrunSection section) {",
"""    /**
     * An NGE trainrun may contain multiple disconnected sections, e.g. partial cancellation.
     * A converter route must not silently discard the other independent components.
     */
    List<EnumMap<RouteDirection, List<TrainrunSection>>> buildComponents(Trainrun.Direction direction) {
        Map<Integer, TrainrunSection> remaining = new HashMap<>(sections);
        List<EnumMap<RouteDirection, List<TrainrunSection>>> components = new java.util.ArrayList<>();
        while (!remaining.isEmpty()) {
            SectionSequenceBuilder component = new SectionSequenceBuilder(nodes);
            remaining.values().forEach(component::add);
            EnumMap<RouteDirection, List<TrainrunSection>> sequence = component.build(direction);
            List<TrainrunSection> visited = sequence.get(RouteDirection.FORWARD);
            if (visited.isEmpty()) {
                throw new IllegalStateException("No trainrun section found for a connected component");
            }
            visited.forEach(section -> remaining.remove(section.getId()));
            components.add(sequence);
        }
        return components;
    }

    void add(TrainrunSection section) {""")
c="lib/src/main/java/ch/sbb/pfi/netzgrafikeditor/converter/core/NetworkGraphicConverter.java"
replace_once(c,
"            createAndAddTransitLine(train, sequence.build(train.getDirection()));",
"""            for (EnumMap<RouteDirection, List<TrainrunSection>> component : sequence.buildComponents(train.getDirection())) {
                createAndAddTransitLine(train, component);
            }""")
print("RESEARCH_FIX_APPLIED: both Java locations; no upstream changes")
