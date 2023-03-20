package org.opentripplanner.raptor.api.request;

import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;

public interface RaptorTransitPriorityGroupCalculator<T extends RaptorTripSchedule> {
  /**
   * Group zero is used to initialize mc-raptor criteria-2 (c2).
   */
  int GROUP_ZERO = 0;

  static <S extends RaptorTripSchedule> RaptorTransitPriorityGroupCalculator<S> noop() {
    return new RaptorTransitPriorityGroupCalculator<S>() {
      @Override
      public int mergeTransitPriorityGroupIds(int currentGroupIds, S trip) {
        return GROUP_ZERO;
      }

      @Override
      public DominanceFunction dominanceFunction() {
        return DominanceFunction.noop();
      }
    };
  }

  /**
   * Merge in the trip transit priority group id with an existing set. Note! Both the set
   * and the group id type is {@code int}.
   *
   * @param currentGroupIds the set of groupIds for all legs in a path.
   * @param trip the trip transit group id to add to the given set.
   * @return the new computed set of groupIds
   */
  int mergeTransitPriorityGroupIds(int currentGroupIds, T trip);

  /**
   * This is the dominance function to use for comparing transit-priority-groupIds.
   * It is critical that the implementation is "static" so it can be inlined, since it
   * is run in the innermost loop of Raptor.
   */
  DominanceFunction dominanceFunction();
}
