package org.opentripplanner.raptor.rangeraptor.standard.configure;

import java.util.function.IntConsumer;
import org.opentripplanner.raptor.rangeraptor.internalapi.RoundProvider;
import org.opentripplanner.raptor.rangeraptor.internalapi.RoutingStrategy;
import org.opentripplanner.raptor.rangeraptor.transit.TransitCalculator;
import org.opentripplanner.raptor.spi.CostCalculator;
import org.opentripplanner.raptor.spi.RaptorAccessEgress;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.raptor.spi.RaptorTripScheduleBoardOrAlightEvent;
import org.opentripplanner.raptor.spi.TransitArrival;
import org.opentripplanner.street.search.state.State;

public class HeuristicWorkerStrategy<T extends RaptorTripSchedule> implements RoutingStrategy<T> {

  private static final int NOT_SET = -1;

  private final CostCalculator<T> costCalculator;
  private final TransitCalculator<T> transitCalculator;
  private final RoundProvider roundProvider;
  private final HeuristicWorkerState<T> state;


  private int onTripIndex;
  private int onTripBoardTime;
  private int onTripBoardStop;
  private T onTrip;
  private int onTripTimeShift;

  public HeuristicWorkerStrategy(TransitCalculator<T> transitCalculator, CostCalculator<T> costCalculator, RoundProvider roundProvider, HeuristicWorkerState<T> state) {
    this.transitCalculator = transitCalculator;
    this.costCalculator = costCalculator;
    this.roundProvider = roundProvider;
    this.state = state;
  }

  @Override
  public void setAccessToStop(RaptorAccessEgress accessPath, int iterationDepartureTime, int timeDependentDepartureTime) {
    state.setAccessToStop(accessPath, iterationDepartureTime);
  }

  @Override
  public void prepareForTransitWith() {
    this.onTripIndex = NOT_SET;
    this.onTripBoardTime = NOT_SET;
    this.onTripBoardStop = NOT_SET;
    this.onTrip = null;
    this.onTripTimeShift = NOT_SET;
  }

  @Override
  public void alight(int stopIndex, int stopPos, int alightSlack) {

    if (onTripIndex != NOT_SET) {
      // Trip alightTime + alight-slack(forward-search) or board-slack(reverse-search)
      final int stopArrivalTime0 = transitCalculator.stopArrivalTime(onTrip, stopPos, alightSlack);

      // Remove the wait time from the arrival-time. We don´t need to use the transit
      // calculator because of the way we compute the time-shift. It is positive in the case
      // of a forward-search and negative int he case of a reverse-search.
      final int stopArrivalTime = stopArrivalTime0 - onTripTimeShift;


      int cost = costCalculator.transitArrivalCost(0, alightSlack, stopArrivalTime - onTripBoardTime, onTrip, stopIndex);

      state.transitToStop(stopIndex, cost);
    }
  }

  @Override
  public void forEachBoarding(int stopIndex, IntConsumer prevStopArrivalTimeConsumer) {
    prevStopArrivalTimeConsumer.accept(0);

  }

  @Override
  public TransitArrival<T> previousTransit(int boardStopIndex) {
    return null;
  }

  @Override
  public void board(int stopIndex, int earliestBoardTime, RaptorTripScheduleBoardOrAlightEvent<T> boarding) {
    onTripIndex = boarding.getTripIndex();
    onTrip = boarding.getTrip();
    onTripBoardTime = earliestBoardTime;
    onTripBoardStop = stopIndex;
    // Calculate the time-shift, the time-shift will be a positive duration in a
    // forward-search, and a negative value in case of a reverse-search.
    onTripTimeShift = boarding.getTime() - onTripBoardTime;

  }



  private int calculateCostAtBoardTime(
    final int round,
    final RaptorTripScheduleBoardOrAlightEvent<T> boardEvent
  ) {
    return (
      costCalculator.boardingCost(
        round == 1,
        boardEvent.getTime(),
        boardEvent.getBoardStopIndex(),
        boardEvent.getTime(),
        boardEvent.getTrip(),
        boardEvent.getTransferConstraint()
      )
    );
  }
}
