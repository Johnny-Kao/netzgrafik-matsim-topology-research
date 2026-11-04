package ch.sbb.pfi.netzgrafikeditor.converter.adapter.matsim;

import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.InfrastructureRepository;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.RouteStop;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.StopFacilityInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.TrackSegmentInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.TransitLineInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.TransitRouteInfo;
import ch.sbb.pfi.netzgrafikeditor.converter.core.supply.TransportMode;
import ch.sbb.pfi.netzgrafikeditor.converter.util.spatial.Coordinate;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.utils.TransitScheduleValidator;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfrastructureBuilderTopologyTest {

    @Test
    void shouldConnectTwoRoutesConvergingOnSameCachedSegment() {
        InfrastructureRepository repository = mock(InfrastructureRepository.class);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        InfrastructureBuilder builder = new InfrastructureBuilder(scenario, new MatsimSupplyFactory(scenario),
                repository);

        StopFacilityInfo a = new StopFacilityInfo("A", "A", new Coordinate(0, 0));
        StopFacilityInfo b = new StopFacilityInfo("B", "B", new Coordinate(0, 1));
        StopFacilityInfo d = new StopFacilityInfo("D", "D", new Coordinate(2, 2));

        builder.buildTransitStopFacility(a);
        builder.buildTransitStopFacility(b);
        builder.buildTransitStopFacility(d);

        TransitLineInfo line = new TransitLineInfo("line", "category", TransportMode.RAIL);
        TransitRouteInfo routeA = new TransitRouteInfo("routeA", line);
        TransitRouteInfo routeB = new TransitRouteInfo("routeB", line);

        Coordinate junction = new Coordinate(1, 1);
        TrackSegmentInfo shared = new TrackSegmentInfo("junction-to-D", junction, d.getCoordinate(), 1000);
        when(repository.getTrack(a, d, routeA)).thenReturn(List.of(
                new TrackSegmentInfo("A-to-junction", a.getCoordinate(), junction, 500), shared));
        when(repository.getTrack(b, d, routeB)).thenReturn(List.of(
                new TrackSegmentInfo("B-to-junction", b.getCoordinate(), junction, 500), shared));

        builder.buildTransitRoute(routeA, List.of(
                new RouteStop(a, Duration.ZERO, Duration.ZERO),
                new RouteStop(d, Duration.ofMinutes(5), Duration.ZERO)));
        builder.buildTransitRoute(routeB, List.of(
                new RouteStop(b, Duration.ZERO, Duration.ZERO),
                new RouteStop(d, Duration.ofMinutes(5), Duration.ZERO)));

        var result = TransitScheduleValidator.validateNetworkRoutes(scenario.getTransitSchedule(),
                scenario.getNetwork());
        assertTrue(result.isValid(), () -> "Converging route topology errors: " + result.getErrors());
    }
}
