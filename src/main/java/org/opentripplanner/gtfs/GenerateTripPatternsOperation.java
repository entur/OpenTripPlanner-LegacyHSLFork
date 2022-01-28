/* 
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/
package org.opentripplanner.gtfs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.graph_builder.annotation.TripDegenerate;
import org.opentripplanner.graph_builder.annotation.TripUndefinedService;
import org.opentripplanner.model.*;
import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.TripPattern;
import org.opentripplanner.routing.edgetype.factory.PatternHopFactory;
import org.opentripplanner.routing.graph.AddBuilderAnnotation;
import org.opentripplanner.routing.trippattern.Deduplicator;
import org.opentripplanner.routing.trippattern.FrequencyEntry;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for generating trip patterns when loading GTFS data. This was
 * previously done in the {@link PatternHopFactory},
 * and the code is extracted out of it to make the PatternHopFactory reusable
 * for NETEX and GTFS file import.
 */
public class GenerateTripPatternsOperation {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateTripPatternsOperation.class);

    private static final int UNKNOWN_DIRECTION_ID = -1;

    private final OtpTransitBuilder transitDaoBuilder;
    private final AddBuilderAnnotation builderAnnotation;
    private final Deduplicator deduplicator;
    private final CalendarService calendarService;

    private final Multimap<StopPattern, TripPattern> tripPatterns;
    private final ListMultimap<Trip, Frequency> frequenciesForTrip = ArrayListMultimap.create();

    private int tripCount = 0;
    private int freqCount = 0;
    private int scheduledCount = 0;



    public GenerateTripPatternsOperation(OtpTransitBuilder builder, AddBuilderAnnotation builderAnnotation,
            Deduplicator deduplicator, CalendarService calendarService) {
        this.transitDaoBuilder = builder;
        this.builderAnnotation = builderAnnotation;
        this.deduplicator = deduplicator;
        this.calendarService = calendarService;
        this.tripPatterns = transitDaoBuilder.getTripPatterns();
    }

    public void run() {
        collectFrequencyByTrip();

        final Collection<Trip> trips = transitDaoBuilder.getTrips().values();
        final int tripsSize = trips.size();

        /* Loop over all trips, handling each one as a frequency-based or scheduled trip. */
        for (Trip trip : trips) {
            if (++tripCount % 100000 == 0) {
                LOG.debug("build trip patterns {}/{}", tripCount, tripsSize);
            }

            buildTripPatternForTrip(trip);
        }

        LOG.info("Added {} frequency-based and {} single-trip timetable entries.", freqCount,
                scheduledCount);
    }

    public boolean hasFrequencyBasedTrips() {
        return freqCount > 0;
    }

    public boolean hasScheduledTrips() {
        return scheduledCount > 0;
    }

    /**
     * First, record which trips are used by one or more frequency entries.
     * These trips will be ignored for the purposes of non-frequency routing, and
     * all the frequency entries referencing the same trip can be added at once to the same
     * Timetable/TripPattern.
     */
    private void collectFrequencyByTrip() {
        for(Frequency freq : transitDaoBuilder.getFrequencies()) {
            frequenciesForTrip.put(freq.getTrip(), freq);
        }
    }

    private void buildTripPatternForTrip(Trip trip) {
        // TODO: move to a validator module
        if (!calendarService.getServiceIds().contains(trip.getServiceId())) {
            builderAnnotation.addBuilderAnnotation(new TripUndefinedService(trip));
            return; // Invalid trip, skip it, it will break later
        }

        int directionId = getDirectionId(trip);
        List<StopTime> stopTimes = transitDaoBuilder.getStopTimesSortedByTrip().get(trip);

        // If after filtering this trip does not contain at least 2 stoptimes, it does not serve any purpose.
        if (stopTimes.size() < 2) {
            builderAnnotation.addBuilderAnnotation(new TripDegenerate(trip));
            return;
        }

        // Get the existing TripPattern for this filtered StopPattern, or create one.
        StopPattern stopPattern = new StopPattern(stopTimes, getAreasById()::get);

        TripPattern tripPattern = findOrCreateTripPattern(stopPattern, trip.getRoute(),
                directionId, GtfsLibrary.getTraverseMode(trip));

        // Create a TripTimes object for this list of stoptimes, which form one trip.
        TripTimes tripTimes = new TripTimes(trip, stopTimes, deduplicator);

        // If this trip is referenced by one or more lines in frequencies.txt, wrap it in a FrequencyEntry.
        List<Frequency> frequencies = frequenciesForTrip.get(trip);
        if (frequencies != null && !(frequencies.isEmpty())) {
            for (Frequency freq : frequencies) {
                tripPattern.add(new FrequencyEntry(freq, tripTimes));
                freqCount++;
            }
            // TODO replace: createGeometry(graph, trip, stopTimes, hops);
        }

        // This trip was not frequency-based. Add the TripTimes directly to the TripPattern's scheduled timetable.
        else {
            tripPattern.add(tripTimes);
            scheduledCount++;
        }
    }

    /**
     * Try to get the direction id for the trip, set to UNKNOWN if not found
     */
    private int getDirectionId(Trip trip) {
        try {
            return Integer.parseInt(trip.getDirectionId());
        } catch (NumberFormatException e) {
            LOG.debug("Trip {} does not have direction id, defaults to -1");
        }
        return UNKNOWN_DIRECTION_ID;
    }

    private TripPattern findOrCreateTripPattern(StopPattern stopPattern, Route route, int directionId, TraverseMode mode) {
        for(TripPattern tripPattern : tripPatterns.get(stopPattern)) {
            if(tripPattern.route.equals(route) && tripPattern.directionId == directionId) {
                return tripPattern;
            }
        }

        TripPattern tripPattern = new TripPattern(route, stopPattern, mode);
        tripPattern.directionId = directionId;
        tripPatterns.put(stopPattern, tripPattern);
        return tripPattern;
    }

    private Map<String, Geometry> getAreasById() {
        Map<String, Geometry> areasById = new HashMap<>();
        for (Area area : transitDaoBuilder.getAreas()) {
            Geometry geometry = GeometryUtils.parseWkt(area.getWkt());
            areasById.put(area.getAreaId(), geometry);
        }
        return areasById;
    }
}
