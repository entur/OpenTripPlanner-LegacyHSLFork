package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.BookingArrangement;
import org.opentripplanner.model.Operator;
import org.opentripplanner.model.Trip;
import org.opentripplanner.model.TripAlteration;
import org.opentripplanner.model.TripAlterationOnDate;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.opentripplanner.netex.loader.NetexDao;
import org.opentripplanner.routing.edgetype.Timetable;
import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.FlexibleLine;
import org.rutebanken.netex.model.FlexibleServiceProperties;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.LineRefStructure;
import org.rutebanken.netex.model.Line_VersionStructure;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.ServiceJourney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.util.Map;
import java.util.Set;

/**
 * Agency id must be added when the stop is related to a line
 */

public class TripMapper {

    private static final Logger LOG = LoggerFactory.getLogger(TripMapper.class);

    private final KeyValueMapper keyValueMapper = new KeyValueMapper();
    private final TransportModeMapper transportModeMapper = new TransportModeMapper();
    private final BookingArrangementMapper bookingArrangementMapper = new BookingArrangementMapper();


    public Trip mapServiceJourney(
            ServiceJourney serviceJourney,
            AgencyAndId serviceId,
            Map<ServiceDate, TripAlterationOnDate> alterations,
            OtpTransitBuilder transitBuilder,
            NetexDao netexDao,
            String defaultFlexMaxTravelTime,
            Set<AgencyAndId> serviceIds
    ){

        Line_VersionStructure line = lineFromServiceJourney(serviceJourney, netexDao);

        Trip trip = new Trip();
        trip.setId(AgencyAndIdFactory.createAgencyAndId(serviceJourney.getId()));

        trip.setRoute(transitBuilder.getRoutes().get(AgencyAndIdFactory.createAgencyAndId(line.getId())));
        if (serviceJourney.getOperatorRef() != null) {
            Operator operator = transitBuilder.getOperatorsById().get(AgencyAndIdFactory.createAgencyAndId(serviceJourney.getOperatorRef().getRef()));
            trip.setTripOperator(operator);
        }

        // This can happen if no valid days are specified
        if (!serviceIds.contains(serviceId)) {
            LOG.warn(
                "Did not find service code for ServiceJourney {}. This can happen if no valid days "
                    + "are specified.",
                serviceJourney.getId()
            );
            return null;
        }

        trip.setServiceId(serviceId);

        if(alterations == null || alterations.isEmpty()) {
            trip.setAlterations(
                TripServiceAlterationMapper.mapAlteration(
                    serviceJourney.getServiceAlteration()
                )
            );
        }
        else {
            trip.setAlterations(alterations);
        }

        if (serviceJourney.getPrivateCode() != null) {
            trip.setTripPrivateCode(serviceJourney.getPrivateCode().getValue());
        }

        if (serviceJourney.getPublicCode() != null) {
            trip.setTripPublicCode(serviceJourney.getPublicCode());
        }

        // Temp fix to prevent frontend from breaking
        if (trip.getTripPrivateCode() == null) {
            trip.setTripPrivateCode("");
        }

        // Temp fix to prevent frontend from breaking
        if (trip.getTripPublicCode() == null) {
            trip.setTripPublicCode("");
        }

        // Temp fix to prevent frontend from breaking
        if (trip.getTripShortName() == null) {
            trip.setTripShortName("");
        }
        trip.setKeyValues(keyValueMapper.mapKeyValues(serviceJourney.getKeyList()));
        trip.setWheelchairAccessible(0); // noInformation

        AllVehicleModesOfTransportEnumeration serviceJourneyTransportMode = serviceJourney.getTransportMode();
        if (serviceJourneyTransportMode != null) {
            trip.setType(transportModeMapper.getTransportMode(serviceJourneyTransportMode, serviceJourney.getTransportSubmode()));
        }

        trip.setTransportSubmode(transportModeMapper.getTransportSubmode(serviceJourney.getTransportSubmode()));
        if (trip.getTransportSubmode()==null) {
            trip.setTransportSubmode(trip.getRoute().getTransportSubmode());
        }

        // Map to right shapeId
        JourneyPattern journeyPattern = netexDao.journeyPatternsById.lookup(serviceJourney.getJourneyPatternRef().getValue().getRef());
        AgencyAndId serviceLinkId = AgencyAndIdFactory.createAgencyAndId(journeyPattern.getId().replace("JourneyPattern", "ServiceLink"));
        if (transitBuilder.getShapePoints().get(serviceLinkId) != null) {
            trip.setShapeId(serviceLinkId);
        }

        // Map to default until support is added in NeTEx
        if (line instanceof FlexibleLine) {
            trip.setDrtMaxTravelTime(defaultFlexMaxTravelTime);
        }

        if (serviceJourney.getFlexibleServiceProperties()!=null) {
            mapFlexibleServicePropertiesProperties(serviceJourney.getFlexibleServiceProperties(), trip);
        }

        if (journeyPattern.getRouteRef() != null) {
            Route route = netexDao.routeById.lookup(journeyPattern.getRouteRef().getRef());
            if (route.getDirectionType() == null) {
                trip.setDirectionId("-1");
            } else {
                switch (route.getDirectionType()) {
                    case OUTBOUND:
                        trip.setDirectionId("0");
                        break;
                    case INBOUND:
                        trip.setDirectionId("1");
                        break;
                    case CLOCKWISE:
                        trip.setDirectionId("2");
                        break;
                    case ANTICLOCKWISE:
                        trip.setDirectionId("3");
                        break;
                }
            }
        }

        return trip;
    }

    private void mapFlexibleServicePropertiesProperties(FlexibleServiceProperties flexibleServiceProperties, Trip otpTrip) {
        if (flexibleServiceProperties.getFlexibleServiceType() != null) {
            otpTrip.setFlexibleTripType(Trip.FlexibleTripTypeEnum.valueOf(flexibleServiceProperties.getFlexibleServiceType().value()));
        }
        BookingArrangement otpBookingArrangement = bookingArrangementMapper.mapBookingArrangement(flexibleServiceProperties.getBookingContact(), flexibleServiceProperties.getBookingNote(),
                flexibleServiceProperties.getBookingAccess(), flexibleServiceProperties.getBookWhen(), flexibleServiceProperties.getBuyWhen(), flexibleServiceProperties.getBookingMethods(),
                flexibleServiceProperties.getMinimumBookingPeriod(), flexibleServiceProperties.getLatestBookingTime());
        otpTrip.setBookingArrangements(otpBookingArrangement);
    }

    public static Line_VersionStructure lineFromServiceJourney(ServiceJourney serviceJourney, NetexDao netexDao) {
        JAXBElement<? extends LineRefStructure> lineRefStruct = serviceJourney.getLineRef();
        String lineRef = null;
        if(lineRefStruct != null){
            lineRef = lineRefStruct.getValue().getRef();
        }else if(serviceJourney.getJourneyPatternRef() != null){
            JourneyPattern journeyPattern = netexDao.journeyPatternsById.lookup(serviceJourney.getJourneyPatternRef().getValue().getRef());
            String routeRef = journeyPattern.getRouteRef().getRef();
            lineRef = netexDao.routeById.lookup(routeRef).getLineRef().getValue().getRef();
        }
        return netexDao.lineById.lookup(lineRef);
    }

    /**
     * This sets DrtAdvanceBookMin to a default value, as the concept does not currently exist
     * in NeTEx.
     *
     * @param trip Trip to be modified
     */
    public void setDrtAdvanceBookMin(Trip trip, int defaultMinimumFlexPaddingTime) {
        trip.setDrtAdvanceBookMin(defaultMinimumFlexPaddingTime);
    }

    private static TripAlteration resolveTripServiceAlteration(
            ServiceJourney sj,
            TripAlteration alternation
    ) {
        TripAlteration sjAlt = TripServiceAlterationMapper.mapAlteration(sj.getServiceAlteration());

        if(alternation == null) {
            return sjAlt == null ? TripAlteration.planned : sjAlt;
        }
        else if(sjAlt == null || sjAlt == alternation) {
            return alternation;
        }
        throw new IllegalStateException(
                "Trip alternation unambiguous. SJ.id=" + sj.getId()
                        + ", sj.alt=" + sjAlt
                        + ", DSJ.alt=" + alternation
        );
    }

}
