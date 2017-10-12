package org.opentripplanner.updater;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * This class is used for matching TripDescriptors without trip_ids to scheduled GTFS data and to
 * feed back that information into a new TripDescriptor with proper trip_id.
 *
 */
public class SiriFuzzyTripMatcher {
    private static final Logger LOG = LoggerFactory.getLogger(SiriFuzzyTripMatcher.class);

    private GraphIndex index;

    private static Map<String, Set<Trip>> mappedTripsCache = new HashMap<>();
    private static Map<String, Set<Route>> mappedRoutesCache = new HashMap<>();
    private static Map<String, Set<Trip>> start_stop_tripCache = new HashMap<>();

    public SiriFuzzyTripMatcher(GraphIndex index) {
        this.index = index;
        initCache(this.index);
    }

    //For testing only
    protected SiriFuzzyTripMatcher(GraphIndex index, boolean forceCacheRebuild) {
        LOG.error("For testing only");
        this.index = index;

        if (forceCacheRebuild) {
            mappedTripsCache.clear();
        }
        initCache(this.index);
    }

    /**
     * Matches VehicleActivity to a set of possible Trips based on tripId
     */
    public Set<Trip> match(VehicleActivityStructure activity) {
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();

        if (monitoredVehicleJourney != null) {

            if (monitoredVehicleJourney.getCourseOfJourneyRef() != null) {
                //TripId is provided in VM-delivery
                return getCachedTripsBySiriId(monitoredVehicleJourney.getCourseOfJourneyRef().getValue());
            } else {
                // Find matches based on trip-data
                if (monitoredVehicleJourney.getDestinationRef() != null &&
                        monitoredVehicleJourney.getDestinationAimedArrivalTime() != null) {
                    String destinationRef = monitoredVehicleJourney.getDestinationRef().getValue();
                    ZonedDateTime arrivalTime = monitoredVehicleJourney.getDestinationAimedArrivalTime();
                    return start_stop_tripCache.get(createStartStopKey(destinationRef, arrivalTime.toLocalTime().toSecondOfDay()));
                }
            }

        }

        return null;
    }

    /**
     * Matches EstimatedVehicleJourney to a set of possible Trips based on tripId
     */
    public Set<Trip> match(EstimatedVehicleJourney journey) {
        Set<Trip> trips = null;
        if (journey.getVehicleRef() != null) {
            trips = getCachedTripsBySiriId(journey.getVehicleRef().getValue());
        }

        if (trips == null || trips.isEmpty()) {
            String datedVehicleRef = null;
            if (journey.getDatedVehicleJourneyRef() != null) {
                datedVehicleRef = journey.getDatedVehicleJourneyRef().getValue();
            } else if (journey.getFramedVehicleJourneyRef() != null) {
                datedVehicleRef = journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
            }
            if (datedVehicleRef != null) {
                trips = mappedTripsCache.get(datedVehicleRef);
            }
        }
        if (trips == null || trips.isEmpty()) {
            List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
            EstimatedCall lastStop = estimatedCalls.get(estimatedCalls.size() - 1);

            String lastStopPoint = lastStop.getStopPointRef().getValue();

            ZonedDateTime arrivalTime = lastStop.getAimedArrivalTime() != null ? lastStop.getAimedArrivalTime() : lastStop.getAimedDepartureTime();

            trips = start_stop_tripCache.get(createStartStopKey(lastStopPoint, arrivalTime.toLocalTime().toSecondOfDay()));
            if (trips == null) {
                //Attempt to fetch trips that started yesterday - i.e. add 24 hours to arrival-time
                int lastStopArrivalTime = arrivalTime.toLocalTime().toSecondOfDay() + (24 * 60 * 60);
                trips = start_stop_tripCache.get(createStartStopKey(lastStopPoint, lastStopArrivalTime));
            }

            if (trips == null || trips.isEmpty()) {
                //SIRI-data may report other platform, but still on the same Parent-stop
                String agencyId = index.agenciesForFeedId.keySet().iterator().next();
                Stop stop = index.stopForId.get(new AgencyAndId(agencyId, lastStopPoint));
                if (stop != null && stop.getParentStation() != null) {
                    Collection<Stop> allQuays = index.stopsForParentStation.get(new AgencyAndId(agencyId, stop.getParentStation()));
                    for (Stop quay : allQuays) {
                        Set<Trip> tripSet = start_stop_tripCache.get(createStartStopKey(quay.getId().getId(), arrivalTime.toLocalTime().toSecondOfDay()));
                        if (tripSet != null) {
                            if (trips == null) {
                                trips = tripSet;
                            } else {
                                trips.addAll(tripSet);
                            }
                        }
                    }
                }
            }
        }
        return trips;
    }

    private Set<Trip> getCachedTripsBySiriId(String tripId) {
        if (tripId == null) {return null;}
        return mappedTripsCache.getOrDefault(tripId, new HashSet<>());
    }

    private static void initCache(GraphIndex index) {
        if (mappedTripsCache.isEmpty()) {

            Set<Trip> trips = index.patternForTrip.keySet();
            for (Trip trip : trips) {

                TripPattern tripPattern = index.patternForTrip.get(trip);

                    String currentTripId = getUnpaddedTripId(trip.getId().getId());

                    if (mappedTripsCache.containsKey(currentTripId)) {
                        mappedTripsCache.get(currentTripId).add(trip);
                    } else {
                        Set<Trip> initialSet = new HashSet<>();
                        initialSet.add(trip);
                        mappedTripsCache.put(currentTripId, initialSet);
                    }

                if (tripPattern != null && tripPattern.mode.equals(TraverseMode.RAIL)) {
                    if (trip.getTripShortName() != null) {
                        String tripShortName = trip.getTripShortName();
                        if (mappedTripsCache.containsKey(tripShortName)) {
                            mappedTripsCache.get(tripShortName).add(trip);
                        } else {
                            Set<Trip> initialSet = new HashSet<>();
                            initialSet.add(trip);
                            mappedTripsCache.put(tripShortName, initialSet);
                        }
                    }
                }
                String lastStopId = tripPattern.getStops().get(tripPattern.getStops().size()-1).getId().getId();

                TripTimes tripTimes = tripPattern.scheduledTimetable.getTripTimes(trip);
                if (tripTimes != null) {
                    int arrivalTime = tripTimes.getArrivalTime(tripTimes.getNumStops() - 1);

                    String key = createStartStopKey(lastStopId, arrivalTime);
                    if (start_stop_tripCache.containsKey(key)) {
                        start_stop_tripCache.get(key).add(trip);
                    } else {
                        Set<Trip> initialSet = new HashSet<>();
                        initialSet.add(trip);
                        start_stop_tripCache.put(key, initialSet);
                    }
                }
            }
            Set<Route> routes = index.patternsForRoute.keySet();
            for (Route route : routes) {

                String currentRouteId = getUnpaddedTripId(route.getId().getId());
                if (mappedRoutesCache.containsKey(currentRouteId)) {
                    mappedRoutesCache.get(currentRouteId).add(route);
                } else {
                    Set<Route> initialSet = new HashSet<>();
                    initialSet.add(route);
                    mappedRoutesCache.put(currentRouteId, initialSet);
                }
            }

            LOG.info("Built route-cache [{}].", mappedRoutesCache.size());
            LOG.info("Built trips-cache [{}].", mappedTripsCache.size());
            LOG.info("Built start-stop-cache [{}].", start_stop_tripCache.size());
        }
    }

    public Trip getTrip (Route route, int direction,
                         int startTime, ServiceDate date) {
        BitSet services = index.servicesRunning(date);
        for (TripPattern pattern : index.patternsForRoute.get(route)) {
            if (pattern.directionId != direction) continue;
            for (TripTimes times : pattern.scheduledTimetable.tripTimes) {
                if (times.getScheduledDepartureTime(0) == startTime &&
                        services.get(times.serviceCode)) {
                    return times.trip;
                }
            }
        }
        return null;
    }

    private static String createStartStopKey(String lastStopId, int lastStopArrivalTime) {
        return lastStopId + ":" + lastStopArrivalTime;
    }

    private static String getUnpaddedTripId(String id) {
        if (id.indexOf("-") > 0) {
            return id.substring(0, id.indexOf("-"));
        } else {
            return id;
        }
    }

    public Set<Route> getRoutesForStop(AgencyAndId siriStopId) {
        Stop stop = index.stopForId.get(siriStopId);
        return index.routesForStop(stop);
    }

    public AgencyAndId getStop(String siriStopId) {
        Collection<Stop> stops = index.stopForId.values();
        for (Stop stop : stops) {
            if (stop.getId().getId().equals(siriStopId)) {
                return stop.getId();
            }
        }
        //No match found in quays - check parent-stops (stopplace)
        for (Stop stop : stops) {
            if (siriStopId.equals(stop.getParentStation())) {
                return new AgencyAndId(stop.getId().getAgencyId(), stop.getParentStation());
            }
        }
        return null;
    }

    public Set<Route> getRoutes(String lineRefValue) {
        return mappedRoutesCache.getOrDefault(lineRefValue, new HashSet<>());
    }

    public AgencyAndId getTripId(String vehicleJourney) {
        Collection<Trip> trips = index.tripForId.values();
        for (Trip trip : trips) {
            if (trip.getId().getId().equals(vehicleJourney)) {
                return trip.getId();
            }
        }
        return null;
    }

    public AgencyAndId getTripIdForTripShortNameServiceDateAndMode(String tripShortName, ServiceDate serviceDate, TraverseMode traverseMode) {

        Set<Trip> cachedTripsBySiriId = getCachedTripsBySiriId(tripShortName);

        for (Trip trip : cachedTripsBySiriId) {
            if (GtfsLibrary.getTraverseMode(trip.getRoute()).equals(traverseMode)) {
                Set<ServiceDate> serviceDates = index.graph.getCalendarService().getServiceDatesForServiceId(trip.getServiceId());

                if (serviceDates.contains(serviceDate) &&
                        trip.getTripShortName() != null &&
                        trip.getTripShortName().equals(tripShortName)) {
                    return trip.getId();
                }
            }
        }
        return null;
    }
}
