package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.util.Optional;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;

/**
 * Encapsulate information about an access or egress path. This interface extends
 * {@link RaptorAccessEgress} with methods relevant only to street routing and
 * access/egress filtering.
 */
public interface RoutingAccessEgress extends RaptorAccessEgress {
  /**
   * Return a new copy of this with the requested penalty.
   * <p>
   * OVERRIDE THIS IF KEEPING THE TYPE IS IMPORTANT!
   */
  RoutingAccessEgress withPenalty(TimeAndCost penalty);

  /**
   * Return the last state both in the case of access and egress.
   */
  State getLastState();

  /**
   * Return true if all edges are traversed on foot.
   */
  boolean isWalkOnly();

  boolean hasPenalty();

  TimeAndCost penalty();

  /**
   * Booking info enforced by the router. By default nothing is returned.
   */
  default Optional<RoutingBookingInfo> routingBookingInfo() {
    return Optional.empty();
  }
}
