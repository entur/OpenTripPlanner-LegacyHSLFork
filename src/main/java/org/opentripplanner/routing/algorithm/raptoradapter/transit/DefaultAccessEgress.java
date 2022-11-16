package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.time.ZonedDateTime;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.mapping.GraphPathToItineraryMapper;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.transit.raptor.api.transit.RaptorAccessEgress;
import org.opentripplanner.util.lang.ToStringBuilder;

/**
 * Default implementation of the RaptorAccessEgress interface.
 */
public class DefaultAccessEgress implements RaptorAccessEgress {

  private final int stop;
  private final int durationInSeconds;
  private final int generalizedCost;

  /**
   * This should be the last state both in the case of access and egress.
   */
  private final State lastState;

  public DefaultAccessEgress(
    int stop,
    int generalizedCost,
    int durationInSeconds,
    State lastState
  ) {
    this.stop = stop;
    this.durationInSeconds = durationInSeconds;
    this.generalizedCost = generalizedCost;
    this.lastState = lastState;
  }

  @Override
  public boolean hasOpeningHours() {
    return false;
  }

  @Override
  public int durationInSeconds() {
    return durationInSeconds;
  }

  @Override
  public int stop() {
    return stop;
  }

  @Override
  public int generalizedCost() {
    return generalizedCost;
  }

  public State lastState() {
    return lastState;
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(DefaultAccessEgress.class)
      .addNum("stop", stop)
      .addNum("durationInSeconds", durationInSeconds)
      .addNum("generalizedCost", generalizedCost)
      .addObj("state", lastState)
      .toString();
  }

  public Itinerary getSubItinerary(
    ZonedDateTime fromTime,
    GraphPathToItineraryMapper graphPathToItineraryMapper
  ) {
    GraphPath graphPath = new GraphPath(lastState());

    Itinerary subItinerary = graphPathToItineraryMapper.generateItinerary(graphPath);

    if (subItinerary.getLegs().isEmpty()) {
      return null;
    }

    return subItinerary.withTimeShiftToStartAt(fromTime);
  }
}
