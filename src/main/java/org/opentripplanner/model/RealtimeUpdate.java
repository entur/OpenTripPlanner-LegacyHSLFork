package org.opentripplanner.model;

import java.time.LocalDate;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;

public record RealtimeUpdate(
  TripPattern pattern,
  TripTimes updatedTripTimes,
  LocalDate serviceDate,
  TripOnServiceDate tripOnServiceDate
) {
  public RealtimeUpdate(TripPattern pattern, TripTimes updatedTripTimes, LocalDate serviceDate) {
    this(pattern, updatedTripTimes, serviceDate, null);
  }
}
