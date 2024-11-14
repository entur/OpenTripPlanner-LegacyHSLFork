package org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapperTest.RequestFeature.RELAX_COST_DEST;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapperTest.RequestFeature.TRANSIT_GROUP_PRIORITY;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapperTest.RequestFeature.VIA_PASS_THROUGH;
import static org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapperTest.RequestFeature.VIA_VISIT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor._data.transit.TestAccessEgress;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.request.RaptorRequest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;
import org.opentripplanner.routing.api.request.via.PassThroughViaLocation;
import org.opentripplanner.routing.api.request.via.VisitViaLocation;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.utils.collection.ListUtils;

class RaptorRequestMapperTest {

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();
  private static final StopLocation STOP_A = TEST_MODEL.stop("Stop:A").build();
  public static final PassThroughViaLocation PASS_THROUGH_VIA_LOCATION = new PassThroughViaLocation(
    "Via A",
    List.of(STOP_A.getId())
  );
  public static final VisitViaLocation VISIT_VIA_LOCATION = new VisitViaLocation(
    "Via A",
    null,
    List.of(STOP_A.getId()),
    List.of()
  );
  private static final List<RaptorAccessEgress> ACCESS = List.of(TestAccessEgress.walk(12, 45));
  private static final List<RaptorAccessEgress> EGRESS = List.of(TestAccessEgress.walk(144, 54));

  private static final CostLinearFunction R1 = CostLinearFunction.of("50 + 1.0x");
  private static final CostLinearFunction R2 = CostLinearFunction.of("0 + 1.5x");
  private static final CostLinearFunction R3 = CostLinearFunction.of("30 + 2.0x");

  private static final Map<FeedScopedId, StopLocation> STOPS_MAP = Map.of(STOP_A.getId(), STOP_A);
  private static final CostLinearFunction RELAX_TRANSIT_GROUP_PRIORITY = CostLinearFunction.of(
    "30m + 1.2t"
  );
  public static final double RELAX_GENERALIZED_COST_AT_DESTINATION = 2.0;

  static List<Arguments> testCasesRelaxedCost() {
    return List.of(
      Arguments.of(CostLinearFunction.NORMAL, 0, 0),
      Arguments.of(CostLinearFunction.NORMAL, 10, 10),
      Arguments.of(R1, 0, 5000),
      Arguments.of(R1, 7, 5007),
      Arguments.of(R2, 0, 0),
      Arguments.of(R2, 100, 150),
      Arguments.of(R3, 0, 3000),
      Arguments.of(R3, 100, 3200)
    );
  }

  @ParameterizedTest
  @MethodSource("testCasesRelaxedCost")
  void mapRelaxCost(CostLinearFunction input, int cost, int expected) {
    var calcCost = RaptorRequestMapper.mapRelaxCost(input);
    assertEquals(expected, calcCost.relax(cost));
  }

  @Test
  void testViaLocation() {
    var req = new RouteRequest();
    var minWaitTime = Duration.ofMinutes(13);

    req.setViaLocations(
      List.of(new VisitViaLocation("Via A", minWaitTime, List.of(STOP_A.getId()), List.of()))
    );

    var result = map(req);

    assertTrue(result.searchParams().hasViaLocations());
    assertEquals(
      "[Via{label: Via A, minWaitTime: 13m, connections: [0 13m]}]",
      result.searchParams().viaLocations().toString()
    );
  }

  @Test
  void testPassThroughPoints() {
    var req = new RouteRequest();

    req.setViaLocations(List.of(new PassThroughViaLocation("Via A", List.of(STOP_A.getId()))));

    var result = map(req);

    assertTrue(result.multiCriteria().hasPassThroughPoints());
    assertEquals(
      "[(Via A, stops: " + STOP_A.getIndex() + ")]",
      result.multiCriteria().passThroughPoints().toString()
    );
  }

  @Test
  void testTransitGroupPriority() {
    var req = new RouteRequest();

    // Set relax transit-group-priority
    req.withPreferences(p ->
      p.withTransit(t -> t.withRelaxTransitGroupPriority(CostLinearFunction.of("30m + 1.2t")))
    );

    var result = map(req);

    assertTrue(result.multiCriteria().transitPriorityCalculator().isPresent());
  }

  static List<Arguments> testViaAndTransitGroupPriorityCombinationsTestCases() {
    return List.of(
      Arguments.of(List.of(VIA_VISIT), List.of(VIA_VISIT), null),
      Arguments.of(List.of(VIA_PASS_THROUGH), List.of(VIA_PASS_THROUGH), null),
      Arguments.of(List.of(TRANSIT_GROUP_PRIORITY), List.of(TRANSIT_GROUP_PRIORITY), null),
      Arguments.of(List.of(RELAX_COST_DEST), List.of(RELAX_COST_DEST), null),
      Arguments.of(
        List.of(VIA_VISIT, TRANSIT_GROUP_PRIORITY),
        List.of(VIA_VISIT, TRANSIT_GROUP_PRIORITY),
        null
      ),
      Arguments.of(
        List.of(VIA_PASS_THROUGH, TRANSIT_GROUP_PRIORITY, RELAX_COST_DEST),
        List.of(VIA_PASS_THROUGH),
        null
      ),
      Arguments.of(
        List.of(VIA_VISIT, TRANSIT_GROUP_PRIORITY, RELAX_COST_DEST),
        List.of(VIA_VISIT, TRANSIT_GROUP_PRIORITY),
        null
      ),
      Arguments.of(
        List.of(TRANSIT_GROUP_PRIORITY, RELAX_COST_DEST),
        List.of(TRANSIT_GROUP_PRIORITY),
        null
      ),
      Arguments.of(
        List.of(VIA_VISIT, VIA_PASS_THROUGH),
        List.of(),
        "A mix of via-locations and pass-through is not allowed in this version."
      )
    );
  }

  @ParameterizedTest
  @MethodSource("testViaAndTransitGroupPriorityCombinationsTestCases")
  void testViaAndTransitGroupPriorityCombinations(
    List<RequestFeature> input,
    List<RequestFeature> expectedEnabledFeatures,
    @Nullable String errorMessage
  ) {
    var req = new RouteRequest();

    for (RequestFeature it : input) {
      req = setFeaturesOnRequest(req, it);
    }

    if (errorMessage == null) {
      var result = map(req);

      for (var feature : RequestFeature.values()) {
        assertFeatureSet(feature, result, expectedEnabledFeatures.contains(feature));
      }
    } else {
      var r = req;
      var ex = Assertions.assertThrows(IllegalArgumentException.class, () -> map(r));
      assertEquals(errorMessage, ex.getMessage());
    }
  }

  private static RaptorRequest<TestTripSchedule> map(RouteRequest request) {
    return RaptorRequestMapper.mapRequest(
      request,
      ZonedDateTime.now(),
      false,
      ACCESS,
      EGRESS,
      null,
      id -> IntStream.of(STOPS_MAP.get(id).getIndex())
    );
  }

  private static void assertFeatureSet(
    RequestFeature feature,
    RaptorRequest<?> result,
    boolean expected
  ) {
    switch (feature) {
      case VIA_VISIT:
        if (expected) {
          assertTrue(result.searchParams().hasViaLocations());
          // One via location exist(no NPE), but it does not allow pass-through
          assertEquals(
            "Via{label: Via A, connections: [0]}",
            result.searchParams().viaLocations().get(0).toString()
          );
        }
        break;
      case VIA_PASS_THROUGH:
        if (expected) {
          assertTrue(result.multiCriteria().hasPassThroughPoints());
          assertEquals(
            "(Via A, stops: 0)",
            result.multiCriteria().passThroughPoints().get(0).toString()
          );
        }
        break;
      case TRANSIT_GROUP_PRIORITY:
        assertEquals(expected, result.multiCriteria().transitPriorityCalculator().isPresent());
        if (expected) {
          assertFalse(result.multiCriteria().hasPassThroughPoints());
        }
        break;
      case RELAX_COST_DEST:
        assertEquals(expected, result.multiCriteria().relaxCostAtDestination() != null);
        if (expected) {
          assertFalse(result.multiCriteria().hasPassThroughPoints());
          assertFalse(result.searchParams().hasViaLocations());
        }
        break;
    }
  }

  private static RouteRequest setFeaturesOnRequest(RouteRequest req, RequestFeature feature) {
    return switch (feature) {
      case VIA_VISIT -> req.setViaLocations(List.of(VISIT_VIA_LOCATION));
      case VIA_PASS_THROUGH -> req.setViaLocations(
        ListUtils.combine(req.getViaLocations(), List.of(PASS_THROUGH_VIA_LOCATION))
      );
      case TRANSIT_GROUP_PRIORITY -> req.withPreferences(p ->
        p.withTransit(t -> t.withRelaxTransitGroupPriority(RELAX_TRANSIT_GROUP_PRIORITY))
      );
      case RELAX_COST_DEST -> req.withPreferences(p ->
        p.withTransit(t ->
          t.withRaptor(r ->
            r.withRelaxGeneralizedCostAtDestination(RELAX_GENERALIZED_COST_AT_DESTINATION)
          )
        )
      );
    };
  }

  enum RequestFeature {
    VIA_VISIT,
    VIA_PASS_THROUGH,
    TRANSIT_GROUP_PRIORITY,
    RELAX_COST_DEST,
  }
}
