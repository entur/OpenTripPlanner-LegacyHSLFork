package org.opentripplanner.ext.flex;

import static org.opentripplanner.model.StopTime.MISSING_VALUE;

import java.util.Objects;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.framework.time.TimeUtils;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;

public final class FlexAccessEgress {

  private final RegularStop stop;
  private final FlexPathDurations pathDurations;
  private final int fromStopIndex;
  private final int toStopIndex;
  private final FlexTrip<?, ?> trip;
  private final State lastState;
  private final boolean stopReachedOnBoard;
  private final int requestedBookingTime;
  private final RoutingBookingInfo routingBookingInfo;

  public FlexAccessEgress(
    RegularStop stop,
    FlexPathDurations pathDurations,
    int fromStopIndex,
    int toStopIndex,
    FlexTrip<?, ?> trip,
    State lastState,
    boolean stopReachedOnBoard,
    int requestedBookingTime
  ) {
    this.stop = stop;
    this.pathDurations = pathDurations;
    this.fromStopIndex = fromStopIndex;
    this.toStopIndex = toStopIndex;
    this.trip = Objects.requireNonNull(trip);
    this.lastState = lastState;
    this.stopReachedOnBoard = stopReachedOnBoard;
    this.routingBookingInfo = createRoutingBookingInfo();
    this.requestedBookingTime = requestedBookingTime;
  }

  public RegularStop stop() {
    return stop;
  }

  public State lastState() {
    return lastState;
  }

  public boolean stopReachedOnBoard() {
    return stopReachedOnBoard;
  }

  public int earliestDepartureTime(int departureTime) {
    int tripDepartureTime = pathDurations.mapToFlexTripDepartureTime(departureTime);

    int tmp = tripDepartureTime;
    tripDepartureTime =
      routingBookingInfo.earliestDepartureTime(requestedBookingTime, tripDepartureTime);

    if (tmp != tripDepartureTime) {
      System.out.println("departure time ....... : " + TimeUtils.timeToStrLong(tmp));
      System.out.println("min notice dep.time .. : " + TimeUtils.timeToStrLong(tripDepartureTime));
    }

    int earliestDepartureTime = trip.earliestDepartureTime(
      tripDepartureTime,
      fromStopIndex,
      toStopIndex,
      pathDurations.trip()
    );
    if (earliestDepartureTime == MISSING_VALUE) {
      return MISSING_VALUE;
    }
    /*
    if (
      !routingBookingInfo.isThereEnoughTimeToBookForDeparture(
        earliestDepartureTime,
        requestedBookingTime
      )
    ) {
      return MISSING_VALUE;
    }
    */
    return pathDurations.mapToRouterDepartureTime(earliestDepartureTime);
  }

  public int latestArrivalTime(int arrivalTime) {
    int tripArrivalTime = pathDurations.mapToFlexTripArrivalTime(arrivalTime);
    int latestArrivalTime = trip.latestArrivalTime(
      tripArrivalTime,
      fromStopIndex,
      toStopIndex,
      pathDurations.trip()
    );
    if (latestArrivalTime == MISSING_VALUE) {
      return MISSING_VALUE;
    }
    if (
      routingBookingInfo.exceedsMinimumBookingNotice(
        latestArrivalTime - pathDurations.trip(),
        requestedBookingTime
      )
    ) {
      return MISSING_VALUE;
    }
    return pathDurations.mapToRouterArrivalTime(latestArrivalTime);
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(FlexAccessEgress.class)
      .addNum("fromStopIndex", fromStopIndex)
      .addNum("toStopIndex", toStopIndex)
      .addObj("durations", pathDurations)
      .addObj("stop", stop)
      .addObj("trip", trip.getId())
      .addObj("lastState", lastState)
      .addBoolIfTrue("stopReachedOnBoard", stopReachedOnBoard)
      .toString();
  }

  private RoutingBookingInfo createRoutingBookingInfo() {
    return RoutingBookingInfo
      .of()
      .withBookingInfo(trip.getPickupBookingInfo(fromStopIndex))
      .withLegDurationInSeconds(pathDurations.total())
      .withTimeOffsetInSeconds(pathDurations.access())
      .build();
  }
}
