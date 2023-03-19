package org.opentripplanner.raptor.rangeraptor.multicriteria.ride.c2;

import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.McStopArrival;
import org.opentripplanner.raptor.rangeraptor.multicriteria.ride.PatternRideFactory;

public class TransitPriorityGroupRideFactory<T extends RaptorTripSchedule>
  implements PatternRideFactory<T, PatternRideC2<T>> {

  private final RaptorTransitPriorityGroupCalculator<T> transitPriorityGroupCalculator;

  public TransitPriorityGroupRideFactory(
    RaptorTransitPriorityGroupCalculator<T> transitPriorityGroupCalculator
  ) {
    this.transitPriorityGroupCalculator = transitPriorityGroupCalculator;
  }

  @Override
  public PatternRideC2<T> createPatternRide(
    McStopArrival<T> prevArrival,
    int boardStopIndex,
    int boardPos,
    int boardTime,
    int boardCost1,
    int relativeC1,
    T trip
  ) {
    int c2 = calculateC2(prevArrival.c2(), trip);
    return new PatternRideC2<>(
      prevArrival,
      boardStopIndex,
      boardPos,
      boardTime,
      boardCost1,
      relativeC1,
      c2,
      trip.tripSortIndex(),
      trip
    );
  }

  /**
   * Currently transit-priority-group is the only usage of c2
   */
  private int calculateC2(int c2, T trip) {
    return transitPriorityGroupCalculator.mergeTransitPriorityGroupIds(c2, trip);
  }
}
