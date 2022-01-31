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

package org.opentripplanner.updater.stoptime;

import com.google.common.base.Preconditions;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.model.Agency;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.Operator;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.StopPattern;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.TransmodelTransportSubmode;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.routing.edgetype.Timetable;
import org.opentripplanner.routing.edgetype.TimetableSnapshot;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.trippattern.RealTimeState;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.updater.GtfsRealtimeFuzzyTripMatcher;
import org.opentripplanner.updater.SiriFuzzyTripMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri20.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.VehicleActivityCancellationStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleModesEnumeration;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import static org.opentripplanner.model.StopPattern.PICKDROP_NONE;
import static org.opentripplanner.model.StopPattern.PICKDROP_SCHEDULED;

/**
 * This class should be used to create snapshots of lookup tables of realtime data. This is
 * necessary to provide planning threads a consistent constant view of a graph with realtime data at
 * a specific point in time.
 */
public class TimetableSnapshotSource {
    private static final Logger LOG = LoggerFactory.getLogger(TimetableSnapshotSource.class);

    /**
     * Number of milliseconds per second
     */
    private static final int MILLIS_PER_SECOND = 1000;

    /**
     * Maximum time in seconds since midnight for arrivals and departures
     */
    private static final long MAX_ARRIVAL_DEPARTURE_TIME = 48 * 60 * 60;

    public int logFrequency = 2000;

    private int appliedBlockCount = 0;

    /**
     * If a timetable snapshot is requested less than this number of milliseconds after the previous
     * snapshot, just return the same one. Throttles the potentially resource-consuming task of
     * duplicating a TripPattern -> Timetable map and indexing the new Timetables.
     */
    public int maxSnapshotFrequency = 1000; // msec

    /**
     * The last committed snapshot that was handed off to a routing thread. This snapshot may be
     * given to more than one routing thread if the maximum snapshot frequency is exceeded.
     */
    private volatile TimetableSnapshot snapshot = null;

    /**
     * The working copy of the timetable snapshot. Should not be visible to routing threads. Should
     * only be modified by a thread that holds a lock on {@link #bufferLock}. All public methods that
     * might modify this buffer will correctly acquire the lock.
     */
    private final TimetableSnapshot buffer = new TimetableSnapshot();

    /**
     * Lock to indicate that buffer is in use
     */
    private final ReentrantLock bufferLock = new ReentrantLock(true);

    /**
     * A synchronized cache of trip patterns that are added to the graph due to GTFS-realtime messages.
     */
    private final TripPatternCache tripPatternCache = new TripPatternCache();

    /** Should expired realtime data be purged from the graph. */
    public boolean purgeExpiredData = true;

    protected ServiceDate lastPurgeDate = null;

    protected long lastSnapshotTime = -1;

    private final TimeZone timeZone;

    private final GraphIndex graphIndex;

    private final Agency dummyAgency;

    public GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher;
    public SiriFuzzyTripMatcher siriFuzzyTripMatcher;
    private String SIRI_FEED_ID;

    public TimetableSnapshotSource(final Graph graph) {
        timeZone = graph.getTimeZone();
        graphIndex = graph.index;

        // Create dummy agency for added trips
        Agency dummy = new Agency();
        dummy.setId("");
        dummy.setName("");
        dummyAgency = dummy;

        siriFuzzyTripMatcher = new SiriFuzzyTripMatcher(graphIndex);
        SIRI_FEED_ID = graphIndex.agenciesForFeedId.keySet().iterator().next();
    }

    public String getFeedId() {
        return SIRI_FEED_ID;
    }

    /**
     * @return an up-to-date snapshot mapping TripPatterns to Timetables. This snapshot and the
     *         timetable objects it references are guaranteed to never change, so the requesting
     *         thread is provided a consistent view of all TripTimes. The routing thread need only
     *         release its reference to the snapshot to release resources.
     */
    public TimetableSnapshot getTimetableSnapshot() {
        TimetableSnapshot snapshotToReturn;

        // Try to get a lock on the buffer
        if (bufferLock.tryLock()) {
            // Make a new snapshot if necessary
            try {
                snapshotToReturn = getTimetableSnapshot(false);
            } finally {
                bufferLock.unlock();
            }
        } else {
            // No lock could be obtained because there is either a snapshot commit busy or updates
            // are applied at this moment, just return the current snapshot
            snapshotToReturn = snapshot;
        }

        return snapshotToReturn;
    }

    private TimetableSnapshot getTimetableSnapshot(final boolean force) {
        final long now = System.currentTimeMillis();
        if (force || now - lastSnapshotTime > maxSnapshotFrequency) {
            if (force || buffer.isDirty()) {
                LOG.debug("Committing {}", buffer.toString());
                snapshot = buffer.commit(force);
            } else {
                LOG.debug("Buffer was unchanged, keeping old snapshot.");
            }
            lastSnapshotTime = System.currentTimeMillis();
        } else {
            LOG.debug("Snapshot frequency exceeded. Reusing snapshot {}", snapshot);
        }
        return snapshot;
    }

    /**
     * Method to apply a trip update list to the most recent version of the timetable snapshot. A
     * GTFS-RT feed is always applied against a single static feed (indicated by SIRI_FEED_ID).
<<<<<<< HEAD
     * 
=======
     *
     * However, multi-feed support is not completed and we currently assume there is only one static
     * feed when matching IDs.
     *
>>>>>>> 7296be8ffd532a13afb0bec263a9f436ab787022
     * @param graph graph to update (needed for adding/changing stop patterns)
     * @param fullDataset true iff the list with updates represent all updates that are active right
     *        now, i.e. all previous updates should be disregarded
     * @param updates GTFS-RT TripUpdate's that should be applied atomically
     * @param feedId
     */
    public void applyTripUpdates(final Graph graph, final boolean fullDataset, final List<TripUpdate> updates, final String feedId) {
        if (updates == null) {
            LOG.warn("updates is null");
            return;
        }

        // Acquire lock on buffer
        bufferLock.lock();

        try {
            if (fullDataset) {
                // Remove all updates from the buffer
                buffer.clear(feedId);
            }

            LOG.debug("message contains {} trip updates", updates.size());
            int uIndex = 0;
            for (TripUpdate tripUpdate : updates) {
                if (fuzzyTripMatcher != null && tripUpdate.hasTrip()) {
                    final TripDescriptor trip = fuzzyTripMatcher.match(feedId, tripUpdate.getTrip());
                    tripUpdate = tripUpdate.toBuilder().setTrip(trip).build();
                }

                if (!tripUpdate.hasTrip()) {
                    LOG.warn("Missing TripDescriptor in gtfs-rt trip update: \n{}", tripUpdate);
                    continue;
                }

                ServiceDate serviceDate = new ServiceDate();
                final TripDescriptor tripDescriptor = tripUpdate.getTrip();

                if (tripDescriptor.hasStartDate()) {
                    try {
                        serviceDate = ServiceDate.parseString(tripDescriptor.getStartDate());
                    } catch (final ParseException e) {
                        LOG.warn("Failed to parse start date in gtfs-rt trip update: \n{}", tripUpdate);
                        continue;
                    }
                } else {
                    // TODO: figure out the correct service date. For the special case that a trip
                    // starts for example at 40:00, yesterday would probably be a better guess.
                }

                uIndex += 1;
                LOG.debug("trip update #{} ({} updates) :",
                        uIndex, tripUpdate.getStopTimeUpdateCount());
                LOG.trace("{}", tripUpdate);

                // Determine what kind of trip update this is
                boolean applied = false;
                final TripDescriptor.ScheduleRelationship tripScheduleRelationship = determineTripScheduleRelationship(
                        tripUpdate);
                switch (tripScheduleRelationship) {
                    case SCHEDULED:
                        applied = handleScheduledTrip(tripUpdate, feedId, serviceDate);
                        break;
                    case ADDED:
                        applied = validateAndHandleAddedTrip(graph, tripUpdate, feedId, serviceDate);
                        break;
                    case UNSCHEDULED:
                        applied = handleUnscheduledTrip(tripUpdate, feedId, serviceDate);
                        break;
                    case CANCELED:
                        applied = handleCanceledTrip(tripUpdate, feedId, serviceDate);
                        break;
                    case MODIFIED:
                        applied = validateAndHandleModifiedTrip(graph, tripUpdate, feedId, serviceDate);
                        break;
                }

                if (applied) {
                    appliedBlockCount++;
                } else {
                    LOG.warn("Failed to apply TripUpdate.");
                    LOG.trace(" Contents: {}", tripUpdate);
                }

                if (appliedBlockCount % logFrequency == 0) {
                    LOG.info("Applied {} trip updates.", appliedBlockCount);
                }
            }
            LOG.debug("end of update message");

            // Make a snapshot after each message in anticipation of incoming requests
            // Purge data if necessary (and force new snapshot if anything was purged)
            // Make sure that the public (locking) getTimetableSnapshot function is not called.
            if (purgeExpiredData) {
                final boolean modified = purgeExpiredData();
                getTimetableSnapshot(modified);
            } else {
                getTimetableSnapshot(false);
            }
        } finally {
            // Always release lock
            bufferLock.unlock();
        }
    }

    /**
     * Method to apply a trip update list to the most recent version of the timetable snapshot.
     *
     *
     * @param graph graph to update (needed for adding/changing stop patterns)
     * @param fullDataset true iff the list with updates represent all updates that are active right
     *        now, i.e. all previous updates should be disregarded
     * @param updates SIRI VehicleMonitoringDeliveries that should be applied atomically
     */
    public void applyVehicleMonitoring(final Graph graph, final boolean fullDataset, final List<VehicleMonitoringDeliveryStructure> updates) {
        if (updates == null) {
            LOG.warn("updates is null");
            return;
        }

        // Acquire lock on buffer
        bufferLock.lock();

        try {
            if (fullDataset) {
                // Remove all updates from the buffer
                buffer.clear(SIRI_FEED_ID);
            }

            for (VehicleMonitoringDeliveryStructure vmDelivery : updates) {

                ServiceDate serviceDate = new ServiceDate();

                List<VehicleActivityStructure> activities = vmDelivery.getVehicleActivities();
                if (activities != null) {
                    //Handle activities
                    LOG.info("Handling {} VM-activities.", activities.size());
                    int handledCounter = 0;
                    int skippedCounter = 0;
                    for (VehicleActivityStructure activity : activities) {
                        boolean handled = handleModifiedTrip(graph, activity, serviceDate);
                        if (handled) {
                            handledCounter++;
                        } else {
                            skippedCounter++;
                        }
                    }
                    LOG.info("Applied {} VM-activities, skipped {}.", handledCounter, skippedCounter);
                }
                List<VehicleActivityCancellationStructure> cancellations = vmDelivery.getVehicleActivityCancellations();
                if (cancellations != null && !cancellations.isEmpty()) {
                    //Handle cancellations
                    LOG.info("TODO: Handle {} cancellations.", cancellations.size());
                }

                List<NaturalLanguageStringStructure> notes = vmDelivery.getVehicleActivityNotes();
                if (notes != null && !notes.isEmpty()) {
                    //Handle notes
                    LOG.info("TODO: Handle {} notes.", notes.size());
                }

            }

            // Make a snapshot after each message in anticipation of incoming requests
            // Purge data if necessary (and force new snapshot if anything was purged)
            // Make sure that the public (locking) getTimetableSnapshot function is not called.
            if (purgeExpiredData) {
                final boolean modified = purgeExpiredData();
                getTimetableSnapshot(modified);
            } else {
                getTimetableSnapshot(false);
            }
        } finally {
            // Always release lock
            bufferLock.unlock();
            if (keepLogging) {
                LOG.info("Reducing SIRI-VM logging until restart");
                keepLogging = false;
            }
        }
    }

    /**
     * Method to apply a trip update list to the most recent version of the timetable snapshot.
     *
     *  @param graph graph to update (needed for adding/changing stop patterns)
     * @param fullDataset true iff the list with updates represent all updates that are active right
     *        now, i.e. all previous updates should be disregarded
     * @param updates SIRI VehicleMonitoringDeliveries that should be applied atomically
     */
    public void applyEstimatedTimetable(final Graph graph, final boolean fullDataset, final List<EstimatedTimetableDeliveryStructure> updates) {
        if (updates == null) {
            LOG.warn("updates is null");
            return;
        }

        // Acquire lock on buffer
        bufferLock.lock();

        try {
            if (fullDataset) {
                // Remove all updates from the buffer
                buffer.clear(SIRI_FEED_ID);
            }

            for (EstimatedTimetableDeliveryStructure etDelivery : updates) {

                List<EstimatedVersionFrameStructure> estimatedJourneyVersions = etDelivery.getEstimatedJourneyVersionFrames();
                if (estimatedJourneyVersions != null) {
                    //Handle deliveries
                    for (EstimatedVersionFrameStructure estimatedJourneyVersion : estimatedJourneyVersions) {
                        List<EstimatedVehicleJourney> journeys = estimatedJourneyVersion.getEstimatedVehicleJourneies();
                        LOG.debug("Handling {} EstimatedVehicleJourneys.", journeys.size());
                        int handledCounter = 0;
                        int skippedCounter = 0;
                        int addedCounter = 0;
                        int notMonitoredCounter = 0;
                        for (EstimatedVehicleJourney journey : journeys) {
                            if (journey.isExtraJourney() != null && journey.isExtraJourney()) {
                                // Added trip
                                try {
                                    if (handleAddedTrip(graph, journey)) {
                                        addedCounter++;
                                    } else {
                                        skippedCounter++;
                                    }
                                } catch (Throwable t) {
                                    // Since this is work in progress - catch everything to continue processing updates
                                    LOG.warn("Adding ExtraJourney with id='{}' failed with '{}'.", journey.getEstimatedVehicleJourneyCode(), t.getMessage());
                                    skippedCounter++;
                                }
                            } else {
                                // Updated trip
                                if (handleModifiedTrip(graph, journey)) {
                                    handledCounter++;
                                } else {
                                    if (journey.isMonitored() != null && !journey.isMonitored()) {
                                        notMonitoredCounter++;
                                    } else {
                                        skippedCounter++;
                                    }
                                }
                            }
                        }
                        LOG.debug("Processed EstimatedVehicleJourneys: updated {}, added {}, skipped {}, not monitored {}.", handledCounter, addedCounter, skippedCounter, notMonitoredCounter);
                    }
                }
            }

            LOG.debug("message contains {} trip updates", updates.size());
            int uIndex = 0;
            LOG.debug("end of update message");

            // Make a snapshot after each message in anticipation of incoming requests
            // Purge data if necessary (and force new snapshot if anything was purged)
            // Make sure that the public (locking) getTimetableSnapshot function is not called.
            if (purgeExpiredData) {
                final boolean modified = purgeExpiredData();
                getTimetableSnapshot(modified);
            } else {
                getTimetableSnapshot(false);
            }
        } finally {
            // Always release lock
            bufferLock.unlock();
        }
    }

    /**
     * Returns any new TripPatterns added by real time information for a given stop.
     *
     * @param stop the stop
     * @return list of TripPatterns created by real time sources for the stop.
     */
    public List<TripPattern> getAddedTripPatternsForStop(Stop stop) {
        return tripPatternCache.getAddedTripPatternsForStop(stop);
    }

    private static boolean keepLogging = true;

    private boolean handleModifiedTrip(Graph graph, VehicleActivityStructure activity, ServiceDate serviceDate) {
        if (activity.getValidUntilTime().isBefore(ZonedDateTime.now())) {
            //Activity has expired
            return false;
        }


        if (activity.getMonitoredVehicleJourney() == null ||
                activity.getMonitoredVehicleJourney().getVehicleRef() == null ||
                activity.getMonitoredVehicleJourney().getLineRef() == null) {
            //No vehicle reference or line reference
            return false;
        }

        Boolean isMonitored = activity.getMonitoredVehicleJourney().isMonitored();
        if (isMonitored != null && !isMonitored) {
            //Vehicle is reported as NOT monitored
            return false;
        }

        Set<Trip> trips = siriFuzzyTripMatcher.match(activity);

        if (trips == null || trips.isEmpty()) {
            if (keepLogging) {
                String lineRef = (activity.getMonitoredVehicleJourney().getLineRef() != null ? activity.getMonitoredVehicleJourney().getLineRef().getValue():null);
                String vehicleRef = (activity.getMonitoredVehicleJourney().getVehicleRef() != null ? activity.getMonitoredVehicleJourney().getVehicleRef().getValue():null);
                String tripId =  (activity.getMonitoredVehicleJourney().getCourseOfJourneyRef() != null ? activity.getMonitoredVehicleJourney().getCourseOfJourneyRef().getValue():null);
                LOG.debug("No trip found for [isMonitored={}, lineRef={}, vehicleRef={}, tripId={}], skipping VehicleActivity.", isMonitored, lineRef, vehicleRef, tripId);
            }

            return false;
        }

        //Find the trip that best corresponds to MonitoredVehicleJourney
        Trip trip = getTripForJourney(trips, activity.getMonitoredVehicleJourney());

        if (trip == null) {
            return false;
        }

        final Set<TripPattern> patterns = getPatternsForTrip(trips, activity.getMonitoredVehicleJourney());

        if (patterns == null) {
            return false;
        }
        boolean success = false;
        for (TripPattern pattern : patterns) {
            if (handleTripPatternUpdate(graph, pattern, activity, trip, serviceDate)) {
                success = true;
            }
        }

        if (!success) {
            LOG.info("Pattern not updated for trip " + trip.getId());
        }
        return success;
    }

    private boolean handleTripPatternUpdate(Graph graph, TripPattern pattern, VehicleActivityStructure activity, Trip trip, ServiceDate serviceDate) {

        // Apply update on the *scheduled* time table and set the updated trip times in the buffer
        final TripTimes updatedTripTimes = getCurrentTimetable(pattern, serviceDate)
            .createUpdatedTripTimes(graph, activity, trip.getId());

        if (updatedTripTimes == null) {
            return false;
        }

        final boolean success = buffer.update(SIRI_FEED_ID, pattern, updatedTripTimes, serviceDate);

        return success;
    }

    /**
     * Get the latest timetable for TripPattern for a given service date.
     *
     * Snapshot timetable is used as source if initialised, trip patterns scheduled timetable if not.
     *
     */
    private Timetable getCurrentTimetable(TripPattern tripPattern, ServiceDate serviceDate) {
        TimetableSnapshot timetableSnapshot = getTimetableSnapshot();
        if (timetableSnapshot!=null) {
            return getTimetableSnapshot().resolve(tripPattern, serviceDate);
        }
        return tripPattern.scheduledTimetable;
    }

    private boolean handleAddedTrip(Graph graph, EstimatedVehicleJourney estimatedVehicleJourney) {

        // Verifying values required in SIRI Profile

        // Added ServiceJourneyId
        String newServiceJourneyRef = estimatedVehicleJourney.getEstimatedVehicleJourneyCode();
        Preconditions.checkNotNull(newServiceJourneyRef, "EstimatedVehicleJourneyCode is required");

        // Replaced/duplicated ServiceJourneyId
//        VehicleJourneyRef existingServiceJourneyRef = estimatedVehicleJourney.getVehicleJourneyRef();
//        Preconditions.checkNotNull(existingServiceJourneyRef, "VehicleJourneyRef is required");

        // LineRef of added trip
        Preconditions.checkNotNull(estimatedVehicleJourney.getLineRef(), "LineRef is required");
        String lineRef = estimatedVehicleJourney.getLineRef().getValue();

        //OperatorRef of added trip
        Preconditions.checkNotNull(estimatedVehicleJourney.getOperatorRef(), "OperatorRef is required");
        String operatorRef = estimatedVehicleJourney.getOperatorRef().getValue();

        //Required in SIRI, but currently not in use by OTP
//        Preconditions.checkNotNull(estimatedVehicleJourney.getRouteRef(), "RouteRef is required");
//        String routeRef = estimatedVehicleJourney.getRouteRef().getValue();

//        Preconditions.checkNotNull(estimatedVehicleJourney.getGroupOfLinesRef(), "GroupOfLinesRef is required");
//        String groupOfLines = estimatedVehicleJourney.getGroupOfLinesRef().getValue();

//        Preconditions.checkNotNull(estimatedVehicleJourney.getExternalLineRef(), "ExternalLineRef is required");

        String externalLineRef;
        if (estimatedVehicleJourney.getExternalLineRef() != null) {
            externalLineRef = estimatedVehicleJourney.getExternalLineRef().getValue();
        } else {
            externalLineRef = lineRef;
        }

        Operator operator = graphIndex.operatorForId.get(new AgencyAndId(SIRI_FEED_ID, operatorRef));
        Preconditions.checkNotNull(operator, "Operator " + operatorRef + " is unknown");

        AgencyAndId tripId = new AgencyAndId(SIRI_FEED_ID, newServiceJourneyRef);
        AgencyAndId serviceId = new AgencyAndId(SIRI_FEED_ID, newServiceJourneyRef);

        Route replacedRoute = null;
        if (externalLineRef != null) {
            replacedRoute = graph.index.routeForId.get(new AgencyAndId(SIRI_FEED_ID, externalLineRef));
        }

        AgencyAndId routeId = new AgencyAndId(SIRI_FEED_ID, lineRef);
        Route route = graph.index.routeForId.get(routeId);

        if (route == null) { // Route is unknown - create new
            route = new Route();
            route.setId(routeId);
            route.setType(getRouteType(estimatedVehicleJourney.getVehicleModes()));
            route.setOperator(operator);

            // TODO: Is there a better way to find authority/Agency?
            // Finding first Route with same Operator, and using same Authority
            Agency agency = graph.index.routeForId.values().stream()
                    .filter(route1 -> route1 != null &&
                            route1.getOperator() != null &&
                            route1.getOperator().equals(operator))
                    .findFirst().get().getAgency();
            route.setAgency(agency);

            if (estimatedVehicleJourney.getPublishedLineNames() != null && !estimatedVehicleJourney.getPublishedLineNames().isEmpty()) {
                route.setShortName("" + estimatedVehicleJourney.getPublishedLineNames().get(0).getValue());
            } else {
                route.setShortName("");
            }
            LOG.info("Adding route {} to graph.", routeId);
            graph.index.routeForId.put(routeId, route);
        }

        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setRoute(route);

        // Set transport-submode base on replaced- and replacement-route
        if (replacedRoute != null) {
            if (replacedRoute.getType() >= 100 && replacedRoute.getType() < 200) { // Replaced-route is RAIL
                if (route.getType() == 100) {
                    // Replacement-route is also RAIL
                    trip.setTransportSubmode(TransmodelTransportSubmode.REPLACEMENT_RAIL_SERVICE);
                } else if (route.getType() == 700 | route.getType() == 714) {
                    // Replacement-route is BUS or RAIL_REPLACEMENT_BUS
                    trip.setTransportSubmode(TransmodelTransportSubmode.RAIL_REPLACEMENT_BUS);
                }
            }
        }

        trip.setServiceId(serviceId);

        // TODO: PublishedLineName not defined in SIRI-profile
        if (estimatedVehicleJourney.getPublishedLineNames() != null && !estimatedVehicleJourney.getPublishedLineNames().isEmpty()) {
            trip.setRouteShortName("" + estimatedVehicleJourney.getPublishedLineNames().get(0).getValue());
        }

        // Use destinationName as default headsign - if proved
        if (estimatedVehicleJourney.getDestinationNames() != null && !estimatedVehicleJourney.getDestinationNames().isEmpty()) {
            trip.setTripHeadsign("" + estimatedVehicleJourney.getDestinationNames().get(0).getValue());
        }

        trip.setTripOperator(operator);

        String vehicleJourneyRef = (estimatedVehicleJourney.getVehicleJourneyRef() != null ? estimatedVehicleJourney.getVehicleJourneyRef().getValue() : null);
        trip.setReplacementForTripId(vehicleJourneyRef);

        // TODO: Populate these?
        trip.setShapeId(null);          // Replacement-trip has different shape
        trip.setTripPrivateCode(null);
        trip.setTripPublicCode(null);
        trip.setBlockId(null);
        trip.setTripShortName(null);
        trip.setKeyValues(null);

        List<Stop> addedStops = new ArrayList<>();
        List<StopTime> aimedStopTimes = new ArrayList<>();

        List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
        ZonedDateTime departureDate = null;
        for (int i = 0; i < estimatedCalls.size(); i++) {
            EstimatedCall estimatedCall = estimatedCalls.get(i);

            Stop stop = getStopForStopId(SIRI_FEED_ID,estimatedCall.getStopPointRef().getValue());

            Preconditions.checkNotNull(stop, "Unknown stop-id: " + estimatedCall.getStopPointRef().getValue());

            StopTime stopTime = new StopTime();
            stopTime.setStop(stop);
            stopTime.setStopSequence(i);
            stopTime.setTrip(trip);

            ZonedDateTime aimedArrivalTime = estimatedCall.getAimedArrivalTime();
            ZonedDateTime aimedDepartureTime = estimatedCall.getAimedDepartureTime();

            if (departureDate == null) {
                // Need to set departureDate from first stop to calculate correct ServiceDate when trip passes midnight
                departureDate = aimedDepartureTime;
            }

            if (aimedArrivalTime != null) {
                stopTime.setArrivalTime(calculateSecondsSinceMidnight(departureDate, aimedArrivalTime));
            }
            if (aimedDepartureTime != null) {
                stopTime.setDepartureTime(calculateSecondsSinceMidnight(departureDate, aimedDepartureTime));
            }

            if (estimatedCall.getArrivalBoardingActivity() == ArrivalBoardingActivityEnumeration.ALIGHTING) {
                stopTime.setDropOffType(PICKDROP_SCHEDULED);
            } else {
                stopTime.setDropOffType(PICKDROP_NONE);
            }

            if (estimatedCall.getDepartureBoardingActivity() == DepartureBoardingActivityEnumeration.BOARDING) {
                stopTime.setPickupType(PICKDROP_SCHEDULED);
            } else {
                stopTime.setPickupType(PICKDROP_NONE);
            }

            if (estimatedCall.getDestinationDisplaies() != null && !estimatedCall.getDestinationDisplaies().isEmpty()) {
                NaturalLanguageStringStructure destinationDisplay = estimatedCall.getDestinationDisplaies().get(0);
                stopTime.setStopHeadsign(destinationDisplay.getValue());
            } else if (trip.getTripHeadsign() == null) {
                // Fallback to empty string
                stopTime.setStopHeadsign("");
            }

            if (i == 0) {
                // Fake arrival on first stop
                stopTime.setArrivalTime(stopTime.getDepartureTime());
            } else if (i == (estimatedCalls.size() - 1)) {
                // Fake departure from last stop
                stopTime.setDepartureTime(stopTime.getArrivalTime());
            }

            addedStops.add(stop);
            aimedStopTimes.add(stopTime);
        }

        StopPattern stopPattern = new StopPattern(aimedStopTimes);
        TripPattern pattern = new TripPattern(trip.getRoute(), stopPattern, GtfsLibrary.getTraverseMode(trip));

        TripTimes tripTimes = new TripTimes(trip, aimedStopTimes, graph.deduplicator);

        boolean isJourneyPredictionInaccurate = (estimatedVehicleJourney.isPredictionInaccurate() != null && estimatedVehicleJourney.isPredictionInaccurate());

        // If added trip is updated with realtime - loop through and add delays
        for (int i = 0; i < estimatedCalls.size(); i++) {
            EstimatedCall estimatedCall = estimatedCalls.get(i);
            ZonedDateTime expectedArrival = estimatedCall.getExpectedArrivalTime();
            ZonedDateTime expectedDeparture = estimatedCall.getExpectedDepartureTime();

            int aimedArrivalTime = aimedStopTimes.get(i).getArrivalTime();
            int aimedDepartureTime = aimedStopTimes.get(i).getDepartureTime();

            if (expectedArrival != null) {
                int expectedArrivalTime = calculateSecondsSinceMidnight(departureDate, expectedArrival);
                tripTimes.updateArrivalDelay(i, expectedArrivalTime - aimedArrivalTime);
            }
            if (expectedDeparture != null) {
                int expectedDepartureTime = calculateSecondsSinceMidnight(departureDate, expectedDeparture);
                tripTimes.updateDepartureDelay(i, expectedDepartureTime - aimedDepartureTime);
            }

            if (estimatedCall.isCancellation() != null) {
                tripTimes.setCancelledStop(i,  estimatedCall.isCancellation());
            }

            boolean isCallPredictionInaccurate = estimatedCall.isPredictionInaccurate() != null && estimatedCall.isPredictionInaccurate();
            tripTimes.setPredictionInaccurate(i, (isJourneyPredictionInaccurate | isCallPredictionInaccurate));

            if (i == 0) {
                // Fake arrival on first stop
                tripTimes.updateArrivalTime(i,tripTimes.getDepartureTime(i));
            } else if (i == (estimatedCalls.size() - 1)) {
                // Fake departure from last stop
                tripTimes.updateDepartureTime(i,tripTimes.getArrivalTime(i));
            }
        }

        // Adding trip to index necessary to include values in graphql-queries
        // TODO: should more data be added to index?
        graph.index.tripForId.put(tripId, trip);
        graph.index.patternForTrip.put(trip, pattern);

        if (estimatedVehicleJourney.isCancellation() != null && estimatedVehicleJourney.isCancellation()) {
            tripTimes.cancel();
        } else {
            tripTimes.setRealTimeState(RealTimeState.ADDED);
        }


        if (!graph.serviceCodes.containsKey(serviceId)) {
            graph.serviceCodes.put(serviceId, graph.serviceCodes.size());
        }
        tripTimes.serviceCode = graph.serviceCodes.get(serviceId);

        pattern.add(tripTimes);

        Preconditions.checkState(tripTimes.timesIncreasing(), "Non-increasing triptimes for added trip");

        ServiceDate serviceDate = getServiceDateForEstimatedVehicleJourney(estimatedVehicleJourney);

        if (graph.getCalendarService().getServiceDatesForServiceId(serviceId) == null ||
                graph.getCalendarService().getServiceDatesForServiceId(serviceId).isEmpty()) {
            LOG.info("Adding serviceId {} to CalendarService", serviceId);
           graph.getCalendarService().addServiceIdAndServiceDates(serviceId, Arrays.asList(serviceDate));
        }


        return addTripToGraphAndBuffer(SIRI_FEED_ID, graph, trip, aimedStopTimes, addedStops, tripTimes, serviceDate);
    }

    /*
     * Resolves TransportMode from SIRI VehicleMode
     */
    private int getRouteType(List<VehicleModesEnumeration> vehicleModes) {
        if (vehicleModes != null && !vehicleModes.isEmpty()) {
            VehicleModesEnumeration vehicleModesEnumeration = vehicleModes.get(0);
            switch (vehicleModesEnumeration) {
                case RAIL:
                    return 100;
                case COACH:
                    return 200;
                case BUS:
                    return 700;
                case METRO:
                    return 701;
                case TRAM:
                    return 900;
                case FERRY:
                    return 1000;
                case AIR:
                    return 1100;
            }
        }
        return 700;
    }

    private boolean handleModifiedTrip(Graph graph, EstimatedVehicleJourney estimatedVehicleJourney) {

        //Check if EstimatedVehicleJourney is reported as NOT monitored
        if (estimatedVehicleJourney.isMonitored() != null && !estimatedVehicleJourney.isMonitored()) {
            //Ignore the notMonitored-flag if the journey is NOT monitored because it has been cancelled
            if (estimatedVehicleJourney.isCancellation() != null && !estimatedVehicleJourney.isCancellation()) {
                return false;
            }
        }

        //Values used in logging
        String operatorRef = (estimatedVehicleJourney.getOperatorRef() != null ? estimatedVehicleJourney.getOperatorRef().getValue() : null);
        String vehicleModes = "" + estimatedVehicleJourney.getVehicleModes();
        String lineRef = estimatedVehicleJourney.getLineRef().getValue();
        String vehicleRef = (estimatedVehicleJourney.getVehicleRef() != null ? estimatedVehicleJourney.getVehicleRef().getValue() : null);

        ServiceDate serviceDate = getServiceDateForEstimatedVehicleJourney(estimatedVehicleJourney);

        if (serviceDate == null) {
            return false;
        }

        Set<TripTimes> times = new HashSet<>();
        Set<TripPattern> patterns = new HashSet<>();

        Trip tripMatchedByServiceJourneyId = siriFuzzyTripMatcher.findTripByDatedVehicleJourneyRef(estimatedVehicleJourney);

        if (tripMatchedByServiceJourneyId != null) {
            /*
              Found exact match
             */
            TripPattern exactPattern = graphIndex.patternForTrip.get(tripMatchedByServiceJourneyId);

            if (exactPattern != null) {
                var timetable = getCurrentTimetable(exactPattern, serviceDate);
                var exactUpdatedTripTimes = timetable.createUpdatedTripTimes(
                    graph, estimatedVehicleJourney, timeZone, tripMatchedByServiceJourneyId.getId()
                );
                if (exactUpdatedTripTimes != null) {
                    times.add(exactUpdatedTripTimes);
                    patterns.add(exactPattern);
                } else {
                    LOG.info("Failed to update TripTimes for trip found by exact match {}", tripMatchedByServiceJourneyId.getId());
                    return false;
                }
            }
        } else {
            /*
                No exact match found - search for trips based on arrival-times/stop-patterns
             */
            Set<Trip> trips = siriFuzzyTripMatcher.match(estimatedVehicleJourney);

            if (trips == null || trips.isEmpty()) {
                LOG.debug("No trips found for EstimatedVehicleJourney. [operator={}, vehicleModes={}, lineRef={}, vehicleRef={}]", operatorRef, vehicleModes, lineRef, vehicleRef);
                return false;
            }

            //Find the trips that best corresponds to EstimatedVehicleJourney
            Set<Trip> matchingTrips = getTripForJourney(trips, estimatedVehicleJourney);

            if (matchingTrips == null || matchingTrips.isEmpty()) {
                LOG.debug("Found no matching trip for SIRI ET (serviceDate, departureTime). [operator={}, vehicleModes={}, lineRef={}, vehicleJourneyRef={}]", operatorRef, vehicleModes, lineRef, vehicleRef);
                return false;
            }

            for (Trip matchingTrip : matchingTrips) {
                TripPattern pattern = getPatternForTrip(matchingTrip, estimatedVehicleJourney);
                if (pattern != null) {
                    var timetable = getCurrentTimetable(pattern, serviceDate);
                    var updatedTripTimes = timetable.createUpdatedTripTimes(
                        graph, estimatedVehicleJourney, timeZone, matchingTrip.getId()
                    );
                    if (updatedTripTimes != null) {
                        patterns.add(pattern);
                        times.add(updatedTripTimes);
                    }
                }
            }
        }

        if (patterns.isEmpty()) {
            LOG.debug("Found no matching pattern for SIRI ET (firstStopId, lastStopId, numberOfStops). [operator={}, vehicleModes={}, lineRef={}, vehicleRef={}]", operatorRef, vehicleModes, lineRef, vehicleRef);
            return false;
        }

        if (times.isEmpty()) {
            return false;
        }

        boolean result = false;
        for (TripTimes tripTimes : times) {
            Trip trip = tripTimes.trip;
            for (TripPattern pattern : patterns) {
                if (tripTimes.getNumStops() == pattern.stopPattern.stops.length) {
                        // All tripTimes should be handled the same way to always allow latest realtime-update
                        // to replace previous update regardless of realtimestate
                        cancelScheduledTrip(SIRI_FEED_ID, trip.getId().getId(), serviceDate);

                        // Check whether trip id has been used for previously ADDED/MODIFIED trip message and cancel
                        // previously created trip
                        cancelPreviouslyAddedTrip(SIRI_FEED_ID, trip.getId().getId(), serviceDate);

                        // Calculate modified stop-pattern
                        Timetable currentTimetable = getCurrentTimetable(pattern, serviceDate);
                        List<Stop> modifiedStops = currentTimetable.createModifiedStops(estimatedVehicleJourney, graphIndex);
                        List<StopTime> modifiedStopTimes = currentTimetable.createModifiedStopTimes(tripTimes, estimatedVehicleJourney, trip, graphIndex);

                        if (modifiedStops != null && modifiedStops.isEmpty()) {
                            tripTimes.cancel();
                        }

                        // Add updated trip
                        result = result | addTripToGraphAndBuffer(SIRI_FEED_ID, graph, trip, modifiedStopTimes, modifiedStops, tripTimes, serviceDate);

                    LOG.debug("Applied realtime data for trip {}", trip.getId().getId());
                } else {
                    LOG.debug("Ignoring update since number of stops do not match");
                }
            }
        }

        return result;
    }

    private ServiceDate getServiceDateForEstimatedVehicleJourney(EstimatedVehicleJourney estimatedVehicleJourney) {
        ZonedDateTime date;
        if (estimatedVehicleJourney.getRecordedCalls() != null && !estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()){
            date = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls().get(0).getAimedDepartureTime();
        } else {
            EstimatedCall firstCall = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls().get(0);
            date = firstCall.getAimedDepartureTime();
        }

        if (date == null) {
            return null;
        }


        return new ServiceDate(date.withZoneSameInstant(timeZone.toZoneId()).toLocalDate());
    }

    private int calculateSecondsSinceMidnight(ZonedDateTime dateTime) {
        return calculateSecondsSinceMidnight(dateTime, dateTime);
    }

    private int calculateSecondsSinceMidnight(ZonedDateTime departureDate, ZonedDateTime dateTime) {

        int daysBetween = 0;
        if (departureDate.getDayOfMonth() != dateTime.getDayOfMonth()) {
            ZonedDateTime midnightOnDepartureDate = departureDate.withZoneSameInstant(timeZone.toZoneId()).withHour(12).withMinute(0).withSecond(0).minusHours(12);
            ZonedDateTime midnightOnCurrentStop = dateTime.withZoneSameInstant(timeZone.toZoneId()).withHour(12).withMinute(0).withSecond(0).minusHours(12);
            daysBetween = (int) ChronoUnit.DAYS.between(midnightOnDepartureDate.withZoneSameInstant(timeZone.toZoneId()), midnightOnCurrentStop.withZoneSameInstant(ZoneId.systemDefault()));
        }
        // If first departure was 'yesterday' - add 24h
        int daysSinceDeparture = daysBetween * (24 * 60 * 60);

        return dateTime.withZoneSameInstant(timeZone.toZoneId()).toLocalTime().toSecondOfDay() + daysSinceDeparture;
    }

    /**
     * Determine how the trip update should be handled.
     *
     * @param tripUpdate trip update
     * @return TripDescriptor.ScheduleRelationship indicating how the trip update should be handled
     */
    private TripDescriptor.ScheduleRelationship determineTripScheduleRelationship(final TripUpdate tripUpdate) {
        // Assume default value
        TripDescriptor.ScheduleRelationship tripScheduleRelationship = TripDescriptor.ScheduleRelationship.SCHEDULED;

        // If trip update contains schedule relationship, use it
        if (tripUpdate.hasTrip() && tripUpdate.getTrip().hasScheduleRelationship()) {
            tripScheduleRelationship = tripUpdate.getTrip().getScheduleRelationship();
        }

        if (tripScheduleRelationship.equals(TripDescriptor.ScheduleRelationship.SCHEDULED)) {
            // Loop over stops to check whether there are ADDED or SKIPPED stops
            boolean hasModifiedStops = false;
            for (final StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
                // Check schedule relationship
                if (stopTimeUpdate.hasScheduleRelationship()) {
                    final StopTimeUpdate.ScheduleRelationship stopScheduleRelationship = stopTimeUpdate
                            .getScheduleRelationship();
                    if (stopScheduleRelationship.equals(StopTimeUpdate.ScheduleRelationship.SKIPPED)
                            // TODO: uncomment next line when StopTimeUpdate.ScheduleRelationship.ADDED exists
//                            || stopScheduleRelationship.equals(StopTimeUpdate.ScheduleRelationship.ADDED)
                            ) {
                        hasModifiedStops = true;
                    }
                }
            }

            // If stops are modified, handle trip update like a modified trip
            if (hasModifiedStops) {
                tripScheduleRelationship = TripDescriptor.ScheduleRelationship.MODIFIED;
            }
        }

        return tripScheduleRelationship;
    }

    private boolean handleScheduledTrip(final TripUpdate tripUpdate, final String feedId, final ServiceDate serviceDate) {
        final TripDescriptor tripDescriptor = tripUpdate.getTrip();
        // This does not include Agency ID or feed ID, trips are feed-unique and we currently assume a single static feed.
        final String tripId = tripDescriptor.getTripId();
        final TripPattern pattern = getPatternForTripId(feedId, tripId);

        if (pattern == null) {
            LOG.warn("No pattern found for tripId {}, skipping TripUpdate.", tripId);
            return false;
        }

        if (tripUpdate.getStopTimeUpdateCount() < 1) {
            LOG.warn("TripUpdate contains no updates, skipping.");
            return false;
        }

        // Apply update on the *scheduled* time table and set the updated trip times in the buffer
        final TripTimes updatedTripTimes = pattern.scheduledTimetable.createUpdatedTripTimes(tripUpdate,
                timeZone, serviceDate);

        if (updatedTripTimes == null) {
            return false;
        }

        // Make sure that updated trip times have the correct real time state
        updatedTripTimes.setRealTimeState(RealTimeState.UPDATED);

        final boolean success = buffer.update(feedId, pattern, updatedTripTimes, serviceDate);
        return success;
    }

    /**
     * Validate and handle GTFS-RT TripUpdate message containing an ADDED trip.
     *
     * @param graph graph to update
     * @param tripUpdate GTFS-RT TripUpdate message
     * @param feedId
     * @param serviceDate
     * @return true iff successful
     */
    private boolean validateAndHandleAddedTrip(final Graph graph, final TripUpdate tripUpdate,
            final String feedId, final ServiceDate serviceDate) {
        // Preconditions
        Preconditions.checkNotNull(graph);
        Preconditions.checkNotNull(tripUpdate);
        Preconditions.checkNotNull(serviceDate);

        //
        // Validate added trip
        //

        // Check whether trip id of ADDED trip is available
        final TripDescriptor tripDescriptor = tripUpdate.getTrip();
        if (!tripDescriptor.hasTripId()) {
            LOG.warn("No trip id found for ADDED trip, skipping.");
            return false;
        }

        // Check whether trip id already exists in graph
        final String tripId = tripDescriptor.getTripId();
        final Trip trip = getTripForTripId(feedId, tripId);
        if (trip != null) {
            // TODO: should we support this and add a new instantiation of this trip (making it
            // frequency based)?
            LOG.warn("Graph already contains trip id of ADDED trip, skipping.");
            return false;
        }

        // Check whether a start date exists
        if (!tripDescriptor.hasStartDate()) {
            // TODO: should we support this and apply update to all days?
            LOG.warn("ADDED trip doesn't have a start date in TripDescriptor, skipping.");
            return false;
        }

        // Check whether at least two stop updates exist
        if (tripUpdate.getStopTimeUpdateCount() < 2) {
            LOG.warn("ADDED trip has less then two stops, skipping.");
            return false;
        }

        // Check whether all stop times are available and all stops exist
        final List<Stop> stops = checkNewStopTimeUpdatesAndFindStops(feedId, tripUpdate);
        if (stops == null) {
            return false;
        }

        //
        // Handle added trip
        //

        final boolean success = handleAddedTrip(graph, tripUpdate, stops, feedId, serviceDate);
        return success;
    }

    /**
     * Check stop time updates of trip update that results in a new trip (ADDED or MODIFIED) and
     * find all stops of that trip.
     *
     * @param feedId feed id this trip update is intented for
     * @param tripUpdate trip update
     * @return stops when stop time updates are correct; null if there are errors
     */
    private List<Stop> checkNewStopTimeUpdatesAndFindStops(final String feedId, final TripUpdate tripUpdate) {
        Integer previousStopSequence = null;
        Long previousTime = null;
        final List<StopTimeUpdate> stopTimeUpdates = tripUpdate.getStopTimeUpdateList();
        final List<Stop> stops = new ArrayList<>(stopTimeUpdates.size());
        for (int index = 0; index < stopTimeUpdates.size(); ++index) {
            final StopTimeUpdate stopTimeUpdate = stopTimeUpdates.get(index);

            // Determine whether stop is skipped
            final boolean skippedStop = isStopSkipped(stopTimeUpdate);

            // Check stop sequence
            if (stopTimeUpdate.hasStopSequence()) {
                final Integer stopSequence = stopTimeUpdate.getStopSequence();

                // Check non-negative
                if (stopSequence < 0) {
                    LOG.warn("Trip update contains negative stop sequence, skipping.");
                    return null;
                }

                // Check whether sequence is increasing
                if (previousStopSequence != null && previousStopSequence > stopSequence) {
                    LOG.warn("Trip update contains decreasing stop sequence, skipping.");
                    return null;
                }
                previousStopSequence = stopSequence;
            } else {
                // Allow missing stop sequences for ADDED and MODIFIED trips
            }

            // Find stops
            if (stopTimeUpdate.hasStopId()) {
                // Find stop
                final Stop stop = getStopForStopId(feedId, stopTimeUpdate.getStopId());
                if (stop != null) {
                    // Remember stop
                    stops.add(stop);
                } else if (skippedStop) {
                    // Set a null value for a skipped stop
                    stops.add(null);
                } else {
                    LOG.warn("Graph doesn't contain stop id \"{}\" of trip update, skipping.",
                            stopTimeUpdate.getStopId());
                    return null;
                }
            } else {
                LOG.warn("Trip update misses some stop ids, skipping.");
                return null;
            }

            // Only check arrival and departure times for non-skipped stops
            if (!skippedStop) {
                // Check arrival time
                if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasTime()) {
                    // Check for increasing time
                    final Long time = stopTimeUpdate.getArrival().getTime();
                    if (previousTime != null && previousTime > time) {
                        LOG.warn("Trip update contains decreasing times, skipping.");
                        return null;
                    }
                    previousTime = time;
                } else {
                    // Only first non-skipped stop is allowed to miss arrival time
                    // TODO: should we support only requiring an arrival time on the last stop and interpolate?
                    for (int earlierIndex = 0; earlierIndex < index; earlierIndex++) {
                        final StopTimeUpdate earlierStopTimeUpdate = stopTimeUpdates.get(earlierIndex);
                        // Determine whether earlier stop is skipped
                        final boolean earlierSkippedStop = isStopSkipped(earlierStopTimeUpdate);
                        if (!earlierSkippedStop) {
                            LOG.warn("Trip update misses arrival time, skipping.");
                            return null;
                        }
                    }
                }

                // Check departure time
                if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasTime()) {
                    // Check for increasing time
                    final Long time = stopTimeUpdate.getDeparture().getTime();
                    if (previousTime != null && previousTime > time) {
                        LOG.warn("Trip update contains decreasing times, skipping.");
                        return null;
                    }
                    previousTime = time;
                } else {
                    // Only last non-skipped stop is allowed to miss departure time
                    // TODO: should we support only requiring a departure time on the first stop and interpolate?
                    for (int laterIndex = stopTimeUpdates.size() - 1; laterIndex > index; laterIndex--) {
                        final StopTimeUpdate laterStopTimeUpdate = stopTimeUpdates.get(laterIndex);
                        // Determine whether later stop is skipped
                        final boolean laterSkippedStop = isStopSkipped(laterStopTimeUpdate);
                        if (!laterSkippedStop) {
                            LOG.warn("Trip update misses departure time, skipping.");
                            return null;
                        }
                    }
                }
            }
        }
        return stops;
    }

    /**
     * Determine whether stop time update represents a SKIPPED stop.
     *
     * @param stopTimeUpdate stop time update
     * @return true iff stop is SKIPPED; false otherwise
     */
    private boolean isStopSkipped(final StopTimeUpdate stopTimeUpdate) {
        final boolean isSkipped = stopTimeUpdate.hasScheduleRelationship() &&
                stopTimeUpdate.getScheduleRelationship().equals(StopTimeUpdate.ScheduleRelationship.SKIPPED);
        return isSkipped;
    }

    /**
     * Handle GTFS-RT TripUpdate message containing an ADDED trip.
     *
     * @param graph graph to update
     * @param tripUpdate GTFS-RT TripUpdate message
     * @param stops the stops of each StopTimeUpdate in the TripUpdate message
     * @param feedId
     * @param serviceDate service date for added trip
     * @return true iff successful
     */
    private boolean handleAddedTrip(final Graph graph, final TripUpdate tripUpdate, final List<Stop> stops,
            final String feedId, final ServiceDate serviceDate) {
        // Preconditions
        Preconditions.checkNotNull(stops);
        Preconditions.checkArgument(tripUpdate.getStopTimeUpdateCount() == stops.size(),
                "number of stop should match the number of stop time updates");

        // Check whether trip id has been used for previously ADDED trip message and cancel
        // previously created trip
        final String tripId = tripUpdate.getTrip().getTripId();
        cancelPreviouslyAddedTrip(feedId, tripId, serviceDate);

        //
        // Create added trip
        //

        Route route = null;
        if (tripUpdate.getTrip().hasRouteId()) {
            // Try to find route
            route = getRouteForRouteId(feedId, tripUpdate.getTrip().getRouteId());
        }

        if (route == null) {
            // Create new Route
            route = new Route();
            // Use route id of trip descriptor if available
            if (tripUpdate.getTrip().hasRouteId()) {
                route.setId(new AgencyAndId(feedId, tripUpdate.getTrip().getRouteId()));
            } else {
                route.setId(new AgencyAndId(feedId, tripId));
            }
            route.setAgency(dummyAgency);
            // Guess the route type as it doesn't exist yet in the specifications
            route.setType(3); // Bus. Used for short- and long-distance bus routes.
            // Create route name
            route.setLongName(tripId);
        }

        // Create new Trip

        final Trip trip = new Trip();
        // TODO: which Agency ID to use? Currently use feed id.
        trip.setId(new AgencyAndId(feedId, tripUpdate.getTrip().getTripId()));
        trip.setRoute(route);

        // Find service ID running on this service date
        final Set<AgencyAndId> serviceIds = graph.getCalendarService().getServiceIdsOnDate(serviceDate);
        if (serviceIds.isEmpty()) {
            // No service id exists: return error for now
            LOG.warn("ADDED trip has service date for which no service id is available, skipping.");
            return false;
        } else {
            // Just use first service id of set
            trip.setServiceId(serviceIds.iterator().next());
        }

        final boolean success = addTripToGraphAndBuffer(feedId, graph, trip, tripUpdate, stops, serviceDate, RealTimeState.ADDED);
        return success;
    }

    /**
     * Add a (new) trip to the graph and the buffer
     *
     * @param graph graph
     * @param trip trip
     * @param tripUpdate trip update containing stop time updates
     * @param stops list of stops corresponding to stop time updates
     * @param serviceDate service date of trip
     * @param realTimeState real-time state of new trip
     * @return true iff successful
     */
    private boolean addTripToGraphAndBuffer(final String feedId, final Graph graph, final Trip trip,
            final TripUpdate tripUpdate, final List<Stop> stops, final ServiceDate serviceDate,
            final RealTimeState realTimeState) {

        // Preconditions
        Preconditions.checkNotNull(stops);
        Preconditions.checkArgument(tripUpdate.getStopTimeUpdateCount() == stops.size(),
                "number of stop should match the number of stop time updates");

        // Calculate seconds since epoch on GTFS midnight (noon minus 12h) of service date
        final Calendar serviceCalendar = serviceDate.getAsCalendar(timeZone);
        final long midnightSecondsSinceEpoch = serviceCalendar.getTimeInMillis() / MILLIS_PER_SECOND;

        // Create StopTimes
        final List<StopTime> stopTimes = new ArrayList<>(tripUpdate.getStopTimeUpdateCount());
        for (int index = 0; index < tripUpdate.getStopTimeUpdateCount(); ++index) {
            final StopTimeUpdate stopTimeUpdate = tripUpdate.getStopTimeUpdate(index);
            final Stop stop = stops.get(index);

            // Determine whether stop is skipped
            final boolean skippedStop = isStopSkipped(stopTimeUpdate);

            // Only create stop time for non-skipped stops
            if (!skippedStop) {
                // Create stop time
                final StopTime stopTime = new StopTime();
                stopTime.setTrip(trip);
                stopTime.setStop(stop);
                // Set arrival time
                if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasTime()) {
                    final long arrivalTime = stopTimeUpdate.getArrival().getTime() - midnightSecondsSinceEpoch;
                    if (arrivalTime < 0 || arrivalTime > MAX_ARRIVAL_DEPARTURE_TIME) {
                        LOG.warn("ADDED trip has invalid arrival time (compared to start date in "
                                + "TripDescriptor), skipping.");
                        return false;
                    }
                    stopTime.setArrivalTime((int) arrivalTime);
                }
                // Set departure time
                if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasTime()) {
                    final long departureTime = stopTimeUpdate.getDeparture().getTime() - midnightSecondsSinceEpoch;
                    if (departureTime < 0 || departureTime > MAX_ARRIVAL_DEPARTURE_TIME) {
                        LOG.warn("ADDED trip has invalid departure time (compared to start date in "
                                + "TripDescriptor), skipping.");
                        return false;
                    }
                    stopTime.setDepartureTime((int) departureTime);
                }
                stopTime.setTimepoint(1); // Exact time
                if (stopTimeUpdate.hasStopSequence()) {
                    stopTime.setStopSequence(stopTimeUpdate.getStopSequence());
                }
                // Set pickup type
                // Set different pickup type for last stop
                if (index == tripUpdate.getStopTimeUpdateCount() - 1) {
                    stopTime.setPickupType(1); // No pickup available
                } else {
                    stopTime.setPickupType(0); // Regularly scheduled pickup
                }
                // Set drop off type
                // Set different drop off type for first stop
                if (index == 0) {
                    stopTime.setDropOffType(1); // No drop off available
                } else {
                    stopTime.setDropOffType(0); // Regularly scheduled drop off
                }

                // Add stop time to list
                stopTimes.add(stopTime);
            }
        }

        // TODO: filter/interpolate stop times like in GTFSPatternHopFactory?

        // Create StopPattern
        final StopPattern stopPattern = new StopPattern(stopTimes);

        // Get cached trip pattern or create one if it doesn't exist yet
        final TripPattern pattern = tripPatternCache.getOrCreateTripPattern(stopPattern, trip, graph, serviceDate);

        // Add service code to bitset of pattern if needed (using copy on write)
        final int serviceCode = graph.serviceCodes.get(trip.getServiceId());
        if (!pattern.getServices().get(serviceCode)) {
            final BitSet services = (BitSet) pattern.getServices().clone();
            services.set(serviceCode);
            pattern.setServices(services);
        }

        // Create new trip times
        final TripTimes newTripTimes = new TripTimes(trip, stopTimes, graph.deduplicator);

        // Update all times to mark trip times as realtime
        // TODO: should we incorporate the delay field if present?
        for (int stopIndex = 0; stopIndex < newTripTimes.getNumStops(); stopIndex++) {
            newTripTimes.updateArrivalTime(stopIndex, newTripTimes.getScheduledArrivalTime(stopIndex));
            newTripTimes.updateDepartureTime(stopIndex, newTripTimes.getScheduledDepartureTime(stopIndex));
        }

        // Set service code of new trip times
        newTripTimes.serviceCode = serviceCode;

        // Make sure that updated trip times have the correct real time state
        newTripTimes.setRealTimeState(realTimeState);

        // Add new trip times to the buffer
        final boolean success = buffer.update(feedId, pattern, newTripTimes, serviceDate);
        return success;
    }


    /**
     * Add a (new) trip to the graph and the buffer
     *
     * @return true if successful
     */
    private boolean addTripToGraphAndBuffer(final String feedId, final Graph graph, final Trip trip,
                                            final List<StopTime> stopTimes, final List<Stop> stops, TripTimes updatedTripTimes,
                                            final ServiceDate serviceDate) {

        // Preconditions
        Preconditions.checkNotNull(stops);
        Preconditions.checkArgument(stopTimes.size() == stops.size(),
                "number of stop should match the number of stop time updates");

        // Create StopPattern
        final StopPattern stopPattern = new StopPattern(stopTimes);

        // Get cached trip pattern or create one if it doesn't exist yet
        final TripPattern pattern = tripPatternCache.getOrCreateTripPattern(stopPattern, trip, graph, serviceDate);

        // Add service code to bitset of pattern if needed (using copy on write)
        final int serviceCode = graph.serviceCodes.get(trip.getServiceId());
        if (!pattern.getServices().get(serviceCode)) {
            final BitSet services = (BitSet) pattern.getServices().clone();
            services.set(serviceCode);
            pattern.setServices(services);
        }


        /*
         * Update pattern with triptimes so get correct dwell times and lower bound on running times.
         * New patterns only affects a single trip, previously added tripTimes is no longer valid, and is therefore removed
         */
        pattern.scheduledTimetable.tripTimes.clear();
        pattern.scheduledTimetable.addTripTimes(updatedTripTimes);
        pattern.scheduledTimetable.finish();

        // Remove trip times to avoid real time trip times being visible for ignoreRealtimeInformation queries
        pattern.scheduledTimetable.tripTimes.clear();

        //TODO: Add pattern to index?

        // Add new trip times to the buffer
        final boolean success = buffer.update(feedId, pattern, updatedTripTimes, serviceDate);
        return success;
    }

    /**
     * Cancel scheduled trip in buffer given trip id (without agency id) on service date
     *
     * @param tripId trip id without agency id
     * @param serviceDate service date
     * @return true if scheduled trip was cancelled
     */
    private boolean cancelScheduledTrip(String feedId, String tripId, final ServiceDate serviceDate) {
        boolean success = false;
        
        final TripPattern pattern = getPatternForTripId(feedId, tripId);

        if (pattern != null) {
            // Cancel scheduled trip times for this trip in this pattern
            final Timetable timetable = pattern.scheduledTimetable;
            final int tripIndex = timetable.getTripIndex(tripId);
            if (tripIndex == -1) {
                LOG.warn("Could not cancel scheduled trip {}", tripId);
            } else {
                final TripTimes newTripTimes = new TripTimes(timetable.getTripTimes(tripIndex));
                newTripTimes.cancel();
                buffer.update(feedId, pattern, newTripTimes, serviceDate);
                success = true;
            }
        }

        return success;
    }

    /**
     * Cancel previously added trip from buffer if there is a previously added trip with given trip
     * id (without agency id) on service date
     *
     * @param feedId feed id the trip id belongs to
     * @param tripId trip id without agency id
     * @param serviceDate service date
     * @return true if a previously added trip was cancelled
     */
    private boolean cancelPreviouslyAddedTrip(final String feedId, final String tripId, final ServiceDate serviceDate) {
        boolean success = false;

        final TripPattern pattern = buffer.getLastAddedTripPattern(feedId, tripId, serviceDate);
        if (pattern != null) {
            // Cancel trip times for this trip in this pattern
            final Timetable timetable = buffer.resolve(pattern, serviceDate);
            final int tripIndex = timetable.getTripIndex(tripId);
            if (tripIndex == -1) {
                LOG.warn("Could not cancel previously added trip {}", tripId);
            } else {
                final TripTimes newTripTimes = new TripTimes(timetable.getTripTimes(tripIndex));
                newTripTimes.cancel();
                buffer.update(feedId, pattern, newTripTimes, serviceDate);
//                buffer.removeLastAddedTripPattern(feedId, tripId, serviceDate);
                success = true;
            }
        }

        return success;
    }

    private boolean handleUnscheduledTrip(final TripUpdate tripUpdate, final String feedId, final ServiceDate serviceDate) {
        // TODO: Handle unscheduled trip
        LOG.warn("Unscheduled trips are currently unsupported. Skipping TripUpdate.");
        return false;
    }

    /**
     * Validate and handle GTFS-RT TripUpdate message containing a MODIFIED trip.
     *
     * @param graph graph to update
     * @param tripUpdate GTFS-RT TripUpdate message
     * @param feedId
     * @param serviceDate
     * @return true iff successful
     */
    private boolean validateAndHandleModifiedTrip(final Graph graph, final TripUpdate tripUpdate, final String feedId, final ServiceDate serviceDate) {
        // Preconditions
        Preconditions.checkNotNull(graph);
        Preconditions.checkNotNull(tripUpdate);
        Preconditions.checkNotNull(serviceDate);

        //
        // Validate modified trip
        //

        // Check whether trip id of MODIFIED trip is available
        final TripDescriptor tripDescriptor = tripUpdate.getTrip();
        if (!tripDescriptor.hasTripId()) {
            LOG.warn("No trip id found for MODIFIED trip, skipping.");
            return false;
        }

        // Check whether trip id already exists in graph
        String tripId = tripDescriptor.getTripId();
        Trip trip = getTripForTripId(feedId, tripId);
        if (trip == null) {
            // TODO: should we support this and consider it an ADDED trip?
            LOG.warn("Graph does not contain trip id of MODIFIED trip, skipping.");
            return false;
        }

        // Check whether a start date exists
        if (!tripDescriptor.hasStartDate()) {
            // TODO: should we support this and apply update to all days?
            LOG.warn("MODIFIED trip doesn't have a start date in TripDescriptor, skipping.");
            return false;
        } else {
            // Check whether service date is served by trip
            final Set<AgencyAndId> serviceIds = graph.getCalendarService().getServiceIdsOnDate(serviceDate);
            if (!serviceIds.contains(trip.getServiceId())) {
                // TODO: should we support this and change service id of trip?
                LOG.warn("MODIFIED trip has a service date that is not served by trip, skipping.");
                return false;
            }
        }

        // Check whether at least two stop updates exist
        if (tripUpdate.getStopTimeUpdateCount() < 2) {
            LOG.warn("MODIFIED trip has less then two stops, skipping.");
            return false;
        }

        // Check whether all stop times are available and all stops exist
        List<Stop> stops = checkNewStopTimeUpdatesAndFindStops(feedId, tripUpdate);
        if (stops == null) {
            return false;
        }

        //
        // Handle modified trip
        //

        final boolean success = handleModifiedTrip(graph, trip, tripUpdate, stops, feedId, serviceDate);

        return success;
    }

    /**
     * Handle GTFS-RT TripUpdate message containing a MODIFIED trip.
     *
     * @param graph graph to update
     * @param trip trip that is modified
     * @param tripUpdate GTFS-RT TripUpdate message
     * @param stops the stops of each StopTimeUpdate in the TripUpdate message
     * @param feedId
     * @param serviceDate service date for modified trip
     * @return true iff successful
     */
    private boolean handleModifiedTrip(final Graph graph, final Trip trip, final TripUpdate tripUpdate, final List<Stop> stops,
            final String feedId, final ServiceDate serviceDate) {
        // Preconditions
        Preconditions.checkNotNull(stops);
        Preconditions.checkArgument(tripUpdate.getStopTimeUpdateCount() == stops.size(),
                "number of stop should match the number of stop time updates");

        // Cancel scheduled trip

        final String tripId = tripUpdate.getTrip().getTripId();
        cancelScheduledTrip(feedId, tripId, serviceDate);

        // Check whether trip id has been used for previously ADDED/MODIFIED trip message and cancel
        // previously created trip
        cancelPreviouslyAddedTrip(feedId, tripId, serviceDate);

        // Add new trip
        final boolean success =
                addTripToGraphAndBuffer(feedId, graph, trip, tripUpdate, stops, serviceDate, RealTimeState.MODIFIED);
        return success;
    }

    private boolean handleCanceledTrip(final TripUpdate tripUpdate, final String feedId, final ServiceDate serviceDate) {
        boolean success = false;
        if (tripUpdate.getTrip().hasTripId()) {
            // Try to cancel scheduled trip
            final String tripId = tripUpdate.getTrip().getTripId();
            final boolean cancelScheduledSuccess = cancelScheduledTrip(feedId, tripId, serviceDate);

            // Try to cancel previously added trip
            final boolean cancelPreviouslyAddedSuccess = cancelPreviouslyAddedTrip(feedId, tripId, serviceDate);

            if (cancelScheduledSuccess || cancelPreviouslyAddedSuccess) {
                success = true;
            } else {
                LOG.warn("No pattern found for tripId {}, skipping TripUpdate.", tripId);
            }
        } else {
            LOG.warn("No trip id in CANCELED trip update, skipping TripUpdate.");
        }

        return success;
    }

    private boolean purgeExpiredData() {
        final ServiceDate today = new ServiceDate();
        final ServiceDate previously = today.previous().previous(); // Just to be safe...

        if(lastPurgeDate != null && lastPurgeDate.compareTo(previously) > 0) {
            return false;
        }

        LOG.debug("purging expired realtime data");

        lastPurgeDate = previously;

        return buffer.purgeExpiredData(previously);
    }

    /**
     * Retrieve a trip pattern given a feed id and trid id.
     *
     * @param feedId feed id for the trip id
     * @param tripId trip id without agency
     * @return trip pattern or null if no trip pattern was found
     */
    private TripPattern getPatternForTripId(String feedId, String tripId) {
        Trip trip = graphIndex.tripForId.get(new AgencyAndId(feedId, tripId));
        return graphIndex.patternForTrip.get(trip);
    }

    private Set<TripPattern> getPatternsForTrip(Set<Trip> matches, VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney) {

        if (monitoredVehicleJourney.getOriginRef() == null) {
            return null;
        }

        ZonedDateTime date = monitoredVehicleJourney.getOriginAimedDepartureTime();
        if (date == null) {
            //If no date is set - assume Realtime-data is reported for 'today'.
            date = ZonedDateTime.now();
        }
        ServiceDate realTimeReportedServiceDate = new ServiceDate(date.toLocalDate());

        Set<TripPattern> patterns = new HashSet<>();
        for (Iterator<Trip> iterator = matches.iterator(); iterator.hasNext(); ) {
            Trip currentTrip = iterator.next();
            TripPattern tripPattern = graphIndex.patternForTrip.get(currentTrip);
            Set<ServiceDate> serviceDates = graphIndex.graph.getCalendarService().getServiceDatesForServiceId(currentTrip.getServiceId());

            if (!serviceDates.contains(realTimeReportedServiceDate)) {
                // Current trip has no service on the date of the 'MonitoredVehicleJourney'
                continue;
            }

            Stop firstStop = tripPattern.getStop(0);
            Stop lastStop = tripPattern.getStop(tripPattern.getStops().size() - 1);

            String siriOriginRef = monitoredVehicleJourney.getOriginRef().getValue();

            if (monitoredVehicleJourney.getDestinationRef() != null) {
                String siriDestinationRef = monitoredVehicleJourney.getDestinationRef().getValue();

                boolean firstStopIsMatch = firstStop.getId().getId().equals(siriOriginRef);
                boolean lastStopIsMatch = lastStop.getId().getId().equals(siriDestinationRef);

                if (!firstStopIsMatch && firstStop.getParentStation() != null) {
                    Stop otherFirstStop = graphIndex.stopForId.get(new AgencyAndId(firstStop.getId().getAgencyId(), siriOriginRef));
                    firstStopIsMatch = (otherFirstStop != null && otherFirstStop.getParentStation() != null && otherFirstStop.getParentStation().equals(firstStop.getParentStation()));
                }

                if (!lastStopIsMatch && lastStop.getParentStation() != null) {
                    Stop otherLastStop = graphIndex.stopForId.get(new AgencyAndId(lastStop.getId().getAgencyId(), siriDestinationRef));
                    lastStopIsMatch = (otherLastStop != null && otherLastStop.getParentStation() != null && otherLastStop.getParentStation().equals(lastStop.getParentStation()));
                }

                if (firstStopIsMatch & lastStopIsMatch) {
                    // Origin and destination matches
                    TripPattern lastAddedTripPattern = buffer.getLastAddedTripPattern(tripPattern.getFeedId(), currentTrip.getId().getId(), realTimeReportedServiceDate);
                    if (lastAddedTripPattern != null) {
                        patterns.add(lastAddedTripPattern);
                    } else {
                        patterns.add(tripPattern);
                    }
                }
            } else {
                //Match origin only - since destination is not defined
                if (firstStop.getId().getId().equals(siriOriginRef)) {
                    tripPattern.scheduledTimetable.tripTimes.get(0).getDepartureTime(0); // TODO does this line do anything?
                    patterns.add(tripPattern);
                }
            }


        }
        return patterns;
    }

    private Set<TripPattern> getPatternForTrip(Set<Trip> trips, EstimatedVehicleJourney journey) {
        Set<TripPattern> patterns = new HashSet<>();
        for (Trip trip : trips) {
            TripPattern pattern = getPatternForTrip(trip, journey);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }
    private TripPattern getPatternForTrip(Trip trip, EstimatedVehicleJourney journey) {

        Set<ServiceDate> serviceDates = graphIndex.graph.getCalendarService().getServiceDatesForServiceId(trip.getServiceId());

        List<RecordedCall> recordedCalls = (journey.getRecordedCalls() != null ? journey.getRecordedCalls().getRecordedCalls():null);
        List<EstimatedCall> estimatedCalls;
        if (journey.getEstimatedCalls() != null) {
            estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
        } else {
            return null;
        }

        String journeyFirstStopId;
        String journeyLastStopId;
        ServiceDate journeyDate;
        //Resolve first stop - check recordedCalls, then estimatedCalls
        if (recordedCalls != null && !recordedCalls.isEmpty()) {
            RecordedCall recordedCall = recordedCalls.get(0);
            journeyFirstStopId = recordedCall.getStopPointRef().getValue();
            journeyDate = new ServiceDate(Date.from(recordedCall.getAimedDepartureTime().toInstant()));
        } else if (estimatedCalls != null && !estimatedCalls.isEmpty()) {
            EstimatedCall estimatedCall = estimatedCalls.get(0);
            journeyFirstStopId = estimatedCall.getStopPointRef().getValue();
            journeyDate = new ServiceDate(Date.from(estimatedCall.getAimedDepartureTime().toInstant()));
        } else {
            return null;
        }

        //Resolve last stop - check estimatedCalls, then recordedCalls
        if (estimatedCalls != null && !estimatedCalls.isEmpty()) {
            EstimatedCall estimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);
            journeyLastStopId = estimatedCall.getStopPointRef().getValue();
        } else if (recordedCalls != null && !recordedCalls.isEmpty()) {
            RecordedCall recordedCall = recordedCalls.get(recordedCalls.size() - 1);
            journeyLastStopId = recordedCall.getStopPointRef().getValue();
        } else {
            return null;
        }

        TripPattern lastAddedTripPattern = null;
        if (getTimetableSnapshot() != null) {
            lastAddedTripPattern  = getTimetableSnapshot().getLastAddedTripPattern(trip.getId().getAgencyId(), trip.getId().getId(), journeyDate);
        }

        TripPattern tripPattern;
        if (lastAddedTripPattern != null) {
            tripPattern = lastAddedTripPattern;
        } else {
            tripPattern = graphIndex.patternForTrip.get(trip);
        }


        Stop firstStop = tripPattern.getStop(0);
        Stop lastStop = tripPattern.getStop(tripPattern.getStops().size() - 1);

        if (serviceDates.contains(journeyDate)) {
            boolean firstStopIsMatch = firstStop.getId().getId().equals(journeyFirstStopId);
            boolean lastStopIsMatch = lastStop.getId().getId().equals(journeyLastStopId);

            if (!firstStopIsMatch && firstStop.getParentStation() != null) {
                Stop otherFirstStop = graphIndex.stopForId.get(new AgencyAndId(firstStop.getId().getAgencyId(), journeyFirstStopId));
                firstStopIsMatch = (otherFirstStop != null && otherFirstStop.getParentStation() != null && otherFirstStop.getParentStation().equals(firstStop.getParentStation()));
            }

            if (!lastStopIsMatch && lastStop.getParentStation() != null) {
                Stop otherLastStop = graphIndex.stopForId.get(new AgencyAndId(lastStop.getId().getAgencyId(), journeyLastStopId));
                lastStopIsMatch = (otherLastStop != null && otherLastStop.getParentStation() != null && otherLastStop.getParentStation().equals(lastStop.getParentStation()));
            }

            if (firstStopIsMatch & lastStopIsMatch) {
                // Found matches
                return tripPattern;
            }

            return null;
        }

        return null;
    }

    /**
     * Finds the correct trip based on OTP-ServiceDate and SIRI-DepartureTime
     * @param trips
     * @param monitoredVehicleJourney
     * @return
     */
    private Trip getTripForJourney(Set<Trip> trips, VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime date = monitoredVehicleJourney.getOriginAimedDepartureTime();
        if (date == null) {
            //If no date is set - assume Realtime-data is reported for 'today'.
            date = ZonedDateTime.now();
        }
        ServiceDate serviceDate = new ServiceDate(date.toLocalDate());

        List<Trip> results = new ArrayList<>();
        for (Iterator<Trip> iterator = trips.iterator(); iterator.hasNext(); ) {

            Trip trip = iterator.next();
            Set<ServiceDate> serviceDatesForServiceId = graphIndex.graph.getCalendarService().getServiceDatesForServiceId(trip.getServiceId());

            for (Iterator<ServiceDate> serviceDateIterator = serviceDatesForServiceId.iterator(); serviceDateIterator.hasNext(); ) {
                ServiceDate next = serviceDateIterator.next();
                if (next.equals(serviceDate)) {
                    results.add(trip);
                }
            }
        }

        if (results.size() == 1) {
            return results.get(0);
        } else if (results.size() > 1) {
            // Multiple possible matches - check if lineRef/routeId matches
            if (monitoredVehicleJourney.getLineRef() != null && monitoredVehicleJourney.getLineRef().getValue() != null) {
                String lineRef = monitoredVehicleJourney.getLineRef().getValue();
                for (Trip trip : results) {
                    if (lineRef.equals(trip.getRoute().getId().getId())) {
                        // Return first trip where the lineRef matches routeId
                        return trip;
                    }
                }
            }

            // Line does not match any routeId - return first result.
            return results.get(0);
        }

        return null;
    }

    /**
     * Finds the correct trip based on OTP-ServiceDate and SIRI-DepartureTime
     * @param trips
     * @param journey
     * @return
     */
    private Set<Trip> getTripForJourney(Set<Trip> trips, EstimatedVehicleJourney journey) {


        List<RecordedCall> recordedCalls = (journey.getRecordedCalls() != null ? journey.getRecordedCalls().getRecordedCalls():null);
        List<EstimatedCall> estimatedCalls = (journey.getEstimatedCalls() != null ? journey.getEstimatedCalls().getEstimatedCalls():null);

        ZonedDateTime date;
        int stopNumber = 1;
        String firstStopId;
        if (recordedCalls != null && !recordedCalls.isEmpty()) {
            RecordedCall recordedCall = recordedCalls.get(0);
            date = recordedCall.getAimedDepartureTime();
            firstStopId = recordedCall.getStopPointRef().getValue();
        } else if (estimatedCalls != null && !estimatedCalls.isEmpty()) {
            EstimatedCall estimatedCall = estimatedCalls.get(0);
            if (estimatedCall.getOrder() != null) {
                stopNumber = estimatedCall.getOrder().intValue();
            } else if (estimatedCall.getVisitNumber() != null) {
                stopNumber = estimatedCall.getVisitNumber().intValue();
            }
            firstStopId = estimatedCall.getStopPointRef().getValue();
            date = estimatedCall.getAimedDepartureTime();
        } else {
            return null;
        }

        if (date == null) {
            //If no date is set - assume Realtime-data is reported for 'today'.
            date = ZonedDateTime.now();
        }
        ServiceDate serviceDate = new ServiceDate(date.toLocalDate());

        int departureInSecondsSinceMidnight = calculateSecondsSinceMidnight(date);
        Set<Trip> result = new HashSet<>();
        for (Iterator<Trip> iterator = trips.iterator(); iterator.hasNext(); ) {

            Trip trip = iterator.next();
            Set<ServiceDate> serviceDatesForServiceId = graphIndex.graph.getCalendarService().getServiceDatesForServiceId(trip.getServiceId());
            if (serviceDatesForServiceId.contains(serviceDate)) {

                TripPattern pattern = graphIndex.patternForTrip.get(trip);

                if (stopNumber < pattern.stopPattern.stops.length) {
                    boolean firstReportedStopIsFound = false;
                    Stop stop = pattern.stopPattern.stops[stopNumber-1];
                    if (firstStopId.equals(stop.getId().getId())) {
                       firstReportedStopIsFound = true;
                    } else {
                        String agencyId = stop.getId().getAgencyId();
                        if (stop.getParentStation() != null) {
                            Stop alternativeStop = graphIndex.stopForId.get(new AgencyAndId(agencyId, firstStopId));
                            if (alternativeStop != null &&
                                    stop.getParentStation().equals(alternativeStop.getParentStation())) {
                                firstReportedStopIsFound = true;
                            }
                        }
                    }
                    if (firstReportedStopIsFound) {
                        for (TripTimes times : getCurrentTimetable(pattern, serviceDate).tripTimes) {
                            if (times.getScheduledDepartureTime(stopNumber - 1) == departureInSecondsSinceMidnight) {
                                if (graphIndex.graph.getCalendarService().getServiceDatesForServiceId(times.trip.getServiceId()).contains(serviceDate)) {
                                    result.add(times.trip);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.size() >= 1) {
            return result;
        } else {
            return null;
        }
    }



    /**
     * Retrieve route given a route id without an agency
     *
     * @param feedId feed id for the route id
     * @param routeId route id without the agency
     * @return route or null if route can't be found in graph index
     */
    private Route getRouteForRouteId(String feedId, String routeId) {
        Route route = graphIndex.routeForId.get(new AgencyAndId(feedId, routeId));
        return route;
    }

    /**
     * Retrieve trip given a trip id without an agency
     *
     * @param feedId feed id for the trip id
     * @param tripId trip id without the agency
     * @return trip or null if trip can't be found in graph index
     */
    private Trip getTripForTripId(String feedId, String tripId) {
        Trip trip = graphIndex.tripForId.get(new AgencyAndId(feedId, tripId));
        return trip;
    }

    /**
     * Retrieve stop given a feed id and stop id.
     *
     * @param feedId feed id for the stop id
     * @param stopId trip id without the agency
     * @return stop or null if stop doesn't exist
     */
    private Stop getStopForStopId(String feedId, String stopId) {
        Stop stop = graphIndex.stopForId.get(new AgencyAndId(feedId, stopId));
        return stop;
    }

}
