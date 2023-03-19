package org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority;

import java.util.stream.IntStream;
import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;

/**
 * TODO C2
 */
public class TransitPriorityGroup30n implements RaptorTransitPriorityGroupCalculator<TripSchedule> {

  private static final int COUNT_MASK = 0x03;
  private static final int COUNT_MASK_INV = ~COUNT_MASK;
  private static final int GROUP_ONE = COUNT_MASK + 1;

  private static final int MAX_SEQ_NO = 30;

  /* Implement RaptorTransitPriorityCalculator interface */

  @Override
  public int mergeTransitPriorityGroupIds(int currentGroupIds, TripSchedule trip) {
    return mergeInGroupId(currentGroupIds, trip.transitPriorityGroup());
  }

  @Override
  public DominanceFunction dominanceFunction() {
    return TransitPriorityGroup256n::dominateTransitPriorityGroup;
  }

  static boolean dominateTransitPriorityGroup(final int left, final int right) {
    if (left == GROUP_ZERO || right == GROUP_ZERO) {
      return false;
    }
    int nLeft = left & COUNT_MASK;
    int nRight = right & COUNT_MASK;
    return nLeft < nRight || (nLeft == nRight && left != right);
  }

  public static int mergeInGroupId(final int currentGroupIds, final int newGroupId) {
    if (currentGroupIds == newGroupId || newGroupId == GROUP_ZERO) {
      return currentGroupIds;
    }
    if (currentGroupIds == GROUP_ZERO) {
      return newGroupId;
    }
    int v = (currentGroupIds | newGroupId) & COUNT_MASK_INV;
    int n = Math.min(Integer.bitCount(v) - 1, COUNT_MASK);
    return v | n;
  }

  public static int groupId(final int priorityGroupIndex) {
    assertValidGroupSeqNo(priorityGroupIndex);
    return switch (priorityGroupIndex) {
      case 0 -> GROUP_ZERO;
      case 1 -> GROUP_ONE;
      default -> GROUP_ONE << (priorityGroupIndex - 1);
    };
  }

  /**
   * Find the combined group-id for the given steam of groupIds.
   * This is a utility method witch uses the {@link #mergeInGroupId(int, int)} to merge
   * all ids in the stream.
   */
  public static int groupIdOf(IntStream groupIds) {
    return groupIds.reduce(GROUP_ZERO, TransitPriorityGroup30n::mergeInGroupId);
  }

  public static void assertValidGroupSeqNo(int priorityGroupIndex) {
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
