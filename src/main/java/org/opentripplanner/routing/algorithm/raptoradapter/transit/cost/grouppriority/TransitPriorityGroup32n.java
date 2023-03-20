package org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority;

import static org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator.GROUP_ZERO;

import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;

/**
 * This is a "BitSet" implementation for groupId. It can store until 32 groups,
 * a set with few elements does NOT dominate a set with more elements.
 */
public class TransitPriorityGroup32n {

  private static final int MAX_SEQ_NO = 32;

  public static RaptorTransitPriorityGroupCalculator<TripSchedule> priorityCalculator() {
    return new RaptorTransitPriorityGroupCalculator<>() {
      @Override
      public int mergeTransitPriorityGroupIds(int currentGroupIds, TripSchedule trip) {
        return mergeInGroupId(currentGroupIds, trip.transitPriorityGroup());
      }

      @Override
      public DominanceFunction dominanceFunction() {
        return TransitPriorityGroup32n::dominate;
      }
    };
  }

  public static boolean dominate(int left, int right) {
    return left != right;
  }

  public static int groupId(final int priorityGroupIndex) {
    assertValidGroupSeqNo(priorityGroupIndex);
    return priorityGroupIndex == 0 ? GROUP_ZERO : 0x01 << (priorityGroupIndex - 1);
  }

  public static int mergeInGroupId(final int currentGroupIds, final int newGroupId) {
    return currentGroupIds | newGroupId;
  }

  private static void assertValidGroupSeqNo(int priorityGroupIndex) {
    if (priorityGroupIndex < 0) {
      throw new IllegalArgumentException(
        "Transit priority group can not be a negative number: " + priorityGroupIndex
      );
    }
    if (priorityGroupIndex > MAX_SEQ_NO) {
      throw new IllegalArgumentException(
        "Transit priority group exceeds max number of groups: " +
        priorityGroupIndex +
        " (MAX=" +
        MAX_SEQ_NO +
        ")"
      );
    }
  }
}
