package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.opentripplanner.model.Trip;
import org.opentripplanner.graph_builder.model.NetexDao;
import org.rutebanken.netex.model.*;

import javax.xml.bind.JAXBElement;

/**
 * Agency id must be added when the stop is related to a line
 */

public class TripMapper {

    public Trip mapServiceJourney(ServiceJourney serviceJourney, OtpTransitBuilder gtfsDao, NetexDao netexDao){

        JAXBElement<? extends LineRefStructure> lineRefStruct = serviceJourney.getLineRef();

        String lineRef = null;
        if(lineRefStruct != null){
            lineRef = lineRefStruct.getValue().getRef();
        }else if(serviceJourney.getJourneyPatternRef() != null){
            JourneyPattern journeyPattern = netexDao.getJourneyPatternsById().get(serviceJourney.getJourneyPatternRef().getValue().getRef());
            String routeRef = journeyPattern.getRouteRef().getRef();
            lineRef = netexDao.getRouteById().get(routeRef).getLineRef().getValue().getRef();
        }

        Trip trip = new Trip();
        trip.setId(AgencyAndIdFactory.createAgencyAndId(serviceJourney.getId()));

        trip.setRoute(gtfsDao.getRoutes().get(AgencyAndIdFactory.createAgencyAndId(lineRef)));
        DayTypeRefs_RelStructure dayTypes = serviceJourney.getDayTypes();

        StringBuilder serviceId = new StringBuilder();
        boolean first = true;
        for(JAXBElement dt : dayTypes.getDayTypeRef()){
            if(!first){
                serviceId.append("+");
            }
            first = false;
            if(dt.getValue() instanceof DayTypeRefStructure){
                DayTypeRefStructure dayType = (DayTypeRefStructure) dt.getValue();
                serviceId.append(dayType.getRef());
            }
        }

        // Add all unique service ids to map. Used when mapping calendars later.
        if (!netexDao.getServiceIds().containsKey(serviceId.toString())) {
            netexDao.getServiceIds().put(serviceId.toString(), serviceId.toString());
        }

        trip.setServiceId(AgencyAndIdFactory.createAgencyAndId(serviceId.toString()));

        return trip;
    }
}
