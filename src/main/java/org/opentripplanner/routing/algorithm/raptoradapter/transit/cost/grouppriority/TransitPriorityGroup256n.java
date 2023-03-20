package org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority;

import java.util.stream.IntStream;
import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.request.RaptorTransitPriorityGroupCalculator;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;

/**
 * TODO C2
 */
public class TransitPriorityGroup256n
  implements RaptorTransitPriorityGroupCalculator<TripSchedule> {

  private static final int GROUP_ONE_MASK = 0xFF;
  private static final int GROUP_TWO_MASK = 0xFF00;
  private static final int GROUP_THREE_MASK = 0xFF0000;
  private static final int GROUP_FOUR_MASK = 0xFF000000;
  private static final int GROUP_ONE_TWO_MASK = 0xFFFF;
  private static final int GROUP_THREE_FOUR_MASK = 0xFFFF0000;

  private static final int GROUP_MANY = 0xFFFFFFFF;

  private static final int MAX_SEQ_NO = GROUP_ONE_MASK + 1;
  private static final int SIZE = 4;
  private static final int N_BITS = 8;
  private static final int N2_BITS = 2 * N_BITS;

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
    int nLeft = count(left);
    int nRight = count(right);
    return nLeft < nRight || (nLeft == nRight && left != right);
  }

  static int mergeInGroupId(final int currentGroupIds, final int newGroupId) {
    if (currentGroupIds == newGroupId || newGroupId == GROUP_ZERO) {
      return currentGroupIds;
    }
    if (currentGroupIds == GROUP_ZERO) {
      return newGroupId;
    }
    int v = newGroupId;
    int a = currentGroupIds & GROUP_ONE_MASK;

    if (v == a) {
      return currentGroupIds;
    }
    if (v < a) {
      return count(currentGroupIds) == SIZE ? GROUP_MANY : ((currentGroupIds << N_BITS) | v);
    }

    v = v << N_BITS;
    a = currentGroupIds & GROUP_TWO_MASK;

    if (v == a) {
      return currentGroupIds;
    }
    if (v < a) {
      return count(currentGroupIds) == SIZE
        ? GROUP_MANY
        : (
          ((currentGroupIds << N_BITS) & GROUP_THREE_FOUR_MASK) |
          v |
          (currentGroupIds & GROUP_ONE_MASK)
        );
    }

    v = v << N_BITS;
    a = currentGroupIds & GROUP_THREE_MASK;

    if (v == a) {
      return currentGroupIds;
    }
    if (v < a) {
      return count(currentGroupIds) == SIZE
        ? GROUP_MANY
        : (
          ((currentGroupIds << N_BITS) & GROUP_FOUR_MASK) |
          v |
          (currentGroupIds & GROUP_ONE_TWO_MASK)
        );
    }

    v = v << N_BITS;
    return (v == (currentGroupIds & GROUP_FOUR_MASK)) ? currentGroupIds : GROUP_MANY;
  }

  private static int count(int value) {
    int v = value >>> N2_BITS;
    if (v == 0) {
      int u = value >>> N_BITS;
      return u == 0 ? 1 : 2;
    }
    int u = v >>> N_BITS;
    return u == 0 ? 3 : 4;
  }

  private static int groupId(final int priorityGroupIndex) {
    assertValidGroupSeqNo(priorityGroupIndex);
    return priorityGroupIndex;
  }

  /**
   * Find the combined group-id for the given steam of groupIds. This is a utility method witch
   * uses the {@link #mergeInGroupId(int, int)} to merge all ids in the stream.
   */
  public static int groupIdOf(IntStream groupIds) {
    return groupIds.reduce(GROUP_ZERO, TransitPriorityGroup256n::mergeInGroupId);
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
