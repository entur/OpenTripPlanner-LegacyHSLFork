package org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority;

import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;

public class TestTransitPriorityCalculator
  implements RaptorTransitPriorityGroupCalculator<TestTripSchedule> {

  public static int groupId(int transitGroupIndex) {
    return TransitPriorityGroup32n.groupId(transitGroupIndex);
  }

  @Override
  public int mergeTransitPriorityGroupIds(int currentGroupIds, TestTripSchedule trip) {
    return TransitPriorityGroup32n.mergeInGroupId(currentGroupIds, trip.transitPriorityGroup());
  }

  @Override
  public DominanceFunction dominanceFunction() {
    return TransitPriorityGroup32n::dominate;
  }
}
