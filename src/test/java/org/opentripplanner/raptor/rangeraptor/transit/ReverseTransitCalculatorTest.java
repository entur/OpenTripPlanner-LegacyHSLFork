package org.opentripplanner.raptor.rangeraptor.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.framework.time.TimeUtils.hm2time;
import static org.opentripplanner.raptor._data.RaptorTestConstants.D1m;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_A;
import static org.opentripplanner.raptor._data.RaptorTestConstants.STOP_B;

import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor._data.transit.TestTransfer;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.spi.IntIterator;

public class ReverseTransitCalculatorTest {

  private int latestArrivalTime = hm2time(8, 0);
  private int searchWindowSizeInSeconds = 2 * 60 * 60;
  private int earliestAcceptableDepartureTime = hm2time(16, 0);
  private int iterationStep = 60;

  @Test
  public void exceedsTimeLimit() {
    earliestAcceptableDepartureTime = 1200;
    var subject = create();

    assertFalse(subject.exceedsTimeLimit(200_000));
    assertFalse(subject.exceedsTimeLimit(1200));
    assertTrue(subject.exceedsTimeLimit(1199));

    earliestAcceptableDepartureTime = hm2time(16, 0);

    assertEquals(
      "The departure time exceeds the time limit, depart to early: 16:00:00.",
      create().exceedsTimeLimitReason()
    );

    earliestAcceptableDepartureTime = -1;
    subject = create();
    assertFalse(subject.exceedsTimeLimit(0));
    assertFalse(subject.exceedsTimeLimit(2_000_000_000));
  }

  @Test
  public void oneIterationOnly() {
    var subject = create();

    assertFalse(subject.oneIterationOnly());

    searchWindowSizeInSeconds = 0;
    subject = create();

    assertTrue(subject.oneIterationOnly());
  }

  @Test
  public void latestArrivalTime() {
    // Ignore board slack for reverse search, boardSlack is added to alight times.
    int slackInSeconds = 75;
    TestTripSchedule s = TestTripSchedule.schedule().departures(500).build();
    assertEquals(425, create().stopArrivalTime(s, 0, slackInSeconds));
  }

  @Test
  public void stopArrivalDepartureTime() {
    var t = TestTripSchedule.schedule().arrivals(10).departures(20).build();
    assertEquals(10, create().stopDepartureTime(t, 0));
    assertEquals(20, create().stopArrivalTime(t, 0));
  }

  @Test
  public void rangeRaptorMinutes() {
    latestArrivalTime = 500;
    searchWindowSizeInSeconds = 200;
    iterationStep = 100;

    assertIntIterator(create().rangeRaptorMinutes(), 400, 500);
  }

  @Test
  public void patternStopIterator() {
    assertIntIterator(create().patternStopIterator(2), 1, 0);
  }

  @Test
  public void getTransfers() {
    var subject = create();
    var transitData = new TestTransitData()
      .withTransfer(STOP_A, TestTransfer.transfer(STOP_B, D1m));

    // Expect transfer from stop A to stop B (reversed)
    var transfersFromStopB = subject.getTransfers(transitData, STOP_B);
    assertTrue(transfersFromStopB.hasNext());
    assertEquals(STOP_A, transfersFromStopB.next().stop());

    // No transfer form stop A expected
    assertFalse(subject.getTransfers(transitData, STOP_A).hasNext());
  }

  private TransitCalculator<TestTripSchedule> create() {
    return new ReverseTransitCalculator<>(
      latestArrivalTime,
      searchWindowSizeInSeconds,
      earliestAcceptableDepartureTime,
      iterationStep
    );
  }

  private void assertIntIterator(IntIterator it, int... values) {
    for (int v : values) {
      assertTrue(it.hasNext());
      assertEquals(v, it.next());
    }
    assertFalse(it.hasNext());
  }
}
