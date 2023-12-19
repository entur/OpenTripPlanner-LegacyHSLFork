package org.opentripplanner.ext.sorlandsbanen;

import java.util.Collection;
import java.util.function.Function;
import org.opentripplanner.framework.geometry.SphericalDistanceLibrary;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequest;
import org.opentripplanner.raptor.api.request.SearchParams;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorRouter;
import org.opentripplanner.raptor.spi.RaptorTransitDataProvider;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitLayer;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.FactorStrategy;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.IndexBasedFactorStrategy;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.StopLocation;

public class EnturHackSorlandsBanen {

  private static final double SOUTH_BOARDER_LIMIT = 59.1;
  private static final int MIN_DISTANCE_LIMIT = 120_000;

  public static <T extends RaptorTripSchedule> boolean match(double extraSearchCoachReluctance) {
    return extraSearchCoachReluctance > 0.1;
  }

  public static <T extends RaptorTripSchedule> RaptorRouter<T> router(
    RaptorRequest<T> mcRequest,
    RaptorTransitDataProvider<T> transitData,
    Function<RaptorTransitDataProvider<T>, RaptorRouter<T>> raptorRouterFactory
  ) {
    //noinspection unchecked
    RaptorTransitDataProvider<T> altTransitData = (RaptorTransitDataProvider<T>) (
      (RaptorRoutingRequestTransitData) transitData
    ).enturHackSorlandsbanen(mapFactors(mcRequest.extraSearchCoachReluctance));

    return new ConcurrentCompositeWorker<>(
      raptorRouterFactory.apply(transitData),
      raptorRouterFactory.apply(altTransitData)
    );
  }

  public static <T extends RaptorTripSchedule> RaptorRequest<T> enableHack(
    RaptorRequest<T> raptorRequest,
    RouteRequest request,
    TransitLayer transitLayer
  ) {
    if (request.preferences().transit().extraSearchCoachReluctance() < 0.1) {
      return raptorRequest;
    }

    SearchParams params = raptorRequest.searchParams();

    WgsCoordinate from = findStopCoordinate(request.from(), params.accessPaths(), transitLayer);
    WgsCoordinate to = findStopCoordinate(request.to(), params.egressPaths(), transitLayer);

    if (from.latitude() > SOUTH_BOARDER_LIMIT && to.latitude() > SOUTH_BOARDER_LIMIT) {
      return raptorRequest;
    }

    double distanceMeters = SphericalDistanceLibrary.distance(
      from.latitude(),
      from.longitude(),
      to.latitude(),
      to.longitude()
    );

    if (distanceMeters < MIN_DISTANCE_LIMIT) {
      return raptorRequest;
    }

    raptorRequest.extraSearchCoachReluctance =
      request.preferences().transit().extraSearchCoachReluctance();
    return raptorRequest;
  }

  /* private methods */

  private static Function<FactorStrategy, FactorStrategy> mapFactors(
    final double extraSearchCoachReluctance
  ) {
    return (FactorStrategy originalFactors) -> {
      int[] modeReluctance = new int[TransitMode.values().length];
      for (TransitMode mode : TransitMode.values()) {
        int index = mode.ordinal();
        int originalFactor = originalFactors.factor(index);
        modeReluctance[index] =
          mode == TransitMode.COACH
            ? (int) (extraSearchCoachReluctance * originalFactor + 0.5)
            : originalFactor;
      }
      return new IndexBasedFactorStrategy(modeReluctance);
    };
  }

  /**
   * Find a coordinate matching the given location, in order:
   *  - First return the coordinate of the location if it exists.
   *  - Then loop through the access/egress stops and try to find the
   *    stop or station given by the location id, return the stop/station coordinate.
   *  - Return the fist stop in the access/egress list coordinate.
   */
  @SuppressWarnings("ConstantConditions")
  private static WgsCoordinate findStopCoordinate(
    GenericLocation location,
    Collection<RaptorAccessEgress> accessEgress,
    TransitLayer transitLayer
  ) {
    if (location.lat != null) {
      return new WgsCoordinate(location.lat, location.lng);
    }

    StopLocation firstStop = null;
    for (RaptorAccessEgress it : accessEgress) {
      StopLocation stop = transitLayer.getStopByIndex(it.stop());
      if (stop.getId().equals(location.stopId)) {
        return stop.getCoordinate();
      }
      if (idIsParentStation(stop, location.stopId)) {
        return stop.getParentStation().getCoordinate();
      }
      if (firstStop == null) {
        firstStop = stop;
      }
    }
    return firstStop.getCoordinate();
  }

  private static boolean idIsParentStation(StopLocation stop, FeedScopedId pId) {
    return stop.getParentStation() != null && stop.getParentStation().getId().equals(pId);
  }
}
