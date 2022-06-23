package org.opentripplanner.routing.graph;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Envelope;
import org.opentripplanner.common.geometry.CompactElevationProfile;
import org.opentripplanner.common.geometry.HashGridSpatialIndex;
import org.opentripplanner.ext.flex.FlexIndex;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.model.MultiModalStation;
import org.opentripplanner.model.TimetableSnapshot;
import org.opentripplanner.model.TripIdAndServiceDate;
import org.opentripplanner.model.TripOnServiceDate;
import org.opentripplanner.model.TripPattern;
import org.opentripplanner.model.calendar.CalendarService;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.routing.vertextype.TransitStopVertex;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.GroupOfRoutes;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.organization.Operator;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.Stop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.util.OTPFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphIndex {

  private static final Logger LOG = LoggerFactory.getLogger(GraphIndex.class);

  // TODO: consistently key on model object or id string

  private final Map<Stop, TransitStopVertex> stopVertexForStop = Maps.newHashMap();
  private final HashGridSpatialIndex<TransitStopVertex> stopSpatialIndex = new HashGridSpatialIndex<>();
  private final Map<ServiceDate, TIntSet> serviceCodesRunningForDate = new HashMap<>();
  private final Map<FeedScopedId, TripOnServiceDate> tripOnServiceDateById = new HashMap<>();
  private final Map<TripIdAndServiceDate, TripOnServiceDate> tripOnServiceDateForTripAndDay = new HashMap<>();
  private final Multimap<GroupOfRoutes, Route> routesForGroupOfRoutes = ArrayListMultimap.create();
  private final Map<FeedScopedId, GroupOfRoutes> groupOfRoutesForId = new HashMap<>();
  private FlexIndex flexIndex = null;

  public GraphIndex(Graph graph) {
    LOG.info("GraphIndex init...");
    CompactElevationProfile.setDistanceBetweenSamplesM(graph.getDistanceBetweenElevationSamples());

    /* We will keep a separate set of all vertices in case some have the same label.
     * Maybe we should just guarantee unique labels. */
    for (Vertex vertex : graph.getVertices()) {
      if (vertex instanceof TransitStopVertex) {
        TransitStopVertex stopVertex = (TransitStopVertex) vertex;
        Stop stop = stopVertex.getStop();
        stopForId.put(stop.getId(), stop);
        stopVertexForStop.put(stop, stopVertex);
      }
    }
    for (TransitStopVertex stopVertex : stopVertexForStop.values()) {
      Envelope envelope = new Envelope(stopVertex.getCoordinate());
      stopSpatialIndex.insert(envelope, stopVertex);
    }
    for (TripPattern pattern : graph.tripPatternForId.values()) {
      patternsForFeedId.put(pattern.getFeedId(), pattern);
      patternsForRoute.put(pattern.getRoute(), pattern);
      pattern
        .scheduledTripsAsStream()
        .forEach(trip -> {
          patternForTrip.put(trip, pattern);
          tripForId.put(trip.getId(), trip);
        });
      for (StopLocation stop : pattern.getStops()) {
        patternsForStopId.put(stop, pattern);
      }
    }
    for (Route route : patternsForRoute.asMap().keySet()) {
      routeForId.put(route.getId(), route);
      for (GroupOfRoutes groupOfRoutes : route.getGroupsOfRoutes()) {
        routesForGroupOfRoutes.put(groupOfRoutes, route);
      }
    }
    for (GroupOfRoutes groupOfRoutes : routesForGroupOfRoutes.keySet()) {
      groupOfRoutesForId.put(groupOfRoutes.getId(), groupOfRoutes);
    }
    for (MultiModalStation multiModalStation : graph.multiModalStationById.values()) {
      for (Station childStation : multiModalStation.getChildStations()) {
        multiModalStationForStations.put(childStation, multiModalStation);
      }
    }

    for (TripOnServiceDate tripOnServiceDate : graph.tripOnServiceDates.values()) {
      tripOnServiceDateById.put(tripOnServiceDate.getId(), tripOnServiceDate);
      tripOnServiceDateForTripAndDay.put(
        new TripIdAndServiceDate(
          tripOnServiceDate.getTrip().getId(),
          tripOnServiceDate.getServiceDate()
        ),
        tripOnServiceDate
      );
    }

    initalizeServiceCodesForDate(graph);

    if (OTPFeature.FlexRouting.isOn()) {
      flexIndex = new FlexIndex(graph);
      for (Route route : flexIndex.routeById.values()) {
        routeForId.put(route.getId(), route);
      }
      for (FlexTrip flexTrip : flexIndex.tripById.values()) {
        tripForId.put(flexTrip.getId(), flexTrip.getTrip());
        flexTrip.getStops().stream().forEach(stop -> stopForId.put(stop.getId(), stop));
      }
    }

    LOG.info("GraphIndex init complete.");
  }



  public Map<Stop, TransitStopVertex> getStopVertexForStop() {
    return stopVertexForStop;
  }



  public Multimap<GroupOfRoutes, Route> getRoutesForGroupOfRoutes() {
    return routesForGroupOfRoutes;
  }

  public Map<FeedScopedId, GroupOfRoutes> getGroupOfRoutesForId() {
    return groupOfRoutesForId;
  }

  public HashGridSpatialIndex<TransitStopVertex> getStopSpatialIndex() {
    return stopSpatialIndex;
  }



}
