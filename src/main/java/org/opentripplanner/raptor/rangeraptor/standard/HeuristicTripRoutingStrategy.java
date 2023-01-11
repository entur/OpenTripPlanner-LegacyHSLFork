package org.opentripplanner.raptor.rangeraptor.standard;

import org.opentripplanner.raptor.rangeraptor.internalapi.RoutingStrategy;
import org.opentripplanner.raptor.rangeraptor.transit.TransitCalculator;
import org.opentripplanner.raptor.spi.RaptorAccessEgress;
import org.opentripplanner.raptor.spi.RaptorConstrainedBoardingSearch;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * Routing strategy for heuristic searches. Does not operate on actual times, but only durations.
 * Does not do any trip searches, but operates on a single heuristic trip.
 */
public class HeuristicTripRoutingStrategy<T extends RaptorTripSchedule>
  implements RoutingStrategy<T> {

  public static final int UNREACHED = 999_999_999;

  private final StdWorkerState<T> state;
  private final TransitCalculator<T> calculator;
  private RaptorTripSchedule currentTrip;
  private int currentBoardingTime;
  private int currentBoardingDuration;
  private int currentBoardingStop;

  public HeuristicTripRoutingStrategy(StdWorkerState<T> state, TransitCalculator<T> calculator) {
    this.state = state;
    this.calculator = calculator;
  }

  @Override
  public void setAccessToStop(RaptorAccessEgress accessPath, int iterationDepartureTime) {
    state.setAccessToStop(accessPath, 0);
  }

  @Override
  public void prepareForTransitWith(RaptorTimeTable<T> timetable) {
    this.currentTrip = timetable.getHeuristicTrip();
    this.currentBoardingTime = UNREACHED;
    this.currentBoardingDuration = UNREACHED;
    this.currentBoardingStop = UNREACHED;
  }

  @Override
  public void boardWithRegularTransfer(int stopIndex, int stopPos, int boardSlack) {
    int arrivalDuration = state.bestTimePreviousRound(stopIndex);
    int boardingDuration = arrivalDuration + boardSlack;

    board(stopIndex, stopPos, boardingDuration);
  }

  @Override
  public void boardWithConstrainedTransfer(
    int stopIndex,
    int stopPos,
    int boardSlack,
    RaptorConstrainedBoardingSearch<T> txSearch
  ) {
    // Assume 0 boarding slack and cost as it is the lower bound for constrained transfers
    board(stopIndex, stopPos, state.bestTimePreviousRound(stopIndex));
  }

  private void board(int stopIndex, int stopPos, int boardingDuration) {
    int boardingTime = calculator.stopDepartureTime(currentTrip, stopPos);

    if (
      currentBoardingDuration == UNREACHED ||
      (
        (currentBoardingDuration - boardingDuration) >
        calculator.duration(boardingTime, currentBoardingTime)
      )
    ) {
      currentBoardingTime = boardingTime;
      currentBoardingDuration = boardingDuration;
      currentBoardingStop = stopIndex;
    }
  }

  @Override
  public void alight(int stopIndex, int stopPos, int alightSlack) {
    if (currentBoardingTime != UNREACHED) {
      int transitDuration = calculator.duration(
        currentBoardingTime,
        calculator.stopArrivalTime(currentTrip, stopPos)
      );
      state.transitToStop(
        stopIndex,
        currentBoardingDuration + alightSlack + transitDuration,
        currentBoardingStop,
        currentBoardingDuration,
        // TODO: This is wrong, but the trip is actually not used in the state
        (T) currentTrip
      );
    }
  }
}
