package org.opentripplanner.netex.mapping;

import org.opentripplanner.model.NoticeAssignment;
import org.opentripplanner.model.Route;
import org.opentripplanner.model.ShapePoint;
import org.opentripplanner.model.Stop;
import org.opentripplanner.model.Transfer;
import org.opentripplanner.model.TripAlterationOnDate;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.model.impl.OtpTransitBuilder;
import org.opentripplanner.netex.loader.NetexDao;
import org.opentripplanner.netex.mapping.calendar.ServiceCalendarBuilder;
import org.opentripplanner.routing.graph.AddBuilderAnnotation;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.Branding;
import org.rutebanken.netex.model.FlexibleStopPlace;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.Line_VersionStructure;
import org.rutebanken.netex.model.Notice;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TariffZone;

import java.util.Collection;
import java.util.Map;

public class NetexMapper {

    private final AuthorityToAgencyMapper authorityToAgencyMapper = new AuthorityToAgencyMapper();

    private final NoticeMapper noticeMapper = new NoticeMapper();

    private final BrandingMapper brandingMapper = new BrandingMapper();

    private final NoticeAssignmentMapper noticeAssignmentMapper = new NoticeAssignmentMapper();

    private final RouteMapper routeMapper = new RouteMapper();

    private final StopMapper stopMapper;

    private final FlexibleStopPlaceMapper flexibleStopPlaceMapper;

    private final TripPatternMapper tripPatternMapper;

    private final ParkingMapper parkingMapper = new ParkingMapper();

    private final OtpTransitBuilder transitBuilder;

    private final OperatorMapper operatorMapper = new OperatorMapper();

    private final ServiceLinkMapper serviceLinkMapper;

    private final TariffZoneMapper tariffZoneMapper = new TariffZoneMapper();

    private final TransferMapper transferMapper = new TransferMapper();

    private final ServiceCalendarBuilder serviceCalendarBuilder;

    private final String agencyId;

    private final String defaultFlexMaxTravelTime;

    private final int defaultMinimumFlexPaddingTime;


    public NetexMapper(
            OtpTransitBuilder transitBuilder,
            String agencyId,
            String defaultFlexMaxTravelTime,
            int defaultMinimumFlexPaddingTime,
            AddBuilderAnnotation addBuilderAnnotation
    ) {
        this.transitBuilder = transitBuilder;
        this.agencyId = agencyId;
        this.defaultFlexMaxTravelTime = defaultFlexMaxTravelTime;
        this.defaultMinimumFlexPaddingTime = defaultMinimumFlexPaddingTime;
        this.tripPatternMapper = new TripPatternMapper(addBuilderAnnotation);
        this.flexibleStopPlaceMapper= new FlexibleStopPlaceMapper(addBuilderAnnotation);
        this.serviceLinkMapper = new ServiceLinkMapper(addBuilderAnnotation);
        this.stopMapper= new StopMapper(addBuilderAnnotation);
        this.serviceCalendarBuilder = new ServiceCalendarBuilder();
    }

    public void mapNetexToOtpEntities(NetexDao netexDao) {
        AgencyAndIdFactory.setAgencyId(agencyId);

        for (Branding branding : netexDao.brandingById.values()) {
            org.opentripplanner.model.Branding otpBranding = brandingMapper.mapBranding(branding);
            transitBuilder.getBrandingById().add(otpBranding);
        }

        for (Authority authority : netexDao.authoritiesById.values()) {
            transitBuilder.getAgencies().add(authorityToAgencyMapper.mapAgency(authority, netexDao.getTimeZone()));
        }

        for (org.rutebanken.netex.model.Operator operator : netexDao.operatorsById.values()) {
            transitBuilder.getOperatorsById().add(operatorMapper.map(operator, transitBuilder));
        }

        for (JourneyPattern journeyPattern : netexDao.journeyPatternsById.values()) {
            for (ShapePoint shapePoint : serviceLinkMapper.getShapePointsByJourneyPattern(journeyPattern, netexDao)) {
                transitBuilder.getShapePoints().put(shapePoint.getShapeId(), shapePoint);
            }
        }

        for (Line_VersionStructure line : netexDao.lineById.values()) {
            Route route = routeMapper.mapRoute(line, transitBuilder, netexDao, netexDao.getTimeZone());
            transitBuilder.getRoutes().add(route);
        }

        for (String tariffZoneId : netexDao.tariffZoneById.keys()) {
            if (tariffZoneId != null) {
                final TariffZone tariffZone = netexDao.tariffZoneById.lookupLastVersionById(
                    tariffZoneId);
                if (tariffZone != null) {
                    org.opentripplanner.model.TariffZone otpTariffZone = tariffZoneMapper.mapTariffZone(
                        tariffZone);
                    transitBuilder.getTariffZones().add(otpTariffZone);
                }
            }
        }

        for (StopPlace stopPlace : netexDao.multimodalStopPlaceById.values()) {
            if (stopPlace != null) {
                Stop stop = stopMapper.mapMultiModalStop(stopPlace, transitBuilder);
                transitBuilder.getMultiModalStops().add(stop);
                transitBuilder.getStops().add(stop);
            }
        }

        for (String stopPlaceId : netexDao.stopPlaceById.keys()) {
            Collection<StopPlace> stopPlaceAllVersions = netexDao.stopPlaceById.lookup(stopPlaceId);
            if (stopPlaceAllVersions != null) {
                Collection<Stop> stops = stopMapper.mapParentAndChildStops(stopPlaceAllVersions, transitBuilder, netexDao);
                for (Stop stop : stops) {
                    transitBuilder.getStops().add(stop);
                }
            }
        }

        for (GroupOfStopPlaces group : netexDao.groupsOfStopPlacesById.values()) {
            if (group != null) {
                Stop stop = stopMapper.mapGroupsOfStopPlaces(group, transitBuilder.getStopByGroupOfStopPlaces(), transitBuilder.getStops());
                transitBuilder.getGroupsOfStopPlaces().add(stop);
                transitBuilder.getStops().add(stop);
            }
        }

        for (String flexibleStopPlaceId : netexDao.flexibleStopPlaceById.keys()) {
            // TODO Consider also checking validity instead of always picking last version, as is being done with stop places
            FlexibleStopPlace flexibleStopPlace = netexDao.flexibleStopPlaceById.lookupLastVersionById(flexibleStopPlaceId);
            if (flexibleStopPlace != null) {
                flexibleStopPlaceMapper.mapFlexibleStopPlaceWithQuay(flexibleStopPlace, transitBuilder);
            }
        }

        // Create Service Calendar
        serviceCalendarBuilder.buildCalendar(netexDao);
        transitBuilder.getCalendarDates().addAll(serviceCalendarBuilder.calendarDates());

        // Parking
        {
            for (String parkingId : netexDao.parkingById.keys()) {
                transitBuilder.getParkings().add(parkingMapper.mapParking(netexDao.parkingById.lookupLastVersionById(parkingId)));
            }
        }

        Map<String, Map<ServiceDate, TripAlterationOnDate>> alterationScheduleBySJId = DatedServiceJourneyMapper.map(
            netexDao.datedServiceJourneyById,
            netexDao.operatingDaysById
        );

        // Create Trips
        for (JourneyPattern journeyPattern : netexDao.journeyPatternsById.values()) {
            tripPatternMapper.mapTripPattern(
                    journeyPattern,
                    serviceCalendarBuilder.serviceIdByServiceJourneyId(),
                    alterationScheduleBySJId,
                    transitBuilder,
                    netexDao,
                    defaultFlexMaxTravelTime,
                    defaultMinimumFlexPaddingTime,
                    transitBuilder.findAllServiceIds()
            );
        }

        // Notices
        for (Notice notice : netexDao.noticeById.values()) {
            org.opentripplanner.model.Notice otpNotice = noticeMapper.mapNotice(notice);
            transitBuilder.getNoticesById().add(otpNotice);
        }

        for (org.rutebanken.netex.model.NoticeAssignment noticeAssignment : netexDao.noticeAssignmentById.values()) {
            Collection<NoticeAssignment> otpNoticeAssignments = noticeAssignmentMapper.mapNoticeAssignment(noticeAssignment, netexDao);
            for (NoticeAssignment otpNoticeAssignment : otpNoticeAssignments) {
                transitBuilder.getNoticeAssignmentsById().add(otpNoticeAssignment);
            }
        }
    }


    /**
     * Relations between entities must be mapped after the entities are mapped, entities may come from
     * different files.
     *
     * One example is Interchanges between lines. Lines may be defined in different files and the interchange may be
     * defined in either one of these files. Therefore the relation can not be mapped before both lines are
     * mapped.
     *
     * NOTE! This is not an ideal solution, since the mapping kode relay on the order of entities in the
     * input files and how the files are read. A much better solution would be to map all entities
     * while reading the files and then creating CommandObjects to do linking of entities(possibly defined in different
     * files). This way all XML-objects can be thrown away for garbitch collection imeadeatly after it is read, not
     * keeping it in the NetexDao.
     */
    public void mapNetexToOtpComplexRelations(NetexDao netexDao) {
        for (org.rutebanken.netex.model.ServiceJourneyInterchange interchange : netexDao.interchanges.values()) {
            if (interchange != null) {
                Transfer transfer = transferMapper.mapTransfer(interchange, transitBuilder, netexDao);
                if (transfer != null) {
                    transitBuilder.getTransfers().add(transfer);
                }
            }
        }
    }

    /**
     * The NeTEx files form a hierarchical tree. When parsed we want to keep all information at the above levels,
     * but discard all data when a level is mapped. The {@link NetexDao} is implemented the same way. Some of the
     * data need to be constructed during the mapping phase of a higher level and made available to the mapping
     * of a lower level. So, we keep a stack of cashed elements. This method clear the last level, and make the
     * cached elements available for GC.
     */
    public void popCache() {
        serviceCalendarBuilder.popCache();
    }

    /**
     * This method create a new cache so the mapper is ready to map a new level. The cached elements are available
     * until the {@link #popCache()} is called.
     */
    public void pushCache() {
        serviceCalendarBuilder.pushCache();
    }
}