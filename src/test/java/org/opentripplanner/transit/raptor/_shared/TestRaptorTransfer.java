package org.opentripplanner.transit.raptor._shared;

import org.opentripplanner.transit.raptor.api.transit.RaptorTransfer;


/**
 * Simple implementation for {@link RaptorTransfer} for use in unit-tests.
 */
public class TestRaptorTransfer implements RaptorTransfer {
  private final int stop;
  private final int duration;

  public TestRaptorTransfer(int stop, int duration) {
    this.stop = stop;
    this.duration = duration;
  }
  @Override public int stop() { return stop; }
  @Override public int durationInSeconds() { return duration; }
}
