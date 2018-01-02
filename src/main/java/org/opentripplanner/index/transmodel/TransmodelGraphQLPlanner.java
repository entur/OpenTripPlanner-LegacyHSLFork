package org.opentripplanner.index.transmodel;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetchingEnvironment;
import org.opentripplanner.api.common.Message;
import org.opentripplanner.api.common.ParameterException;
import org.opentripplanner.api.common.RoutingResource;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.error.PlannerError;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.api.resource.DebugOutput;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.index.transmodel.mapping.TransmodelMappingUtil;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.util.ResourceBundleSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TransmodelGraphQLPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(TransmodelGraphQLPlanner.class);

    private TransmodelMappingUtil mappingUtil;

    public TransmodelGraphQLPlanner(TransmodelMappingUtil mappingUtil) {
        this.mappingUtil = mappingUtil;
    }

    public Map<String, Object> plan(DataFetchingEnvironment environment) {
        Router router = environment.getContext();
        RoutingRequest request = createRequest(environment);
        GraphPathFinder gpFinder = new GraphPathFinder(router);

        TripPlan plan = new TripPlan(
                                            new Place(request.from.lng, request.from.lat, request.getFromPlace().name),
                                            new Place(request.to.lng, request.to.lat, request.getToPlace().name),
                                            request.getDateTime());
        List<Message> messages = new ArrayList<>();
        DebugOutput debugOutput = new DebugOutput();

        try {
            List<GraphPath> paths = gpFinder.graphPathFinderEntryPoint(request);
            plan = GraphPathToTripPlanConverter.generatePlan(paths, request);
        } catch (Exception e) {
            PlannerError error = new PlannerError(e);
            if (!PlannerError.isPlanningError(e.getClass()))
                LOG.warn("Error while planning path: ", e);
            messages.add(error.message);
        } finally {
            if (request != null) {
                if (request.rctx != null) {
                    debugOutput = request.rctx.debugOutput;
                }
                request.cleanup(); // TODO verify that this cleanup step is being done on Analyst web services
            }
        }

        return ImmutableMap.<String, Object>builder()
                       .put("plan", plan)
                       .put("messages", messages)
                       .put("debugOutput", debugOutput)
                       .build();
    }

    private static <T> void call(Map<String, T> m, String name, Consumer<T> consumer) {
        if (!name.contains(".")) {
            if (hasArgument(m, name)) {
                T v = m.get(name);
                consumer.accept(v);
            }
        } else {
            String[] parts = name.split("\\.");
            if (hasArgument(m, parts[0])) {
                Map<String, T> nm = (Map<String, T>) m.get(parts[0]);
                call(nm, String.join(".", Arrays.copyOfRange(parts, 1, parts.length)), consumer);
            }
        }
    }

    private static <T> void call(DataFetchingEnvironment environment, String name, Consumer<T> consumer) {
        if (!name.contains(".")) {
            if (hasArgument(environment, name)) {
                consumer.accept(environment.getArgument(name));
            }
        } else {
            String[] parts = name.split("\\.");
            if (hasArgument(environment, parts[0])) {
                Map<String, T> nm = (Map<String, T>) environment.getArgument(parts[0]);
                call(nm, String.join(".", Arrays.copyOfRange(parts, 1, parts.length)), consumer);
            }
        }
    }

    private static class CallerWithEnvironment {
        private final DataFetchingEnvironment environment;

        public CallerWithEnvironment(DataFetchingEnvironment e) {
            this.environment = e;
        }

        private <T> void argument(String name, Consumer<T> consumer) {
            call(environment, name, consumer);
        }
    }

    private GenericLocation toGenericLocation(Map<String, Object> m) {
        Map<String, Object> coordinates = (Map<String, Object>) m.get("coordinates");
        Double lat = null;
        Double lon = null;
        if (coordinates != null) {
            lat = (Double) coordinates.get("latitude");
            lon = (Double) coordinates.get("longitude");
        }

        String placeRef = mappingUtil.preparePlaceRef((String) m.get("place"));
        String name = m.get("name") == null ? "" : (String) m.get("name");

        String place = Joiner.on(",").join(Arrays.asList(placeRef, lat, lon).stream().filter(o -> o != null).collect(Collectors.toList()));

        return new GenericLocation(name, place);
    }

    private RoutingRequest createRequest(DataFetchingEnvironment environment) {
        Router router = environment.getContext();
        RoutingRequest request = router.defaultRoutingRequest.clone();
        request.routerId = router.id;

        TransmodelGraphQLPlanner.CallerWithEnvironment callWith = new TransmodelGraphQLPlanner.CallerWithEnvironment(environment);

        callWith.argument("from", (Map<String, Object> v) -> request.from = toGenericLocation(v));
        callWith.argument("to", (Map<String, Object> v) -> request.to = toGenericLocation(v));


        callWith.argument("dateTime", millisSinceEpoch -> request.setDateTime(new Date((long) millisSinceEpoch)));

        callWith.argument("wheelchair", request::setWheelchairAccessible);
        callWith.argument("numItineraries", request::setNumItineraries);
        callWith.argument("maxWalkDistance", request::setMaxWalkDistance);
        callWith.argument("maxPreTransitTime", request::setMaxPreTransitTime);
        callWith.argument("walkReluctance", request::setWalkReluctance);
        callWith.argument("walkOnStreetReluctance", request::setWalkOnStreetReluctance);
        callWith.argument("waitReluctance", request::setWaitReluctance);
        callWith.argument("waitAtBeginningFactor", request::setWaitAtBeginningFactor);
        callWith.argument("walkSpeed", (Double v) -> request.walkSpeed = v);
        callWith.argument("bikeSpeed", (Double v) -> request.bikeSpeed = v);
        callWith.argument("bikeSwitchTime", (Integer v) -> request.bikeSwitchTime = v);
        callWith.argument("bikeSwitchCost", (Integer v) -> request.bikeSwitchCost = v);

        OptimizeType optimize = environment.getArgument("optimize");

        if (optimize == OptimizeType.TRIANGLE) {
            callWith.argument("triangle.safetyFactor", request::setTriangleSafetyFactor);
            callWith.argument("triangle.slopeFactor", request::setTriangleSlopeFactor);
            callWith.argument("triangle.timeFactor", request::setTriangleTimeFactor);
            try {
                RoutingRequest.assertTriangleParameters(request.triangleSafetyFactor, request.triangleTimeFactor, request.triangleSlopeFactor);
            } catch (ParameterException e) {
                throw new RuntimeException(e);
            }
        }

        callWith.argument("arriveBy", request::setArriveBy);
        request.showIntermediateStops = true;
        callWith.argument("vias", (List<Map<String, Object>> v) -> request.intermediatePlaces = v.stream().map(this::toGenericLocation).collect(Collectors.toList()));
        callWith.argument("preferred.lines", lines -> request.setPreferredRoutes(mappingUtil.prepareListOfAgencyAndId((List<String>) lines, "__")));
        callWith.argument("preferred.otherThanPreferredLinesPenalty", request::setOtherThanPreferredRoutesPenalty);
        callWith.argument("preferred.organisations", organisations -> request.setPreferredAgencies(mappingUtil.mapCollectionOfValues((Collection<String>) organisations, in -> in)));
        callWith.argument("unpreferred.lines", lines -> request.setUnpreferredRoutes(mappingUtil.prepareListOfAgencyAndId((List<String>) lines, "__")));
        callWith.argument("unpreferred.organisations", organisations -> request.setUnpreferredAgencies(mappingUtil.mapCollectionOfValues((Collection<String>) organisations, in -> in)));

        callWith.argument("banned.lines", lines -> request.setBannedRoutes(mappingUtil.prepareListOfAgencyAndId((List<String>) lines, "__")));
        callWith.argument("banned.organisations", organisations -> request.setBannedAgencies(mappingUtil.mapCollectionOfValues((Collection<String>) organisations, in -> in)));
        callWith.argument("banned.serviceJourneys", serviceJourneys -> request.bannedTrips = RoutingResource.makeBannedTripMap(mappingUtil.prepareListOfAgencyAndId((List<String>) serviceJourneys)));

        callWith.argument("banned.quays", quays -> request.setBannedStops(mappingUtil.prepareListOfAgencyAndId((List<String>) quays)));
        callWith.argument("banned.quaysHard", quaysHard -> request.setBannedStopsHard(mappingUtil.prepareListOfAgencyAndId((List<String>) quaysHard)));

        callWith.argument("whiteListed.lines", lines -> request.setWhiteListedRoutes(mappingUtil.prepareListOfAgencyAndId((List<String>) lines, "__")));
        callWith.argument("whiteListed.organisations", organisations -> request.setWhiteListedAgencies(mappingUtil.mapCollectionOfValues((Collection<String>) organisations, in -> in)));

        callWith.argument("transferPenalty", (Integer v) -> request.transferPenalty = v);
        if (optimize == OptimizeType.TRANSFERS) {
            optimize = OptimizeType.QUICK;
            request.transferPenalty += 1800;
        }

        callWith.argument("batch", (Boolean v) -> request.batch = v);

        if (optimize != null) {
            request.optimize = optimize;
        }

        if (hasArgument(environment, "modes")) {
            new QualifiedModeSet(mappingUtil.mapListOfModes(environment.getArgument("modes"))).applyToRoutingRequest(request);
            request.setModes(request.modes);
        }

        if (request.allowBikeRental && !hasArgument(environment, "bikeSpeed")) {
            //slower bike speed for bike sharing, based on empirical evidence from DC.
            request.bikeSpeed = 4.3;
        }

        callWith.argument("minimumTransferTime", (Integer v) -> request.transferSlack = v);
        request.assertSlack();

        callWith.argument("maximumTransfers", (Integer v) -> request.maxTransfers = v);

        final long NOW_THRESHOLD_MILLIS = 15 * 60 * 60 * 1000;
        boolean tripPlannedForNow = Math.abs(request.getDateTime().getTime() - new Date().getTime()) < NOW_THRESHOLD_MILLIS;
        request.useBikeRentalAvailabilityInformation = (tripPlannedForNow); // TODO the same thing for GTFS-RT


        callWith.argument("ignoreRealtimeUpdates", (Boolean v) -> request.ignoreRealtimeUpdates = v);
        callWith.argument("locale", (String v) -> request.locale = ResourceBundleSingleton.INSTANCE.getLocale(v));


        return request;
    }

    public static boolean hasArgument(DataFetchingEnvironment environment, String name) {
        return environment.containsArgument(name) && environment.getArgument(name) != null;
    }

    public static <T> boolean hasArgument(Map<String, T> m, String name) {
        return m.containsKey(name) && m.get(name) != null;
    }

}