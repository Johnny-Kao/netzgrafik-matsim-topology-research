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
        // The editor interprets A-B and C-D (missing B-C) as two independent trainrun parts.
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
        // Start from an official converter fixture with two valid unconnected sections.
        // Make the second IC section part of the same trainrun but leave the two ports unconnected.
        ObjectMapper mapper = new ObjectMapper();
        var original = new TestFile("ng/connections", "connections.json");
        ObjectNode graphic = (ObjectNode) mapper.readTree(Files.readString(original.getPath()));
        ArrayNode trains = (ArrayNode) graphic.get("trainruns");
        for (int i = trains.size() - 1; i >= 0; --i) {
            if (trains.get(i).get("id").asInt() == 2) {
                trains.remove(i);
            }
        }
        ArrayNode sections = (ArrayNode) graphic.get("trainrunSections");
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).get("id").asInt() == 21) {
                ((ObjectNode) sections.get(i)).put("trainrunId", 1);
            }
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

            // Expect two distinct IC route components. Current converter emits only one.
            verify(builder, times(2)).addTransitRoute(startsWith("IC_"), anyString(), anyString(), any());
        } finally {
            Files.deleteIfExists(input);
        }
    }
}
