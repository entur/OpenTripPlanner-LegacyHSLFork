package org.opentripplanner.netex.mapping.calendar;

import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.ServiceCalendarDate;
import org.opentripplanner.model.TripAlteration;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.netex.loader.NetexDao;
import org.opentripplanner.netex.loader.support.HierarchicalMap;
import org.opentripplanner.netex.mapping.AgencyAndIdFactory;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.DayTypeRefs_RelStructure;
import org.rutebanken.netex.model.ServiceJourney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * This class is responsible for creating a OTP service calendar from NeTEx data. It uses the
 * calendar mappers to map from the Netex domain, and keep calendar information in an internal
 * structure until the mapping is complete. It keeps the mapping between a {@code ServiceJourney},
 * {@code DayType}, {@code DatedServiceJourney} and {@code ServiceDay} until the entire calendar
 * can be build and OTP serviceIds is possible to generate.
 * <p>
 * This class keep the internal data in a hierarchy to match the input file hierarchy. The
 * lifecycle methods {@link #pushCache()} and {@link #popCache()} must be called by the
 * "controlling" class, the navigating up and down in the hierarchy. The caller must grab the
 * result after each invocation of the {@link #buildCalendar(NetexDao)} method by getting the
 * {@link #calendarDates()} and {@link #serviceIdByServiceJourneyId()}. The result is cleared
 * between each call to the {@link #buildCalendar(NetexDao)}.
 */
public class ServiceCalendarBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceCalendarBuilder.class);

    private static final AtomicLong serviceIdCounter = new AtomicLong(0);

    // Cached elements available when mapping at "lower" levels
    private HierarchicalMap<String, Set<ServiceDate>> calendarByDayTypeId = new HierarchicalMap<>();


    // The result is stored in the following collections (cleared before each calendar build)
    private final List<ServiceCalendarDate> calendarDates = new ArrayList<>();
    private final Map<String, AgencyAndId> calendarIdBySJId = new HashMap<>();

    /** The current netexDao */
    private NetexDao netexDao;


    public List<ServiceCalendarDate> calendarDates() {
        return calendarDates;
    }

    public Map<String, AgencyAndId> serviceIdByServiceJourneyId() {
        return calendarIdBySJId;
    }

    public Map<String, TripAlteration> tripServiceAlterationsBySJId() {
        return CalendarMapper.tripServiceAlterationsBySJId(netexDao.datedServiceJourneyById);
    }

    public void buildCalendar(NetexDao netexDao) {
        setupBuild(netexDao);

        Map<String, Set<ServiceDate>> calendarBySJId = createCalendarBySJId();
        Map<Collection<ServiceDate>, AgencyAndId> serviceIds = CalendarMapper.mapDatesToServiceId(
                calendarBySJId.values(),
                this::createServiceId
        );
        saveCalendarDates(serviceIds);

        for (String sjId : calendarBySJId.keySet()) {
            calendarIdBySJId.put(sjId, serviceIds.get(calendarBySJId.get(sjId)));
        }
    }

    /**
     * This class is reused, make sure the result lists are cleared before adding stuff
     */
    private void setupBuild(NetexDao netexDao) {
        this.netexDao = netexDao;
        calendarDates.clear();
        calendarIdBySJId.clear();
    }

    /**
     * The NeTEx files form a hierarchical tree. When parsed we want to keep all information at the
     * above levels, but discard all data when a level is mapped. The {@link NetexDao} is
     * implemented the same way. Some of the data need to be constructed during the mapping phase
     * of a higher level and made available to the mapping of a lower level. So, we keep a stack of
     * cashed elements. This method clear the last level, and make the cached elements available
     * for GC.
     */
    public void popCache() {
        calendarByDayTypeId = (HierarchicalMap<String, Set<ServiceDate>>) calendarByDayTypeId.pop();
    }

    /**
     * This method create a new cache so the mapper is ready to map a new level. The cached
     * elements are available until the {@link #popCache()} is called.
     */
    public void pushCache() {
        calendarByDayTypeId = new HierarchicalMap<>(calendarByDayTypeId);
    }

    /**
     * Return a map of: SJ.id -> ServiceDates
     */
    private Map<String, Set<ServiceDate>> createCalendarBySJId() {
        final Map<String, Set<ServiceDate>> result;

        // Map DatedServiceJourney
        result = CalendarMapper.createDatedServiceJourneyCalendar(
            netexDao.datedServiceJourneyById,
            netexDao.operatingDaysById
        );

        // Map ServiceJourney DayTypeAssignments and add to result
        {
            calendarByDayTypeId.addAll(
                CalendarMapper.mapDayTypesToLocalDates(
                    netexDao.dayTypeById,
                    netexDao.dayTypeAssignmentByDayTypeId,
                    netexDao.operatingPeriodById,
                    netexDao.operatingDaysById
                )
            );


            // Combine the results
            List<DayTypeAndServiceJourneyId> dayTypeAndSJIds = dayTypeAndServiceJourneyIds(netexDao);

            for (DayTypeAndServiceJourneyId it : dayTypeAndSJIds) {

                Set<ServiceDate> dates = calendarByDayTypeId.lookup(it.dayTypeId());

                if (dates == null) {
                    LOG.error("No calendar dates found for dayType: " + it.dayTypeId());
                } else {
                    var existing = result.get(it.serviceJourneyId());
                    var merged = existing == null
                        ? dates
                        : Stream.of(existing, dates)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toUnmodifiableSet());
                    result.put(it.serviceJourneyId(), merged);
                }
            }
        }
        return result;
    }

    private void saveCalendarDates(Map<Collection<ServiceDate>, AgencyAndId> serviceIds) {
        for (Map.Entry<Collection<ServiceDate>, AgencyAndId> e : serviceIds.entrySet()) {
            for (ServiceDate date : e.getKey()) {
                calendarDates.add(new ServiceCalendarDate(e.getValue(), date, ServiceCalendarDate.EXCEPTION_TYPE_ADD));
            }
        }
    }

    private AgencyAndId createServiceId() {
        return AgencyAndIdFactory.createAgencyAndId(String.format("%06d", serviceIdCounter.incrementAndGet()));
    }

    private static List<DayTypeAndServiceJourneyId> dayTypeAndServiceJourneyIds(NetexDao netexDao) {
        List<DayTypeAndServiceJourneyId> result = new ArrayList<>();
        for (ServiceJourney sj : netexDao.serviceJourneyById.values()) {
            DayTypeRefs_RelStructure dayTypes = sj.getDayTypes();
            if (dayTypes != null) {
                for (JAXBElement<? extends DayTypeRefStructure> dt : dayTypes.getDayTypeRef()) {
                    result.add(
                            new DayTypeAndServiceJourneyId(
                                    dt.getValue().getRef(),
                                    sj.getId()
                            )
                    );
                }
            }
        }
        return result;
    }

}
