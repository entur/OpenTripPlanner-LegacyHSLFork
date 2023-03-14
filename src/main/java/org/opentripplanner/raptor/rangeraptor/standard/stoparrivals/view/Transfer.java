package org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.view;

import org.opentripplanner.raptor.api.model.RaptorTransfer;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.view.ArrivalView;
import org.opentripplanner.raptor.rangeraptor.standard.stoparrivals.StopArrivalState;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;

final class Transfer<T extends RaptorTripSchedule> extends StopArrivalViewAdapter<T> {

  private final StopArrivalState<T> arrival;
  private final StopsCursor<T> cursor;

  Transfer(int round, int stop, StopArrivalState<T> arrival, StopsCursor<T> cursor) {
    super(round, stop);
    this.arrival = arrival;
    this.cursor = cursor;
  }

  @Override
  public int c1() {
    return RaptorCostCalculator.ZERO_COST;
  }

  @Override
  public int c2() {
    throw new UnsupportedOperationException("C2 is not available for the C1 implementation");
  }

  @Override
  public int arrivalTime() {
    return arrival.time();
  }

  @Override
  public ArrivalView<T> previous() {
    return cursor.stop(round(), arrival.transferFromStop(), true);
  }

  @Override
  public boolean arrivedByTransfer() {
    return true;
  }

  @Override
  public RaptorTransfer transfer() {
    return arrival.transferPath();
  }

  @Override
  public boolean arrivedOnBoard() {
    return false;
  }
}
