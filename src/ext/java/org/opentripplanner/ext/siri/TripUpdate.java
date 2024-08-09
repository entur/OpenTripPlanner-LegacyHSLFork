package org.opentripplanner.ext.siri;

import java.time.LocalDate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.model.timetable.TripTimes;

record TripUpdate(
  @Nonnull StopPattern stopPattern,
  @Nonnull TripTimes tripTimes,
  @Nonnull LocalDate serviceDate,
  @Nullable TripOnServiceDate tripOnServiceDate,
  @Nullable TripPattern tripPattern
) {
  public TripUpdate(
    StopPattern stopPattern,
    RealTimeTripTimes updatedTripTimes,
    LocalDate serviceDate
  ) {
    this(stopPattern, updatedTripTimes, serviceDate, null, null);
  }
}
