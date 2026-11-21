package ch.sbb.pfi.netzgrafikeditor.converter.io.csv;

import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.StopFacilityInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.TrackSegmentInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.test.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CsvInfrastructureRepositoryTest {

    @TempDir
    Path tempDir;

    private CsvInfrastructureRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        repo = new CsvInfrastructureRepository(TestScenario.ALL_TEST_CASES.getStopFacilityInfoCsvFilePath());
    }

    @Test
    void getStopFacility() {
        StopFacilityInfo stopFacilityInfo = repo.getStopFacility("A", "Stop A", 0., 0.);
        assertEquals("A", stopFacilityInfo.getId());
        assertEquals(46.948, stopFacilityInfo.getCoordinate().getLatitude());
        assertEquals(7.4474, stopFacilityInfo.getCoordinate().getLongitude());
    }

    @Test
    void getTrack() {
        List<TrackSegmentInfo> trackSegmentInfos = repo.getTrack(new StopFacilityInfo("A", "Stop A", null),
                new StopFacilityInfo("B", "Stop B", null), null);
        assertEquals(1, trackSegmentInfos.size());
        assertEquals(655.3, trackSegmentInfos.getFirst().getLength(), 0.1);

        // the segment runs from the node of the from stop to the node of the to stop, at the coordinates of the stops
        TrackSegmentInfo segment = trackSegmentInfos.getFirst();
        assertEquals("A", segment.getFromNode().getId());
        assertEquals("B", segment.getToNode().getId());
        assertEquals(repo.getStopFacility("A", "Stop A", 0., 0.).getCoordinate(),
                segment.getFromNode().getCoordinate());
        assertEquals(repo.getStopFacility("B", "Stop B", 0., 0.).getCoordinate(), segment.getToNode().getCoordinate());
    }

    @Test
    void tracksWithHyphensInDifferentStopIdsHaveDifferentSegmentIds() throws IOException {
        Path stops = tempDir.resolve("stops.csv");
        Files.writeString(stops, """
                stop_id,stop_name,stop_lat,stop_lon
                A-B,First origin,46.0,7.0
                C,First destination,46.1,7.1
                A,Second origin,46.2,7.2
                B-C,Second destination,46.3,7.3
                """);
        CsvInfrastructureRepository hyphenatedRepo = new CsvInfrastructureRepository(stops);

        TrackSegmentInfo first = hyphenatedRepo.getTrack(new StopFacilityInfo("A-B", "", null),
                new StopFacilityInfo("C", "", null), null).getFirst();
        TrackSegmentInfo second = hyphenatedRepo.getTrack(new StopFacilityInfo("A", "", null),
                new StopFacilityInfo("B-C", "", null), null).getFirst();

        assertNotEquals(first.getSegmentId(), second.getSegmentId());
    }
}
