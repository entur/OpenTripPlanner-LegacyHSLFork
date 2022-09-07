package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.opentripplanner.ext.flex.FlexAccessEgress;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.ext.flex.FlexRouter;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.AStarRequest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.TemporaryVerticesContainer;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.transit.service.TransitService;

public class FlexAccessEgressRouter {

  private FlexAccessEgressRouter() {}

  public static Collection<FlexAccessEgress> routeAccessEgress(
    RouteRequest rr,
    Graph graph,
    TemporaryVerticesContainer verticesContainer,
    TransitService transitService,
    AdditionalSearchDays searchDays,
    FlexParameters params,
    boolean isEgress
  ) {
    StreetRequest streetRequest = new StreetRequest(StreetMode.WALK);
    AStarRequest request = rr.getStreetSearchRequest(streetRequest);

    Collection<NearbyStop> accessStops = !isEgress
      ? AccessEgressRouter.streetSearch(
        request,
        rr.journey().transit(),
        verticesContainer.getFromVertices(),
        graph,
        transitService,
        false
      )
      : List.of();

    Collection<NearbyStop> egressStops = isEgress
      ? AccessEgressRouter.streetSearch(
        request,
        rr.journey().transit(),
        verticesContainer.getToVertices(),
        graph,
        transitService,
        true
      )
      : List.of();

    FlexRouter flexRouter = new FlexRouter(
      graph,
      transitService,
      params,
      rr.dateTime(),
      rr.arriveBy(),
      searchDays.additionalSearchDaysInPast(),
      searchDays.additionalSearchDaysInFuture(),
      accessStops,
      egressStops
    );

    return isEgress ? flexRouter.createFlexEgresses() : flexRouter.createFlexAccesses();
  }
}
