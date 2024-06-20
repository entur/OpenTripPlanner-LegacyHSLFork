package org.opentripplanner.routing.algorithm.filterchain.filters.system;

import java.util.Comparator;
import java.util.function.ToIntFunction;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority.TransitGroupPriority32n;

/**
 * Comparator used to compare a SINGE criteria for dominance. The difference between this and the
 * {@link org.opentripplanner.raptor.util.paretoset.ParetoComparator} is that:
 * <ol>
 *   <li>This applies to one criteria, not multiple.</li>
 *   <li>This interface apply to itineraries; It is not generic.</li>
 * </ol>
 * A set of instances of this interface can be used to create a pareto-set. See
 * {@link org.opentripplanner.raptor.util.paretoset.ParetoSet} and
 * {@link org.opentripplanner.raptor.util.paretoset.ParetoComparator}.
 * <p/>
 * This interface extends {@link Comparator} so elements can be sorted as well. Not all criteria
 * can be sorted, if so the {@link #strictOrder()} should return false (this is the default).
 */
@FunctionalInterface
public interface SingeCriteriaComparator {
  /**
   * The left criteria dominates the right criteria. Note! The right criteria my dominate
   * the left criteria if there is no {@link #strictOrder()}. If left and right are equals, then
   * there is no dominance.
   */
  boolean leftDominanceExist(Itinerary left, Itinerary right);

  /**
   * Return true if the criteria can be deterministically sorted.
   */
  default boolean strictOrder() {
    return false;
  }

  static SingeCriteriaComparator compareNumTransfers() {
    return compareLessThan(Itinerary::getNumberOfTransfers);
  }

  static SingeCriteriaComparator compareGeneralizedCost() {
    return compareLessThan(Itinerary::getGeneralizedCost);
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  static SingeCriteriaComparator compareTransitGroupsPriority() {
    return (left, right) ->
      TransitGroupPriority32n.dominate(
        left.getGeneralizedCost2().get(),
        right.getGeneralizedCost2().get()
      );
  }

  static SingeCriteriaComparator compareLessThan(final ToIntFunction<Itinerary> op) {
    return new SingeCriteriaComparator() {
      @Override
      public boolean leftDominanceExist(Itinerary left, Itinerary right) {
        return op.applyAsInt(left) < op.applyAsInt(right);
      }

      @Override
      public boolean strictOrder() {
        return true;
      }
    };
  }
}