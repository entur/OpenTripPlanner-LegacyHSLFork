/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype.factory;

import com.beust.jcommander.internal.Maps;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.apache.commons.math3.util.FastMath;
import org.opentripplanner.model.*;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.model.P2;
import org.opentripplanner.graph_builder.annotation.BogusShapeDistanceTraveled;
import org.opentripplanner.graph_builder.annotation.BogusShapeGeometry;
import org.opentripplanner.graph_builder.annotation.BogusShapeGeometryCaught;
import org.opentripplanner.graph_builder.annotation.NonStationParentStation;
import org.opentripplanner.graph_builder.module.GtfsFeedId;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.car_park.CarPark;
import org.opentripplanner.routing.car_park.CarParkService;
import org.opentripplanner.routing.core.StopTransfer;
import org.opentripplanner.routing.core.TransferTable;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.FreeEdge;
import org.opentripplanner.routing.edgetype.ParkAndRideEdge;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.PatternInterlineDwell;
import org.opentripplanner.routing.edgetype.PreAlightEdge;
import org.opentripplanner.routing.edgetype.PreBoardEdge;
import org.opentripplanner.routing.edgetype.StationStopEdge;
import org.opentripplanner.routing.edgetype.TimedTransferEdge;
import org.opentripplanner.routing.edgetype.Timetable;
import org.opentripplanner.routing.edgetype.TransferEdge;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.impl.DefaultFareServiceFactory;
import org.opentripplanner.routing.impl.OnBoardDepartServiceImpl;
import org.opentripplanner.routing.services.FareService;
import org.opentripplanner.routing.services.FareServiceFactory;
import org.opentripplanner.routing.services.OnBoardDepartService;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStation;
import org.opentripplanner.routing.vertextype.TransitStationStop;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.routing.vertextype.TransitStopArrive;
import org.opentripplanner.routing.vertextype.TransitStopDepart;
import org.opentripplanner.util.NonLocalizedString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

// Filtering out (removing) stoptimes from a trip forces us to either have two copies of that list,
// or do all the steps within one loop over trips. It would be clearer if there were multiple loops over the trips.

/** A wrapper class for Trips that allows them to be sorted. */
class InterliningTrip  implements Comparable<InterliningTrip> {
    public Trip trip;
    public StopTime firstStopTime;
    public StopTime lastStopTime;
    TripPattern tripPattern;

    InterliningTrip(Trip trip, List<StopTime> stopTimes, TripPattern tripPattern) {
        this.trip = trip;
        this.firstStopTime = stopTimes.get(0);
        this.lastStopTime = stopTimes.get(stopTimes.size() - 1);
        this.tripPattern = tripPattern;
    }

    public int getPatternIndex() {
        return tripPattern.getTripIndex(trip);
    }
    
    @Override
    public int compareTo(InterliningTrip o) {
        return firstStopTime.getArrivalTime() - o.firstStopTime.getArrivalTime();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof InterliningTrip) {
            return compareTo((InterliningTrip) o) == 0;
        }
        return false;
    }
    
}

/** 
 * This compound key object is used when grouping interlining trips together by (serviceId, blockId). 
 */
class BlockIdAndServiceId {
    public String blockId;
    public AgencyAndId serviceId;

    BlockIdAndServiceId(Trip trip) {
        this.blockId = trip.getBlockId();
        this.serviceId = trip.getServiceId();
    }
    
    public boolean equals(Object o) {
        if (o instanceof BlockIdAndServiceId) {
            BlockIdAndServiceId other = ((BlockIdAndServiceId) o);
            return other.blockId.equals(blockId) && other.serviceId.equals(serviceId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return blockId.hashCode() * 31 + serviceId.hashCode();
    }
}

/* TODO Move this stuff into the geometry library */
class IndexedLineSegment {
    private static final double RADIUS = SphericalDistanceLibrary.RADIUS_OF_EARTH_IN_M;
    int index;
    Coordinate start;
    Coordinate end;
    private double lineLength;

    public IndexedLineSegment(int index, Coordinate start, Coordinate end) {
        this.index = index;
        this.start = start;
        this.end = end;
        this.lineLength = SphericalDistanceLibrary.fastDistance(start, end);
    }

    // in radians
    static double bearing(Coordinate c1, Coordinate c2) {
        double deltaLon = (c2.x - c1.x) * FastMath.PI / 180;
        double lat1Radians = c1.y * FastMath.PI / 180;
        double lat2Radians = c2.y * FastMath.PI / 180;
        double y = FastMath.sin(deltaLon) * FastMath.cos(lat2Radians);
        double x = FastMath.cos(lat1Radians)*FastMath.sin(lat2Radians) -
                FastMath.sin(lat1Radians)*FastMath.cos(lat2Radians)*FastMath.cos(deltaLon);
        return FastMath.atan2(y, x);
    }

    double crossTrackError(Coordinate coord) {
        double distanceFromStart = SphericalDistanceLibrary.fastDistance(start, coord);
        double bearingToCoord = bearing(start, coord);
        double bearingToEnd = bearing(start, end);
        return FastMath.asin(FastMath.sin(distanceFromStart / RADIUS)
            * FastMath.sin(bearingToCoord - bearingToEnd))
            * RADIUS;
    }

    double distance(Coordinate coord) {
        double cte = crossTrackError(coord);
        double atd = alongTrackDistance(coord, cte);
        double inverseAtd = inverseAlongTrackDistance(coord, -cte);
        double distanceToStart = SphericalDistanceLibrary.fastDistance(coord, start);
        double distanceToEnd = SphericalDistanceLibrary.fastDistance(coord, end);

        if (distanceToStart < distanceToEnd) {
            //we might be behind the line start
            if (inverseAtd > lineLength) {
                //we are behind line start
                return distanceToStart;
            } else {
                //we are within line
                return Math.abs(cte);
            }
        } else {
            //we might be after line end
            if (atd > lineLength) {
                //we are behind line end, so we that's the nearest point
                return distanceToEnd;
            } else {
                //we are within line
                return Math.abs(cte);
            }
        }
    }

    private double inverseAlongTrackDistance(Coordinate coord, double inverseCrossTrackError) {
        double distanceFromEnd = SphericalDistanceLibrary.fastDistance(end, coord);
        double alongTrackDistance = FastMath.acos(FastMath.cos(distanceFromEnd / RADIUS)
            / FastMath.cos(inverseCrossTrackError / RADIUS))
            * RADIUS;
        return alongTrackDistance;
    }

    public double fraction(Coordinate coord) {
        double cte = crossTrackError(coord);
        double distanceToStart = SphericalDistanceLibrary.fastDistance(coord, start);
        double distanceToEnd = SphericalDistanceLibrary.fastDistance(coord, end);

        if (cte < distanceToStart && cte < distanceToEnd) {
            double atd = alongTrackDistance(coord, cte);
            return atd / lineLength;
        } else {
            if (distanceToStart < distanceToEnd) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    private double alongTrackDistance(Coordinate coord, double crossTrackError) {
        double distanceFromStart = SphericalDistanceLibrary.fastDistance(start, coord);
        double alongTrackDistance = FastMath.acos(FastMath.cos(distanceFromStart / RADIUS)
            / FastMath.cos(crossTrackError / RADIUS))
            * RADIUS;
        return alongTrackDistance;
    }
}

class IndexedLineSegmentComparator implements Comparator<IndexedLineSegment> {

    private Coordinate coord;

    public IndexedLineSegmentComparator(Coordinate coord) {
        this.coord = coord;
    }

    @Override
    public int compare(IndexedLineSegment a, IndexedLineSegment b) {
        return (int) FastMath.signum(a.distance(coord) - b.distance(coord));
    }
}

/**
 * Generates a set of edges from GTFS.
 */
public class PatternHopFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PatternHopFactory.class);

    private static GeometryFactory _geometryFactory = GeometryUtils.getGeometryFactory();

    private GtfsFeedId _feedId;

    private OtpTransitService _transitService;

    private Map<ShapeSegmentKey, LineString> _geometriesByShapeSegmentKey = new HashMap<ShapeSegmentKey, LineString>();

    private Map<AgencyAndId, LineString> _geometriesByShapeId = new HashMap<AgencyAndId, LineString>();

    private Map<AgencyAndId, double[]> _distancesByShapeId = new HashMap<AgencyAndId, double[]>();

    private Map<String, Geometry> _areasById = new HashMap<>();

    private FareServiceFactory fareServiceFactory;

    private GtfsStopContext context = new GtfsStopContext();

    // the location types for transfers.txt
    public static final int STOP_LOCATION_TYPE = 0;
    public static final int PARENT_STATION_LOCATION_TYPE = 1;

    public int subwayAccessTime = 0;

    private double maxStopToShapeSnapDistance = 150;

    public int maxInterlineDistance = 200;


    public PatternHopFactory(GtfsContext context) {
        this._feedId = context.getFeedId();
        this._transitService = context.getTransitBuilder().build();
    }
    
    public PatternHopFactory() {
        this._feedId = null;
        this._transitService = null;
    }

    public PatternHopFactory(
            GtfsFeedId feedId, OtpTransitService transitService, FareServiceFactory fareServiceFactory,
            double maxStopToShapeSnapDistance, int subwayAccessTime, int maxInterlineDistance
    ) {

        this._feedId = feedId;
        this._transitService = transitService;
        this.fareServiceFactory = fareServiceFactory;
        this.maxStopToShapeSnapDistance = maxStopToShapeSnapDistance;
        this.subwayAccessTime = subwayAccessTime;
        this.maxInterlineDistance = maxInterlineDistance;
    }

    /** Generate the edges. Assumes that there are already vertices in the graph for the stops. */
    public void run(Graph graph) {
        if (fareServiceFactory == null) {
            fareServiceFactory = new DefaultFareServiceFactory();
        }
        fareServiceFactory.processGtfs(_transitService);

        // TODO: Why are we loading stops? The Javadoc above says this method assumes stops are aleady loaded.
        loadStops(graph);
        loadPathways(graph);
        loadFeedInfo(graph);
        loadAgencies(graph);
        loadOperators(graph);

        // TODO: Why is there cached "data", and why are we clearing it? Due to a general lack of comments, I have no idea.
        // Perhaps it is to allow name collisions with previously loaded feeds.
        clearCachedData();

        loadAreaMap();
        loadAreasIntoGraph(graph);

        /* Assign 0-based numeric codes to all GTFS service IDs. */
        for (AgencyAndId serviceId : _transitService.getAllServiceIds()) {
            // TODO: FIX Service code collision for multiple feeds.
            graph.serviceCodes.put(serviceId, graph.serviceCodes.size());
        }

        LOG.debug("building hops from trips");

        /* The hops don't actually exist when we build their geometries, but we have to build their geometries
         * below, before we throw away the modified stopTimes, saving only the tripTimes (which don't have enough
         * information to build a geometry). So we keep them here.
         *
         *  A trip pattern actually does not have a single geometry, but one per hop, so we store an array.
         *  FIXME _why_ doesn't it have a single geometry?
         */
        Map<TripPattern, LineString[]> geometriesByTripPattern = Maps.newHashMap();

        Collection<TripPattern> tripPatterns = _transitService.getTripPatterns();

        /* Loop over all new TripPatterns, creating edges, setting the service codes and geometries, etc. */
        for (TripPattern tripPattern : tripPatterns) {
            for (Trip trip : tripPattern.getTrips()) {
                // create geometries if they aren't already created
                // note that this is not only done on new trip patterns, because it is possible that
                // there would be a trip pattern with no geometry yet because it failed some of these tests
                if (!geometriesByTripPattern.containsKey(tripPattern) && trip.getShapeId() != null
                        && trip.getShapeId().getId() != null && !trip.getShapeId().getId().equals("")) {
                    // save the geometry to later be applied to the hops
                    geometriesByTripPattern.put(tripPattern,
                            createGeometry(graph, trip, _transitService.getStopTimesForTrip(trip)));
                }
            }
        }

        /* Generate unique human-readable names for all the TableTripPatterns. */
        TripPattern.generateUniqueNames(tripPatterns, graph);

        /* Generate unique short IDs for all the TableTripPatterns. */
        TripPattern.generateUniqueIds(tripPatterns);

        /* Loop over all new TripPatterns, creating edges, setting the service codes and geometries, etc. */
        for (TripPattern tripPattern : tripPatterns) {
            tripPattern.makePatternVerticesAndEdges(graph, context.stationStopNodes);
            // Add the geometries to the hop edges.
            LineString[] geom = geometriesByTripPattern.get(tripPattern);
            if (geom != null) {
                for (int i = 0; i < tripPattern.hopEdges.length; i++) {
                    tripPattern.hopEdges[i].setGeometry(geom[i]);
                }
            }
            tripPattern.setServiceCodes(graph.serviceCodes); // TODO this could be more elegant

            /* Iterate over all stops in this pattern recording mode information. */
            TraverseMode mode = tripPattern.mode;
            for (TransitStop tstop : tripPattern.stopVertices) {
                tstop.addMode(mode);
                if (mode == TraverseMode.SUBWAY) {
                    tstop.setStreetToStopTime(subwayAccessTime);
                }
                graph.addTransitMode(mode);
            }

        }

        /* Identify interlined trips and create the necessary edges. */
        interline(tripPatterns, graph);

        /* Interpret the transfers explicitly defined in transfers.txt. */
        loadTransfers(graph);

        /* Is this the wrong place to do this? It should be done on all feeds at once, or at deserialization. */
        // it is already done at deserialization, but standalone mode allows using graphs without serializing them.
        for (TripPattern tableTripPattern : tripPatterns) {
            tableTripPattern.scheduledTimetable.finish();
        }

        graph.setNoticeMap(_transitService.getNoticeById());
        for (NoticeAssignment noticeAssignment : _transitService.getNoticeAssignmentById().values()) {
            Notice notice = _transitService.getNoticeById().get(noticeAssignment.getNoticeId());
            if (graph.getNoticeAssignmentMap().containsKey(noticeAssignment.getElementId())) {
                graph.getNoticeAssignmentMap().get(noticeAssignment.getElementId()).add(notice);
            } else {
                graph.getNoticeAssignmentMap()
                        .put(noticeAssignment.getElementId(), new ArrayList(Arrays.asList(notice)));
            }
        }
        
        clearCachedData(); // eh?
        graph.putService(FareService.class, fareServiceFactory.makeFareService());
        graph.putService(OnBoardDepartService.class, new OnBoardDepartServiceImpl());
    }

    /**
     * Identify interlined trips (where a physical vehicle continues on to another logical trip)
     * and update the TripPatterns accordingly. This must be called after all the pattern edges and vertices
     * are already created, because it creates interline dwell edges between existing pattern arrive/depart vertices.
     */
    private void interline(Collection<TripPattern> tripPatterns, Graph graph) {

        /* Record which Pattern each interlined TripTimes belongs to. */
        Map<TripTimes, TripPattern> patternForTripTimes = Maps.newHashMap();

        /* TripTimes grouped by the block ID and service ID of their trips. Must be a ListMultimap to allow sorting. */
        ListMultimap<BlockIdAndServiceId, TripTimes> tripTimesForBlock = ArrayListMultimap.create();

        LOG.info("Finding interlining trips based on block IDs.");
        for (TripPattern pattern : tripPatterns) {
            Timetable timetable = pattern.scheduledTimetable;
            /* TODO: Block semantics seem undefined for frequency trips, so skip them? */
            for (TripTimes tripTimes : timetable.tripTimes) {
                Trip trip = tripTimes.trip;
                if (!Strings.isNullOrEmpty(trip.getBlockId())) {
                    tripTimesForBlock.put(new BlockIdAndServiceId(trip), tripTimes);
                    // For space efficiency, only record times that are part of a block.
                    patternForTripTimes.put(tripTimes, pattern);
                }
            }
        }

        // Associate pairs of TripPatterns with lists of trips that continue from one pattern to the other.
        Multimap<P2<TripPattern>, P2<Trip>> interlines = ArrayListMultimap.create();

        // Sort trips within each block by first departure time, then iterate over trips in this block and service,
        // linking them. Has no effect on single-trip blocks.
        SERVICE_BLOCK:
        for (BlockIdAndServiceId block : tripTimesForBlock.keySet()) {
            List<TripTimes> blockTripTimes = tripTimesForBlock.get(block);
            Collections.sort(blockTripTimes);
            TripTimes prev = null;
            for (TripTimes curr : blockTripTimes) {
                if (prev != null) {
                    if (prev.getDepartureTime(prev.getNumStops() - 1) > curr.getArrivalTime(0)) {
                        LOG.error(
                                "Trip times within block {} are not increasing on service {} after trip {}.",
                                block.blockId, block.serviceId, prev.trip.getId());
                        continue SERVICE_BLOCK;
                    }
                    TripPattern prevPattern = patternForTripTimes.get(prev);
                    TripPattern currPattern = patternForTripTimes.get(curr);
                    Stop fromStop = prevPattern.getStop(prevPattern.getStops().size() - 1);
                    Stop toStop = currPattern.getStop(0);
                    double teleportationDistance = SphericalDistanceLibrary
                            .fastDistance(fromStop.getLat(), fromStop.getLon(), toStop.getLat(),
                                    toStop.getLon());
                    if (teleportationDistance > maxInterlineDistance) {
                        // FIXME Trimet data contains a lot of these -- in their data, two trips sharing a block ID just
                        // means that they are served by the same vehicle, not that interlining is automatically allowed.
                        // see #1654
                        // LOG.error(graph.addBuilderAnnotation(new InterliningTeleport(prev.trip, block.blockId, (int)teleportationDistance)));
                        // Only skip this particular interline edge; there may be other valid ones in the block.
                    } else {
                        interlines.put(new P2<TripPattern>(prevPattern, currPattern),
                                new P2<Trip>(prev.trip, curr.trip));
                    }
                }
                prev = curr;
            }
        }

        // Create the PatternInterlineDwell edges linking together TripPatterns.
        // All the pattern vertices and edges must already have been created.
        for (P2<TripPattern> patterns : interlines.keySet()) {
            TripPattern prevPattern = patterns.first;
            TripPattern nextPattern = patterns.second;
            // This is a single (uni-directional) edge which may be traversed forward and backward.
            PatternInterlineDwell edge = new PatternInterlineDwell(prevPattern, nextPattern);
            for (P2<Trip> trips : interlines.get(patterns)) {
                edge.add(trips.first, trips.second);
            }
        }
        LOG.info("Done finding interlining trips and creating the corresponding edges.");
    }

    /**
     * Creates a set of geometries for a single trip, considering the GTFS shapes.txt,
     * The geometry is broken down into one geometry per inter-stop segment ("hop"). We also need a shape for the entire
     * trip and tripPattern, but given the complexity of the existing code for generating hop geometries, we will create
     * the full-trip geometry by simply concatenating the hop geometries.
     *
     * This geometry will in fact be used for an entire set of trips in a trip pattern. Technically one of the trips
     * with exactly the same sequence of stops could follow a different route on the streets, but that's very uncommon.
     */
    private LineString[] createGeometry(Graph graph, Trip trip, List<StopTime> stopTimes) {
        AgencyAndId shapeId = trip.getShapeId();

        // One less geometry than stoptime as array indexes represetn hops not stops (fencepost problem).
        LineString[] geoms = new LineString[stopTimes.size() - 1];

        // Detect presence or absence of shape_dist_traveled on a per-trip basis
        StopTime st0 = stopTimes.get(0);
        boolean hasShapeDist = st0.isShapeDistTraveledSet();
        if (hasShapeDist) {
            // this trip has shape_dist in stop_times
            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                geoms[i] = getHopGeometryViaShapeDistTraveled(graph, shapeId, st0, st1);
            }
            return geoms;
        }
        LineString shape = getLineStringForShapeId(shapeId);
        if (shape == null) {
            // this trip has a shape_id, but no such shape exists, and no shape_dist in stop_times
            // create straight line segments between stops for each hop
            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                LineString geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
                geoms[i] = geometry;
            }
            return geoms;
        }
        // This trip does not have shape_dist in stop_times, but does have an associated shape.
        ArrayList<IndexedLineSegment> segments = new ArrayList<IndexedLineSegment>();
        for (int i = 0 ; i < shape.getNumPoints() - 1; ++i) {
            segments.add(new IndexedLineSegment(i, shape.getCoordinateN(i), shape.getCoordinateN(i + 1)));
        }
        // Find possible segment matches for each stop.
        List<List<IndexedLineSegment>> possibleSegmentsForStop = new ArrayList<List<IndexedLineSegment>>();
        int minSegmentIndex = 0;
        for (int i = 0; i < stopTimes.size() ; ++i) {
            Stop stop = stopTimes.get(i).getStop();
            Coordinate coord = new Coordinate(stop.getLon(), stop.getLat());
            List<IndexedLineSegment> stopSegments = new ArrayList<IndexedLineSegment>();
            double bestDistance = Double.MAX_VALUE;
            IndexedLineSegment bestSegment = null;
            int maxSegmentIndex = -1;
            int index = -1;
            int minSegmentIndexForThisStop = -1;
            for (IndexedLineSegment segment : segments) {
                index++;
                if (segment.index < minSegmentIndex) {
                    continue;
                }
                double distance = segment.distance(coord);
                if (distance < maxStopToShapeSnapDistance) {
                    stopSegments.add(segment);
                    maxSegmentIndex = index;
                    if (minSegmentIndexForThisStop == -1)
                        minSegmentIndexForThisStop = index;
                } else if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSegment = segment;
                    if (maxSegmentIndex != -1) {
                        maxSegmentIndex = index;
                    }
                }
            }
            if (stopSegments.size() == 0) {
                //no segments within 150m
                //fall back to nearest segment
                stopSegments.add(bestSegment);
                minSegmentIndex = bestSegment.index;
            } else {
                minSegmentIndex = minSegmentIndexForThisStop;
                Collections.sort(stopSegments, new IndexedLineSegmentComparator(coord));
            }

            for (int j = i - 1; j >= 0; j --) {
                for (Iterator<IndexedLineSegment> it = possibleSegmentsForStop.get(j).iterator(); it.hasNext(); ) {
                    IndexedLineSegment segment = it.next();
                    if (segment.index > maxSegmentIndex) {
                        it.remove();
                    }
                }
            }
            possibleSegmentsForStop.add(stopSegments);
        }

        List<LinearLocation> locations = getStopLocations(possibleSegmentsForStop, stopTimes, 0, -1);

        if (locations == null) {
            // this only happens on shape which have points very far from
            // their stop sequence. So we'll fall back to trivial stop-to-stop
            // linking, even though theoretically we could do better.

            for (int i = 0; i < stopTimes.size() - 1; ++i) {
                st0 = stopTimes.get(i);
                StopTime st1 = stopTimes.get(i + 1);
                LineString geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
                geoms[i] = geometry;
                //this warning is not strictly correct, but will do
                graph.addBuilderAnnotation(new BogusShapeGeometryCaught(shapeId, st0, st1));
            }
            return geoms;
        }

        Iterator<LinearLocation> locationIt = locations.iterator();
        LinearLocation endLocation = locationIt.next();
        double distanceSoFar = 0;
        int last = 0;
        for (int i = 0; i < stopTimes.size() - 1; ++i) {
            LinearLocation startLocation = endLocation;
            endLocation = locationIt.next();

            //convert from LinearLocation to distance
            //advance distanceSoFar up to start of segment containing startLocation;
            //it does not matter at all if this is accurate so long as it is consistent
            for (int j = last; j < startLocation.getSegmentIndex(); ++j) {
                Coordinate from = shape.getCoordinateN(j);
                Coordinate to = shape.getCoordinateN(j + 1);
                double xd = from.x - to.x;
                double yd = from.y - to.y;
                distanceSoFar += FastMath.sqrt(xd * xd + yd * yd);
            }
            last = startLocation.getSegmentIndex();

            double startIndex = distanceSoFar + startLocation.getSegmentFraction() * startLocation.getSegmentLength(shape);
            //advance distanceSoFar up to start of segment containing endLocation
            for (int j = last; j < endLocation.getSegmentIndex(); ++j) {
                Coordinate from = shape.getCoordinateN(j);
                Coordinate to = shape.getCoordinateN(j + 1);
                double xd = from.x - to.x;
                double yd = from.y - to.y;
                distanceSoFar += FastMath.sqrt(xd * xd + yd * yd);
            }
            last = startLocation.getSegmentIndex();
            double endIndex = distanceSoFar + endLocation.getSegmentFraction() * endLocation.getSegmentLength(shape);

            ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startIndex, endIndex);
            LineString geometry = _geometriesByShapeSegmentKey.get(key);

            if (geometry == null) {
                LocationIndexedLine locationIndexed = new LocationIndexedLine(shape);
                geometry = (LineString) locationIndexed.extractLine(startLocation, endLocation);

                // Pack the resulting line string
                CoordinateSequence sequence = new PackedCoordinateSequence.Double(geometry
                        .getCoordinates(), 2);
                geometry = _geometryFactory.createLineString(sequence);
            }
            geoms[i] = geometry;
        }

        return geoms;
    }

    /**
     * Find a consistent, increasing list of LinearLocations along a shape for a set of stops.
     * Handles loops routes.
     */
    private List<LinearLocation> getStopLocations(List<List<IndexedLineSegment>> possibleSegmentsForStop,
            List<StopTime> stopTimes, int index, int prevSegmentIndex) {

        if (index == stopTimes.size()) {
            return new LinkedList<LinearLocation>();
        }

        StopTime st = stopTimes.get(index);
        Stop stop = st.getStop();
        Coordinate stopCoord = new Coordinate(stop.getLon(), stop.getLat());

        for (IndexedLineSegment segment : possibleSegmentsForStop.get(index)) {
            if (segment.index < prevSegmentIndex) {
                //can't go backwards along line
                continue;
            }
            List<LinearLocation> locations = getStopLocations(possibleSegmentsForStop, stopTimes, index + 1, segment.index);
            if (locations != null) {
                LinearLocation location = new LinearLocation(0, segment.index, segment.fraction(stopCoord));
                locations.add(0, location);
                return locations; //we found one!
            }
        }

        return null;
    }

    private void loadAgencies(Graph graph) {
        for (Agency agency : _transitService.getAllAgencies()) {
            graph.addAgency(_feedId.getId(), agency);
        }
    }

    private void loadOperators(Graph graph) {
        graph.getOperators().addAll(_transitService.getAllOperators());
    }

    private void loadFeedInfo(Graph graph) {
        for (FeedInfo info : _transitService.getAllFeedInfos()) {
            graph.addFeedInfo(info);
        }
    }

    private void loadPathways(Graph graph) {
        for (Pathway pathway : _transitService.getAllPathways()) {
            Vertex fromVertex = context.stationStopNodes.get(pathway.getFromStop());
            Vertex toVertex = context.stationStopNodes.get(pathway.getToStop());
            if (pathway.isWheelchairTraversalTimeSet()) {
                new PathwayEdge(fromVertex, toVertex, pathway.getTraversalTime(), pathway.getWheelchairTraversalTime());
            } else {
                new PathwayEdge(fromVertex, toVertex, pathway.getTraversalTime());
            }
        }
    }

    private void loadStops(Graph graph) {
        for (Stop stop : _transitService.getAllStops()) {
            if (context.stops.contains(stop.getId())) {
                LOG.error("Skipping stop {} because we already loaded an identical ID.", stop.getId());
                continue;
            }
            context.stops.add(stop.getId());

            int locationType = stop.getLocationType();

            //add a vertex representing the stop
            if (locationType == 1) {
                context.stationStopNodes.put(stop, new TransitStation(graph, stop));
            } else {
                TransitStop stopVertex = new TransitStop(graph, stop);
                context.stationStopNodes.put(stop, stopVertex);
                if (locationType != 2) {
                    // Add a vertex representing arriving at the stop
                    TransitStopArrive arrive = new TransitStopArrive(graph, stop, stopVertex);
                    // FIXME no need for this context anymore, we just put references to these nodes in the stop vertices themselves.
                    context.stopArriveNodes.put(stop, arrive);
                    stopVertex.arriveVertex = arrive;

                    // Add a vertex representing departing from the stop
                    TransitStopDepart depart = new TransitStopDepart(graph, stop, stopVertex);
                    // FIXME no need for this context anymore, we just put references to these nodes in the stop vertices themselves.
                    context.stopDepartNodes.put(stop, depart);
                    stopVertex.departVertex = depart;

                    // Add edges from arrive to stop and stop to depart
                    new PreAlightEdge(arrive, stopVertex);
                    new PreBoardEdge(stopVertex, depart);
                }
            }
        }
    }

    private void clearCachedData() {
        LOG.debug("shapes=" + _geometriesByShapeId.size());
        LOG.debug("segments=" + _geometriesByShapeSegmentKey.size());
        _geometriesByShapeId.clear();
        _distancesByShapeId.clear();
        _geometriesByShapeSegmentKey.clear();
    }

    private void loadTransfers(Graph graph) {
        Collection<Transfer> transfers = _transitService.getAllTransfers();
        TransferTable transferTable = graph.getTransferTable();
        for (Transfer sourceTransfer : transfers) {
            // Transfers may be specified using parent stations (https://developers.google.com/transit/gtfs/reference/transfers-file)
            // "If the stop ID refers to a station that contains multiple stops, this transfer rule applies to all stops in that station."
            // we thus expand transfers that use parent stations to all the member stops.
            for (Transfer t : expandTransfer(sourceTransfer)) {
                Stop fromStop = t.getFromStop();
                Stop toStop = t.getToStop();
                Route fromRoute = t.getFromRoute();
                Route toRoute = t.getToRoute();
                Trip fromTrip = t.getFromTrip();
                Trip toTrip = t.getToTrip();
                Vertex fromVertex = context.stopArriveNodes.get(fromStop);
                Vertex toVertex = context.stopDepartNodes.get(toStop);
                switch (t.getTransferType()) {
                    case 1:
                        // timed (synchronized) transfer
                        // Handle with edges that bypass the street network.
                        // from and to vertex here are stop_arrive and stop_depart vertices

                        TimedTransferEdge timedTransferEdge = new TimedTransferEdge(fromVertex, toVertex);
                        timedTransferEdge.setTransferDetails(t);

                        // add to transfer table to handle specificity
                        transferTable.addTransferTime(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, StopTransfer.TIMED_TRANSFER);
                        break;
                    case 2:
                        // min transfer time
                        transferTable.addTransferTime(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, t.getMinTransferTime());
                        break;
                    case 3:
                        // forbidden transfer
                        transferTable.addTransferTime(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, StopTransfer.FORBIDDEN_TRANSFER);
                        break;
                    case 0:
                    default:
                        // preferred transfer
                        transferTable.addTransferTime(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, StopTransfer.PREFERRED_TRANSFER);
                        break;
                }
            }
        }
    }


    private LineString getHopGeometryViaShapeDistTraveled(Graph graph, AgencyAndId shapeId, StopTime st0, StopTime st1) {

        double startDistance = st0.getShapeDistTraveled();
        double endDistance = st1.getShapeDistTraveled();

        ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startDistance, endDistance);
        LineString geometry = _geometriesByShapeSegmentKey.get(key);
        if (geometry != null)
            return geometry;

        double[] distances = getDistanceForShapeId(shapeId);

        if (distances == null) {
            graph.addBuilderAnnotation(new BogusShapeGeometry(shapeId));
            return null;
        } else {
            LinearLocation startIndex = getSegmentFraction(distances, startDistance);
            LinearLocation endIndex = getSegmentFraction(distances, endDistance);

            if (equals(startIndex, endIndex)) {
                //bogus shape_dist_traveled
                graph.addBuilderAnnotation(new BogusShapeDistanceTraveled(st1));
                return createSimpleGeometry(st0.getStop(), st1.getStop());
            }
            LineString line = getLineStringForShapeId(shapeId);
            LocationIndexedLine lol = new LocationIndexedLine(line);

            geometry = getSegmentGeometry(graph, shapeId, lol, startIndex, endIndex, startDistance,
                    endDistance, st0, st1);

            return geometry;
        }
    }

    private static boolean equals(LinearLocation startIndex, LinearLocation endIndex) {
        return startIndex.getSegmentIndex() == endIndex.getSegmentIndex()
                && startIndex.getSegmentFraction() == endIndex.getSegmentFraction()
                && startIndex.getComponentIndex() == endIndex.getComponentIndex();
    }

    /** create a 2-point linestring (a straight line segment) between the two stops */
    private LineString createSimpleGeometry(Stop s0, Stop s1) {

        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(s0.getLon(), s0.getLat()),
                new Coordinate(s1.getLon(), s1.getLat())
        };
        CoordinateSequence sequence = new PackedCoordinateSequence.Double(coordinates, 2);

        return _geometryFactory.createLineString(sequence);
    }

    private boolean isValid(Geometry geometry, Stop s0, Stop s1) {
        Coordinate[] coordinates = geometry.getCoordinates();
        if (coordinates.length < 2) {
            return false;
        }
        if (geometry.getLength() == 0) {
            return false;
        }
        for (Coordinate coordinate : coordinates) {
            if (Double.isNaN(coordinate.x) || Double.isNaN(coordinate.y)) {
                return false;
            }
        }
        Coordinate geometryStartCoord = coordinates[0];
        Coordinate geometryEndCoord = coordinates[coordinates.length - 1];

        Coordinate startCoord = new Coordinate(s0.getLon(), s0.getLat());
        Coordinate endCoord = new Coordinate(s1.getLon(), s1.getLat());
        if (SphericalDistanceLibrary.fastDistance(startCoord, geometryStartCoord) > maxStopToShapeSnapDistance) {
            return false;
        } else if (SphericalDistanceLibrary.fastDistance(endCoord, geometryEndCoord) > maxStopToShapeSnapDistance) {
            return false;
        }
        return true;
    }

    private LineString getSegmentGeometry(Graph graph, AgencyAndId shapeId,
            LocationIndexedLine locationIndexedLine, LinearLocation startIndex,
            LinearLocation endIndex, double startDistance, double endDistance,
            StopTime st0, StopTime st1) {

        ShapeSegmentKey key = new ShapeSegmentKey(shapeId, startDistance, endDistance);

        LineString geometry = _geometriesByShapeSegmentKey.get(key);
        if (geometry == null) {

            geometry = (LineString) locationIndexedLine.extractLine(startIndex, endIndex);

            // Pack the resulting line string
            CoordinateSequence sequence = new PackedCoordinateSequence.Double(geometry
                    .getCoordinates(), 2);
            geometry = _geometryFactory.createLineString(sequence);

            if (!isValid(geometry, st0.getStop(), st1.getStop())) {
                graph.addBuilderAnnotation(new BogusShapeGeometryCaught(shapeId, st0, st1));
                //fall back to trivial geometry
                geometry = createSimpleGeometry(st0.getStop(), st1.getStop());
            }
            _geometriesByShapeSegmentKey.put(key, (LineString) geometry);
        }

        return geometry;
    }

    /**
     * If a shape appears in more than one feed, the shape points will be loaded several
     * times, and there will be duplicates in the DAO. Filter out duplicates and repeated
     * coordinates because 1) they are unnecessary, and 2) they define 0-length line segments
     * which cause JTS location indexed line to return a segment location of NaN,
     * which we do not want.
     */
    private List<ShapePoint> getUniqueShapePointsForShapeId(AgencyAndId shapeId) {
        Collection<ShapePoint> points = _transitService.getShapePointsForShapeId(shapeId);
        ArrayList<ShapePoint> filtered = new ArrayList<ShapePoint>(points.size());
        ShapePoint last = null;
        for (ShapePoint sp : points) {
            if (last == null || last.getSequence() != sp.getSequence()) {
                if (last != null &&
                    last.getLat() == sp.getLat() &&
                    last.getLon() == sp.getLon()) {
                    LOG.trace("pair of identical shape points (skipping): {} {}", last, sp);
                } else {
                    filtered.add(sp);
                }
            }
            last = sp;
        }
        if (filtered.size() != points.size()) {
            filtered.trimToSize();
            return filtered;
        } else {
            return new ArrayList<>(points);
        }
    }

    private LineString getLineStringForShapeId(AgencyAndId shapeId) {

        LineString geometry = _geometriesByShapeId.get(shapeId);

        if (geometry != null)
            return geometry;

        List<ShapePoint> points = getUniqueShapePointsForShapeId(shapeId);
        if (points.size() < 2) {
            return null;
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        double[] distances = new double[points.size()];

        boolean hasAllDistances = true;

        int i = 0;
        for (ShapePoint point : points) {
            coordinates[i] = new Coordinate(point.getLon(), point.getLat());
            distances[i] = point.getDistTraveled();
            if (!point.isDistTraveledSet())
                hasAllDistances = false;
            i++;
        }

        /*
         * If we don't have distances here, we can't calculate them ourselves because we can't
         * assume the units will match
         */

        if (!hasAllDistances) {
            distances = null;
        }

        CoordinateSequence sequence = new PackedCoordinateSequence.Double(coordinates, 2);
        geometry = _geometryFactory.createLineString(sequence);
        _geometriesByShapeId.put(shapeId, geometry);
        _distancesByShapeId.put(shapeId, distances);

        return geometry;
    }

    private double[] getDistanceForShapeId(AgencyAndId shapeId) {
        getLineStringForShapeId(shapeId);
        return _distancesByShapeId.get(shapeId);
    }

    private LinearLocation getSegmentFraction(double[] distances, double distance) {
        int index = Arrays.binarySearch(distances, distance);
        if (index < 0)
            index = -(index + 1);
        if (index == 0)
            return new LinearLocation(0, 0.0);
        if (index == distances.length)
            return new LinearLocation(distances.length, 0.0);

        double prevDistance = distances[index - 1];
        if (prevDistance == distances[index]) {
            return new LinearLocation(index - 1, 1.0);
        }
        double indexPart = (distance - distances[index - 1])
                / (distances[index] - prevDistance);
        return new LinearLocation(index - 1, indexPart);
    }

    public void setFareServiceFactory(FareServiceFactory fareServiceFactory) {
        this.fareServiceFactory = fareServiceFactory;
    }

    /** 
     * Create bidirectional, "free" edges (zero-time, low-cost edges) between stops and their 
     * parent stations. This is used to produce implicit transfers between all stops that are
     * part of the same parent station. It was introduced as a workaround to allow in-station 
     * transfers for underground/grade-separated transportation systems like the NYC subway (where
     * it's important to provide in-station transfers for fare computation).
     * 
     * This step used to be automatically applied whenever transfers.txt was used to create 
     * transfers (rather than or in addition to transfers through the street netowrk),
     * but has been separated out since it is really a separate process.
     */
    public void createParentStationTransfers() {
        for (Stop stop : _transitService.getAllStops()) {
            String parentStation = stop.getParentStation();
            if (parentStation != null) {
                Vertex stopVertex = context.stationStopNodes.get(stop);

                AgencyAndId parentStationId = stop.getParentStationAgencyAndId();

                Stop parentStop = _transitService.getStopForId(parentStationId);
                Vertex parentStopVertex = context.stationStopNodes.get(parentStop);

                new FreeEdge(parentStopVertex, stopVertex);
                new FreeEdge(stopVertex, parentStopVertex);

                // Stops with location_type=2 (entrances as defined in the pathways.txt
                // proposal) have no arrive/depart vertices, hence the null checks.
                Vertex stopArriveVertex = context.stopArriveNodes.get(stop);
                Vertex parentStopArriveVertex = context.stopArriveNodes.get(parentStop);
                if (stopArriveVertex != null && parentStopArriveVertex != null) {
                    new FreeEdge(parentStopArriveVertex, stopArriveVertex);
                    new FreeEdge(stopArriveVertex, parentStopArriveVertex);
                }

                Vertex stopDepartVertex = context.stopDepartNodes.get(stop);
                Vertex parentStopDepartVertex = context.stopDepartNodes.get(parentStop);
                if (stopDepartVertex != null && parentStopDepartVertex != null) {
                    new FreeEdge(parentStopDepartVertex, stopDepartVertex);
                    new FreeEdge(stopDepartVertex, parentStopDepartVertex);
                }

                // TODO: provide a cost for these edges when stations and
                // stops have different locations
            }
        }
    }
    
    /**
     * Links the vertices representing parent stops to their child stops bidirectionally. This is
     * not intended to provide implicit transfers (i.e. child stop to parent station to another
     * child stop) but instead to allow beginning or ending a path (itinerary) at a parent station.
     * 
     * Currently this linking is only intended for use in the long distance path service. The
     * pathparsers should ensure that it is effectively ignored in other path services, and even in
     * the long distance path service anywhere but the beginning or end of a path.
     */
    public void linkStopsToParentStations(Graph graph) {
        for (Stop stop : _transitService.getAllStops()) {
            String parentStation = stop.getParentStation();
            if (parentStation != null) {
                TransitStop stopVertex = (TransitStop) context.stationStopNodes.get(stop);

                Stop parentStop = _transitService.getStopForId(stop.getParentStationAgencyAndId());
                if(context.stationStopNodes.get(parentStop) instanceof TransitStation) {
                    TransitStation parentStopVertex = (TransitStation)
                            context.stationStopNodes.get(parentStop);
                    new StationStopEdge(parentStopVertex, stopVertex);
                    new StationStopEdge(stopVertex, parentStopVertex);
                } else {
                    graph.addBuilderAnnotation(new NonStationParentStation(stopVertex));
                }
            }
        }
    }

    /**
     * Links multimodal stops to stops the same way as parent stops are linked in linkStopsToParentStations
     * @param graph
     */

    public void linkMultiModalStops(Graph graph) {
        for (Map.Entry<Stop, Collection<Stop>> entry : _transitService.getStationsByMultiModalStop().entrySet()) {
            Stop multiModalStop = entry.getKey();
            TransitStation multiModalStopVertex = (TransitStation) context.stationStopNodes.get(multiModalStop);
            if(!entry.getValue().isEmpty()) {
                for (Stop station : entry.getValue()) {
                    for (Stop stop : _transitService.getStopsForStation(station)) {
                        TransitStop stopVertex = (TransitStop) context.stationStopNodes.get(stop);
                        new StationStopEdge(multiModalStopVertex, stopVertex);
                        new StationStopEdge(stopVertex, multiModalStopVertex);
                    }
                }
            }
            else {
                LOG.warn("Multimodal stop " + multiModalStop.getId() + " does not contain any stations.");
            }
        }
    }

    /**
     * Links groupsOfStopPlaces to regular stops and multimodal stops
     * @param graph
     */

    public void linkGroupsOfStopPlaces(Graph graph) {
        for (Map.Entry<Stop, Collection<Stop>> entry : _transitService.getStopsByGroupOfStopPlace()) {
            Stop group = entry.getKey();
            TransitStation groupOfStopPlacesVertex = (TransitStation) context.stationStopNodes.get(group);
            if(!entry.getValue().isEmpty()) {
                for (Stop stopOrStation : entry.getValue()) {
                    // Entry is multimodal StopPlace
                    if ((_transitService.getStationsByMultiModalStop()).containsKey(stopOrStation)) {
                        for (Stop station : (_transitService.getStationsByMultiModalStop()).get(stopOrStation)) {
                            if (_transitService.getStopsForStation(station) != null) {
                                for (Stop stop : _transitService.getStopsForStation(station)) {
                                    TransitStop stopVertex = (TransitStop) context.stationStopNodes.get(stop);
                                    new StationStopEdge(groupOfStopPlacesVertex, stopVertex);
                                    new StationStopEdge(stopVertex, groupOfStopPlacesVertex);
                                }
                            }
                        }
                    }
                    // Entry is regular StopPlace
                    else if (_transitService.getStopsForStation(stopOrStation) != null) {
                        for (Stop stop : _transitService.getStopsForStation(stopOrStation)) {
                            TransitStop stopVertex = (TransitStop) context.stationStopNodes.get(stop);
                            new StationStopEdge(groupOfStopPlacesVertex, stopVertex);
                            new StationStopEdge(stopVertex, groupOfStopPlacesVertex);
                        }
                    }
                }
            }
            else {
                LOG.warn("Multimodal stop " + group.getId() + " does not contain any stations.");
            }
        }
    }

    /**
     * Create transfer edges between stops which are listed in transfers.txt.
     * 
     * NOTE: this method is only called when transfersTxtDefinesStationPaths is set to
     * True for a given GFTS feed. 
     */
    public void createTransfersTxtTransfers() {

        /* Create transfer edges based on transfers.txt. */
        for (Transfer transfer : _transitService.getAllTransfers()) {

            int type = transfer.getTransferType();
            if (type == 3) // type 3 = transfer not possible
                continue;
            if (transfer.getFromStop().equals(transfer.getToStop())) {
                continue;
            }
            TransitStationStop fromv = context.stationStopNodes.get(transfer.getFromStop());
            TransitStationStop tov = context.stationStopNodes.get(transfer.getToStop());

            double distance = SphericalDistanceLibrary.distance(fromv.getCoordinate(), tov.getCoordinate());
            int time;
            if (transfer.getTransferType() == 2) {
                time = transfer.getMinTransferTime();
            } else {
                time = (int) distance; // fixme: handle timed transfers
            }

            TransferEdge transferEdge = new TransferEdge(fromv, tov, distance, time);
            CoordinateSequence sequence = new PackedCoordinateSequence.Double(new Coordinate[] {
                    fromv.getCoordinate(), tov.getCoordinate() }, 2);
            LineString geometry = _geometryFactory.createLineString(sequence);
            transferEdge.setGeometry(geometry);
        }
    }

    public void setStopContext(GtfsStopContext context) {
        this.context = context;
    }

    public double getMaxStopToShapeSnapDistance() {
        return maxStopToShapeSnapDistance;
    }

    public void setMaxStopToShapeSnapDistance(double maxStopToShapeSnapDistance) {
        this.maxStopToShapeSnapDistance = maxStopToShapeSnapDistance;
    }

    private Collection<Transfer> expandTransfer (Transfer source) {
        Stop fromStop = source.getFromStop();
        Stop toStop = source.getToStop();

        if (fromStop.getLocationType() == STOP_LOCATION_TYPE && toStop.getLocationType() == STOP_LOCATION_TYPE) {
            // simple, no need to copy anything
            return Arrays.asList(source);
        } else {
            // at least one of the stops is a parent station
            // all the stops this transfer originates with
            List<Stop> fromStops;

            // all the stops this transfer terminates with
            List<Stop> toStops;

            if (fromStop.getLocationType() == PARENT_STATION_LOCATION_TYPE) {
                fromStops = _transitService.getStopsForStation(fromStop);
            } else {
                fromStops = Arrays.asList(fromStop);
            }

            if (toStop.getLocationType() == PARENT_STATION_LOCATION_TYPE) {
                toStops = _transitService.getStopsForStation(toStop);
            } else {
                toStops = Arrays.asList(toStop);
            }

            List<Transfer> expandedTransfers = new ArrayList<>(fromStops.size() * toStops.size());

            for (Stop expandedFromStop : fromStops) {
                for (Stop expandedToStop : toStops) {
                    Transfer expanded = new Transfer(source);
                    expanded.setFromStop(expandedFromStop);
                    expanded.setToStop(expandedToStop);
                    expandedTransfers.add(expanded);
                }
            }

            LOG.info(
                    "Expanded transfer between stations \"{} ({})\" and \"{} ({})\" to {} transfers between {} and {} stops",
                    fromStop.getName(), fromStop.getId(), toStop.getName(), toStop.getId(),
                    expandedTransfers.size(), fromStops.size(), toStops.size());

            return expandedTransfers;
        }
    }

    public void createParkAndRide(Graph graph) {
        CarParkService carParkService = graph.getService(
                CarParkService.class, true);
        for (Parking parking : _transitService.getAllParkings()) {
            if (parking.getParkingVehicleType().equals(Parking.ParkingVehicleType.CAR)) {
                CarPark carPark = new CarPark();
                carPark.id = parking.getId();
                carPark.name = new NonLocalizedString(parking.getName());
                carPark.realTimeData = false;
                carPark.x = parking.getLon();
                carPark.y = parking.getLat();
                carPark.spacesAvailable = parking.getPrincipalCapacity();
                carPark.maxCapacity = parking.getTotalCapacity();

                ParkAndRideVertex parkAndRideVertex = new ParkAndRideVertex(graph, carPark);
                new ParkAndRideEdge(parkAndRideVertex);
                carParkService.addCarPark(carPark);
            }
        }
    }

    private void loadAreaMap() {
        for (Area area : _transitService.getAllAreas()) {
            Geometry geometry = GeometryUtils.parseWkt(area.getWkt());
            _areasById.put(area.getAreaId(), geometry);
        }
    }

    private void loadAreasIntoGraph(Graph graph) {
        for (Map.Entry<String, Geometry> entry : _areasById.entrySet()) {
            AgencyAndId id = new AgencyAndId(_feedId.getId(), entry.getKey());
            graph.areasById.put(id, entry.getValue());
        }
    }
}
