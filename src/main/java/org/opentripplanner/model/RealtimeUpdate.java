package org.opentripplanner.model;

import java.time.LocalDate;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;

public record RealtimeUpdate(
  TripPattern pattern,
  TripTimes updatedTripTimes,
  LocalDate serviceDate,
  boolean newRoute
) {
  public RealtimeUpdate(TripPattern pattern, TripTimes updatedTripTimes, LocalDate serviceDate) {
    this(pattern, updatedTripTimes, serviceDate, false);
  }
}
