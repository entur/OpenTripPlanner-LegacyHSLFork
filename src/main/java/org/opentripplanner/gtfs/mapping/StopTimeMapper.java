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

package org.opentripplanner.gtfs.mapping;

import org.onebusaway2.gtfs.model.StopTime;
import org.opentripplanner.netex.mapping.AgencyAndIdFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class StopTimeMapper {
    private final StopMapper stopMapper;

    private final TripMapper tripMapper;

    private Map<org.onebusaway.gtfs.model.StopTime, StopTime> mappedStopTimes = new HashMap<>();

    StopTimeMapper(StopMapper stopMapper, TripMapper tripMapper) {
        this.stopMapper = stopMapper;
        this.tripMapper = tripMapper;
    }

    Collection<StopTime> map(Collection<org.onebusaway.gtfs.model.StopTime> times) {
        return MapCollection.mapCollection(times, this::map);
    }

    StopTime map(org.onebusaway.gtfs.model.StopTime orginal) {
        return orginal == null ? null : mappedStopTimes.computeIfAbsent(orginal, this::doMap);
    }

    private StopTime doMap(org.onebusaway.gtfs.model.StopTime rhs) {
        StopTime lhs = new StopTime();

        lhs.setId(AgencyAndIdFactory.getAgencyAndId(rhs.getId().toString()));
        lhs.setTrip(tripMapper.map(rhs.getTrip()));
        lhs.setStop(stopMapper.map(rhs.getStop()));
        lhs.setArrivalTime(rhs.getArrivalTime());
        lhs.setDepartureTime(rhs.getDepartureTime());
        lhs.setTimepoint(rhs.getTimepoint());
        lhs.setStopSequence(rhs.getStopSequence());
        lhs.setStopHeadsign(rhs.getStopHeadsign());
        lhs.setRouteShortName(rhs.getRouteShortName());
        lhs.setPickupType(rhs.getPickupType());
        lhs.setDropOffType(rhs.getDropOffType());
        lhs.setShapeDistTraveled(rhs.getShapeDistTraveled());
        lhs.setFarePeriodId(rhs.getFarePeriodId());

        // Skip mapping of proxy
        // private transient StopTimeProxy proxy;
        if (rhs.getProxy() != null) {
            throw new IllegalStateException("Did not expect proxy to be set!");
        }

        return lhs;
    }

}
