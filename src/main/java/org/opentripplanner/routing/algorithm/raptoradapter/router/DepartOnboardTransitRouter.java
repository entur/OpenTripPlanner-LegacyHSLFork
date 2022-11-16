package org.opentripplanner.routing.algorithm.raptoradapter.router;

import static org.opentripplanner.transit.raptor.api.transit.RaptorTransferConstraint.REGULAR_TRANSFER;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DepartOnboardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.AccessEgressMapper;
import org.opentripplanner.routing.api.request.DepartOnboardRouteRequest;
import org.opentripplanner.routing.core.TemporaryVerticesContainer;
import org.opentripplanner.routing.framework.DebugTimingAggregator;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.raptor.api.transit.RaptorTripPattern;

public class DepartOnboardTransitRouter extends TransitRouter {

  private final DepartOnboardRouteRequest request;
  private final ZoneId timeZone;

  public DepartOnboardTransitRouter(
    DepartOnboardRouteRequest request,
    OtpServerRequestContext serverContext,
    ZonedDateTime transitSearchTimeZero,
    AdditionalSearchDays additionalSearchDays,
    DebugTimingAggregator debugTimingAggregator
  ) {
    super(
      request,
      serverContext,
      transitSearchTimeZero,
      additionalSearchDays,
      debugTimingAggregator
    );
    this.request = request;
    this.timeZone = transitSearchTimeZero.getZone();
  }

  @Override
  Collection<DefaultAccessEgress> getAccessEgresses(
    AccessEgressMapper accessEgressMapper,
    TemporaryVerticesContainer temporaryVertices,
    boolean isEgress
  ) {
    if (isEgress) {
      return super.getAccessEgresses(accessEgressMapper, temporaryVertices, isEgress);
    }

    var pattern = request.tripPattern();
    var tripTimes = request.tripTimes();

    int startIndex = request.stopPositionInPattern();
    int startStopIndex = pattern.getStop(startIndex).getIndex();
    int departureTime = tripTimes.getDepartureTime(startIndex);

    TripSchedule tripSchedule = new MyTripSchedule(tripTimes, pattern);

    List<DefaultAccessEgress> result = new ArrayList<>();

    var costCalculator = requestTransitDataProvider.multiCriteriaCostCalculator();

    for (int endIndex = startIndex + 1; endIndex < pattern.numberOfStops(); endIndex++) {
      if (!pattern.canAlight(endIndex)) {
        continue;
      }

      int arrivalTime = tripTimes.getArrivalTime(endIndex);
      int durationInSeconds = arrivalTime - departureTime;
      int boardingCost = costCalculator.boardingCost(
        true,
        departureTime,
        startStopIndex,
        departureTime,
        tripSchedule,
        REGULAR_TRANSFER
      );

      int alightCost = costCalculator.transitArrivalCost(
        boardingCost,
        0,
        durationInSeconds,
        tripSchedule,
        endIndex
      );

      result.add(
        new DepartOnboardAccess(
          pattern.getStop(endIndex).getIndex(),
          null,
          departureTime,
          alightCost,
          durationInSeconds,
          tripTimes,
          pattern,
          startIndex,
          endIndex,
          request.serviceDate(),
          timeZone,
          departureTime,
          arrivalTime
        )
      );
    }

    return result;
  }

  private class MyTripSchedule implements TripSchedule {

    private final TripTimes tripTimes;
    private final TripPattern pattern;

    public MyTripSchedule(TripTimes tripTimes, TripPattern pattern) {
      this.tripTimes = tripTimes;
      this.pattern = pattern;
    }

    @Override
    public LocalDate getServiceDate() {
      return request.serviceDate();
    }

    @Override
    public TripTimes getOriginalTripTimes() {
      return tripTimes;
    }

    @Override
    public TripPattern getOriginalTripPattern() {
      return pattern;
    }

    @Override
    public int transitReluctanceFactorIndex() {
      return pattern.getRoutingTripPattern().transitReluctanceFactorIndex();
    }

    @Override
    public Accessibility wheelchairBoarding() {
      return tripTimes.wheelchairAccessibility;
    }

    @Override
    public int tripSortIndex() {
      return 0;
    }

    @Override
    public int arrival(int stopPosInPattern) {
      return tripTimes.getArrivalTime(stopPosInPattern);
    }

    @Override
    public int departure(int stopPosInPattern) {
      return tripTimes.getDepartureTime(stopPosInPattern);
    }

    @Override
    public RaptorTripPattern pattern() {
      return pattern.getRoutingTripPattern();
    }
  }
}
