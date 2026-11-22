package ch.sbb.pfi.netzgrafikeditor.converter.core;

import ch.sbb.pfi.netzgrafikeditor.converter.adapter.gtfs.model.GtfsSchedule;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.SupplyBuilder;
import ch.sbb.pfi.netzgrafikeditor.converter.core.validation.ValidationStrategy;
import ch.sbb.pfi.netzgrafikeditor.converter.io.netzgrafik.JsonFileReader;
import ch.sbb.pfi.netzgrafikeditor.converter.test.TestFile;
import ch.sbb.pfi.netzgrafikeditor.converter.util.time.ServiceDayTime;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Pr239ConnectionReviewTest {

    private final TestFile connections = new TestFile("ng/connections", "connections.json");

    private SupplyBuilder<GtfsSchedule> runAcrossMidnight() throws IOException {
        @SuppressWarnings("unchecked")
        SupplyBuilder<GtfsSchedule> builder = mock(SupplyBuilder.class);
        @SuppressWarnings("unchecked")
        ConverterSink<GtfsSchedule> sink = mock(ConverterSink.class);
        NetworkGraphicConverterConfig config = NetworkGraphicConverterConfig.builder()
                .validationStrategy(ValidationStrategy.WARN_ON_ISSUES)
                .serviceDayStart(ServiceDayTime.of(23, 0, 0))
                .serviceDayEnd(ServiceDayTime.of(25, 0, 0))
                .build();
        new NetworkGraphicConverter<>(config, new JsonFileReader(connections.getPath()), builder, sink).run();
        return builder;
    }

    @Test
    void forwardConnectionIsPairedOnBothSidesOfMidnight() throws IOException {
        SupplyBuilder<GtfsSchedule> builder = runAcrossMidnight();
        verify(builder).addConnection("B", "IC_A_B_FORWARD", ServiceDayTime.of(23, 5, 0),
                "IC_B_C_FORWARD", ServiceDayTime.of(23, 14, 0));
        verify(builder).addConnection("B", "IC_A_B_FORWARD", ServiceDayTime.of(24, 5, 0),
                "IC_B_C_FORWARD", ServiceDayTime.of(24, 14, 0));
    }

    @Test
    void differentFrequenciesDoNotCreateSpuriousMidnightTransfers() throws IOException {
        SupplyBuilder<GtfsSchedule> builder = runAcrossMidnight();
        verify(builder, never()).addConnection(eq("B"), eq("IC_A_B_FORWARD"),
                eq(ServiceDayTime.of(23, 35, 0)), anyString(), any());
        verify(builder, never()).addConnection(eq("B"), eq("IC_A_B_FORWARD"),
                eq(ServiceDayTime.of(24, 35, 0)), anyString(), any());
        verify(builder).addConnection("B", "IC_B_C_REVERSE", ServiceDayTime.of(23, 40, 0),
                "IC_A_B_REVERSE", ServiceDayTime.of(23, 50, 0));
        verify(builder).addConnection("B", "IC_B_C_REVERSE", ServiceDayTime.of(24, 40, 0),
                "IC_A_B_REVERSE", ServiceDayTime.of(24, 50, 0));
    }
}
