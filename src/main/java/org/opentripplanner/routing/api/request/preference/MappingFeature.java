package org.opentripplanner.routing.api.request.preference;

import org.opentripplanner.framework.doc.DocumentedEnum;
import org.opentripplanner.routing.algorithm.mapping.RaptorPathToItineraryMapper;

/**
 * Opt-in features for {@link RaptorPathToItineraryMapper}.
 */
public enum MappingFeature implements DocumentedEnum {
  /**
   * If a transfer starts and ends at the very same stop, should a zero-length transfer leg be
   * added to the itinerary?
   */
  TRANSFER_LEG_ON_SAME_STOP,
}
