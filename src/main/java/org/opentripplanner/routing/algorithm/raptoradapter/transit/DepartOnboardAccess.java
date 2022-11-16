package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.ScheduledTransitLeg;
import org.opentripplanner.routing.algorithm.mapping.GraphPathToItineraryMapper;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.util.time.DurationUtils;
import org.opentripplanner.util.time.ServiceDateUtils;

public class DepartOnboardAccess extends DefaultAccessEgress {

  private final int departureTime;
  private final TripTimes tripTimes;
  private final TripPattern tripPattern;
  private final int fromStopPositionInPattern;
  private final int toStopPositionInPattern;
  private final LocalDate serviceDate;
  private final ZoneId timeZone;
  private final int boardingTime;
  private final int alightingTime;

  public DepartOnboardAccess(
    int stop,
    State lastState,
    int departureTime,
    int generalizedCost,
    int durationInSeconds,
    TripTimes tripTimes,
    TripPattern tripPattern,
    int fromStopPositionInPattern,
    int toStopPositionInPattern,
    LocalDate serviceDate,
    ZoneId timeZone,
    int boardingTime,
    int alightingTime
  ) {
    super(stop, generalizedCost, durationInSeconds, lastState);
    this.departureTime = departureTime;
    this.tripTimes = tripTimes;
    this.tripPattern = tripPattern;
    this.fromStopPositionInPattern = fromStopPositionInPattern;
    this.toStopPositionInPattern = toStopPositionInPattern;
    this.serviceDate = serviceDate;
    this.timeZone = timeZone;
    this.boardingTime = boardingTime;
    this.alightingTime = alightingTime;
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    if (requestedDepartureTime > departureTime) {
      return -1;
    }
    return departureTime;
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    if (requestedArrivalTime < departureTime + durationInSeconds()) {
      return -1;
    }
    return departureTime + durationInSeconds();
  }

  @Override
  public int numberOfRides() {
    return 1;
  }

  @Override
  public boolean hasRides() {
    return true;
  }

  @Override
  public boolean hasOpeningHours() {
    return true;
  }

  @Override
  public boolean stopReachedOnBoard() {
    return true;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public String asString() {
    return String.format(
      "OnBoard %s %s ~ %d",
      tripTimes.getTrip().logName(),
      DurationUtils.durationToStr(durationInSeconds()),
      stop()
    );
  }

  @Override
  public Itinerary getSubItinerary(
    ZonedDateTime fromTime,
    GraphPathToItineraryMapper graphPathToItineraryMapper
  ) {
    return new Itinerary(
      List.of(
        new ScheduledTransitLeg(
          tripTimes,
          tripPattern,
          fromStopPositionInPattern,
          toStopPositionInPattern,
          ServiceDateUtils.toZonedDateTime(serviceDate, timeZone, boardingTime),
          ServiceDateUtils.toZonedDateTime(serviceDate, timeZone, alightingTime),
          serviceDate,
          timeZone,
          null,
          null,
          0, // TODO: What should we have here
          null
        )
      )
    );
  }
}
