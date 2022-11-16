package org.opentripplanner.routing.api.request;

import static org.opentripplanner.util.time.DurationUtils.durationInSeconds;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.function.Consumer;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.SortOrder;
import org.opentripplanner.model.plan.pagecursor.PageCursor;
import org.opentripplanner.model.plan.pagecursor.PageType;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.request.JourneyRequest;
import org.opentripplanner.util.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A trip planning request. Some parameters may not be honored by the trip planner for some or all
 * itineraries.
 * <p>
 * All defaults should be specified here in the RouteRequest, NOT as annotations on query
 * parameters in web services that create RouteRequests. This establishes a priority chain for
 * default values: RouteRequest field initializers, then JSON router config, then query
 * parameters.
 *
 */
public class RegularRouteRequest implements RouteRequest {

  private static final Logger LOG = LoggerFactory.getLogger(RegularRouteRequest.class);

  private static final long NOW_THRESHOLD_SEC = durationInSeconds("15h");

  /* FIELDS UNIQUELY IDENTIFYING AN SPT REQUEST */

  private GenericLocation from;

  private GenericLocation to;

  private Instant dateTime = Instant.now();

  private Duration searchWindow;

  private PageCursor pageCursor;

  private boolean timetableView = true;

  private boolean arriveBy = false;

  private int numItineraries = 50;

  private Locale locale = new Locale("en", "US");

  private RoutingPreferences preferences = new RoutingPreferences();

  private JourneyRequest journey = new JourneyRequest();

  private boolean wheelchair = false;

  /* CONSTRUCTORS */

  /** Constructor for options; modes defaults to walk and transit */
  public RegularRouteRequest() {
    // So that they are never null.
    from = new GenericLocation(null, null);
    to = new GenericLocation(null, null);
  }

  /* ACCESSOR/SETTER METHODS */

  public void setArriveBy(boolean arriveBy) {
    this.arriveBy = arriveBy;
  }

  @Override
  public JourneyRequest journey() {
    return journey;
  }

  @Override
  public RoutingPreferences preferences() {
    return preferences;
  }

  public void withPreferences(Consumer<RoutingPreferences.Builder> body) {
    this.preferences = preferences.copyOf().apply(body).build();
  }

  @Override
  public boolean wheelchair() {
    return wheelchair;
  }

  public void setWheelchair(boolean wheelchair) {
    this.wheelchair = wheelchair;
  }

  @Override
  public Instant dateTime() {
    return dateTime;
  }

  public void setDateTime(Instant dateTime) {
    this.dateTime = dateTime;
  }

  public void setDateTime(String date, String time, ZoneId tz) {
    ZonedDateTime dateObject = DateUtils.toZonedDateTime(date, time, tz);
    setDateTime(dateObject == null ? Instant.now() : dateObject.toInstant());
  }

  /**
   * Is the trip originally planned withing the previous/next 15h?
   */
  public boolean isTripPlannedForNow() {
    return Duration.between(dateTime, Instant.now()).abs().toSeconds() < NOW_THRESHOLD_SEC;
  }

  @Override
  public SortOrder itinerariesSortOrder() {
    if (pageCursor != null) {
      return pageCursor.originalSortOrder;
    }
    return arriveBy ? SortOrder.STREET_AND_DEPARTURE_TIME : SortOrder.STREET_AND_ARRIVAL_TIME;
  }

  @Override
  public void applyPageCursor() {
    if (pageCursor != null) {
      // We switch to "depart-after" search when paging next(lat==null). It does not make
      // sense anymore to keep the latest-arrival-time when going to the "next page".
      if (pageCursor.latestArrivalTime == null) {
        arriveBy = false;
      }
      this.dateTime = arriveBy ? pageCursor.latestArrivalTime : pageCursor.earliestDepartureTime;
      journey.setModes(journey.modes().copyOf().withDirectMode(StreetMode.NOT_SET).build());
      LOG.debug("Request dateTime={} set from pageCursor.", dateTime);
    }
  }

  @Override
  public boolean maxNumberOfItinerariesCropHead() {
    if (pageCursor == null) {
      return false;
    }

    var previousPage = pageCursor.type == PageType.PREVIOUS_PAGE;
    return pageCursor.originalSortOrder.isSortedByArrivalTimeAcceding() == previousPage;
  }

  @Override
  public boolean doCropSearchWindowAtTail() {
    if (pageCursor == null) {
      return itinerariesSortOrder().isSortedByArrivalTimeAcceding();
    }
    return pageCursor.type == PageType.NEXT_PAGE;
  }

  public String toString(String sep) {
    return from + sep + to + sep + dateTime + sep + arriveBy + sep + journey.modes();
  }

  /* INSTANCE METHODS */

  /**
   * This method is used to clone the default message, and insert a current time. A typical use-case
   * is to copy the default request(from router-config), and then set all user specified parameters
   * before performing a routing search.
   */
  public RegularRouteRequest copyWithDateTimeNow() {
    RegularRouteRequest copy = clone();
    copy.setDateTime(Instant.now());
    return copy;
  }

  @Override
  public RegularRouteRequest clone() {
    try {
      RegularRouteRequest clone = (RegularRouteRequest) super.clone();
      clone.journey = journey.clone();

      return clone;
    } catch (CloneNotSupportedException e) {
      /* this will never happen since our super is the cloneable object */
      throw new RuntimeException(e);
    }
  }

  public String toString() {
    return toString(" ");
  }

  @Override
  public GenericLocation from() {
    return from;
  }

  public void setFrom(GenericLocation from) {
    this.from = from;
  }

  @Override
  public GenericLocation to() {
    return to;
  }

  public void setTo(GenericLocation to) {
    this.to = to;
  }

  @Override
  public Duration searchWindow() {
    return searchWindow;
  }

  public void setSearchWindow(Duration searchWindow) {
    this.searchWindow = searchWindow;
  }

  @Override
  public Locale locale() {
    return locale;
  }

  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  @Override
  public PageCursor pageCursor() {
    return pageCursor;
  }

  public void setPageCursorFromEncoded(String pageCursor) {
    this.pageCursor = PageCursor.decode(pageCursor);
  }

  @Override
  public boolean timetableView() {
    return timetableView;
  }

  public void setTimetableView(boolean timetableView) {
    this.timetableView = timetableView;
  }

  @Override
  public boolean arriveBy() {
    return arriveBy;
  }

  @Override
  public int numItineraries() {
    return numItineraries;
  }

  public void setNumItineraries(int numItineraries) {
    this.numItineraries = numItineraries;
  }
}
