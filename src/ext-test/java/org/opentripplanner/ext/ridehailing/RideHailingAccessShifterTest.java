package org.opentripplanner.ext.ridehailing;

import static graphql.Assert.assertTrue;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.opentripplanner._support.time.ZoneIds;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.street.search.state.TestStateBuilder;
import org.opentripplanner.test.support.VariableSource;

class RideHailingAccessShifterTest {

  private static final Instant TIME = OffsetDateTime.parse("2023-03-23T17:00:00+01:00").toInstant();
  private static final GenericLocation FROM = new GenericLocation(0d, 0d);
  private static final GenericLocation TO = new GenericLocation(1d, 1d);

  RideHailingService service = new TestRideHailingService(
    TestRideHailingService.DEFAULT_ARRIVAL_TIMES,
    List.of()
  );

  static Stream<Arguments> testCases = Stream.of(
    // leave now, so shift by 10 minutes
    Arguments.of(TIME, TestRideHailingService.DEFAULT_ARRIVAL_DURATION),
    // only shift by 9 minutes because we are wanting to leave in 1 minute
    Arguments.of(TIME.plus(ofMinutes(1)), ofMinutes(9)),
    // only shift by 7 minutes because we are wanting to leave in 3 minutes
    Arguments.of(TIME.plus(ofMinutes(3)), ofMinutes(7)),
    // no shifting because it's in the future
    Arguments.of(TIME.plus(ofMinutes(15)), ZERO),
    Arguments.of(TIME.plus(ofMinutes(30)), ZERO),
    Arguments.of(TIME.plus(ofMinutes(40)), ZERO)
  );

  @ParameterizedTest
  @VariableSource("testCases")
  void arrivalDelay(Instant searchTime, Duration expectedArrival) {
    var req = new RouteRequest();
    req.setTo(FROM);
    req.setFrom(TO);
    req.setDateTime(searchTime);
    req.journey().setModes(RequestModes.of().withAccessMode(StreetMode.CAR_HAILING).build());

    var result = RideHailingAccessShifter.arrivalDelay(req, List.of(service), TIME);

    assertTrue(result.isSuccess());
    var actualArrival = result.successValue();

    // start time should be shifted by 10 minutes
    assertEquals(expectedArrival, actualArrival);
  }

  @Test
  void shiftAccesses() {
    var drivingState = TestStateBuilder.ofDriving().streetEdge().streetEdge().build();
    var access = new DefaultAccessEgress(0, drivingState);

    var req = new RouteRequest();
    req.setDateTime(TIME);
    req.setFrom(FROM);
    req.setFrom(TO);
    req.journey().setModes(RequestModes.of().withAccessMode(StreetMode.CAR_HAILING).build());

    var shifted = RideHailingAccessShifter.shiftAccesses(
      true,
      List.of(access),
      List.of(service),
      req,
      TIME
    );

    var shiftedAccess = shifted.get(0);

    var shiftedStart = shiftedAccess.earliestDepartureTime(
      TIME.atZone(ZoneIds.BERLIN).toLocalTime().toSecondOfDay()
    );

    var earliestStartTime = LocalTime.ofSecondOfDay(shiftedStart);

    assertEquals(LocalTime.of(17, 10), earliestStartTime);
  }
}
