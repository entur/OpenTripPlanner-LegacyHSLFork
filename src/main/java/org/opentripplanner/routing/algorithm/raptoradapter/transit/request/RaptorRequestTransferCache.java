package org.opentripplanner.routing.algorithm.raptoradapter.transit.request;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransferIndex;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.Transfer;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.WheelchairAccessibilityPreferences;
import org.opentripplanner.routing.core.BicycleOptimizeType;
import org.opentripplanner.routing.core.RoutingContext;

public class RaptorRequestTransferCache {

  private final LoadingCache<CacheKey, RaptorTransferIndex> transferCache;

  public RaptorRequestTransferCache(int maximumSize) {
    transferCache = CacheBuilder.newBuilder().maximumSize(maximumSize).build(cacheLoader());
  }

  public LoadingCache<CacheKey, RaptorTransferIndex> getTransferCache() {
    return transferCache;
  }

  public RaptorTransferIndex get(
    List<List<Transfer>> transfersByStopIndex,
    RoutingPreferences preferences,
    StreetMode mode
  ) {
    try {
      return transferCache.get(new CacheKey(transfersByStopIndex, preferences, mode));
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to get item from transfer cache", e);
    }
  }

  private CacheLoader<CacheKey, RaptorTransferIndex> cacheLoader() {
    return new CacheLoader<>() {
      @Override
      public RaptorTransferIndex load(@javax.annotation.Nonnull CacheKey cacheKey) {
        return RaptorTransferIndex.create(
          cacheKey.transfersByStopIndex,
          cacheKey.preferences,
          cacheKey.mode
        );
      }
    };
  }

  private static class CacheKey {

    private final List<List<Transfer>> transfersByStopIndex;
    private final RoutingPreferences preferences;
    private final StreetMode mode;
    private final StreetRelevantOptions options;

    private CacheKey(
      List<List<Transfer>> transfersByStopIndex,
      RoutingPreferences preferences,
      StreetMode mode
    ) {
      this.transfersByStopIndex = transfersByStopIndex;
      this.preferences = prepareTransferPreferences(preferences.clone());
      this.mode = mode;
      this.options = new StreetRelevantOptions(preferences, mode);
    }

    @Override
    public int hashCode() {
      // transfersByStopIndex is ignored on purpose since it should not change (there is only
      // one instance per graph) and calculating the hashCode() would be expensive
      return options.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CacheKey cacheKey = (CacheKey) o;
      // transfersByStopIndex is checked using == on purpose since the instance should not change
      // (there is only one instance per graph)
      return (
        transfersByStopIndex == cacheKey.transfersByStopIndex && options.equals(cacheKey.options)
      );
    }
  }

  public static RoutingPreferences prepareTransferPreferences(RoutingPreferences preferences) {
    var bikePreferences = preferences.bike();
    var walkPreferences = preferences.walk();
    var streetPreferences = preferences.street();

    // Some values are rounded to ease caching in RaptorRequestTransferCache
    bikePreferences.setTriangleSafetyFactor(roundTo(bikePreferences.triangleSafetyFactor(), 1));
    bikePreferences.setTriangleSlopeFactor(roundTo(bikePreferences.triangleSlopeFactor(), 1));
    bikePreferences.setTriangleTimeFactor(
      1.0 - bikePreferences.triangleSafetyFactor() - bikePreferences.triangleSlopeFactor()
    );
    bikePreferences.setSwitchCost(roundTo100(bikePreferences.switchCost()));
    bikePreferences.setSwitchTime(roundTo100(bikePreferences.switchTime()));

    // it's a record (immutable) so can be safely reused
    preferences.wheelchair().setAccessibility(preferences.wheelchair().accessibility());

    walkPreferences.setSpeed(roundToHalf(walkPreferences.speed()));
    bikePreferences.setSpeed(roundToHalf(bikePreferences.speed()));

    walkPreferences.setReluctance(roundTo(walkPreferences.reluctance(), 1));
    walkPreferences.setStairsReluctance(roundTo(walkPreferences.stairsReluctance(), 1));
    walkPreferences.setStairsTimeFactor(roundTo(walkPreferences.stairsTimeFactor(), 1));
    streetPreferences.setTurnReluctance(roundTo(streetPreferences.turnReluctance(), 1));
    walkPreferences.setSafetyFactor(roundTo(walkPreferences.safetyFactor(), 1));

    streetPreferences.setElevatorBoardCost(roundTo100(streetPreferences.elevatorBoardCost()));
    streetPreferences.setElevatorBoardTime(roundTo100(streetPreferences.elevatorBoardTime()));
    streetPreferences.setElevatorHopCost(roundTo100(streetPreferences.elevatorHopCost()));
    streetPreferences.setElevatorHopTime(roundTo100(streetPreferences.elevatorHopTime()));

    return preferences;
  }

  private static double roundToHalf(double input) {
    return ((int) (input * 2 + 0.5)) / 2.0;
  }

  private static double roundTo(double input, int decimals) {
    return Math.round(input * Math.pow(10, decimals)) / Math.pow(10, decimals);
  }

  private static int roundTo100(int input) {
    if (input > 0 && input < 100) {
      return 100;
    }

    return ((input + 50) / 100) * 100;
  }

  /**
   * This contains an extract of the parameters which may influence transfers. The possible values
   * are somewhat limited by rounding in {@link Transfer#prepareTransferRoutingRequest(RoutingRequest, RoutingPreferences)}.
   * <p>
   * TODO VIA: the bikeWalking options are not used.
   * TODO VIA: Should we use StreetPreferences instead?
   */
  private static class StreetRelevantOptions {

    private final StreetMode transferMode;
    private final BicycleOptimizeType optimize;
    private final double bikeTriangleSafetyFactor;
    private final double bikeTriangleSlopeFactor;
    private final double bikeTriangleTimeFactor;
    private final WheelchairAccessibilityPreferences wheelchairAccessibility;
    private final double walkSpeed;
    private final double bikeSpeed;
    private final double walkReluctance;
    private final double stairsReluctance;
    private final double stairsTimeFactor;
    private final double turnReluctance;
    private final int elevatorBoardCost;
    private final int elevatorBoardTime;
    private final int elevatorHopCost;
    private final int elevatorHopTime;
    private final int bikeSwitchCost;
    private final int bikeSwitchTime;

    public StreetRelevantOptions(RoutingPreferences preferences, StreetMode mode) {
      this.transferMode = mode;

      this.optimize = preferences.bike().optimizeType();
      this.bikeTriangleSafetyFactor = preferences.bike().triangleSafetyFactor();
      this.bikeTriangleSlopeFactor = preferences.bike().triangleSlopeFactor();
      this.bikeTriangleTimeFactor = preferences.bike().triangleTimeFactor();
      this.bikeSwitchCost = preferences.bike().switchCost();
      this.bikeSwitchTime = preferences.bike().switchTime();

      this.wheelchairAccessibility = preferences.wheelchair().accessibility().round();

      this.walkSpeed = preferences.walk().speed();
      this.bikeSpeed = preferences.bike().speed();

      this.walkReluctance = preferences.walk().reluctance();
      this.stairsReluctance = preferences.walk().stairsReluctance();
      this.stairsTimeFactor = preferences.walk().stairsTimeFactor();
      this.turnReluctance = preferences.street().turnReluctance();

      this.elevatorBoardCost = preferences.street().elevatorBoardCost();
      this.elevatorBoardTime = preferences.street().elevatorBoardTime();
      this.elevatorHopCost = preferences.street().elevatorHopCost();
      this.elevatorHopTime = preferences.street().elevatorHopTime();
    }

    @Override
    public int hashCode() {
      return Objects.hash(
        transferMode,
        optimize,
        bikeTriangleSafetyFactor,
        bikeTriangleSlopeFactor,
        bikeTriangleTimeFactor,
        wheelchairAccessibility,
        walkSpeed,
        bikeSpeed,
        walkReluctance,
        stairsReluctance,
        turnReluctance,
        elevatorBoardCost,
        elevatorBoardTime,
        elevatorHopCost,
        elevatorHopTime,
        bikeSwitchCost,
        bikeSwitchTime,
        stairsTimeFactor
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final StreetRelevantOptions that = (StreetRelevantOptions) o;
      return (
        Double.compare(that.bikeTriangleSafetyFactor, bikeTriangleSafetyFactor) == 0 &&
        Double.compare(that.bikeTriangleSlopeFactor, bikeTriangleSlopeFactor) == 0 &&
        Double.compare(that.bikeTriangleTimeFactor, bikeTriangleTimeFactor) == 0 &&
        Double.compare(that.walkSpeed, walkSpeed) == 0 &&
        Double.compare(that.bikeSpeed, bikeSpeed) == 0 &&
        Double.compare(that.walkReluctance, walkReluctance) == 0 &&
        Double.compare(that.stairsReluctance, stairsReluctance) == 0 &&
        Double.compare(that.stairsTimeFactor, stairsTimeFactor) == 0 &&
        Double.compare(that.turnReluctance, turnReluctance) == 0 &&
        wheelchairAccessibility.equals(that.wheelchairAccessibility) &&
        elevatorBoardCost == that.elevatorBoardCost &&
        elevatorBoardTime == that.elevatorBoardTime &&
        elevatorHopCost == that.elevatorHopCost &&
        elevatorHopTime == that.elevatorHopTime &&
        bikeSwitchCost == that.bikeSwitchCost &&
        bikeSwitchTime == that.bikeSwitchTime &&
        transferMode == that.transferMode &&
        optimize == that.optimize
      );
    }
  }
}
