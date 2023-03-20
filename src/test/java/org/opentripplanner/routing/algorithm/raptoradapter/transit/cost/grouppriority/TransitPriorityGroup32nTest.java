package org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;

class TransitPriorityGroup32nTest {

  private static final RaptorTransitPriorityGroupCalculator<TripSchedule> calculator = TransitPriorityGroup32n.priorityCalculator();
  private static final int GROUP_INDEX_0 = 0;
  private static final int GROUP_INDEX_1 = 1;
  private static final int GROUP_INDEX_2 = 2;
  private static final int GROUP_INDEX_31 = 31;

  private static final int GROUP_0 = TransitPriorityGroup32n.groupId(GROUP_INDEX_0);
  private static final int GROUP_1 = TransitPriorityGroup32n.groupId(GROUP_INDEX_1);
  private static final int GROUP_2 = TransitPriorityGroup32n.groupId(GROUP_INDEX_2);
  private static final int GROUP_31 = TransitPriorityGroup32n.groupId(GROUP_INDEX_31);

  @Test
  void mergeTransitPriorityGroupIds() {
    var scheduleBuilder = TestTripSchedule.schedule("01:00 02:00");

    var trip = scheduleBuilder.transitPriorityGroupIndex(GROUP_INDEX_0).build();
    int groups = calculator.mergeTransitPriorityGroupIds(GROUP_0, trip);
    assertEquals(GROUP_0, groups);

    trip = scheduleBuilder.transitPriorityGroupIndex(GROUP_INDEX_1).build();
    groups = calculator.mergeTransitPriorityGroupIds(groups, trip);
    assertEquals(GROUP_0 | GROUP_1, groups);

    trip = scheduleBuilder.transitPriorityGroupIndex(GROUP_INDEX_2).build();
    groups = calculator.mergeTransitPriorityGroupIds(groups, trip);
    assertEquals(GROUP_0 | GROUP_1 | GROUP_2, groups);

    trip = scheduleBuilder.transitPriorityGroupIndex(GROUP_INDEX_31).build();
    groups = calculator.mergeTransitPriorityGroupIds(groups, trip);
    assertEquals(GROUP_0 | GROUP_1 | GROUP_2 | GROUP_31, groups);
  }

  @Test
  void dominanceFunction() {
    assertFalse(calculator.dominanceFunction().leftDominateRight(GROUP_0, GROUP_0));
    assertFalse(calculator.dominanceFunction().leftDominateRight(GROUP_31, GROUP_31));
    assertFalse(
      calculator.dominanceFunction().leftDominateRight(GROUP_1 | GROUP_2, GROUP_1 | GROUP_2)
    );
  }
}
