package ch.sbb.pfi.netzgrafikeditor.converter.core;

import ch.sbb.pfi.netzgrafikeditor.converter.adapter.gtfs.model.GtfsSchedule;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Node;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Port;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.TrainrunSection;
import ch.sbb.pfi.netzgrafikeditor.converter.core.model.Trainrun;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.SupplyBuilder;
import ch.sbb.pfi.netzgrafikeditor.converter.core.validation.ValidationStrategy;
import ch.sbb.pfi.netzgrafikeditor.converter.io.netzgrafik.JsonFileReader;
import ch.sbb.pfi.netzgrafikeditor.converter.test.TestFile;
import ch.sbb.pfi.netzgrafikeditor.converter.util.time.ServiceDayTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisconnectedTrainrunResearchTest {
    @Test
    void everySectionOfAnIncompleteTrainrunMustRemainInOutput() {
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

    @Test
    void fullConverterMustPreserveBothDisconnectedParts() throws IOException {
        // Official simple.json has A-B, B-C, C-D for one trainrun.
        // Remove middle B-C and its endpoints' now-obsolete transitions: an Editor-supported hole.
        ObjectMapper mapper = new ObjectMapper();
        var original = new TestFile("ng/cases", "simple.json");
        ObjectNode graphic = (ObjectNode) mapper.readTree(Files.readString(original.getPath()));
        ((ArrayNode) graphic.get("trainrunSections")).remove(1);
        ((ObjectNode) ((ArrayNode) graphic.get("trainruns")).get(0)).put("direction", "one_way");
        for (var node : (ArrayNode) graphic.get("nodes")) {
            ArrayNode ports = (ArrayNode) node.get("ports");
            for (int i = ports.size() - 1; i >= 0; --i) {
                if (ports.get(i).get("trainrunSectionId").asInt() == 4) ports.remove(i);
            }
            ((ArrayNode) ((ObjectNode) node).get("transitions")).removeAll();
        }
        var input = Files.createTempFile("disconnected-nge-", ".json");
        try {
            mapper.writeValue(input.toFile(), graphic);
            @SuppressWarnings("unchecked")
            SupplyBuilder<GtfsSchedule> builder = mock(SupplyBuilder.class);
            @SuppressWarnings("unchecked")
            ConverterSink<GtfsSchedule> sink = mock(ConverterSink.class);
            var config = NetworkGraphicConverterConfig.builder()
                    .validationStrategy(ValidationStrategy.WARN_ON_ISSUES)
                    .serviceDayStart(ServiceDayTime.of(6, 0, 0))
                    .serviceDayEnd(ServiceDayTime.of(8, 0, 0))
                    .build();
            new NetworkGraphicConverter<>(config, new JsonFileReader(input), builder, sink).run();
            // Both A-B and C-D are independent one-way routes in the editor model.
            verify(builder, times(2)).addTransitRoute(startsWith("IC_"), anyString(), anyString(), any());
        } finally {
            Files.deleteIfExists(input);
        }
    }
}
