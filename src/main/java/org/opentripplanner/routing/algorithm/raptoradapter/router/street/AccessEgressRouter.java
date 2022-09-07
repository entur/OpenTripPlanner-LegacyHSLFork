package org.opentripplanner.routing.algorithm.raptoradapter.router.street;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.opentripplanner.ext.vehicletostopheuristics.BikeToStopSkipEdgeStrategy;
import org.opentripplanner.ext.vehicletostopheuristics.VehicleToStopSkipEdgeStrategy;
import org.opentripplanner.graph_builder.module.NearbyStopFinder;
import org.opentripplanner.routing.algorithm.astar.strategies.ComposingSkipEdgeStrategy;
import org.opentripplanner.routing.algorithm.astar.strategies.DurationSkipEdgeStrategy;
import org.opentripplanner.routing.algorithm.astar.strategies.SkipEdgeStrategy;
import org.opentripplanner.routing.api.request.AStarRequest;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.api.request.request.TransitRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.util.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This uses a street search to find paths to all the access/egress stop within range
 */
public class AccessEgressRouter {

  private static final Logger LOG = LoggerFactory.getLogger(AccessEgressRouter.class);

  private AccessEgressRouter() {}

  /**
   * @param fromTarget whether to route from or towards the point provided in the routing request
   *                   (access or egress)
   * @return Transfer objects by access/egress stop
   */
  public static Collection<NearbyStop> streetSearch(
    AStarRequest streetRequest,
    TransitRequest transitRequest,
    Set<Vertex> vertices,
    Graph graph,
    TransitService transitService,
    boolean fromTarget
  ) {
    final Duration durationLimit = streetRequest
      .preferences()
      .street()
      .maxAccessEgressDuration(streetRequest.mode());

    NearbyStopFinder nearbyStopFinder = new NearbyStopFinder(graph, transitService, durationLimit);
    List<NearbyStop> nearbyStopList = nearbyStopFinder.findNearbyStopsViaStreets(
      vertices,
      fromTarget,
      streetRequest,
      fromTarget
        ? null
        : getSkipEdgeStrategy(streetRequest.mode(), transitRequest, durationLimit, transitService)
    );

    LOG.debug("Found {} {} stops", nearbyStopList.size(), fromTarget ? "egress" : "access");

    return nearbyStopList;
  }

  private static SkipEdgeStrategy getSkipEdgeStrategy(
    StreetMode streetMode,
    TransitRequest transitRequest,
    Duration durationLimit,
    TransitService transitService
  ) {
    var durationSkipEdgeStrategy = new DurationSkipEdgeStrategy(durationLimit);

    // if we compute the accesses for Park+Ride, Bike+Ride and Bike+Transit we don't want to
    // search the full durationLimit as this returns way too many stops.
    // this is both slow and returns suboptimal results as it favours long drives with short
    // transit legs.
    // therefore, we use a heuristic based on the number of routes and their mode to determine
    // what are "good" stops for those accesses. if we have reached a threshold of "good" stops
    // we stop the access search.
    if (
      OTPFeature.VehicleToStopHeuristics.isOn() &&
      VehicleToStopSkipEdgeStrategy.applicableModes.contains(streetMode)
    ) {
      var strategy = new VehicleToStopSkipEdgeStrategy(
        transitService::getRoutesForStop,
        transitRequest.modes().stream().map(MainAndSubMode::mainMode).toList()
      );
      return new ComposingSkipEdgeStrategy(strategy, durationSkipEdgeStrategy);
    } else if (OTPFeature.VehicleToStopHeuristics.isOn() && streetMode == StreetMode.BIKE) {
      var strategy = new BikeToStopSkipEdgeStrategy(transitService::getTripsForStop);
      return new ComposingSkipEdgeStrategy(strategy, durationSkipEdgeStrategy);
    } else {
      return durationSkipEdgeStrategy;
    }
  }
}
