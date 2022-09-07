package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opentripplanner.ext.flex.FlexRouter;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.AStarRequest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.TemporaryVerticesContainer;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

public class DirectFlexRouter {

  public static List<Itinerary> route(
    OtpServerRequestContext serverContext,
    RouteRequest routeRequest,
    AdditionalSearchDays additionalSearchDays
  ) {
    if (!StreetMode.FLEXIBLE.equals(routeRequest.journey().direct().mode())) {
      return Collections.emptyList();
    }
    StreetRequest streetRequest = new StreetRequest(StreetMode.WALK);
    AStarRequest request = routeRequest.getStreetSearchRequest(streetRequest);

    try (var temporaryVertices = new TemporaryVerticesContainer(serverContext.graph(), request)) {
      // Prepare access/egress transfers
      Collection<NearbyStop> accessStops = AccessEgressRouter.streetSearch(
        request,
        routeRequest.journey().transit(),
        temporaryVertices.getFromVertices(),
        serverContext.graph(),
        serverContext.transitService(),
        false
      );
      Collection<NearbyStop> egressStops = AccessEgressRouter.streetSearch(
        request,
        routeRequest.journey().transit(),
        temporaryVertices.getToVertices(),
        serverContext.graph(),
        serverContext.transitService(),
        true
      );

      FlexRouter flexRouter = new FlexRouter(
        serverContext.graph(),
        serverContext.transitService(),
        serverContext.routerConfig().flexParameters(request.preferences()),
        routeRequest.dateTime(),
        routeRequest.arriveBy(),
        additionalSearchDays.additionalSearchDaysInPast(),
        additionalSearchDays.additionalSearchDaysInFuture(),
        accessStops,
        egressStops
      );

      return new ArrayList<>(flexRouter.createFlexOnlyItineraries());
    }
  }
}
