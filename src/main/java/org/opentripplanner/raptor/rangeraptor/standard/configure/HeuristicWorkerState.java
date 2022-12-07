package org.opentripplanner.raptor.rangeraptor.standard.configure;

import static org.opentripplanner.framework.lang.IntUtils.intArray;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import org.opentripplanner.raptor.api.path.Path;
import org.opentripplanner.raptor.api.response.StopArrivals;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerLifeCycle;
import org.opentripplanner.raptor.rangeraptor.internalapi.WorkerState;
import org.opentripplanner.raptor.spi.IntIterator;
import org.opentripplanner.raptor.spi.RaptorAccessEgress;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.raptor.util.BitSetIterator;

public class HeuristicWorkerState<T extends RaptorTripSchedule> implements WorkerState<T> {

  /** best cost of arriving transit or transfer */
  private final int[] overallGeneralizedCosts;

  /** best cost of arriving transit **/
  private final int[] transitGeneralizedCosts;
  private final WorkerLifeCycle lifeCycle;

  private final BitSet reachedByTransitCurrentRound;
  private BitSet reachedCurrentRound;
  private BitSet reachedLastRound;

  public HeuristicWorkerState(int nStops, WorkerLifeCycle lifeCycle) {
    this.overallGeneralizedCosts = intArray(nStops, Integer.MAX_VALUE);
    this.transitGeneralizedCosts = intArray(nStops, Integer.MAX_VALUE);
    this.reachedCurrentRound = new BitSet(overallGeneralizedCosts.length);
    this.reachedLastRound = new BitSet(overallGeneralizedCosts.length);
    this.reachedByTransitCurrentRound = new BitSet(overallGeneralizedCosts.length);
    ;
    this.lifeCycle = lifeCycle;

    lifeCycle.onSetupIteration(ignore -> setupIteration());
    lifeCycle.onPrepareForNextRound(ignore -> prepareForNextRound());

  }


  @Override
  public boolean isNewRoundAvailable() {
    return !reachedCurrentRound.isEmpty();
  }

  @Override
  public IntIterator stopsTouchedPreviousRound() {
    return new BitSetIterator(reachedLastRound);
  }

  @Override
  public IntIterator stopsTouchedByTransitCurrentRound() {
    return new BitSetIterator(reachedByTransitCurrentRound);
  }

  @Override
  public boolean isDestinationReachedInCurrentRound() {
    return false;
  }

  @Override
  public void setAccessToStop(RaptorAccessEgress accessPath, int iterationDepartureTime) {
    final int generalizedCost = accessPath.generalizedCost();
    final int stop = accessPath.stop();
    if(accessPath.stopReachedOnBoard()) {
      newBestTransitGeneralizedCost(stop, generalizedCost);
    }
    newOverallBestGeneralizedCost(stop, generalizedCost);
  }

  private boolean newBestTransitGeneralizedCost(int stop, int generalizedCost) {
    if (transitGeneralizedCosts[stop] > generalizedCost) {
      transitGeneralizedCosts[stop] = generalizedCost;
      reachedByTransitCurrentRound.set(stop);
      return true;
    }
    return false;
  }

  private boolean newOverallBestGeneralizedCost(int stop, int generalizedCost) {
    if (overallGeneralizedCosts[stop] > generalizedCost) {
      overallGeneralizedCosts[stop] = generalizedCost;
      reachedCurrentRound.set(stop);
      return true;
    }
    return false;
  }

  public void transitToStop(int stop, int cost) {
    if (newBestTransitGeneralizedCost(stop, cost)) {
      newOverallBestGeneralizedCost(stop, cost);
    }
  }

  @Override
  public void transferToStops(int fromStop, Iterator<? extends RaptorTransfer> transfers) {

  }

  @Override
  public Collection<Path<T>> extractPaths() {
    return null;
  }

  @Override
  public StopArrivals extractStopArrivals() {
    return null;
  }


  public int bestOverallCost(int stopIndex) {
    return overallGeneralizedCosts[stopIndex];
  }

  // Lifecycle

  private void setupIteration() {
    // clear all touched stops to avoid constant re-exploration
    reachedCurrentRound.clear();
    reachedByTransitCurrentRound.clear();
  }

  private void prepareForNextRound() {
    swapReachedCurrentAndLastRound();
    reachedCurrentRound.clear();
    reachedByTransitCurrentRound.clear();
  }

  private void swapReachedCurrentAndLastRound() {
    BitSet tmp = reachedLastRound;
    reachedLastRound = reachedCurrentRound;
    reachedCurrentRound = tmp;
  }
}
