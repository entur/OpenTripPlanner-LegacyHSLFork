package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;

public interface ArrivalParetoSetComparatorFactory<T extends McStopArrival<?>> {
  /**
   * This comparator is used to compare regular stop arrivals. It uses {@code arrivalTime},
   * {@code paretoRound} and {@code generalizedCost} to compare arrivals. It does NOT include
   * {@code arrivedOnBoard}. Normally arriving on-board should give the arrival an advantage
   * - you can continue on foot, walking to the next stop. But, we only do this if it happens
   * in the same Raptor iteration and round - if it does it is taken care of by the order
   * witch the algorithm work - not by this comparator.
   */
  ParetoComparator<T> compareArrivalTimeRoundAndCost();

  /**
   * This includes {@code arrivedOnBoard} in the comparison compared with
   * {@link #compareArrivalTimeRoundAndCost()}.
   */
  ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival();

  static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<T> factory(
    final RelaxFunction relaxArrivalTime,
    final RelaxFunction relaxC1
  ) {
    if (relaxArrivalTime == null && relaxC1 == null) {
      return new ArrivalParetoSetComparatorFactory<T>() {
        @Override
        public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
          return McStopArrival::compareBase;
        }

        @Override
        public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
          return (l, r) ->
            McStopArrival.compareBase(l, r) || McStopArrival.compareArrivedOnBoard(l, r);
        }
      };
    } else {
      final RelaxFunction rat = relaxArrivalTime == null ? RelaxFunction.NORMAL : relaxArrivalTime;
      final RelaxFunction rc1 = relaxC1 == null ? RelaxFunction.NORMAL : relaxC1;

      return new ArrivalParetoSetComparatorFactory<>() {
        @Override
        public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
          return (l, r) -> McStopArrival.relaxedCompareBase(rat, rc1, l, r);
        }

        @Override
        public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
          return (l, r) ->
            McStopArrival.relaxedCompareBase(rat, rc1, l, r) ||
            McStopArrival.compareArrivedOnBoard(l, r);
        }
      };
    }
  }
}
