package org.opentripplanner.routing.graph;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.schema.GraphQLSchema;
import org.apache.lucene.util.PriorityQueue;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.common.LuceneIndex;
import org.opentripplanner.common.geometry.HashGridSpatialIndex;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.index.IndexGraphQLSchema;
import org.opentripplanner.index.model.StopTimesInPattern;
import org.opentripplanner.index.model.TripTimeShort;
import org.opentripplanner.model.Agency;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.CalendarService;
import org.opentripplanner.model.DatedServiceJourney;
import org.opentripplanner.model.FeedInfo;
import org.opentripplanner.model.IdentityBean;
import org.opentripplanner.model.Notice;
import org.opentripplanner.model.Operator;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopPattern;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.TripAlterationOnDate;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.routing.alertpatch.AlertPatch;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.ExtendedTraverseVisitor;
import org.opentripplanner.routing.algorithm.TraverseVisitor;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.bike_park.BikePark;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.routing.car_park.CarPark;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.ServiceDay;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.ParkAndRideLinkEdge;
import org.opentripplanner.routing.edgetype.StreetBikeParkLink;
import org.opentripplanner.routing.edgetype.TablePatternEdge;
import org.opentripplanner.routing.edgetype.Timetable;
import org.opentripplanner.routing.edgetype.TimetableSnapshot;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.impl.AlertPatchServiceImpl;
import org.opentripplanner.routing.services.AlertPatchService;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.trippattern.FrequencyEntry;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.BikeParkVertex;
import org.opentripplanner.routing.vertextype.BikeRentalStationVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStation;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.updater.alerts.GtfsRealtimeAlertsUpdater;
import org.opentripplanner.updater.alerts.SiriSXUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;

/**
 * This class contains all the transient indexes of graph elements -- those that are not
 * serialized with the graph. Caching these maps is essentially an optimization, but a big one.
 * The index is bootstrapped from the graph's list of edges.
 */
public class GraphIndex {

    private static final Logger LOG = LoggerFactory.getLogger(GraphIndex.class);

    // TODO: consistently key on model object or id string
    public final Map<String, Vertex> vertexForId = Maps.newHashMap();
    public final Map<String, Map<String, Agency>> agenciesForFeedId = Maps.newHashMap();
    public final Map<AgencyAndId, Operator> operatorForId = Maps.newHashMap();
    public final Map<String, FeedInfo> feedInfoForId = Maps.newHashMap();
    public final Map<AgencyAndId, Stop> stopForId = Maps.newHashMap();
    public final Map<AgencyAndId, Stop> stationForId = Maps.newHashMap();
    public final Map<AgencyAndId, Trip> tripForId = Maps.newHashMap();

    /**
     * This is a NeTEx concept. This is subject to be refactored in OTP2, but is kept
     * as close to the NeTEx model here.
     */
    public final Map<AgencyAndId, DatedServiceJourney> datedServiceJourneyForId = Maps.newHashMap();

    public final Map<AgencyAndId, Route> routeForId = Maps.newHashMap();
    public final Map<AgencyAndId, String> serviceForId = Maps.newHashMap();
    public final Map<String, TripPattern> patternForId = Maps.newHashMap();
    public final Map<Stop, TransitStop> stopVertexForStop = Maps.newHashMap();
    public final Map<Trip, TripPattern> patternForTrip = Maps.newHashMap();
    public final Multimap<String, TripPattern> patternsForFeedId = ArrayListMultimap.create();
    public final Multimap<Route, TripPattern> patternsForRoute = ArrayListMultimap.create();
    public final Multimap<Stop, TripPattern> patternsForStop = ArrayListMultimap.create();
    public final Multimap<AgencyAndId, Stop> stopsForParentStation = ArrayListMultimap.create();
    final HashGridSpatialIndex<TransitStop> stopSpatialIndex = new HashGridSpatialIndex<TransitStop>();
    public final Map<AgencyAndId, Geometry> areasById = Maps.newHashMap();
    private Map<AgencyAndId, Notice> noticeMap = new HashMap<>();
    private Map<AgencyAndId, List<Notice>> noticeAssignmentMap = new HashMap<>();

    /* Should eventually be replaced with new serviceId indexes. */
    private final CalendarService calendarService;
    private final Map<AgencyAndId,Integer> serviceCodes;

    private AlertPatchService alertPatchService;

    /* Full-text search extensions */
    public LuceneIndex luceneIndex;

    /* This is a workaround, and should probably eventually be removed. */
    public Graph graph;

    /** Used for finding first/last trip of the day. This is the time at which service ends for the day. */
    public final int overnightBreak = 60 * 60 * 2; // FIXME not being set, this was done in transitIndex

    private static final int NUMBER_OF_SECONDS_IN_DAY = 86400;

    final GraphQLSchema indexSchema;

    public final ExecutorService threadPool;

    public GraphIndex (Graph graph) {
        LOG.info("Indexing graph...");

        for (String feedId : graph.getFeedIds()) {
            for (Agency agency : graph.getAgencies(feedId)) {
                Map<String, Agency> agencyForId = agenciesForFeedId.getOrDefault(feedId, new HashMap<>());
                agencyForId.put(agency.getId(), agency);
                this.agenciesForFeedId.put(feedId, agencyForId);
            }
            this.feedInfoForId.put(feedId, graph.getFeedInfo(feedId));
        }

        for (Operator operator : graph.getOperators()) {
            this.operatorForId.put(operator.getId(), operator);
        }


        Collection<Edge> edges = graph.getEdges();
        /* We will keep a separate set of all vertices in case some have the same label.
         * Maybe we should just guarantee unique labels. */
        Set<Vertex> vertices = Sets.newHashSet();
        for (Edge edge : edges) {
            vertices.add(edge.getFromVertex());
            vertices.add(edge.getToVertex());
            if (edge instanceof TablePatternEdge) {
                TablePatternEdge patternEdge = (TablePatternEdge) edge;
                TripPattern pattern = patternEdge.getPattern();
                patternForId.put(pattern.code, pattern);
            }
        }
        for (Vertex vertex : vertices) {
            vertexForId.put(vertex.getLabel(), vertex);
            if (vertex instanceof TransitStop) {
                TransitStop transitStop = (TransitStop) vertex;
                Stop stop = transitStop.getStop();
                stopForId.put(stop.getId(), stop);
                stopVertexForStop.put(stop, transitStop);
                if (stop.getParentStation() != null) {
                    stopsForParentStation.put(
                        stop.getParentStationAgencyAndId(), stop);
                }

                // Temporary solution until multimodal stations are fully part of the stop hierarchy
                if (stop.getMultiModalStation() != null) {
                    stopsForParentStation.put(
                            new AgencyAndId(stop.getId().getAgencyId(), stop.getMultiModalStation()), stop);
                }
            }
            else if (vertex instanceof TransitStation) {
                TransitStation transitStation = (TransitStation) vertex;
                Stop stop = transitStation.getStop();
                stationForId.put(stop.getId(), stop);
            }
        }
        for (TransitStop stopVertex : stopVertexForStop.values()) {
            Envelope envelope = new Envelope(stopVertex.getCoordinate());
            stopSpatialIndex.insert(envelope, stopVertex);
        }
        for (TripPattern pattern : patternForId.values()) {
            patternsForFeedId.put(pattern.getFeedId(), pattern);
            patternsForRoute.put(pattern.route, pattern);

            for (Trip trip : pattern.getTrips()) {
                patternForTrip.put(trip, pattern);
                tripForId.put(trip.getId(), trip);
            }
            for (Stop stop: pattern.getStops()) {
                if (!patternsForStop.containsEntry(stop, pattern)) {
                    patternsForStop.put(stop, pattern);
                }
            }
        }
        for (Route route : patternsForRoute.asMap().keySet()) {
            routeForId.put(route.getId(), route);
        }

        for (Trip trip : tripForId.values()) {
            for (TripAlterationOnDate it : trip.listTripAlterationOnDates()) {
                datedServiceJourneyForId.put(it.getId(), new DatedServiceJourney(trip, it));
            }
        }

        noticeMap = graph.getNoticeMap();
        noticeAssignmentMap = graph.getNoticeAssignmentMap();

        // Copy these two service indexes from the graph until we have better ones.
        calendarService = graph.getCalendarService();
        serviceCodes = graph.serviceCodes;
        this.graph = graph;
        threadPool = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("GraphQLExecutor-" + graph.routerId + "-%d")
                .build()
        );

        indexSchema = new IndexGraphQLSchema(this).indexSchema;
        getLuceneIndex();

        LOG.info("Initializing areas....");
        if (graph.areasById != null) {
            for (AgencyAndId id : graph.areasById.keySet()) {
                areasById.put(id, graph.areasById.get(id));
            }
        }

        LOG.info("Done indexing graph.");
    }

    /* TODO: an almost similar function exists in ProfileRouter, combine these.
    *  Should these live in a separate class? */
    public List<StopAndDistance> findClosestStopsByWalking(double lat, double lon, int radius) {
        // Make a normal OTP routing request so we can traverse edges and use GenericAStar
        // TODO make a function that builds normal routing requests from profile requests
        RoutingRequest rr = new RoutingRequest(TraverseMode.WALK);
        rr.from = new GenericLocation(lat, lon);
        // FIXME requires destination to be set, not necessary for analyst
        rr.to = new GenericLocation(lat, lon);
        rr.batch = true;
        rr.setRoutingContext(graph);
        rr.walkSpeed = 1;
        rr.dominanceFunction = new DominanceFunction.LeastWalk();
        // RR dateTime defaults to currentTime.
        // If elapsed time is not capped, searches are very slow.
        rr.worstTime = (rr.dateTime + radius);
        AStar astar = new AStar();
        rr.setNumItineraries(1);
        StopFinderTraverseVisitor visitor = new StopFinderTraverseVisitor();
        astar.setTraverseVisitor(visitor);
        astar.getShortestPathTree(rr, 1); // timeout in seconds
        // Destroy the routing context, to clean up the temporary edges & vertices
        rr.rctx.destroy();
        return visitor.stopsFound;
    }

    public List<PlaceAndDistance> findClosestPlacesByWalking(double lat, double lon, int maxDistance, int maxResults,
            List<TraverseMode> filterByModes,
            List<PlaceType> filterByPlaceTypes,
            List<AgencyAndId> filterByStops,
            List<AgencyAndId> filterByRoutes,
            List<String> filterByBikeRentalStations,
            List<String> filterByBikeParks,
            List<String> filterByCarParks,
            boolean filterByInUse) {
        RoutingRequest rr = new RoutingRequest(TraverseMode.WALK);
        rr.allowBikeRental = true;
        //rr.bikeParkAndRide = true;
        //rr.parkAndRide = true;
        //rr.modes = new TraverseModeSet(TraverseMode.WALK, TraverseMode.BICYCLE, TraverseMode.CAR);
        rr.from = new GenericLocation(lat, lon);
        rr.batch = true;
        rr.setRoutingContext(graph);
        rr.walkSpeed = 1;
        rr.dominanceFunction = new DominanceFunction.LeastWalk();
        // RR dateTime defaults to currentTime.
        // If elapsed time is not capped, searches are very slow.
        rr.worstTime = (rr.dateTime + maxDistance);
        rr.setNumItineraries(1);
        //rr.arriveBy = true;
        PlaceFinderTraverseVisitor visitor = new PlaceFinderTraverseVisitor(filterByModes, filterByPlaceTypes, filterByStops,
                                                                                   filterByRoutes, filterByBikeRentalStations,
                                                                                   filterByBikeParks, filterByCarParks, filterByInUse);
        AStar astar = new AStar();
        astar.setTraverseVisitor(visitor);
        SearchTerminationStrategy strategy = new SearchTerminationStrategy() {
            @Override
            public boolean shouldSearchTerminate(Vertex origin, Vertex target, State current, ShortestPathTree spt,
                    RoutingRequest traverseOptions) {
                // the first n stops the search visit may not be the nearest n
                // but when we have at least n stops found, we can update the
                // max distance to be the furthest of the places so far
                // and let the search terminate at that distance
                // and then return the first n
                if (visitor.placesFound.size() >= maxResults) {
                    int furthestDistance = 0;
                    for (PlaceAndDistance pad : visitor.placesFound) {
                        if (pad.distance > furthestDistance) {
                            furthestDistance = pad.distance;
                        }
                    }

                    // Calculate an estimated worst time by assuming walking distance equals seconds of walk time.
                    // Only updating the search worstTime if this estimate is actually better than the existing worstTime.
                    // The distance = time assumption is obviously wrong, and in the case of elevation data being used more so than what is comfortable.
                    long worstTimeEstimate = rr.dateTime + furthestDistance;
                    if (worstTimeEstimate < rr.worstTime) {
                        rr.worstTime = worstTimeEstimate;
                    }
                }
                return false;
            }
        };
        astar.getShortestPathTree(rr, 10, strategy); // timeout in seconds
        // Destroy the routing context, to clean up the temporary edges & vertices
        rr.rctx.destroy();
        List<PlaceAndDistance> results = visitor.placesFound;
        results.sort((pad1, pad2) -> pad1.distance - pad2.distance);
        return results.subList(0, min(results.size(), maxResults));
    }

    public LuceneIndex getLuceneIndex() {
        synchronized (this) {
            if (luceneIndex == null) {
                File directory;
                try {
                    directory = Files.createTempDirectory(graph.routerId + "_lucene",
                        (FileAttribute<?>[]) new FileAttribute[]{}).toFile();
                } catch (IOException e) {
                    return null;
                }
                // Synchronously lazy-initialize the Lucene index
                luceneIndex = new LuceneIndex(this, directory, false);
            }
            return luceneIndex;
        }
    }

    public static class StopAndDistance {
        public Stop stop;
        public int distance;

        public StopAndDistance(Stop stop, int distance){
            this.stop = stop;
            this.distance = distance;
        }
    }

    public static enum PlaceType {
        STOP, DEPARTURE_ROW, BICYCLE_RENT, BIKE_PARK, CAR_PARK;
    }

    public static class PlaceAndDistance {
        public Object place;
        public int distance;

        public PlaceAndDistance(Object place, int distance) {
            this.place = place;
            this.distance = distance;
        }

        public int getDistance() {
            return distance;
        }
    }

    static private class StopFinderTraverseVisitor implements TraverseVisitor {
        List<StopAndDistance> stopsFound = new ArrayList<>();
        @Override public void visitEdge(Edge edge, State state) { }
        @Override public void visitEnqueue(State state) { }
        // Accumulate stops into ret as the search runs.
        @Override public void visitVertex(State state) {
            Vertex vertex = state.getVertex();
            if (vertex instanceof TransitStop) {
                stopsFound.add(new StopAndDistance(((TransitStop) vertex).getStop(),
                    (int) state.getElapsedTimeSeconds()));
            }
        }
    }

    public static class DepartureRow {
        public String id;
        public Stop stop;
        public TripPattern pattern;

        public DepartureRow(Stop stop, TripPattern pattern) {
            this.id = toId(stop, pattern);
            this.stop = stop;
            this.pattern = pattern;
        }

        private static String toId(Stop stop, TripPattern pattern) {
            return stop.getId().getAgencyId() + ";" + stop.getId().getId() + ";" + pattern.code;
        }

        public Set<TripTimeShort> getStoptimes(GraphIndex index, long startTime, int timeRange, int numberOfDepartures, boolean omitNonPickups) {
            return index.stopTimesForPattern(stop, pattern, startTime, timeRange, numberOfDepartures, omitNonPickups, false, false);
        }

        public static DepartureRow fromId(GraphIndex index, String id) {
            String[] parts = id.split(";", 3);
            AgencyAndId stopId = new AgencyAndId(parts[0], parts[1]);
            String code = parts[2];
            return new DepartureRow(index.stopForId.get(stopId), index.patternForId.get(code));
        }
    }

    private static <T> Set<T> toSet(List<T> list) {
        if (list == null) return null;
        return new HashSet<T>(list);
    }

    private class PlaceFinderTraverseVisitor implements ExtendedTraverseVisitor {
        public List<PlaceAndDistance> placesFound = new ArrayList<>();
        private Set<TraverseMode> filterByModes;
        private Set<PlaceType> filterByPlaceTypes;
        private Set<AgencyAndId> filterByStops;
        private Set<AgencyAndId> filterByRoutes;
        private Set<String> filterByBikeRentalStation;
        private Set<String> seenDepartureRows = new HashSet<String>();
        private Set<AgencyAndId> seenStops = new HashSet<AgencyAndId>();
        private Set<String> seenBicycleRentalStations = new HashSet<String>();
        private Set<String> seenBikeParks = new HashSet<String>();
        private Set<String> seenCarParks = new HashSet<String>();
        private Set<String> filterByBikeParks;
        private Set<String> filterByCarParks;
        private boolean includeStops;
        private boolean includeDepartureRows;
        private boolean includeBikeShares;
        private boolean includeBikeParks;
        private boolean includeCarParks;
        private boolean filterByInUse;

        public PlaceFinderTraverseVisitor(
                List<TraverseMode> filterByModes,
                List<PlaceType> filterByPlaceTypes,
                List<AgencyAndId> filterByStops,
                List<AgencyAndId> filterByRoutes,
                List<String> filterByBikeRentalStations,
                List<String> filterByBikeParks,
                List<String> filterByCarParks,
                boolean filterByInUse) {
            this.filterByModes = toSet(filterByModes);
            this.filterByPlaceTypes = toSet(filterByPlaceTypes);
            this.filterByStops = toSet(filterByStops);
            this.filterByRoutes = toSet(filterByRoutes);
            this.filterByBikeRentalStation = toSet(filterByBikeRentalStations);
            this.filterByBikeParks = toSet(filterByBikeParks);
            this.filterByCarParks = toSet(filterByCarParks);
            this.filterByInUse = filterByInUse;

            includeStops = filterByPlaceTypes == null || filterByPlaceTypes.contains(PlaceType.STOP);
            includeDepartureRows = filterByPlaceTypes == null || filterByPlaceTypes.contains(PlaceType.DEPARTURE_ROW);
            includeBikeShares = filterByPlaceTypes == null || filterByPlaceTypes.contains(PlaceType.BICYCLE_RENT);
            includeBikeParks = filterByPlaceTypes == null || filterByPlaceTypes.contains(PlaceType.BIKE_PARK);
            includeCarParks = filterByPlaceTypes == null || filterByPlaceTypes.contains(PlaceType.CAR_PARK);
        }

        @Override public void preVisitEdge(Edge edge, State state) {
            if (edge instanceof ParkAndRideLinkEdge) {
                visitVertex(state.edit(edge).makeState());
            } else if (edge instanceof StreetBikeParkLink) {
                visitVertex(state.edit(edge).makeState());
            }
        }
        @Override public void visitEdge(Edge edge, State state) {
        }
        @Override public void visitEnqueue(State state) {
        }
        @Override public void visitVertex(State state) {
            Vertex vertex = state.getVertex();
            int distance = (int)state.getWalkDistance();
            if (vertex instanceof TransitStop) {
                visitStop(((TransitStop)vertex).getStop(), distance);
            } else if (vertex instanceof BikeRentalStationVertex) {
                visitBikeRentalStation(((BikeRentalStationVertex)vertex).getStation(), distance);
            } else if (vertex instanceof BikeParkVertex) {
                visitBikePark(((BikeParkVertex)vertex).getBikePark(), distance);
            } else if (vertex instanceof ParkAndRideVertex) {
                visitCarPark(((ParkAndRideVertex)vertex).getCarPark(), distance);
            }
        }
        private void visitBikeRentalStation(BikeRentalStation station, int distance) {
            handleBikeRentalStation(station, distance);
        }

        private void visitStop(Stop stop, int distance) {
            handleStop(stop, distance);
            handleDepartureRows(stop, distance);
        }

        private void visitBikePark(BikePark bikePark, int distance) {
            handleBikePark(bikePark, distance);
        }

        private void visitCarPark(CarPark carPark, int distance) {
            handleCarPark(carPark, distance);
        }

        private void handleStop(Stop stop, int distance) {
            if (filterByStops != null && !filterByStops.contains(stop.getId())) return;
            if (includeStops && !seenStops.contains(stop.getId()) && (filterByModes == null || stopHasRoutesWithMode(stop, filterByModes))) {

                if (filterByInUse && getPatternsForStop(stop, true).isEmpty()) {
                    // Stop is not in use
                    return;
                }
                placesFound.add(new PlaceAndDistance(stop, distance));
                seenStops.add(stop.getId());
            }

        }

        private void handleDepartureRows(Stop stop, int distance) {
            if (includeDepartureRows) {
                List<TripPattern> patterns = patternsForStop.get(stop)
                    .stream()
                    .filter(pattern -> filterByModes == null || filterByModes.contains(pattern.mode))
                    .filter(pattern -> filterByRoutes == null || filterByRoutes.contains(pattern.route.getId()))
                    .filter(pattern -> pattern.canBoard(pattern.getStopIndex(stop)))
                    .collect(toList());

                for (TripPattern pattern : patterns) {
                    String seenKey = GtfsLibrary.convertIdToString(pattern.route.getId()) + ":" + pattern.code;
                    if (!seenDepartureRows.contains(seenKey)) {
                        DepartureRow row = new DepartureRow(stop, pattern);
                        PlaceAndDistance place = new PlaceAndDistance(row, distance);
                        placesFound.add(place);
                        seenDepartureRows.add(seenKey);
                    }
                }
            }
        }

        private void handleBikeRentalStation(BikeRentalStation station, int distance) {
            if (!includeBikeShares) return;
            if (filterByBikeRentalStation != null && !filterByBikeRentalStation.contains(station.id)) return;
            if (seenBicycleRentalStations.contains(station.id)) return;
            seenBicycleRentalStations.add(station.id);
            placesFound.add(new PlaceAndDistance(station, distance));
        }

        private void handleBikePark(BikePark bikePark, int distance) {
            if (!includeBikeParks) return;
            if (filterByBikeParks != null && !filterByBikeParks.contains(bikePark.id)) return;
            if (seenBikeParks.contains(bikePark.id)) return;
            seenBikeParks.add(bikePark.id);
            placesFound.add(new PlaceAndDistance(bikePark, distance));
        }

        private void handleCarPark(CarPark carPark, int distance) {
            if (!includeCarParks) return;
            if (filterByCarParks != null && !filterByCarParks.contains(carPark.id)) return;
            if (seenCarParks.contains(carPark.id)) return;
            seenCarParks.add(carPark.id);
            placesFound.add(new PlaceAndDistance(carPark, distance));
        }
    }

    private Stream<TraverseMode> modesForStop(Stop stop) {
        return patternsForStop.get(stop).stream().map(tripPattern -> tripPattern.mode);
    }

    private boolean stopHasRoutesWithMode(Stop stop, Set<TraverseMode> modes) {
        return modesForStop(stop).anyMatch(modes::contains);
    }

    /** An OBA Service Date is a local date without timezone, only year month and day. */
    public BitSet servicesRunning (ServiceDate date) {
        BitSet services = new BitSet(calendarService.getServiceIds().size());
        for (AgencyAndId serviceId : calendarService.getServiceIdsOnDate(date)) {
            int n = serviceCodes.get(serviceId);
            if (n < 0) continue;
            services.set(n);
        }
        return services;
    }

    /**
     * Wraps the other servicesRunning whose parameter is an OBA ServiceDate.
     * Joda LocalDate is a similar class.
     */
    public BitSet servicesRunning(LocalDate date) {
        return servicesRunning(new ServiceDate(date));
    }

    /** Dynamically generate the set of Routes passing though a Stop on demand. */
    public Set<Route> routesForStop(Stop stop) {
        Set<Route> routes = Sets.newHashSet();
        for (TripPattern p : patternsForStop.get(stop)) {
            routes.add(p.route);
        }
        return routes;
    }

    /**
     * Fetch upcoming vehicle departures from a stop.
     * Fetches two departures for each pattern during the next 24 hours as default
     */
    public Collection<StopTimesInPattern> stopTimesForStop(Stop stop, boolean omitNonPickups) {
        return stopTimesForStop(stop, System.currentTimeMillis()/1000, 24 * 60 * 60, 2, omitNonPickups);
    }

    public List<StopTimesInPattern> stopTimesForStop(final Stop stop, final long startTime, final int timeRange, final int numberOfDepartures, final boolean omitNonPickups) {
        return stopTimesForStop(stop, startTime, timeRange, numberOfDepartures, omitNonPickups, false);
    }

    /**
     * Fetch upcoming vehicle departures from a stop. It goes though all patterns passing the stop
     * for the previous, current and next service date. It uses a priority queue to keep track of
     * the next departures. The queue is shared between all dates, as services from the previous
     * service date can visit the stop later than the current service date's services. This happens
     * eg. with sleeper trains.
     * <p>
     * If the stop is visited more than once (a loop in the pattern), then
     * trip times are collected for all visits. The {@code numberOfDepartures} is
     * applied to each stop visit, not the total.
     *
     * @param stop Stop object to perform the search for
     * @param startTime Start time for the search. Seconds from UNIX epoch
     * @param timeRange Searches forward for timeRange seconds from startTime
     * @param numberOfDepartures Number of departures to fetch per stop visit in pattern
     * @param omitNonPickups If true, do not include vehicles that will not pick up passengers.
     * @param includeCancelledTrips If true, trips cancelled in realtime-data will be included in result.
     */
    public List<StopTimesInPattern> stopTimesForStop(final Stop stop, final long startTime, final int timeRange, final int numberOfDepartures, final boolean omitNonPickups, final boolean includeCancelledTrips) {

        final List<StopTimesInPattern> ret = new ArrayList<>();

        final Collection<TripPattern> graphPatterns = getPatternsForStop(stop, false);
        final Collection<TripPattern> realtimePatterns = getPatternsForStop(stop, true);

        if (realtimePatterns != null) {
            // Ensure that realtimePatterns only include realtime-departures
            realtimePatterns.removeAll(graphPatterns);
        }

        /*
         First, check all TripPatterns without realtime-patterns to get all planned stops.
         Flag to include realtime-cancelled trips is always set to false in this step to avoid replaced trips from being included
         since a planned trip will be cancelled when it is replaced with a modified stopPattern.
         Planned cancellations will be included based on provided parameter
         */
        for (final TripPattern pattern : graphPatterns) {

            final Set<TripTimeShort> stopTimesForStop = stopTimesForPattern(stop, pattern, startTime, timeRange, numberOfDepartures, omitNonPickups, false, includeCancelledTrips);

            if (stopTimesForStop.size() >0) {
                final StopTimesInPattern stopTimes = new StopTimesInPattern(pattern);
                stopTimes.times.addAll(stopTimesForStop);
                ret.add(stopTimes);
            }
        }

        /*
        Second, check realtime-TripPatterns, with the provided value for includeCancelledTrips.
        Planned cancellations is always set to false in this step.
         */
        for (final TripPattern pattern : realtimePatterns) {

            final Set<TripTimeShort> stopTimesForStop = stopTimesForPattern(stop, pattern, startTime, timeRange, numberOfDepartures, omitNonPickups, includeCancelledTrips, false);

            if (stopTimesForStop.size() > 0) {
                final StopTimesInPattern stopTimes = new StopTimesInPattern(pattern);
                stopTimes.times.addAll(stopTimesForStop);
                ret.add(stopTimes);
            }
        }
        return ret;
    }

    public Collection<TripPattern> getPatternsForStop(Stop stop, boolean includeRealtimeUpdates) {
        List<TripPattern> tripPatterns = new ArrayList<>(patternsForStop.get(stop));

        if (includeRealtimeUpdates && graph.timetableSnapshotSource != null) {
            tripPatterns.addAll(graph.timetableSnapshotSource.getAddedTripPatternsForStop(stop));
        }
        return tripPatterns;
    }

    /**
     * Fetch next n upcoming vehicle departures for a stop of pattern. It goes
     * though the previous, current and next service date. It uses a priority
     * queue to keep track of the next departures. The queue is shared between
     * all dates, as services from the previous service date can visit the stop
     * later than the current service date's services. This happens eg. with
     * sleeper trains.
     * <p>
     * If the stop is visited more than once (a loop in the pattern), then
     * trip times are collected for all visits. The {@code numberOfDepartures} is
     * applied to each stop visit, not the total.
     *
     * @param stop
     *            Stop object to perform the search for
     * @param pattern
     *            The selected pattern. If null an empty list is returned.
     * @param startTime
     *            Start time for the search. Seconds from UNIX epoch
     * @param timeRange
     *            Searches forward for timeRange seconds from startTime
     * @param numberOfDepartures
     *            Number of departures to fetch per stop visits in pattern
     * @param omitNonPickups If true, do not include vehicles that will not pick up passengers.
     *
     * @param includeRealtimeCancellations If true, realtime-cancelled trips will also be included
     * @param includePlannedCancellations If true, planned cancelled trips will also be included (serviceAlteration=cancelled)
     * @return a sorted set of trip times, sorted on depature time.
     */
    public Set<TripTimeShort> stopTimesForPattern(
        final Stop stop,
        final TripPattern pattern,
        long startTime,
        final int timeRange,
        int numberOfDepartures,
        boolean omitNonPickups,
        boolean includeRealtimeCancellations,
        boolean includePlannedCancellations
    ) {
        if (pattern == null) {
            return Collections.emptySet();
        }

        if (startTime == 0) {
            startTime = System.currentTimeMillis() / 1000;
        }

        final Set<TripTimeShort> result = new TreeSet<>(TripTimeShort.compareByDeparture());

        final PriorityQueue<TripTimeShort> tripTimesQueue = new PriorityQueue<TripTimeShort>(numberOfDepartures) {
            @Override
            protected boolean lessThan(final TripTimeShort t1, final TripTimeShort t2) {
                return (t1.serviceDay + t1.realtimeDeparture) > (t2.serviceDay
                        + t2.realtimeDeparture);
            }
        };

        final TimetableSnapshot snapshot = (graph.timetableSnapshotSource != null)
            ? graph.timetableSnapshotSource.getTimetableSnapshot() : null;

        // For trips that cross midnight more than once, extended serviceDates need to be used
        int nStops = pattern.stopPattern.size;
        boolean useExtendedDates = !pattern.scheduledTimetable.tripTimes.isEmpty() && (pattern.scheduledTimetable.tripTimes.stream()
                .mapToInt(tripTimes -> tripTimes.getArrivalTime(nStops - 1)).max()
                .getAsInt() - NUMBER_OF_SECONDS_IN_DAY) > NUMBER_OF_SECONDS_IN_DAY;

        Date date = new Date(startTime * 1000);
        final ServiceDate[] serviceDates = {new ServiceDate(date).previous(), new ServiceDate(date), new ServiceDate(date).next()};

        // Add extended serviceDates both before and after standard yesterday/today/tomorrow dates
        final ServiceDate[] extendedServiceDates = new ServiceDate[17];
        if (useExtendedDates) {
            int additionalDays = 7;
            for (int dayOffset = -1 - additionalDays; dayOffset <= 1 + additionalDays; dayOffset++) {
                int test = dayOffset + additionalDays + 1;
                extendedServiceDates[dayOffset + additionalDays + 1] = new ServiceDate(date).shift(dayOffset);
            }
        }

        // Loop through all possible days
        for (final ServiceDate serviceDate : useExtendedDates ? extendedServiceDates : serviceDates) {
            final ServiceDay sd = new ServiceDay(graph, serviceDate, calendarService,
                    pattern.route.getAgency().getId());
            Timetable tt;

            if (snapshot != null) {
                tt = snapshot.resolve(pattern, serviceDate);
            } else {
                tt = pattern.scheduledTimetable;
            }

            if (!includeRealtimeCancellations && !tt.temporallyViable(sd, startTime, timeRange, true))
                continue;

            final int starttimeSecondsSinceMidnight = sd.secondsSinceMidnight(startTime);
            int stopIndex = 0;

            // loop through all stops of pattern
            for (final Stop currStop : tt.pattern.stopPattern.stops) {
                if (currStop.equals(stop)) {

                    if (!includeRealtimeCancellations) {
                        //Pattern added by realtime should not be checked at this point
                        if (omitNonPickups && pattern.stopPattern.pickups[stopIndex] == StopPattern.PICKDROP_NONE) {
                            continue;
                        }
                    }

                    for (final TripTimes triptimes : tt.tripTimes) {

                        if (!includeRealtimeCancellations && !sd.serviceRunning(triptimes.serviceCode)) {
                            continue;
                        }

                        if (!includePlannedCancellations) {
                            // Check if trip has been cancelled via planned data
                            if(!triptimes.trip.isRunningOnDate(serviceDate)) { continue; }
                        }


                        // Check if pickup has been cancelled via realtime-data, and also NOT wanted in result
                        if (!includeRealtimeCancellations && triptimes.getPickupType(stopIndex) == StopPattern.PICKDROP_NONE) {
                            continue;
                        }

                        if (stopIndex < triptimes.getNumStops()-1) {
                            // Last stop should never be included in a departureboard
                            int stopDepartureTime = triptimes.getDepartureTime(stopIndex);

                            // true if cancelled trips are requested, and stop is cancelled
                            boolean isCancelledAndRequested = ((includePlannedCancellations || includeRealtimeCancellations) && triptimes.isCancelledStop(stopIndex));  // Stop is cancelled, but cancelled stops should be included in this request

                            if (isCancelledAndRequested) {
                                // Cancelled trips should be included in this request - use scheduled times for time-verification
                                stopDepartureTime = triptimes.getScheduledDepartureTime(stopIndex);
                            }

                            // true if a stop is planned, and not cancelled
                            boolean isScheduledStop = !triptimes.isCancelledStop(stopIndex) & triptimes.getPickupType(stopIndex) == StopPattern.PICKDROP_SCHEDULED;


                            boolean includeByCancellation = isScheduledStop | isCancelledAndRequested;

                            boolean includeByDepartureTime = (stopDepartureTime != -1 && stopDepartureTime >= starttimeSecondsSinceMidnight && stopDepartureTime < starttimeSecondsSinceMidnight + timeRange);

                            if (includeByCancellation & includeByDepartureTime) {
                                tripTimesQueue.insertWithOverflow(new TripTimeShort(triptimes, stopIndex, currStop, sd));
                            }
                        }
                    }

                    // TODO: This needs to be adapted after #1647 is merged
                    for (final FrequencyEntry freq : tt.frequencyEntries) {
                        if (!sd.serviceRunning(freq.tripTimes.serviceCode))
                            continue;
                        int departureTime = freq.nextDepartureTime(stopIndex, starttimeSecondsSinceMidnight);
                        if (departureTime == -1)
                            continue;
                        final int lastDeparture = freq.endTime + freq.tripTimes.getArrivalTime(stopIndex)
                                - freq.tripTimes.getDepartureTime(0);

                        while (departureTime <= lastDeparture && tripTimesQueue.size() < numberOfDepartures) {
                            tripTimesQueue.insertWithOverflow(new TripTimeShort(freq.materialize(stopIndex, departureTime, true),
                                    stopIndex, currStop, sd));
                            departureTime += freq.headway;
                        }
                    }
                    while(tripTimesQueue.size()>0) {
                        result.add(tripTimesQueue.pop());
                    }
                    tripTimesQueue.clear();
                }

                stopIndex++;
            }
        }
        return result;
    }


    /**
     * Get a list of all trips that pass through a stop during a single ServiceDate. Useful when creating complete stop
     * timetables for a single day.
     *
     * @param stop Stop object to perform the search for
     * @param serviceDate Return all departures for the specified date
     */
    public List<StopTimesInPattern> getStopTimesForStop(Stop stop, ServiceDate serviceDate, boolean omitNonPickups) {
        List<StopTimesInPattern> ret = new ArrayList<>();
        TimetableSnapshot snapshot = null;
        if (graph.timetableSnapshotSource != null) {
            snapshot = graph.timetableSnapshotSource.getTimetableSnapshot();
        }
        Collection<TripPattern> patterns = patternsForStop.get(stop);
        for (TripPattern pattern : patterns) {
            StopTimesInPattern stopTimes = new StopTimesInPattern(pattern);
            Timetable tt;
            if (snapshot != null){
                tt = snapshot.resolve(pattern, serviceDate);
            } else {
                tt = pattern.scheduledTimetable;
            }
            ServiceDay sd = new ServiceDay(graph, serviceDate, calendarService, pattern.route.getAgency().getId());
            int sidx = 0;
            for (Stop currStop : pattern.stopPattern.stops) {
                if (currStop.equals(stop)) {
                    if(omitNonPickups && pattern.stopPattern.pickups[sidx] == StopPattern.PICKDROP_NONE) continue;
                    for (TripTimes t : tt.tripTimes) {
                        if (!sd.serviceRunning(t.serviceCode)) continue;
                        stopTimes.times.add(new TripTimeShort(t, sidx, stop, sd));
                    }
                }
                sidx++;
            }
            ret.add(stopTimes);
        }
        return ret;
    }

    public List<ServiceDate> getActiveDays(Trip trip) {
        Collection<ServiceDate> dates = graph.getCalendarService().getServiceDatesForServiceId(trip.getServiceId());
        return dates.stream().filter(trip::isRunningOnDate).collect(toList());
    }

    /**
     * Get the most up-to-date timetable for the given TripPattern, as of right now.
     * There should probably be a less awkward way to do this that just gets the latest entry from the resolver without
     * making a fake routing request.
     */
    public Timetable currentUpdatedTimetableForTripPattern (TripPattern tripPattern) {
        RoutingRequest req = new RoutingRequest();
        req.setRoutingContext(graph, (Vertex)null, (Vertex)null);
        // The timetableSnapshot will be null if there's no real-time data being applied.
        if (req.rctx.timetableSnapshot == null) return tripPattern.scheduledTimetable;
        // Get the updated times for right now, which is the only reasonable default since no date is supplied.
        Calendar calendar = Calendar.getInstance();
        ServiceDate serviceDate = new ServiceDate(calendar.getTime());
        return req.rctx.timetableSnapshot.resolve(tripPattern, serviceDate);
    }

    public Response getGraphQLResponse(String query, Router router, Map<String, Object> variables, String operationName, int timeout, long maxResolves) {
        Response.ResponseBuilder res = Response.status(Response.Status.OK);
        HashMap<String, Object> content = getGraphQLExecutionResult(query, router, variables,
            operationName, timeout, maxResolves);
        if (content.get("errors") != null) {
            // TODO: Put correct error code, eg. 400 for syntax error
            res = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return res.entity(content).build();
    }

    public HashMap<String, Object> getGraphQLExecutionResult(String query, Router router,
                                                                    Map<String, Object> variables, String operationName, int timeout, long maxResolves) {
        MaxQueryComplexityInstrumentation instrumentation = new MaxQueryComplexityInstrumentation((int) maxResolves);
        GraphQL graphQL = GraphQL.newGraphQL(indexSchema).instrumentation(instrumentation).build();

        if (variables == null) {
            variables = new HashMap<>();
        }

        ExecutionResult executionResult = graphQL.execute(query, operationName, router, variables);
        HashMap<String, Object> content = new HashMap<>();
        if (!executionResult.getErrors().isEmpty()) {
            content.put("errors",
                executionResult
                    .getErrors()
                    .stream()
                    .map(error -> {
                        if (error instanceof ExceptionWhileDataFetching) {
                            HashMap<String, Object> response = new HashMap<String, Object>();
                            response.put("message", error.getMessage());
                            response.put("locations", error.getLocations());
                            response.put("errorType", error.getErrorType());
                            // Convert stack trace to propr format
                            Stream<StackTraceElement> stack = Arrays.stream(((ExceptionWhileDataFetching) error).getException().getStackTrace());
                            response.put("stack", stack.map(StackTraceElement::toString).collect(Collectors.toList()));
                            return response;
                        } else {
                            return error;
                        }
                    })
                    .collect(Collectors.toList()));
        }
        if (executionResult.getData() != null) {
            content.put("data", executionResult.getData());
        }
        return content;
    }

    private AlertPatchService getSiriAlertPatchService() {
        if (graph.updaterManager == null) {
            return new AlertPatchServiceImpl(graph);
        }
        if (alertPatchService == null) {
            Optional<AlertPatchService> patchServiceOptional = graph.updaterManager.getUpdaterList().stream()
                    .filter(SiriSXUpdater.class::isInstance)
                    .map(SiriSXUpdater.class::cast)
                    .map(SiriSXUpdater::getAlertPatchService).findFirst();

            if (!patchServiceOptional.isPresent()) {
                patchServiceOptional = graph.updaterManager.getUpdaterList().stream()
                        .filter(GtfsRealtimeAlertsUpdater.class::isInstance)
                        .map(GtfsRealtimeAlertsUpdater.class::cast)
                        .map(GtfsRealtimeAlertsUpdater::getAlertPatchService).findFirst();
            }
            if (patchServiceOptional.isPresent()) {
                alertPatchService = patchServiceOptional.get();
            } else {
                alertPatchService = new AlertPatchServiceImpl(graph);
            }
        }
        return alertPatchService;
    }

    public Collection<AlertPatch> getAlerts() {
        return getSiriAlertPatchService().getAllAlertPatches();
    }

    public Collection<AlertPatch> getAlertsForRoute(Route route) {
        return getSiriAlertPatchService().getRoutePatches(route.getId());
    }

    public Collection<AlertPatch> getAlertsForRouteId(AgencyAndId routeId) {
        return getSiriAlertPatchService().getRoutePatches(routeId);
    }

    public Collection<AlertPatch> getAlertsForTrip(Trip trip) {
        return getSiriAlertPatchService().getTripPatches(trip.getId());
    }

    public Collection<AlertPatch> getAlertsForTripId(AgencyAndId tripId) {
        return getSiriAlertPatchService().getTripPatches(tripId);
    }

    public Collection<AlertPatch> getAlertsForPattern(TripPattern pattern) {
        return getSiriAlertPatchService().getTripPatternPatches(pattern);
    }

    public Collection<AlertPatch> getAlertsForAgency(Agency agency) {
        return getSiriAlertPatchService().getAgencyPatches(agency.getId());
    }

    public AlertPatch getAlertForId(String id) {
        return getSiriAlertPatchService().getPatchById(id);
    }

    public Collection<AlertPatch> getAlertsForStop(Stop stop) {
        return getSiriAlertPatchService().getStopPatches(stop.getId());
    }

    public Collection<AlertPatch> getAlertsForStopId(AgencyAndId stopId) {
        return getSiriAlertPatchService().getStopPatches(stopId);
    }

    public Collection<AlertPatch> getAlertsForStopAndRoute(Stop stop, Route route) {
        return getSiriAlertPatchService().getStopAndRoutePatches(stop.getId(), route.getId());
    }

    public Collection<AlertPatch> getAlertsForStopAndRoute(AgencyAndId stopId, AgencyAndId routeId) {
        return getSiriAlertPatchService().getStopAndRoutePatches(stopId, routeId);
    }

    public Collection<AlertPatch> getAlertsForStopAndTrip(Stop stop, Trip trip) {
        return getSiriAlertPatchService().getStopAndTripPatches(stop.getId(), trip.getId());
    }
    public Collection<AlertPatch> getAlertsForStopAndTrip(AgencyAndId stopId, AgencyAndId tripId) {
        return getSiriAlertPatchService().getStopAndTripPatches(stopId, tripId);
    }

    public Collection<AlertPatch> getAlertsForDatedServiceJourney(AgencyAndId dsjId) {
        return getSiriAlertPatchService().getDatedServiceJourneyPatches(null, dsjId);
    }

    public Collection<AlertPatch> getAlertsForDatedServiceJourney(DatedServiceJourney dsjId) {
        return getAlertsForDatedServiceJourney(dsjId.getId());
    }

    public Collection<AlertPatch> getAlertsForStopAndDatedServiceJourney(AgencyAndId stopId, AgencyAndId dsjId) {
        return getSiriAlertPatchService().getDatedServiceJourneyPatches(stopId, dsjId);
    }

    /**
     * Fetch an agency by its string ID, ignoring the fact that this ID should be scoped by a feedId.
     * This is a stopgap (i.e. hack) method for fetching agencies where no feed scope is available.
     * I am creating this method only to allow merging pull request #2032 which adds GraphQL.
     * Note that if the same agency ID is defined in several feeds, this will return one of them
     * at random. That is obviously not the right behavior. The problem is that agencies are
     * not currently keyed on an AgencyAndId object, but on separate feedId and id Strings.
     * A real fix will involve replacing or heavily modifying the OBA GTFS loader, which is now
     * possible since we have forked it.
     */
    public Agency getAgencyWithoutFeedId(String agencyId) {
        // Iterate over the agency map for each feed.
        for (Map<String, Agency> agencyForId : agenciesForFeedId.values()) {
            Agency agency = agencyForId.get(agencyId);
            if (agency != null) {
                return agency;
            }
        }
        return null;
    }

    /**
     * Construct a set of all Agencies in this graph, spanning across all feed IDs.
     * I am creating this method only to allow merging pull request #2032 which adds GraphQL.
     * This should probably be done some other way, see javadoc on getAgencyWithoutFeedId.
     */
    public Set<Agency> getAllAgencies() {
        Set<Agency> allAgencies = new HashSet<>();
        for (Map<String, Agency> agencyForId : agenciesForFeedId.values()) {
            allAgencies.addAll(agencyForId.values());
        }
        return allAgencies;
    }

    /**
     * Get a list of all operators spanning across all feeds.
     */
    public Collection<Operator> getAllOperators() {
        return operatorForId.values();
    }

    /**
     * Construct a list of all Agencies and Operators in this graph, spanning across all feed IDs.
     */
    public Collection<IdentityBean> getAllOrganizations() {
        List<IdentityBean> all = new ArrayList<>();
        all.addAll(getAllAgencies());
        all.addAll(getAllOperators());
        return all;
    }

    public void setNoticeMap(Map<AgencyAndId, Notice> noticeMap) {
        this.noticeMap = noticeMap;
    }

    public void setNoticeAssignmentMap(Map<AgencyAndId, List<Notice>> noticeAssignmentMap) {
        this.noticeAssignmentMap = noticeAssignmentMap;
    }

    public Map<AgencyAndId, Notice> getNoticeMap() {
        return noticeMap;
    }

    public Map<AgencyAndId, List<Notice>> getNoticeAssignmentMap() {
        return noticeAssignmentMap;
    }

    public Collection<Notice> getNoticesForElement(AgencyAndId id) {
        return this.noticeAssignmentMap.containsKey(id) ? this.noticeAssignmentMap.get(id) : new ArrayList<>();
    }

    // Heuristic for deciding if trip is call-n-ride, only used for transfer and banning rules
    public boolean tripIsCallAndRide(AgencyAndId tripId) {
        Trip trip = tripForId.get(tripId);
        TripPattern pattern = patternForTrip.get(trip);
        return pattern.getGeometry() == null;
    }
}
