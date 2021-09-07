package org.opentripplanner.netex.loader;

import org.opentripplanner.netex.loader.support.DelegateToParentHierarchicalMap;
import org.opentripplanner.netex.loader.support.HierarchicalMap;
import org.opentripplanner.netex.loader.support.HierarchicalMapById;
import org.opentripplanner.netex.loader.support.HierarchicalMultimap;
import org.opentripplanner.netex.loader.support.HierarchicalMultimapById;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.Branding;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.FlexibleStopPlace;
import org.rutebanken.netex.model.GroupOfLines;
import org.rutebanken.netex.model.GroupOfStopPlaces;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.Line_VersionStructure;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.Notice;
import org.rutebanken.netex.model.NoticeAssignment;
import org.rutebanken.netex.model.OperatingDay;
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Parking;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.ServiceJourneyInterchange;
import org.rutebanken.netex.model.ServiceLink;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.TariffZone;

/**
 * This class holds indexes of Netex objects for lookup during
 * the NeTEx import.
 * <p>
 * A NeTEx import is grouped into several levels: <em>shard data</em>, <em>group shared data</em>,
 * and <em>singel files</em>. To discard objects not needed any more; this class support the
 * creation of multiple levels, by storing a refernece to a parent at an higher level. The
 * <code>HierarchicalMap.lookup</code> method first look in the local index, and then if nothing is found
 * delegate the lookup to its parent.
 * <p>
 */
public class NetexDao {

    public final HierarchicalMap<String, Authority> authoritiesByGroupOfLinesId;
    public final HierarchicalMapById<Authority> authoritiesById;
    public final HierarchicalMap<String, Authority> authoritiesByNetworkId;
    public final HierarchicalMapById<Branding> brandingById;
    public final HierarchicalMapById<DatedServiceJourney> datedServiceJourneyById;
    public final HierarchicalMultimap<String, DayTypeAssignment> dayTypeAssignmentByDayTypeId;
    public final HierarchicalMap<String, Boolean> dayTypeAvailable;
    public final HierarchicalMapById<DayType> dayTypeById;
    public final HierarchicalMapById<DestinationDisplay> destinationDisplayById;
    public final HierarchicalMultimapById<FlexibleStopPlace> flexibleStopPlaceById;
    public final HierarchicalMap<String, String> flexibleStopPlaceIdByStopPointRef;
    public final HierarchicalMapById<GroupOfLines> groupOfLinesById;
    public final HierarchicalMap<String, GroupOfLines> groupOfLinesByLineId;
    public final HierarchicalMapById<GroupOfStopPlaces> groupsOfStopPlacesById;
    public final HierarchicalMap<String, ServiceJourneyInterchange> interchanges;
    public final HierarchicalMapById<JourneyPattern> journeyPatternsById;
    public final HierarchicalMap<String, JourneyPattern> journeyPatternsByStopPointId;
    public final HierarchicalMapById<Line_VersionStructure> lineById;
    public final HierarchicalMapById<StopPlace> multimodalStopPlaceById;
    public final HierarchicalMapById<Network> networkById;
    public final HierarchicalMap<String, Network> networkByLineId;
    public final HierarchicalMapById<NoticeAssignment> noticeAssignmentById;
    public final HierarchicalMapById<Notice> noticeById;
    public final HierarchicalMapById<OperatingDay> operatingDaysById;
    public final HierarchicalMapById<OperatingPeriod> operatingPeriodById;
    public final HierarchicalMapById<Operator> operatorsById;
    public final HierarchicalMultimapById<Parking> parkingById;
    public final HierarchicalMultimapById<Quay> quayById;
    public final HierarchicalMap<String, String> quayIdByStopPointRef;
    public final HierarchicalMapById<Route> routeById;
    public final HierarchicalMapById<ServiceJourney> serviceJourneyById;
    public final HierarchicalMultimap<String, ServiceJourney> serviceJourneyByPatternId;
    public final HierarchicalMapById<ServiceLink> serviceLinkById;
    public final HierarchicalMultimapById<StopPlace> stopPlaceById;
    public final HierarchicalMapById<StopPointInJourneyPattern> stopPointInJourneyPatternById;
    public final HierarchicalMultimapById<TariffZone> tariffZoneById;

    private String timeZone = null;
    private final NetexDao parent;

    /**
     * Create a root node.
     */
    NetexDao() {
        this.parent = null;

        this.authoritiesByGroupOfLinesId = new HierarchicalMap<>();
        this.authoritiesById = new HierarchicalMapById<>();
        this.authoritiesByNetworkId = new HierarchicalMap<>();
        this.brandingById = new HierarchicalMapById<>();
        this.datedServiceJourneyById = new HierarchicalMapById<>();
        this.dayTypeAssignmentByDayTypeId = new HierarchicalMultimap<>();
        this.dayTypeAvailable = new HierarchicalMap<>();
        this.dayTypeById = new HierarchicalMapById<>();
        this.destinationDisplayById = new HierarchicalMapById<>();
        this.flexibleStopPlaceById = new HierarchicalMultimapById<>();
        this.flexibleStopPlaceIdByStopPointRef = new HierarchicalMap<>();
        this.groupOfLinesById = new HierarchicalMapById<>();
        this.groupOfLinesByLineId = new HierarchicalMap<>();
        this.groupsOfStopPlacesById = new HierarchicalMapById<>();
        this.interchanges = new HierarchicalMap<>();
        this.journeyPatternsById = new HierarchicalMapById<>();
        this.journeyPatternsByStopPointId = new HierarchicalMap<>();
        this.lineById = new HierarchicalMapById<>();
        this.multimodalStopPlaceById = new HierarchicalMapById<>();
        this.networkById = new HierarchicalMapById<>();
        this.networkByLineId = new HierarchicalMap<>();
        this.noticeAssignmentById = new HierarchicalMapById<>();
        this.noticeById = new HierarchicalMapById<>();
        this.operatingDaysById = new HierarchicalMapById<>();
        this.operatingPeriodById = new HierarchicalMapById<>();
        this.operatorsById = new HierarchicalMapById<>();
        this.parkingById = new HierarchicalMultimapById<>();
        this.quayById = new HierarchicalMultimapById<>();
        this.quayIdByStopPointRef = new HierarchicalMap<>();
        this.routeById = new HierarchicalMapById<>();
        this.serviceJourneyById = new HierarchicalMapById<>();
        this.serviceJourneyByPatternId = new HierarchicalMultimap<>();
        this.serviceLinkById = new HierarchicalMapById<>();
        this.stopPlaceById = new HierarchicalMultimapById<>();
        this.stopPointInJourneyPatternById = new HierarchicalMapById<>();
        this.tariffZoneById = new HierarchicalMultimapById<>();
    }

    /**
     * Create a child node.
     * @param parent can not be <code>null</code>.
     */
    NetexDao(NetexDao parent) {
        this.parent = parent;

        this.authoritiesByGroupOfLinesId = new HierarchicalMap<>(parent.authoritiesByGroupOfLinesId);
        this.authoritiesById = new HierarchicalMapById<>(parent.authoritiesById);
        this.authoritiesByNetworkId = new HierarchicalMap<>(parent.authoritiesByNetworkId);
        this.brandingById = new HierarchicalMapById<>(parent.brandingById);
        this.datedServiceJourneyById = new HierarchicalMapById<>(parent.datedServiceJourneyById);
        this.dayTypeAssignmentByDayTypeId = new HierarchicalMultimap<>(parent.dayTypeAssignmentByDayTypeId);
        this.dayTypeAvailable = new HierarchicalMap<>(parent.dayTypeAvailable);
        this.dayTypeById = new HierarchicalMapById<>(parent.dayTypeById);
        this.destinationDisplayById = new HierarchicalMapById<>(parent.destinationDisplayById);
        this.flexibleStopPlaceById = new HierarchicalMultimapById<>(parent.flexibleStopPlaceById);
        this.flexibleStopPlaceIdByStopPointRef = new HierarchicalMap<>(parent.flexibleStopPlaceIdByStopPointRef);
        this.groupOfLinesById = new HierarchicalMapById<>(parent.groupOfLinesById);
        this.groupOfLinesByLineId = new HierarchicalMap<>(parent.groupOfLinesByLineId);
        this.groupsOfStopPlacesById = new HierarchicalMapById<>(parent.groupsOfStopPlacesById);
        // Interchanges are complex relations and the mapping is done AFTER all lines in a group is read
        this.interchanges = new DelegateToParentHierarchicalMap<>(parent.interchanges);
        this.journeyPatternsById = new HierarchicalMapById<>(parent.journeyPatternsById);
        this.journeyPatternsByStopPointId = new HierarchicalMap<>(parent.journeyPatternsByStopPointId);
        this.lineById = new HierarchicalMapById<>(parent.lineById);
        this.multimodalStopPlaceById = new HierarchicalMapById<>(parent.multimodalStopPlaceById);
        this.networkById = new HierarchicalMapById<>(parent.networkById);
        this.networkByLineId = new HierarchicalMap<>(parent.networkByLineId);
        this.noticeAssignmentById = new HierarchicalMapById<>(parent.noticeAssignmentById);
        this.noticeById = new HierarchicalMapById<>(parent.noticeById);
        this.operatingDaysById = new HierarchicalMapById<>(parent.operatingDaysById);
        this.operatingPeriodById = new HierarchicalMapById<>(parent.operatingPeriodById);
        this.operatorsById = new HierarchicalMapById<>(parent.operatorsById);
        this.parkingById = new HierarchicalMultimapById<>(parent.parkingById);
        this.quayById = new HierarchicalMultimapById<>(parent.quayById);
        // QuayIdByStopPointRefs are used for mapping Interchanges; hence must be available AFTER all lines are read
        this.quayIdByStopPointRef = new DelegateToParentHierarchicalMap<>(parent.quayIdByStopPointRef);
        this.routeById = new HierarchicalMapById<>(parent.routeById);
        this.serviceLinkById = new HierarchicalMapById<>(parent.serviceLinkById);
        this.serviceJourneyById = new HierarchicalMapById<>(parent.serviceJourneyById);
        this.serviceJourneyByPatternId = new HierarchicalMultimap<>(parent.serviceJourneyByPatternId);
        this.stopPlaceById = new HierarchicalMultimapById<>(parent.stopPlaceById);
        this.stopPointInJourneyPatternById = new HierarchicalMapById<>(parent.stopPointInJourneyPatternById);
        this.tariffZoneById = new HierarchicalMultimapById<>(parent.tariffZoneById);
    }

    /**
     * Retrive timezone from this class, if not found delegate up to the parent NetexDao.
     */
    public String getTimeZone() {
        return (timeZone != null || parent == null) ? timeZone : parent.getTimeZone();
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * This is used to be able to create a NetexDao outside the package.
     * SHOULD ONLY BE USED IN UNIT TESTS.
     */
    public static NetexDao createForTest(NetexDao parent) {
        return parent == null ? new NetexDao() : new NetexDao(parent);
    }
}
