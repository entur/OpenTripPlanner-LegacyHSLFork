package org.opentripplanner.routing.api.request;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.model.plan.SortOrder;
import org.opentripplanner.model.plan.pagecursor.PageCursor;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.request.JourneyRequest;

public interface RouteRequest extends Cloneable, Serializable {
  JourneyRequest journey();

  RoutingPreferences preferences();

  boolean wheelchair();

  /**
   * The epoch date/time in seconds that the trip should depart (or arrive, for requests where
   * arriveBy is true)
   * <p>
   * The search time for the current request. If the client have moved to the next page then this is
   * the adjusted search time - the dateTime passed in is ignored and replaced with by a time from
   * the pageToken.
   */
  Instant dateTime();

  SortOrder itinerariesSortOrder();

  /**
   * Adjust the 'dateTime' if the page cursor is set to "goto next/previous page". The date-time is
   * used for many things, for example finding the days to search, but the transit search is using
   * the cursor[if exist], not the date-time.
   */
  void applyPageCursor();

  /**
   * When paging we must crop the list of itineraries in the right end according to the sorting of
   * the original search and according to the page cursor type (next or previous).
   * <p>
   * We need to flip the cropping and crop the head/start of the itineraries when:
   * <ul>
   * <li>Paging to the previous page for a {@code depart-after/sort-on-arrival-time} search.
   * <li>Paging to the next page for a {@code arrive-by/sort-on-departure-time} search.
   * </ul>
   */
  boolean maxNumberOfItinerariesCropHead();

  /**
   * Related to {@link #maxNumberOfItinerariesCropHead()}, but is {@code true} if we should crop the
   * search-window head(in the beginning) or tail(in the end).
   * <p>
   * For the first search we look if the sort is ascending(crop tail) or descending(crop head), and
   * for paged results we look at the paging type: next(tail) and previous(head).
   */
  boolean doCropSearchWindowAtTail();

  /** The start location */
  GenericLocation from();

  /** The end location */
  GenericLocation to();

  /**
   * This is the time/duration in seconds from the earliest-departure-time(EDT) to
   * latest-departure-time(LDT). In case of a reverse search it will be the time from earliest to
   * latest arrival time (LAT - EAT).
   * <p>
   * All optimal travels that depart within the search window is guarantied to be found.
   * <p>
   * This is sometimes referred to as the Range Raptor Search Window - but could be used in a none
   * Transit search as well; Hence this is named search-window and not raptor-search-window. Do not
   * confuse this with the travel-window, which is the time between EDT to LAT.
   * <p>
   * Use {@code null} to unset, and {@link Duration#ZERO} to do one Raptor iteration. The value is
   * dynamically  assigned a suitable value, if not set. In a small to medium size operation you may
   * use a fixed value, like 60 minutes. If you have a mixture of high frequency cities routes and
   * infrequent long distant journeys, the best option is normally to use the dynamic auto
   * assignment.
   * <p>
   * There is no need to set this when going to the next/previous page any more.
   */
  Duration searchWindow();

  Locale locale();

  /**
   * Use the cursor to go to the next or previous "page" of trips. You should pass in the original
   * request as is.
   * <p>
   * The next page of itineraries will depart after the current results and the previous page of
   * itineraries will depart before the current results.
   * <p>
   * The paging does not support timeTableView=false and arriveBy=true, this will result in none
   * pareto-optimal results.
   */
  PageCursor pageCursor();

  /**
   * Search for the best trip options within a time window. If {@code true} two itineraries are
   * considered optimal if one is better on arrival time(earliest wins) and the other is better on
   * departure time(latest wins).
   * <p>
   * In combination with {@code arriveBy} this parameter cover the following 3 use cases:
   * <ul>
   *   <li>
   *     The traveler want to find the best alternative within a time window. Set
   *     {@code timetableView=true} and {@code arriveBy=false}. This is the default, and if the
   *     intention of the traveler is unknown, this gives the best result. This use-case includes
   *     all itineraries in the two next use-cases. This option also work well with paging.
   * <p>
   *     Setting the {@code arriveBy=false}, covers the same use-case, but the input time is
   *     interpreted as latest-arrival-time, and not earliest-departure-time.
   *   </li>
   *   <li>
   *     The traveler want to find the best alternative with departure after a specific time.
   *     For example: I am at the station now and want to get home as quickly as possible.
   *     Set {@code timetableView=true} and {@code arriveBy=false}. Do not support paging.
   *   </li>
   *   <li>
   *     Traveler want to find the best alternative with arrival before specific time. For
   *     example going to a meeting. Set {@code timetableView=true} and {@code arriveBy=false}.
   *     Do not support paging.
   *   </li>
   * </ul>
   * Default: true
   */
  boolean timetableView();

  /**
   * Whether the trip should depart at dateTime (false, the default), or arrive at dateTime.
   */
  boolean arriveBy();

  /**
   * The maximum number of itineraries to return. In OTP1 this parameter terminates the search, but
   * in OTP2 it crops the list of itineraries AFTER the search is complete. This parameter is a post
   * search filter function. A side effect from reducing the result is that OTP2 cannot guarantee to
   * find all pareto-optimal itineraries when paging. Also, a large search-window and a small {@code
   * numItineraries} waste computer CPU calculation time.
   * <p>
   * The default value is 50. This is a reasonably high threshold to prevent large amount of data to
   * be returned. Consider tuning the search-window instead of setting this to a small value.
   */
  int numItineraries();

  RouteRequest clone();
}
