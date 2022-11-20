package org.opentripplanner.raptor.heuristic;

import static org.opentripplanner.framework.lang.IntUtils.intArray;

import java.util.BitSet;
import java.util.function.IntConsumer;
import org.opentripplanner.raptor.rangeraptor.internalapi.HeuristicAtStop;
import org.opentripplanner.raptor.rangeraptor.internalapi.Heuristics;

public class RoundOnlyHeuristics implements Heuristics {

  private final BitSet[] reachedStopsByRound;
  private final int[] egressStops;
  private final int numStops;

  public RoundOnlyHeuristics(BitSet[] reachedStopsByRound, int[] egressStops, int numStops) {
    this.reachedStopsByRound = reachedStopsByRound;
    this.egressStops = egressStops;
    this.numStops = numStops;
  }

  @Override
  public HeuristicAtStop createHeuristicAtStop(int stop) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int[] bestTravelDurationToIntArray(int unreached) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int[] bestNumOfTransfersToIntArray(int unreached) {
    int[] numTransfers = intArray(numStops, unreached);
    for (byte i = 0; i < reachedStopsByRound.length; i++) {
      BitSet reached = reachedStopsByRound[i];
      final byte round = i;
      final IntConsumer action = stop -> numTransfers[stop] = round;
      reached.stream().forEach(action);
    }

    return numTransfers;
  }

  @Override
  public int[] bestGeneralizedCostToIntArray(int unreached) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return numStops;
  }

  @Override
  public int bestOverallJourneyTravelDuration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int bestOverallJourneyNumOfTransfers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int minWaitTimeForJourneysReachingDestination() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean destinationReached() {
    for (var reached : reachedStopsByRound) {
      for (final int egressStop : egressStops) {
        if (reached.get(egressStop)) {
          return true;
        }
      }
    }
    return false;
  }
}
