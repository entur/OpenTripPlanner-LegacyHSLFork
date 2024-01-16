package org.opentripplanner.routing.algorithm.raptoradapter.transit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.framework.time.TimeUtils;
import org.opentripplanner.model.BookingInfo;
import org.opentripplanner.model.BookingTime;
import org.opentripplanner.raptor.api.model.RaptorConstants;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BookingTimeAccessEgress implements RoutingAccessEgress {

  private static final Logger LOG = LoggerFactory.getLogger(BookingTimeAccessEgress.class);

  private final RoutingAccessEgress delegate;
  private final int earliestDepartureTime;

  /**
   * package local to be able to unit-test it.
   */
  BookingTimeAccessEgress(int earliestDepartureTime, RoutingAccessEgress delegate) {
    this.delegate = delegate;
    this.earliestDepartureTime = earliestDepartureTime;
  }

  public static RoutingAccessEgress of(
    Instant requestedEarliestBookingTime,
    ZonedDateTime transitSearchTimeZero,
    FlexAccessEgressAdapter delegate
  ) {
    var bookingInfo = delegate.getFlexTrip().getPickupBookingInfo(0);
    var lbt = bookingInfo.getLatestBookingTime();

    if (lbt != null) {
      // TODO: We calculate the latest booking time based on the time-zone of the internal model of
      //       OTP, this is not correct - it should be calculated using the time-zone of the
      //       imported data. One way to this is to store the time-zone in the BookingTime as well.
      var latestBookingTime = transitSearchTimeZero
        .minusDays(lbt.getDaysPrior())
        .with(lbt.getTime());

      return requestedEarliestBookingTime.isAfter(latestBookingTime.toInstant()) ? null : delegate;
    }

    int requestedEbt = TimeUtils.otpTime(transitSearchTimeZero, requestedEarliestBookingTime);
    Duration minimumBookingNotice = bookingInfo.getMinimumBookingNotice();
    if (minimumBookingNotice != null) {
      int earliestBoardingTime = requestedEbt + (int) minimumBookingNotice.toSeconds();
      return new BookingTimeAccessEgress(earliestBoardingTime, delegate);
    }
    // If a bookingInfo without latestBookingTime or minimumBookingNotice is not valid,
    // then it should be validated else where, not here. To be robust, this code should
    // just throw an exception or return the delegate (depending on the business rules)
    LOG.error(
      "Missing both latest booking time and minimum booking notice. Falling back " +
        "to default earliest booking time. Access/egress: {}",
      delegate.getFlexTrip().getId()
    );
    return delegate;
  }

  @Override
  public int stop() {
    return delegate.stop();
  }

  @Override
  public int c1() {
    return delegate.c1();
  }

  @Override
  public int durationInSeconds() {
    return delegate.durationInSeconds();
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    int edt = delegate.earliestDepartureTime(requestedDepartureTime);
    if (edt == RaptorConstants.TIME_NOT_SET) {
      return RaptorConstants.TIME_NOT_SET;
    }
    return Math.max(earliestDepartureTime, requestedDepartureTime);
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    // To alight from this access/egress we must be able to alight AFTER the
    // calculated earliestArrivalTime. If we cannot, then it is not possible to use
    // this access/egress
    int eat = earliestDepartureTime + delegate.durationInSeconds();
    int lat = delegate.latestArrivalTime(requestedArrivalTime);
    return lat < eat ? RaptorConstants.TIME_NOT_SET : lat;
  }

  @Override
  public boolean hasOpeningHours() {
    return delegate.hasOpeningHours();
  }

  @Override
  @Nullable
  public String openingHoursToString() {
    return delegate.openingHoursToString();
  }

  @Override
  public int numberOfRides() {
    return delegate.numberOfRides();
  }

  @Override
  public boolean hasRides() {
    return delegate.hasRides();
  }

  @Override
  public boolean stopReachedOnBoard() {
    return delegate.stopReachedOnBoard();
  }

  @Override
  public boolean stopReachedByWalking() {
    return delegate.stopReachedByWalking();
  }

  @Override
  public boolean isFree() {
    return delegate.isFree();
  }

  @Override
  public String defaultToString() {
    return delegate.defaultToString();
  }

  @Override
  public String asString(boolean includeStop, boolean includeCost, @Nullable String summary) {
    return delegate.asString(includeStop, includeCost, summary);
  }

  @Override
  public RoutingAccessEgress withPenalty(TimeAndCost penalty) {
    return delegate.withPenalty(penalty);
  }

  @Override
  public State getLastState() {
    return delegate.getLastState();
  }

  @Override
  public boolean isWalkOnly() {
    return delegate.isWalkOnly();
  }

  @Override
  public boolean hasPenalty() {
    return delegate.hasPenalty();
  }

  @Override
  public TimeAndCost penalty() {
    return delegate.penalty();
  }

  @Override
  public int timeShiftDepartureTimeToActualTime(int computedDepartureTimeIncludingPenalty) {
    return delegate.timeShiftDepartureTimeToActualTime(computedDepartureTimeIncludingPenalty);
  }
}
