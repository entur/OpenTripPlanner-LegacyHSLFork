package org.opentripplanner.routing.algorithm.filterchain.filters.transit;

import static org.opentripplanner.routing.algorithm.filterchain.framework.sort.SortOrderComparator.numberOfTransfersComparator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.model.SystemNotice;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.framework.spi.ItineraryListFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter makes sure that the itinerary with the fewest transfers is not removed.
 * It iterates over the itineraries and removes the SystemNotice if it contains the provided set
 * of {@code filterKeys}. The itinerary must match all {@code filterKeys}, and if so the given
 * keys are removed. Itineraries with other system notices are ignored.
 */
public class KeepItinerariesWithFewestTransfers implements ItineraryListFilter {
  private static final Logger LOG = LoggerFactory.getLogger(KeepItinerariesWithFewestTransfers.class);

  private final Set<String> filterKeys;

  public KeepItinerariesWithFewestTransfers(List<String> filterKeys) {
    this.filterKeys = new HashSet<>(filterKeys);
  }

  @Override
  public List<Itinerary> filter(List<Itinerary> itineraries) {
    itineraries
      .stream()
      .min(numberOfTransfersComparator())
      .filter(it ->
        filterKeys.containsAll(it.getSystemNotices().stream().map(SystemNotice::tag).toList())
      )
      .ifPresent(it -> {
        LOG.debug("Include TxMin Itinerary. Remove {} from {}.", filterKeys, it);
        it.removeDeletionFlags(filterKeys);
      });

    return itineraries;
  }
}
