package org.opentripplanner.transit.model.trip.search;

import javax.annotation.Nonnull;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.raptor.api.model.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorBoardOrAlightEvent;
import org.opentripplanner.raptor.spi.RaptorTripScheduleSearch;
import org.opentripplanner.transit.model.trip.Timetable;
import org.opentripplanner.transit.model.trip.TripOnDay;

public class ForwardSearch
  implements RaptorTripScheduleSearch<TripOnDay>, RaptorBoardOrAlightEvent<TripOnDay> {

  private final Timetable timetable;

  private int tripIndex = NOT_FOUND;
  private TripOnDay trip;
  private int stopPositionInPattern;
  private int earliestBoardTime;

  public ForwardSearch(Timetable timetable) {
    this.timetable = timetable;
  }

  /* Implement RaptorTripScheduleSearch */

  @Override
  public RaptorBoardOrAlightEvent<TripOnDay> search(
    int earliestBoardTime,
    int stopPositionInPattern,
    int tripIndexLimit
  ) {
    this.earliestBoardTime = earliestBoardTime;
    this.stopPositionInPattern = stopPositionInPattern;
    searchForTrip();
    return this;
  }

  /* Implement RaptorBoardOrAlightEvent */

  @Override
  public int tripIndex() {
    return tripIndex;
  }

  @Override
  public TripOnDay trip() {
    if (this.trip == null) {
      if (empty()) {
        throw new IllegalStateException("Trip is not found! Check before calling this method.");
      }
      this.trip = new TripOnDay(tripIndex, timetable);
    }
    return trip;
  }

  @Override
  public int stopPositionInPattern() {
    return stopPositionInPattern;
  }

  @Override
  public int time() {
    return timetable.boardTime(tripIndex, stopPositionInPattern);
  }

  @Override
  public int earliestBoardTime() {
    return earliestBoardTime;
  }

  @Nonnull
  @Override
  public RaptorTransferConstraint transferConstraint() {
    return RaptorTransferConstraint.REGULAR_TRANSFER;
  }

  @Override
  public boolean empty() {
    return tripIndex == NOT_FOUND;
  }

  private void searchForTrip() {
    int index = timetable.findTripIndexBoardingAfter(stopPositionInPattern, earliestBoardTime);
    if (index == Timetable.NEXT_TIME_TABLE_INDEX) {
      // TODO RTM - We need to search the timetable for next day
      return;
    }
    if (index == Timetable.NOT_AVAILABLE) {
      return;
    }
    this.tripIndex = index;
  }

  @Override
  public String toString() {
    var builder = ToStringBuilder.of(ForwardSearch.class);
    if (!empty()) {
      builder
        .addNum("tripIndex", tripIndex)
        .addNum("stopPosition", stopPositionInPattern)
        .addServiceTime("earliestBoardTime", earliestBoardTime);
    }
    builder.addObj("timetable", timetable);
    return builder.toString();
  }
}
