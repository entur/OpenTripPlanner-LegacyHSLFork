package org.opentripplanner.transit.model.trip;

import java.util.BitSet;
import org.opentripplanner.raptor.api.model.RaptorTripPattern;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.SlackProvider;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * This class include the information about a pattern that is needed for Raptor to route on. A
 * {@link ScheduledTripPattern} may include variations witch Raptor need to threat as separate
 * patterns. TODO RTM
 */
public class RoutingTripPattern implements RaptorTripPattern {

  private final int[] stopsIndexes;
  private final BitSet boarding;
  private final BitSet alighting;
  private final int slackIndex;
  private final String debugInfo;

  public RoutingTripPattern(
    int[] stopsIndexes,
    BitSet boarding,
    BitSet alighting,
    TransitMode mode,
    String shortName
  ) {
    this.stopsIndexes = stopsIndexes;
    this.boarding = boarding;
    this.alighting = alighting;
    this.slackIndex = SlackProvider.slackIndex(mode);
    this.debugInfo = mode.name() + " " + shortName;
  }

  @Override
  public int patternIndex() {
    // TODO RTM
    return 0;
  }

  @Override
  public int numberOfStopsInPattern() {
    return stopsIndexes.length;
  }

  @Override
  public int stopIndex(int stopPositionInPattern) {
    return stopsIndexes[stopPositionInPattern];
  }

  @Override
  public boolean boardingPossibleAt(int stopPositionInPattern) {
    return boarding.get(stopPositionInPattern);
  }

  @Override
  public boolean alightingPossibleAt(int stopPositionInPattern) {
    return alighting.get(stopPositionInPattern);
  }

  @Override
  public int slackIndex() {
    return slackIndex;
  }

  @Override
  public String debugInfo() {
    return debugInfo;
  }

  @Override
  public int findStopPositionAfter(int startPos, int stopIndex) {
    return RaptorTripPattern.super.findStopPositionAfter(startPos, stopIndex);
  }

  @Override
  public int findStopPositionBefore(int startPos, int stopIndex) {
    return RaptorTripPattern.super.findStopPositionBefore(startPos, stopIndex);
  }
}