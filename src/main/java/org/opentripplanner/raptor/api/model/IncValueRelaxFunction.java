package org.opentripplanner.raptor.api.model;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.opentripplanner.framework.time.DurationUtils;

/**
 * This relax-function is used to relax increasing values by:
 * <pre>
 *   v' := v * ratio + slack
 * </pre>
 * The {@code ratio} is rounded of to the closest 1/16. This is done for
 * performance reasons since we then can use shift-right 4 bit to divide by 16.
 * <p>
 * "Increasing" means that {@code v} is better than {@code u}, if {@code v < u}.
 */
public final class IncValueRelaxFunction implements RelaxFunction {

  private static final int FOUR_HOURS = (int) Duration.ofHours(4).toSeconds();

  /** Max time slack is set to 1 hour */
  public static final int SLACK_TIME_MAX = FOUR_HOURS;

  /**
   * Max cost slack is set to the cost equivalent of riding transit for 1 hour.
   * Raptor cost is in centi-seconds.
   */
  public static final int SLACK_COST_MAX = FOUR_HOURS * 100;
  public static final int SLACK_MIN = 0;

  /** Keep the RATIO_RESOLUTION a power of 2 for performance reasons. */
  private static final int RATIO_RESOLUTION = 16;
  public static final double RATIO_MIN = 1.0;
  public static final double RATIO_MAX = 4.0;
  private final int ratioOf16s;
  private final int slack;

  private IncValueRelaxFunction(int normalizedRatio, int slack) {
    this.ratioOf16s = normalizedRatio;
    this.slack = slack;
  }

  private static RelaxFunction of(double ratio, int slack, int maxSlack) {
    assertRatioInRange(ratio);
    assertSlackInRange(slack, maxSlack);

    int normalizedRatio = normalizedRatio(ratio);
    if (isNormal(normalizedRatio, slack)) {
      return NORMAL;
    }
    return new IncValueRelaxFunction(normalizedRatio, slack);
  }

  /**
   * Create a relax function for increasing time values. The relax function will increase the
   * value passed into it. Can be used with {@code arrival-time} and {@code duration}, but not
   * with {@code departure-time} and {@code iteration-departure-time}.
   */
  public static RelaxFunction ofIncreasingTime(double ratio, int slack) {
    return of(ratio, slack, SLACK_TIME_MAX);
  }

  public static RelaxFunction ofCost(double ratio, int slack) {
    return of(ratio, slack, SLACK_COST_MAX);
  }

  public static RelaxFunction ofCost(double ratio) {
    return ofCost(ratio, SLACK_MIN);
  }

  public int relax(int value) {
    return ((value * ratioOf16s) / RATIO_RESOLUTION) + slack;
  }

  @Override
  public String toString() {
    return String.format(
      Locale.ROOT,
      "f(v) = %.2f*v + %s",
      ratioOf16s / 16.0,
      DurationUtils.durationToStr(slack)
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IncValueRelaxFunction that = (IncValueRelaxFunction) o;
    return ratioOf16s == that.ratioOf16s && slack == that.slack;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ratioOf16s, slack);
  }

  private static int normalizedRatio(double ratio) {
    return (int) Math.round(ratio * RATIO_RESOLUTION);
  }

  private static boolean isNormal(int normalizedRatio, int slack) {
    return normalizedRatio == RATIO_RESOLUTION && slack == 0;
  }

  private static void assertSlackInRange(int slack, int max) {
    if (slack < SLACK_MIN || slack > max) {
      throw new IllegalArgumentException(
        "Value is not in range. v=%d != [%d..%d]".formatted(slack, SLACK_MIN, max)
      );
    }
  }

  private static void assertRatioInRange(double ratio) {
    if (ratio < RATIO_MIN || ratio > RATIO_MAX) {
      throw new IllegalArgumentException(
        String.format(
          Locale.ROOT,
          "Value is not in range. v=%.2f != [%.1f..%.1f]",
          ratio,
          RATIO_MIN,
          RATIO_MAX
        )
      );
    }
  }
}
