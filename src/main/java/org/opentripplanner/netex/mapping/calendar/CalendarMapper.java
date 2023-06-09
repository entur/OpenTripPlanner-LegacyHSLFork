package org.opentripplanner.netex.mapping.calendar;

import org.glassfish.jersey.internal.util.Producer;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.TripAlteration;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.netex.loader.support.HierarchicalMapById;
import org.opentripplanner.netex.loader.support.HierarchicalMultimap;
import org.opentripplanner.netex.mapping.TripServiceAlterationMapper;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


class CalendarMapper {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarMapper.class);

    static Map<String, Set<ServiceDate>> mapDayTypesToLocalDates(
        HierarchicalMapById<DayType> dayTypeById,
        HierarchicalMultimap<String, DayTypeAssignment> dayTypeAssignmentByDayTypeId,
        HierarchicalMapById<OperatingPeriod> operatingPeriodById,
        HierarchicalMapById<OperatingDay> operatingDayById
    ) {
        Map<String, Set<ServiceDate>> result = new HashMap<>();
        for (String dayTypeId : dayTypeById.keys()) {

            DayType dayType = dayTypeById.lookup(dayTypeId);
            Collection<LocalDateTime> serviceCalendarDatesForDayType = new HashSet<>();
            Collection<LocalDateTime> serviceCalendarDatesRemoveForDayType = new HashSet<>();

            for (DayTypeAssignment dtAssignment : dayTypeAssignmentByDayTypeId.lookup(dayTypeId)) {
                boolean available = dtAssignment.isIsAvailable() == null || dtAssignment.isIsAvailable();

                // Add or remove single days
                if (dtAssignment.getDate() != null) {
                    LocalDateTime date = dtAssignment.getDate();
                    if (available) {
                        serviceCalendarDatesForDayType.add(date);
                    } else {
                        serviceCalendarDatesRemoveForDayType.add(date);
                    }
                }
                // Add or remove periods
                else if (assignmentHasOperatingPeriod(dtAssignment, operatingPeriodById)) {
                    OperatingPeriod operatingPeriod = operatingPeriodById.lookup(
                        dtAssignment.getOperatingPeriodRef().getRef()
                    );
                    LocalDateTime fromDate = getOperatingPeriodStartDate(operatingPeriod, operatingDayById);
                    LocalDateTime toDate = getOperatingPeriodEndDate(operatingPeriod, operatingDayById);

                    EnumSet<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);

                    if (dayType.getProperties() != null) {
                        for (PropertyOfDay property : dayType.getProperties().getPropertyOfDay()) {
                            daysOfWeek.addAll(DayOfWeekMapper.mapDayOfWeeks(property.getDaysOfWeek()));
                        }
                    }

                    LocalDateTime toDateExclusive = toDate.plusDays(1);
                    for (LocalDateTime date = fromDate; date.isBefore(toDateExclusive); date = date.plusDays(1)) {
                        if(daysOfWeek.contains(date.getDayOfWeek())) {
                            if (available) {
                                serviceCalendarDatesForDayType.add(date);
                            } else {
                                serviceCalendarDatesRemoveForDayType.add(date);
                            }
                        }
                    }
                }
            }
            serviceCalendarDatesForDayType.removeAll(serviceCalendarDatesRemoveForDayType);

            // Map to ServiceDate
            Set<ServiceDate> newDates = serviceCalendarDatesForDayType
                            .stream()
                            .map(d -> new ServiceDate(d.toLocalDate()))
                            // Prevent accidental updates to the set by making it READ-ONLY
                            .collect(Collectors.toUnmodifiableSet());

            // Add to result
            result.put(dayTypeId, newDates);
        }
        return result;
    }

    static Map<Collection<ServiceDate>, AgencyAndId> mapDatesToServiceId(
            Collection<Set<ServiceDate>> serviceDates,
            Producer<AgencyAndId> serviceIdGenerator
    ) {
        Map<Collection<ServiceDate>, AgencyAndId> serviceIds = new HashMap<>();
        for (Set<ServiceDate> dates : serviceDates) {
            serviceIds.computeIfAbsent(dates, it -> serviceIdGenerator.call());
        }
        return serviceIds;
    }

    static Map<String, Set<ServiceDate>> createDatedServiceJourneyCalendar(
        HierarchicalMapById<DatedServiceJourney> datedServiceJourneyById,
        HierarchicalMapById<OperatingDay> operatingDaysById
    ) {
        Map<String, Set<ServiceDate>> map = new HashMap<>();

        for (DatedServiceJourney dsj : datedServiceJourneyById.values()) {
            var sjId = dsj.getJourneyRef().get(0).getValue().getRef();
            var operatingDay = operatingDaysById.lookup(dsj.getOperatingDayRef().getRef());
            var date = operatingDay.getCalendarDate();
            var serviceDate = new ServiceDate(date.toLocalDate());
            map.computeIfAbsent(sjId, k -> new HashSet<>()).add(serviceDate);
        }
        return map;
    }

    static Map<String, TripAlteration> tripServiceAlterationsBySJId(
        final HierarchicalMapById<DatedServiceJourney> datedServiceJourneyById
    ) {
        Map<String, TripAlteration> alternations = new HashMap<>();

        for (DatedServiceJourney dsj : datedServiceJourneyById.values()) {
            var sjId = dsj.getJourneyRef().get(0).getValue().getRef();
            var alt = mapAlterationWithDefaultPlanned(dsj.getServiceAlteration());

            if(alternations.containsKey(sjId)) {
                if(alternations.get(sjId) != alt) {
                    LOG.error(
                        "ERROR! Service alternation miss-match for SJ=" + sjId
                            + "(" + alt + "), expected: " + alternations.get(sjId)
                    );
                }
            }
            else {
                alternations.put(sjId, alt == null ? TripAlteration.planned : alt);
            }
        }
        return alternations;
    }

    private static boolean assignmentHasOperatingPeriod(
        DayTypeAssignment dtAssignment,
        HierarchicalMapById<OperatingPeriod> operatingPeriodById
    ) {
        var ref = dtAssignment.getOperatingPeriodRef();
        return ref != null && operatingPeriodById.containsKey(ref.getRef());
    }

    private static TripAlteration mapAlterationWithDefaultPlanned(ServiceAlterationEnumeration netexValue) {
        if (netexValue == null) {
            return TripAlteration.planned;
        }
        return TripServiceAlterationMapper.mapAlteration(netexValue);
    }

    private static LocalDateTime getOperatingPeriodStartDate(OperatingPeriod operatingPeriod, HierarchicalMapById<OperatingDay> operatingDayById) {
        if (operatingPeriod.getFromDate() != null) {
            return operatingPeriod.getFromDate();
        }
        if (operatingPeriod.getFromOperatingDayRef() != null) {
            return lookupOperatingDay(operatingPeriod.getFromOperatingDayRef(), operatingDayById);
        }
        throw new IllegalArgumentException(
                "Missing start date for operating period " + operatingPeriod.getId()
        );
    }

    private static LocalDateTime getOperatingPeriodEndDate(OperatingPeriod operatingPeriod, HierarchicalMapById<OperatingDay> operatingDayById) {
        if (operatingPeriod.getToDate() != null) {
            return operatingPeriod.getToDate();
        }
        if (operatingPeriod.getToOperatingDayRef() != null) {
            return lookupOperatingDay(operatingPeriod.getToOperatingDayRef(), operatingDayById);
        }
        throw new IllegalArgumentException(
                "Missing end date for operating period " + operatingPeriod.getId()
        );
    }

    private static LocalDateTime lookupOperatingDay(OperatingDayRefStructure operatingDayRef, HierarchicalMapById<OperatingDay> operatingDayById) {
        return operatingDayById.lookup(operatingDayRef.getRef()).getCalendarDate();
    }
}