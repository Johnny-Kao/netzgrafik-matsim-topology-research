#!/usr/bin/env python3
"""Apply the isolated disconnected-trainrun candidate to the frozen upstream checkout."""
from pathlib import Path

def replace_once(path, before, after):
    file = Path(path)
    content = file.read_text()
    assert content.count(before) == 1, f"{path}: expected one matching code block"
    file.write_text(content.replace(before, after, 1))

sequence = "lib/src/main/java/ch/sbb/pfi/netzgrafikeditor/converter/core/SectionSequenceBuilder.java"
replace_once(sequence,
"""import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;""",
"""import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;""")

old_build = Path(sequence).read_text()
start = old_build.index('    /**\n     * Traverse the sections and build an ordered, directed section sequences.')
end = old_build.index('    private TrainrunSection getNextSection(', start)
original = old_build[start:end]

replacement = """    /**
     * Build a separate route for each connected part of a trainrun.
     * The editor retains disconnected parts, for example after a partial cancellation.
     */
    List<EnumMap<RouteDirection, List<TrainrunSection>>> buildComponents(Trainrun.Direction direction) {
        Map<Integer, TrainrunSection> remaining = new TreeMap<>(sections);
        List<EnumMap<RouteDirection, List<TrainrunSection>>> components = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<TrainrunSection> orderedSections = new LinkedList<>();

            // find one end of this connected part
            TrainrunSection randomSection = remaining.values().iterator().next();
            AtomicReference<TrainrunSection> firstSection = new AtomicReference<>();
            traverse(randomSection, firstSection::set, remaining, false);

            // collect only this part and remove its sections from the remaining ones
            traverse(firstSection.get(), orderedSections::addFirst, remaining, true);

            SectionAligner.align(orderedSections);
            TrainrunSection first = orderedSections.getFirst();
            boolean drawnDirection = first.getSourceNodeId() == sections.get(first.getId()).getSourceNodeId();

            components.add(switch (direction) {
                case ROUND_TRIP -> new EnumMap<>(Map.of(RouteDirection.FORWARD, orderedSections,
                        RouteDirection.REVERSE, SectionAligner.reverse(orderedSections)));
                case ONE_WAY -> new EnumMap<>(Map.of(RouteDirection.FORWARD,
                        drawnDirection ? orderedSections : SectionAligner.reverse(orderedSections)));
            });
        }

        return components;
    }

    private void traverse(TrainrunSection root, Consumer<TrainrunSection> action,
                          Map<Integer, TrainrunSection> remaining, boolean removeVisited) {
        Set<Integer> visited = new HashSet<>();
        TrainrunSection current = root;

        while (current != null) {
            action.accept(current);
            visited.add(current.getId());
            if (removeVisited) {
                remaining.remove(current.getId());
            }

            TrainrunSection next = getNextSection(current, TraversalMode.TARGET, remaining, visited);
            if (next == null) {
                next = getNextSection(current, TraversalMode.SOURCE, remaining, visited);
            }
            current = next;
        }
    }

"""
replace_once(sequence, original, replacement)
replace_once(sequence,
"""    private TrainrunSection getNextSection(TrainrunSection section, TraversalMode traversalMode, Map<Integer, TrainrunSection> sectionsToVisit) {""",
"""    private TrainrunSection getNextSection(TrainrunSection section, TraversalMode traversalMode,
                                            Map<Integer, TrainrunSection> sectionsToVisit, Set<Integer> visited) {""")

replace_once(sequence,
"""                    if (transition.getPort1Id() == port.getId()) {
                        return sectionsToVisit.get(ports.get(transition.getPort2Id()).getTrainrunSectionId());
                    }

                    // transition connects on port 2, return next section on port 1
                    if (transition.getPort2Id() == port.getId()) {
                        return sectionsToVisit.get(ports.get(transition.getPort1Id()).getTrainrunSectionId());
                    }""",
"""                    if (transition.getPort1Id() == port.getId()) {
                        int nextId = ports.get(transition.getPort2Id()).getTrainrunSectionId();
                        return visited.contains(nextId) ? null : sectionsToVisit.get(nextId);
                    }

                    // transition connects on port 2, return next section on port 1
                    if (transition.getPort2Id() == port.getId()) {
                        int nextId = ports.get(transition.getPort1Id()).getTrainrunSectionId();
                        return visited.contains(nextId) ? null : sectionsToVisit.get(nextId);
                    }""")

converter = "lib/src/main/java/ch/sbb/pfi/netzgrafikeditor/converter/core/NetworkGraphicConverter.java"
replace_once(converter,
"""            createAndAddTransitLine(train, sequence.build(train.getDirection()));""",
"""            for (EnumMap<RouteDirection, List<TrainrunSection>> component : sequence.buildComponents(train.getDirection())) {
                createAndAddTransitLine(train, component);
            }""")

print("Applied source-only fix: complete all connected parts without copying the remaining map.")
