package org.opentripplanner.transit.model.timetable.booking;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.framework.lang.IntUtils;
import org.opentripplanner.framework.tostring.ToStringBuilder;

/**
 * This is the contract between booking info and the router. The router will enforce
 * this information if the request sets the earliest-booking-time request parameter.
 * <p>
 * Both {@code latestBookingTime} and {@code minimumBookingNotice} can be {@code null},
 * but at least one ot them must be none {@code null}.
 * <p>
 * This class is not used by Raptor directly, but used by the BookingTimeAccessEgress with
 * implement the RaptorAccessEgress interface.
 */
public final class RoutingBookingInfo {

  public static final int NOT_SET = -1_999_999;
  private static final int ZERO = 0;
  private static final RoutingBookingInfo UNRESTRICTED = new RoutingBookingInfo();

  private final int latestBookingTime;
  private final int minimumBookingNotice;
  private final int legDurationInSeconds;
  private final int timeOffsetInSeconds;

  private RoutingBookingInfo(
    int latestBookingTime,
    int minimumBookingNotice,
    int legDurationInSeconds,
    int timeOffsetInSeconds
  ) {
    if (latestBookingTime == NOT_SET && minimumBookingNotice == NOT_SET) {
      throw new IllegalArgumentException(
        "Either latestBookingTime or minimumBookingNotice must be set."
      );
    }
    this.latestBookingTime = latestBookingTime;
    this.minimumBookingNotice = minimumBookingNotice;
    this.legDurationInSeconds =
      IntUtils.requireNotNegative(legDurationInSeconds, "legDurationInSeconds");
    this.timeOffsetInSeconds =
      IntUtils.requireNotNegative(timeOffsetInSeconds, "timeOffsetInSeconds");
  }

  private RoutingBookingInfo() {
    this.latestBookingTime = NOT_SET;
    this.minimumBookingNotice = NOT_SET;
    this.legDurationInSeconds = ZERO;
    this.timeOffsetInSeconds = ZERO;
  }

  /** See {@link #isUnrestricted()} */
  public static RoutingBookingInfo unrestricted() {
    return UNRESTRICTED;
  }

  public static RoutingBookingInfo of(@Nullable BookingInfo bookingInfo) {
    return bookingInfo == null ? unrestricted() : of().withBookingInfo(bookingInfo).build();
  }

  public static RoutingBookingInfo.Builder of() {
    return new Builder();
  }

  /**
   * Return {@code true} if there are no booking restrictions. Note! there can be other
   * booking-related information associated with the trip.
   */
  public boolean isUnrestricted() {
    return (
      (this == UNRESTRICTED) || (latestBookingTime == NOT_SET && minimumBookingNotice == NOT_SET)
    );
  }

  /**
   * Check if requested board-time can be booked according to the booking info rules. See
   * {@link BookingInfo}.
   * <p>
   * If not the case, the RaptorConstants.TIME_NOT_SET is returned.
   */
  public boolean isThereEnoughTimeToBookForDeparture(int departureTime, int requestedBookingTime) {
    if (isUnrestricted(requestedBookingTime)) {
      return true;
    }
    return isThereEnoughTimeToBook(departureTime + timeOffsetInSeconds, requestedBookingTime);
  }

  /**
   * Check if requested board-time can be booked according to the booking info rules. See
   * {@link BookingInfo}.
   * <p>
   * If not the case, the RaptorConstants.TIME_NOT_SET is returned.
   */
  public boolean isThereEnoughTimeToBookForArrival(int arrivalTime, int requestedBookingTime) {
    if (isUnrestricted(requestedBookingTime)) {
      return true;
    }
    return isThereEnoughTimeToBook(
      arrivalTime - legDurationInSeconds + timeOffsetInSeconds,
      requestedBookingTime
    );
  }

  public int earliestDepartureTime(int requestedDepartureTime, int departureTime) {
    if (requestedDepartureTime == NOT_SET || minimumBookingNotice == NOT_SET) {
      return departureTime;
    }
    return Math.max(requestedDepartureTime + minimumBookingNotice, departureTime);
  }

  private boolean isUnrestricted(int requestedBookingTime) {
    return requestedBookingTime == NOT_SET || isUnrestricted();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var other = (RoutingBookingInfo) o;
    return (
      Objects.equals(latestBookingTime, other.latestBookingTime) &&
      Objects.equals(minimumBookingNotice, other.minimumBookingNotice)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(latestBookingTime, minimumBookingNotice);
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(RoutingBookingInfo.class)
      .addServiceTime("latestBookingTime", latestBookingTime, NOT_SET)
      .addDurationSec("minimumBookingNotice", minimumBookingNotice, NOT_SET)
      .addDurationSec("timeOffsetInSeconds", timeOffsetInSeconds, ZERO)
      .addDurationSec("legDurationInSeconds", legDurationInSeconds, ZERO)
      .toString();
  }

  /**
   * Check if requested board-time can be booked according to the booking info rules. See
   * {@link BookingInfo}.
   * <p>
   * If not the case, the RaptorConstants.TIME_NOT_SET is returned.
   */
  private boolean isThereEnoughTimeToBook(int time, int requestedBookingTime) {
    // This can be optimized/simplified; it can be done before the search start since it
    // only depends on the latestBookingTime and requestedBookingTime, not the departure time.
    if (exceedsLatestBookingTime(requestedBookingTime)) {
      return false;
    }
    if (exceedsMinimumBookingNotice(time, requestedBookingTime)) {
      return false;
    }
    return true;
  }

  public boolean exceedsLatestBookingTime(int requestedBookingTime) {
    return (
      exist(requestedBookingTime) &&
      exist(latestBookingTime) &&
      exceedsLatestBookingTime(requestedBookingTime, latestBookingTime)
    );
  }

  private static boolean exceedsLatestBookingTime(int requestedBookingTime, int latestBookingTime) {
    return requestedBookingTime > latestBookingTime;
  }

  /**
   * Check if the given time is after (or eq to) the earliest time allowed according to the minimum
   * booking notice.
   */
  public boolean exceedsMinimumBookingNotice(int departureTime, int requestedBookingTime) {
    return (
      exist(requestedBookingTime) &&
      exist(minimumBookingNotice) &&
      (departureTime - minimumBookingNotice < requestedBookingTime)
    );
  }

  private static boolean exist(int value) {
    return value != NOT_SET;
  }

  public static class Builder {

    private int latestBookingTime;
    private int minimumBookingNotice;
    private int legDurationInSeconds = 0;
    private int timeOffsetInSeconds = 0;

    public Builder() {
      setUnrestricted();
    }

    /**
     * Convenience method to add booking info to builder.
     */
    public Builder withBookingInfo(@Nullable BookingInfo bookingInfo) {
      // Clear booking
      if (bookingInfo == null) {
        setUnrestricted();
        return this;
      }
      withLatestBookingTime(
        bookingInfo.getLatestBookingTime() == null
          ? NOT_SET
          : bookingInfo.getLatestBookingTime().relativeTimeSeconds()
      );
      withMinimumBookingNotice(
        bookingInfo.getMinimumBookingNotice() == null
          ? NOT_SET
          : (int) bookingInfo.getMinimumBookingNotice().toSeconds()
      );
      return this;
    }

    public Builder withLatestBookingTime(int latestBookingTime) {
      this.latestBookingTime = latestBookingTime;
      return this;
    }

    public Builder withMinimumBookingNotice(int minimumBookingNotice) {
      this.minimumBookingNotice = minimumBookingNotice;
      return this;
    }

    /**
     * The total time of the leg including any access and egress.
     * See {@link #withTimeOffsetInSeconds(int)}
     */
    public Builder withLegDurationInSeconds(int legDurationInSeconds) {
      this.legDurationInSeconds = legDurationInSeconds;
      return this;
    }

    /**
     * The offset is used to calculate when the "real" boardingTime is for the bookable service.
     * For example, when a Flex Service is part of access, there might be a walking section before
     * the flex service is boarded. In such a case the {@code timeOffsetInSeconds} should be set
     * to the time it takes to walk, before boarding the flex.
     */
    public Builder withTimeOffsetInSeconds(int timeOffsetInSeconds) {
      this.timeOffsetInSeconds = timeOffsetInSeconds;
      return this;
    }

    public RoutingBookingInfo build() {
      if (latestBookingTime == NOT_SET && minimumBookingNotice == NOT_SET) {
        return RoutingBookingInfo.unrestricted();
      }
      return new RoutingBookingInfo(
        latestBookingTime,
        minimumBookingNotice,
        legDurationInSeconds,
        timeOffsetInSeconds
      );
    }

    private void setUnrestricted() {
      latestBookingTime = NOT_SET;
      minimumBookingNotice = NOT_SET;
    }
  }
}
