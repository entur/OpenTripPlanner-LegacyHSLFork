package org.opentripplanner.routing.algorithm;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.ItineraryListFilterChain;
import org.opentripplanner.routing.algorithm.mapping.RouteRequestToFilterChainMapper;
import org.opentripplanner.routing.algorithm.mapping.RoutingResponseMapper;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.algorithm.raptoradapter.router.DepartOnboardTransitRouter;
import org.opentripplanner.routing.api.request.DepartOnboardRouteRequest;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingResponse;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.routing.framework.DebugTimingAggregator;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.raptor.api.request.SearchParams;
import org.opentripplanner.util.time.ServiceDateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does a complete transit search, including access and egress legs.
 * <p>
 * This class has a request scope, hence the "Worker" name.
 */
public class DepartOnboardRoutingWorker {

  private static final Logger LOG = LoggerFactory.getLogger(DepartOnboardRoutingWorker.class);

  /** An object that accumulates profiling and debugging info for inclusion in the response. */
  public final DebugTimingAggregator debugTimingAggregator;

  private final DepartOnboardRouteRequest request;
  private final OtpServerRequestContext serverContext;
  /**
   * The transit service time-zero normalized for the current search. All transit times are relative
   * to a "time-zero". This enables us to use an integer(small memory footprint). The times are
   * number for seconds past the {@code transitSearchTimeZero}. In the internal model all times are
   * stored relative to the {@link java.time.LocalDate}, but to be able
   * to compare trip times for different service days we normalize all times by calculating an
   * offset. Now all times for the selected trip patterns become relative to the {@code
   * transitSearchTimeZero}.
   */
  private final ZonedDateTime transitSearchTimeZero;
  private final AdditionalSearchDays additionalSearchDays;
  private SearchParams raptorSearchParamsUsed = null;

  public DepartOnboardRoutingWorker(
    OtpServerRequestContext serverContext,
    DepartOnboardRouteRequest request,
    ZoneId zoneId
  ) {
    this.request = request;
    this.serverContext = serverContext;
    this.debugTimingAggregator =
      new DebugTimingAggregator(
        serverContext.meterRegistry(),
        request.preferences().system().tags()
      );
    this.transitSearchTimeZero = ServiceDateUtils.asStartOfService(request.dateTime(), zoneId);
    this.additionalSearchDays =
      new AdditionalSearchDays(
        false,
        ZonedDateTime.ofInstant(request.dateTime(), zoneId),
        Duration.ZERO,
        Duration.ZERO,
        request.preferences().system().maxJourneyDuration()
      );
  }

  public RoutingResponse route() {
    this.debugTimingAggregator.finishedPrecalculating();

    List<Itinerary> itineraries;
    Set<RoutingError> routingErrors = new HashSet<>();

    debugTimingAggregator.startedTransitRouting();
    try {
      var transitRouter = new DepartOnboardTransitRouter(
        request,
        serverContext,
        transitSearchTimeZero,
        additionalSearchDays,
        debugTimingAggregator
      );
      var transitResults = transitRouter.route();
      raptorSearchParamsUsed = transitResults.getSearchParams();
      itineraries = transitResults.getItineraries();
    } catch (RoutingValidationException e) {
      routingErrors.addAll(e.getRoutingErrors());
      itineraries = List.of();
    } finally {
      debugTimingAggregator.finishedTransitRouter();
    }

    debugTimingAggregator.finishedRouting();

    // Filter itineraries
    ItineraryListFilterChain filterChain = RouteRequestToFilterChainMapper.createFilterChain(
      request.itinerariesSortOrder(),
      request.preferences().itineraryFilter(),
      request.numItineraries(),
      null,
      false,
      false,
      null,
      request.wheelchair(),
      request.preferences().wheelchair().maxSlope(),
      serverContext.graph().getFareService(),
      serverContext.transitService().getTransitAlertService(),
      serverContext.transitService()::getMultiModalStationForStation
    );

    List<Itinerary> filteredItineraries = filterChain.filter(itineraries);

    routingErrors.addAll(filterChain.getRoutingErrors());

    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "Return TripPlan with {} filtered itineraries out of {} total.",
        filteredItineraries.stream().filter(it -> !it.isFlaggedForDeletion()).count(),
        itineraries.size()
      );
    }

    this.debugTimingAggregator.finishedFiltering();

    return RoutingResponseMapper.map(
      request,
      transitSearchTimeZero,
      raptorSearchParamsUsed,
      Duration.ZERO,
      null,
      filteredItineraries,
      routingErrors,
      debugTimingAggregator
    );
  }
}
