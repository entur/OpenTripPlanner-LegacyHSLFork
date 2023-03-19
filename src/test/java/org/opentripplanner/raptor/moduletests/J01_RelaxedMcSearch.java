package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.free;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTransfer.transfer;
import static org.opentripplanner.raptor._data.transit.TestTripPattern.pattern;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.model.IncValueRelaxFunction;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority.TestTransitPriorityCalculator;

/**
 *
 */
public class J01_RelaxedMcSearch implements RaptorTestConstants {

  static String p0 =
    "A ~ BUS L1a 0:01 0:03 ~ B ~ Walk 1s ~ E ~ BUS L99 0:05 0:07 ~ F [0:01 0:07 6m 1tx $1561]";
  static String p1 =
    "A ~ BUS L1b 0:02 0:08 ~ D ~ Walk 1s ~ E ~ BUS L99 0:09 0:11 ~ F [0:02 0:11 8m 1tx $1681]";
  static String p2 =
    "A ~ BUS L1a 0:03 0:05 ~ B ~ Walk 1s ~ E ~ BUS L99 0:07 0:09 ~ F [0:03 0:09 6m 1tx $1561]";
  static String p3 =
    "C ~ BUS L1b 0:04 0:08 ~ D ~ Walk 1s ~ E ~ BUS L99 0:10 0:12 ~ F [0:04 0:12 8m 1tx $1681]";

  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = new RaptorRequestBuilder<>();
  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  @BeforeEach
  void setup() {
    data
      .withRoutes(
        route(pattern("L1a", STOP_A, STOP_B))
          .withTimetable(schedule("00:01 00:03"), schedule("00:03 00:05")),
        route(pattern("L1b", STOP_C, STOP_D))
          .withTimetable(
            schedule("00:02 00:06").transitPriorityGroupIndex(1),
            schedule("00:04 00:08").transitPriorityGroupIndex(1)
          ),
        route(pattern("L1c", STOP_C, STOP_B))
          .withTimetable(
            schedule("00:01 00:05").transitPriorityGroupIndex(2),
            schedule("00:03 00:07").transitPriorityGroupIndex(2)
          ),
        route(pattern("L99", STOP_E, STOP_F))
          .withTimetable(
            schedule("00:03 00:05"),
            schedule("00:04 00:06"),
            schedule("00:05 00:07"),
            schedule("00:06 00:08"),
            schedule("00:07 00:09"),
            schedule("00:08 00:10"),
            schedule("00:09 00:11"),
            schedule("00:10 00:12"),
            schedule("00:11 00:13"),
            schedule("00:12 00:14")
          )
      )
      .withTransfer(STOP_B, transfer(STOP_E, D1s))
      .withTransfer(STOP_D, transfer(STOP_E, D1s));
    requestBuilder
      .searchParams()
      .addAccessPaths(free(STOP_A), free(STOP_C))
      .addEgressPaths(free(STOP_F))
      .earliestDepartureTime(T00_00)
      .latestArrivalTime(T00_30)
      .searchWindow(Duration.ofMinutes(5))
      .timetable(true);

    // TODO - Test plain MC, add tests for MD later
    requestBuilder.clearOptimizations();

    ModuleTestDebugLogging.setupDebugLogging(data, requestBuilder);
  }

  @Test
  void testRaptorWithoutRelaxedCriteria() {
    var request = requestBuilder.build();
    var res = raptorService.route(request, data);

    var expected = join(p0, p2, p3);

    assertEquals(join(p0, p2, p3), pathsToString(res.paths()));
  }

  @Test
  void testRaptorRelaxedC1() {
    var request = requestBuilder
      .withMultiCriteria(c ->
        c
          .withTransitPriorityCalculator(new TestTransitPriorityCalculator())
          .withRelaxC1(IncValueRelaxFunction.ofCost(2.0, 60000))
      )
      .build();
    var res = raptorService.route(request, data);

    assertEquals(join(p0, p1, p2, p3), pathsToString(res.paths()));
  }

  private static String join(String... paths) {
    return String.join("\n", paths);
  }
}
