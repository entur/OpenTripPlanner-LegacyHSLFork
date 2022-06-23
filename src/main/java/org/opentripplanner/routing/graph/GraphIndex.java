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

    LOG.info("GraphIndex init complete.");
  }

  public Map<Stop, TransitStopVertex> getStopVertexForStop() {
    return stopVertexForStop;
  }

  public HashGridSpatialIndex<TransitStopVertex> getStopSpatialIndex() {
    return stopSpatialIndex;
  }



}
