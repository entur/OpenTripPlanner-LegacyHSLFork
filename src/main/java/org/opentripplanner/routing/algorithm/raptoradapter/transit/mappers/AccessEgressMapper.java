package org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.opentripplanner.ext.flex.FlexAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.FlexAccessEgressAdapter;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.RaptorCostConverter;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.transit.model.site.RegularStop;

public class AccessEgressMapper {

  public DefaultAccessEgress mapNearbyStop(NearbyStop nearbyStop, boolean isEgress) {
    if (!(nearbyStop.stop instanceof RegularStop)) {
      return null;
    }

    State lastState = isEgress ? nearbyStop.state.reverse() : nearbyStop.state;
    return new DefaultAccessEgress(
      nearbyStop.stop.getIndex(),
      RaptorCostConverter.toRaptorCost((lastState).getWeight()),
      (int) (lastState).getElapsedTimeSeconds(),
      lastState
    );
  }

  public List<DefaultAccessEgress> mapNearbyStops(
    Collection<NearbyStop> accessStops,
    boolean isEgress
  ) {
    return accessStops
      .stream()
      .map(stopAtDistance -> mapNearbyStop(stopAtDistance, isEgress))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public Collection<DefaultAccessEgress> mapFlexAccessEgresses(
    Collection<FlexAccessEgress> flexAccessEgresses,
    boolean isEgress
  ) {
    return flexAccessEgresses
      .stream()
      .map(flexAccessEgress ->
        new FlexAccessEgressAdapter(
          flexAccessEgress,
          isEgress ? flexAccessEgress.lastState.reverse() : flexAccessEgress.lastState
        )
      )
      .collect(Collectors.toList());
  }
}
