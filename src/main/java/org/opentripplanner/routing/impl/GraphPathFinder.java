package org.opentripplanner.routing.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.routing.algorithm.astar.AStarBuilder;
import org.opentripplanner.routing.algorithm.astar.TraverseVisitor;
import org.opentripplanner.routing.api.request.AStarRequest;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.GraphPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains the logic for repeatedly building shortest path trees and accumulating paths
 * through the graph until the requested number of them have been found. It is used in
 * point-to-point (i.e. not one-to-many / analyst) routing.
 * <p>
 * Its exact behavior will depend on whether the routing request allows transit.
 * <p>
 * When using transit it will incorporate techniques from what we called "long distance" mode, which
 * is designed to provide reasonable response times when routing over large graphs (e.g. the entire
 * Netherlands or New York State). In this case it only uses the street network at the first and
 * last legs of the trip, and all other transfers between transit vehicles will occur via
 * PathTransfer edges which are pre-computed by the graph builder.
 * <p>
 * More information is available on the OTP wiki at: https://github.com/openplans/OpenTripPlanner/wiki/LargeGraphs
 * <p>
 * One instance of this class should be constructed per search (i.e. per RoutingRequest: it is
 * request-scoped). Its behavior is undefined if it is reused for more than one search.
 * <p>
 * It is very close to being an abstract library class with only static functions. However it turns
 * out to be convenient and harmless to have the OTPServer object etc. in fields, to avoid passing
 * context around in function parameters.
 */
public class GraphPathFinder {

  private static final Logger LOG = LoggerFactory.getLogger(GraphPathFinder.class);

  @Nullable
  private final TraverseVisitor traverseVisitor;

  private final Duration streetRoutingTimeout;

  public GraphPathFinder(@Nullable TraverseVisitor traverseVisitor, Duration streetRoutingTimeout) {
    this.traverseVisitor = traverseVisitor;
    this.streetRoutingTimeout = streetRoutingTimeout;
  }

  /**
   * This no longer does "trip banning" to find multiple itineraries. It just searches once trying
   * to find a non-transit path.
   */
  public List<GraphPath> getPaths(RoutingContext routingContext) {
    if (routingContext == null) {
      LOG.error("PathService was passed a null routing context.");
      return null;
    }

    AStarRequest options = routingContext.opt;
    RoutingPreferences preferences = routingContext.opt.preferences();

    AStarBuilder aStar = AStarBuilder
      .oneToOneMaxDuration(preferences.street().maxDirectDuration(options.mode()))
      // FORCING the dominance function to weight only
      .setDominanceFunction(new DominanceFunction.MinimumWeight(options.from(), options.to()))
      .setContext(routingContext)
      .setTimeout(streetRoutingTimeout);

    // If the search has a traverseVisitor(GraphVisualizer) attached to it, set it as a callback
    // for the AStar search
    if (traverseVisitor != null) {
      aStar.setTraverseVisitor(traverseVisitor);
    }

    LOG.debug("rreq={}", options);

    long searchBeginTime = System.currentTimeMillis();
    LOG.debug("BEGIN SEARCH");

    List<GraphPath> paths = aStar.getPathsToTarget();

    LOG.debug("we have {} paths", paths.size());
    LOG.debug("END SEARCH ({} msec)", System.currentTimeMillis() - searchBeginTime);
    paths.sort(preferences.street().pathComparator(options.arriveBy()));
    return paths;
  }

  /**
   * Try to find N paths through the Graph
   */
  public List<GraphPath> graphPathFinderEntryPoint(RoutingContext routingContext) {
    AStarRequest request = routingContext.opt;
    Instant reqTime = request.dateTime().truncatedTo(ChronoUnit.SECONDS);

    List<GraphPath> paths = getPaths(routingContext);

    // Detect and report that most obnoxious of bugs: path reversal asymmetry.
    // Removing paths might result in an empty list, so do this check before the empty list check.
    if (paths != null) {
      Iterator<GraphPath> gpi = paths.iterator();
      while (gpi.hasNext()) {
        GraphPath graphPath = gpi.next();
        // TODO check, is it possible that arriveBy and time are modifed in-place by the search?
        if (request.arriveBy()) {
          if (graphPath.states.getLast().getTime().isAfter(reqTime)) {
            LOG.error("A graph path arrives after the requested time. This implies a bug.");
            gpi.remove();
          }
        } else {
          if (graphPath.states.getFirst().getTime().isBefore(reqTime)) {
            LOG.error("A graph path leaves before the requested time. This implies a bug.");
            gpi.remove();
          }
        }
      }
    }

    if (paths == null || paths.size() == 0) {
      LOG.debug("Path not found: " + request.from() + " : " + request.to());
      throw new PathNotFoundException();
    }

    return paths;
  }
}
