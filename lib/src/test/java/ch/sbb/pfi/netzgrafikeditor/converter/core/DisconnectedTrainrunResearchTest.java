package ch.sbb.pfi.netzgrafikeditor.converter.core;

import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Node;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Port;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.TrainrunSection;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Trainrun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectedTrainrunResearchTest {
    @Test
    void everySectionOfAnIncompleteTrainrunMustRemainInOutput() {
        // The editor explicitly interprets A-B and C-D (missing B-C) as two independent trainruns.
        var a = Node.builder().id(1).ports(List.of(Port.builder().id(11).trainrunSectionId(101).build())).transitions(List.of()).build();
        var b = Node.builder().id(2).ports(List.of(Port.builder().id(12).trainrunSectionId(101).build())).transitions(List.of()).build();
        var c = Node.builder().id(3).ports(List.of(Port.builder().id(13).trainrunSectionId(102).build())).transitions(List.of()).build();
        var d = Node.builder().id(4).ports(List.of(Port.builder().id(14).trainrunSectionId(102).build())).transitions(List.of()).build();

        var builder = new SectionSequenceBuilder(Map.of(1, a, 2, b, 3, c, 4, d));
        builder.add(TrainrunSection.builder().id(101).trainrunId(7).sourceNodeId(1).targetNodeId(2).build());
        builder.add(TrainrunSection.builder().id(102).trainrunId(7).sourceNodeId(3).targetNodeId(4).build());

        Set<Integer> emitted = builder.build(Trainrun.Direction.ONE_WAY)
                .values().stream().flatMap(List::stream).map(TrainrunSection::getId).collect(Collectors.toSet());
        assertEquals(Set.of(101, 102), emitted, "an unfinished trainrun must not silently lose one component");
    }
}
