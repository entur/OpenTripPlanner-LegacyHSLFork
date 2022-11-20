package org.opentripplanner.raptor.heuristic;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.opentripplanner.raptor.api.debug.RaptorTimers;
import org.opentripplanner.raptor.api.path.Path;
import org.opentripplanner.raptor.api.response.StopArrivals;
import org.opentripplanner.raptor.rangeraptor.internalapi.RoundProvider;
import org.opentripplanner.raptor.rangeraptor.internalapi.Worker;
import org.opentripplanner.raptor.rangeraptor.lifecycle.LifeCycleEventPublisher;
import org.opentripplanner.raptor.rangeraptor.transit.AccessPaths;
import org.opentripplanner.raptor.rangeraptor.transit.RoundTracker;
import org.opentripplanner.raptor.rangeraptor.transit.TransitCalculator;
import org.opentripplanner.raptor.spi.IntIterator;
import org.opentripplanner.raptor.spi.RaptorAccessEgress;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.raptor.util.BitSetIterator;

public class RoundOnlyHeuristicRaptorWorker<T extends RaptorTripSchedule> implements Worker<T> {

  private final RaptorTransitDataProvider<T> transitData;
  private final AccessPaths accessPaths;
  private final int[] egressStops;
  private final TransitCalculator<T> calculator;
  private final RoundTracker roundTracker;
  private final int nRounds;
  private final LifeCycleEventPublisher lifeCycle;
  private final RaptorTimers timers;

  /* State fields */
  private final BitSet[] reachedStopsByRound;
  private final BitSet reachedPreviousRounds;
  /** Stops touched in the CURRENT round. */
  private BitSet reachedCurrentRound;
  /** Stops touched by in LAST round. */
  private BitSet reachedLastRound;

  public RoundOnlyHeuristicRaptorWorker(
    RaptorTransitDataProvider<T> transitData,
    AccessPaths accessPaths,
    int[] egressStops,
    TransitCalculator<T> calculator,
    RoundProvider roundProvider,
    LifeCycleEventPublisher lifeCyclePublisher,
    RaptorTimers timers,
    int nRounds
  ) {
    this.transitData = transitData;
    this.calculator = calculator;
    this.timers = timers;
    this.accessPaths = accessPaths;
    this.nRounds = Math.max(nRounds, accessPaths.calculateMaxNumberOfRides());

    // We do a cast here to avoid exposing the round tracker  and the life cycle publisher to
    // "everyone" by providing access to it in the context.
    this.roundTracker = (RoundTracker) roundProvider;
    this.lifeCycle = lifeCyclePublisher;

    this.reachedStopsByRound = new BitSet[this.nRounds + 1];

    this.reachedPreviousRounds = new BitSet(transitData.numberOfStops());
    this.reachedCurrentRound = new BitSet(transitData.numberOfStops());
    this.reachedStopsByRound[0] = reachedCurrentRound;

    this.egressStops = egressStops;
  }

  @Override
  public void route() {
    timers.route(this::routeInternal);
  }

  private void routeInternal() {
    lifeCycle.notifyRouteSearchStart(calculator.searchForward());
    transitData.setup();
    lifeCycle.setupIteration(0);
    findAccessOnStreetForRound();

    while (hasMoreRounds()) {
      lifeCycle.prepareForNextRound(roundTracker.nextRound());
      prepareForNextRound();

      // NB since we have transfer limiting not bothering to cut off search when there are no
      // more transfers as that will be rare and complicates the code
      timers.findTransitForRound(this::findTransitForRound);

      boolean destinationReachedInCurrentRound = isDestinationReachedInCurrentRound();

      findAccessOnBoardForRound();

      timers.findTransfersForRound(this::findTransfersForRound);

      lifeCycle.roundComplete(destinationReachedInCurrentRound);

      findAccessOnStreetForRound();
    }

    // This state is repeatedly modified as the outer loop progresses over departure minutes.
    // We have to be careful here, the next iteration will modify the state, so we need to make
    // protective copies of any information we want to retain.
    lifeCycle.iterationComplete();
  }

  @Override
  public Collection<Path<T>> paths() {
    return List.of();
  }

  @Override
  public StopArrivals stopArrivals() {
    return null;
  }

  private boolean hasMoreRounds() {
    return round() < nRounds && isNewRoundAvailable();
  }

  private void findTransitForRound() {
    IntIterator stops = new BitSetIterator(reachedLastRound);
    IntIterator routeIndexIterator = transitData.routeIndexIterator(stops);

    while (routeIndexIterator.hasNext()) {
      var routeIndex = routeIndexIterator.next();
      var route = transitData.getRouteForIndex(routeIndex);
      var pattern = route.pattern();

      boolean boarded = false;

      IntIterator stop = calculator.patternStopIterator(pattern.numberOfStopsInPattern());

      while (stop.hasNext()) {
        int stopPos = stop.next();
        int stopIndex = pattern.stopIndex(stopPos);

        // attempt to alight if we're on board, this is done above the board search
        // so that we don't alight on first stop boarded
        if (calculator.alightingPossibleAt(pattern, stopPos)) {
          if (boarded && !reachedPreviousRounds.get(stopIndex)) {
            reachedCurrentRound.set(stopIndex);
          }
        }

        if (calculator.boardingPossibleAt(pattern, stopPos)) {
          if (reachedLastRound.get(stopIndex)) {
            boarded = true;
          }
        }
      }
    }
    lifeCycle.transitsForRoundComplete();
  }

  private void findTransfersForRound() {
    IntIterator it = new BitSetIterator((BitSet) reachedCurrentRound.clone());

    while (it.hasNext()) {
      final int fromStop = it.next();
      Iterator<? extends RaptorTransfer> transfers = calculator.getTransfers(transitData, fromStop);
      while (transfers.hasNext()) {
        int stop = transfers.next().stop();
        if (!reachedPreviousRounds.get(stop)) {
          reachedCurrentRound.set(stop);
        }
      }
    }

    lifeCycle.transfersForRoundComplete();
  }

  private void findAccessOnStreetForRound() {
    addAccessPaths(accessPaths.arrivedOnStreetByNumOfRides().get(round()));
  }

  private void findAccessOnBoardForRound() {
    addAccessPaths(accessPaths.arrivedOnBoardByNumOfRides().get(round()));
  }

  /**
   * Set the departure time in the scheduled search to the given departure time, and prepare for the
   * scheduled search at the next-earlier minute.
   */
  private void addAccessPaths(Collection<RaptorAccessEgress> accessPaths) {
    if (accessPaths == null) {
      return;
    }

    for (RaptorAccessEgress it : accessPaths) {
      int stop = it.stop();
      if (!reachedPreviousRounds.get(stop)) {
        reachedCurrentRound.set(stop);
      }
    }
  }

  private int round() {
    return roundTracker.round();
  }

  public boolean isNewRoundAvailable() {
    return !reachedCurrentRound.isEmpty();
  }

  public boolean isDestinationReachedInCurrentRound() {
    // This is fast enough, we could use a BitSet for egressStops, but it takes up more
    // memory and the performance is the same.
    for (final int egressStop : egressStops) {
      if (reachedCurrentRound.get(egressStop)) {
        return true;
      }
    }
    return false;
  }

  public RoundOnlyHeuristics heuristics() {
    return new RoundOnlyHeuristics(reachedStopsByRound, egressStops, transitData.numberOfStops());
  }

  private void prepareForNextRound() {
    reachedLastRound = reachedCurrentRound;
    reachedPreviousRounds.or(reachedLastRound);
    reachedCurrentRound = new BitSet(transitData.numberOfStops());
    reachedStopsByRound[round()] = reachedCurrentRound;
  }
}
