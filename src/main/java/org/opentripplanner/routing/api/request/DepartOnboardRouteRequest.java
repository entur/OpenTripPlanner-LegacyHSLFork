package org.opentripplanner.routing.api.request;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Consumer;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.SortOrder;
import org.opentripplanner.model.plan.pagecursor.PageCursor;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.request.JourneyRequest;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.TripTimes;

public class DepartOnboardRouteRequest implements RouteRequest {

  private JourneyRequest journey;
  private RoutingPreferences preferences;
  private final boolean wheelchair;

  private final TripTimes tripTimes;

  private final int stopPositionInPattern;
  // This is only needed because TripTimes has no reference to TripPattern
  private final TripPattern tripPattern;
  private final LocalDate serviceDate;
  private final long midnight;
  private final GenericLocation to;
  private final Locale locale;

  public DepartOnboardRouteRequest(
    JourneyRequest journey,
    RoutingPreferences preferences,
    boolean wheelchair,
    TripTimes tripTimes,
    int stopPositionInPattern,
    TripPattern tripPattern,
    LocalDate serviceDate,
    long midnight,
    GenericLocation to,
    Locale locale
  ) {
    this.journey = journey;
    this.preferences = preferences;
    this.wheelchair = wheelchair;
    this.tripTimes = tripTimes;
    this.stopPositionInPattern = stopPositionInPattern;
    this.tripPattern = tripPattern;
    this.serviceDate = serviceDate;
    this.midnight = midnight;
    this.to = to;
    this.locale = locale;
  }

  @Override
  public JourneyRequest journey() {
    return journey;
  }

  public void withPreferences(Consumer<RoutingPreferences.Builder> body) {
    this.preferences = preferences.copyOf().apply(body).build();
  }

  @Override
  public RoutingPreferences preferences() {
    return preferences;
  }

  @Override
  public boolean wheelchair() {
    return wheelchair;
  }

  @Override
  public Instant dateTime() {
    return Instant.ofEpochSecond(midnight + tripTimes.getDepartureTime(stopPositionInPattern));
  }

  @Override
  public SortOrder itinerariesSortOrder() {
    return SortOrder.STREET_AND_ARRIVAL_TIME;
  }

  @Override
  public void applyPageCursor() {}

  @Override
  public boolean maxNumberOfItinerariesCropHead() {
    return false;
  }

  @Override
  public boolean doCropSearchWindowAtTail() {
    return true;
  }

  @Override
  public GenericLocation from() {
    final StopLocation stop = tripPattern.getStop(stopPositionInPattern);
    return new GenericLocation(
      stop.getName().toString(locale),
      stop.getId(),
      stop.getLat(),
      stop.getLon()
    );
  }

  @Override
  public GenericLocation to() {
    return to;
  }

  @Override
  public Duration searchWindow() {
    return Duration.ZERO;
  }

  @Override
  public Locale locale() {
    return locale;
  }

  @Override
  public PageCursor pageCursor() {
    return null;
  }

  @Override
  public boolean timetableView() {
    return false;
  }

  @Override
  public boolean arriveBy() {
    return false;
  }

  @Override
  public int numItineraries() {
    return -1;
  }

  public int stopPositionInPattern() {
    return stopPositionInPattern;
  }

  public TripTimes tripTimes() {
    return tripTimes;
  }

  public TripPattern tripPattern() {
    return tripPattern;
  }

  public LocalDate serviceDate() {
    return serviceDate;
  }

  @Override
  public RouteRequest clone() {
    try {
      DepartOnboardRouteRequest clone = (DepartOnboardRouteRequest) super.clone();
      clone.journey = journey.clone();

      return clone;
    } catch (CloneNotSupportedException e) {
      /* this will never happen since our super is the cloneable object */
      throw new RuntimeException(e);
    }
  }
}
