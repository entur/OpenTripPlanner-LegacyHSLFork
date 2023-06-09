package org.opentripplanner.netex.mapping.calendar;

import org.junit.Ignore;
import org.junit.Test;
import org.opentripplanner.model.AgencyAndId;
import org.opentripplanner.model.TripAlteration;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.netex.loader.support.HierarchicalMapById;
import org.opentripplanner.netex.loader.support.HierarchicalMultimap;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.OperatingDay;
import org.rutebanken.netex.model.OperatingPeriod;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opentripplanner.netex.NetexTestDataSupport.createDatedServiceJourney;
import static org.opentripplanner.netex.NetexTestDataSupport.createDayType;
import static org.opentripplanner.netex.NetexTestDataSupport.createDayTypeAssignment;
import static org.opentripplanner.netex.NetexTestDataSupport.createOperatingDay;
import static org.opentripplanner.netex.NetexTestDataSupport.createOperatingPeriod;
import static org.rutebanken.netex.model.DayOfWeekEnumeration.EVERYDAY;
import static org.rutebanken.netex.model.DayOfWeekEnumeration.WEEKDAYS;
import static org.rutebanken.netex.model.ServiceAlterationEnumeration.CANCELLATION;
import static org.rutebanken.netex.model.ServiceAlterationEnumeration.EXTRA_JOURNEY;
import static org.rutebanken.netex.model.ServiceAlterationEnumeration.PLANNED;
import static org.rutebanken.netex.model.ServiceAlterationEnumeration.REPLACED;

public class CalendarMapperTest {

  private static final String FEED = "F";

  private final static ServiceDate SD1 = new ServiceDate(2020, 10, 1);
  private final static ServiceDate SD2 = new ServiceDate(2020, 10, 2);
  private final static ServiceDate SD3 = new ServiceDate(2020, 10, 3);
  private final static ServiceDate SD4 = new ServiceDate(2020, 10, 4);
  private final static ServiceDate SD1_COPY = new ServiceDate(2020, 10, 1);

  private static final LocalDate D2020_10_21 = LocalDate.of(2020, 10, 21);
  private static final LocalDate D2020_11_01 = LocalDate.of(2020, 11, 1);
  private static final LocalDate D2020_11_03 = LocalDate.of(2020, 11, 3);
  private static final LocalDate D2020_11_27 = LocalDate.of(2020, 11, 27);
  private static final LocalDate D2020_11_30 = LocalDate.of(2020, 11, 30);
  private static final LocalDate D2020_12_22 = LocalDate.of(2020, 12, 22);
  private static final LocalDate D2020_12_24 = LocalDate.of(2020, 12, 24);
  private static final LocalDate D2020_12_31 = LocalDate.of(2020, 12, 31);

  private static final String OP_1 = "OP-1";
  private static final String OP_2 = "OP-2";
  private static final String OP_3 = "OP-3";

  private static final String OP_DAY_1 = "OD-1";
  private static final String OP_DAY_2 = "OD-2";

  private static final String DAY_TYPE_1 = "D1";
  private static final String DAY_TYPE_2 = "D2";
  private static final String DAY_TYPE_3 = "D3";
  private static final String DAY_TYPE_4 = "D4";

  private static final String SJ_1 = "SJ-1";
  private static final String SJ_2 = "SJ-2";
  private static final String SJ_3 = "SJ-3";
  private static final String SJ_4 = "SJ-4";
  private static final String SJ_5 = "SJ-5";
  private static final String SJ_6 = "SJ-6";

  private static final Boolean AVAILABLE = TRUE;
  private static final Boolean NOT_AVAILABLE = FALSE;


  @Test
  public void mapDayTypesToLocalDates() {
    // GIVEN
    var dayTypes = new HierarchicalMapById<DayType>();
    var assignments = new HierarchicalMultimap<String, DayTypeAssignment>();
    var periods = new HierarchicalMapById<OperatingPeriod>();
    var operatingDays = new HierarchicalMapById<OperatingDay>();

    // Simple assignment on 21.10.2020
    dayTypes.add(createDayType(DAY_TYPE_1));
    assignments.add(DAY_TYPE_1, createDayTypeAssignment(DAY_TYPE_1, D2020_10_21, AVAILABLE));


    // Skip DAY_TYPE_2  - Should support dayTypes which is not assigned any value
    dayTypes.add(createDayType(DAY_TYPE_2));

    // DayType 3 - Schedule in November
    {
      // Every day in November, except 6. - 23
      dayTypes.add(createDayType(DAY_TYPE_3, EVERYDAY));
      periods.add(createOperatingPeriod(OP_1, D2020_11_01, D2020_11_30));
      assignments.add(DAY_TYPE_3, createDayTypeAssignment(DAY_TYPE_3, OP_1, AVAILABLE));
      // Except 06.11.2020 to 23.11.2020
      periods.add(createOperatingPeriod(OP_2, D2020_11_03, D2020_11_27));
      assignments.add(DAY_TYPE_3, createDayTypeAssignment(DAY_TYPE_3, OP_2, NOT_AVAILABLE));
    }

    // All weekdays in December (except 24.12, se above)
    {
      dayTypes.add(createDayType(DAY_TYPE_4, WEEKDAYS));
      periods.add(createOperatingPeriod(OP_3, D2020_12_22, D2020_12_31));
      assignments.add(DAY_TYPE_4, createDayTypeAssignment(DAY_TYPE_4, OP_3, AVAILABLE));
      // Do not run service on christmas eve
      assignments.add(DAY_TYPE_4, createDayTypeAssignment(DAY_TYPE_4, D2020_12_24, NOT_AVAILABLE));
    }

    // WHEN - create calendar
    Map<String, Set<ServiceDate>> result = CalendarMapper.mapDayTypesToLocalDates(
        dayTypes,
        assignments,
        periods,
        operatingDays
    );


    // THEN - verify
    assertEquals("[2020-10-21]", toStr(result, DAY_TYPE_1));
    assertEquals("[]", toStr(result, DAY_TYPE_2));
    assertEquals(
        "[2020-11-01, 2020-11-02, 2020-11-28, 2020-11-29, 2020-11-30]",
        toStr(result, DAY_TYPE_3));
    assertEquals(
        "[2020-12-22, 2020-12-23, 2020-12-25, 2020-12-28, 2020-12-29, 2020-12-30, 2020-12-31]",
        toStr(result, DAY_TYPE_4)
    );
  }

  private <E> String toStr(Map<String, Set<ServiceDate>> result, String key) {
    return result.get(key).stream().sorted().collect(Collectors.toList()).toString();
  }

  @Test
  public void mapDatesToServiceId() {
    // given
    var input = List.of(
        Set.of(SD1),
        // Duplicate copy
        Set.of(SD1_COPY),
        Set.of(SD2, SD3),
        // Duplicate exact same, but different collection in reverse order
        new HashSet<>(Arrays.asList(SD3, SD2)),
        Set.of(SD3, SD4, SD1),
        Set.of(SD4, SD1_COPY, SD3)
    );
    final IdProducer idProducer = new IdProducer();

    // When
    var result = CalendarMapper.mapDatesToServiceId(input, idProducer::newId);

    // Then
    assertEquals(new AgencyAndId(FEED, "1"), result.get(Set.of(SD1)));
    assertEquals(new AgencyAndId(FEED, "1"), result.get(Set.of(SD1_COPY)));
    assertEquals(new AgencyAndId(FEED, "2"), result.get(Set.of(SD3, SD2)));
    assertEquals(new AgencyAndId(FEED, "3"), result.get(Set.of(SD1, SD3, SD4)));
  }


  @SuppressWarnings("unchecked")
  @Test
  public void createDatedServiceJourneyCalendar() {
    // Given
    HierarchicalMapById<DatedServiceJourney> dsjById = new HierarchicalMapById<>();
    HierarchicalMapById<OperatingDay> opDaysById = new HierarchicalMapById<>();

    dsjById.add(createDatedServiceJourney("ANY-ID-1", OP_DAY_1, SJ_1));
    dsjById.add(createDatedServiceJourney("ANY-ID-2", OP_DAY_2, SJ_1));

    opDaysById.add(createOperatingDay(OP_DAY_1, D2020_10_21));
    opDaysById.add(createOperatingDay(OP_DAY_2, D2020_12_24));

    // When
    var result = CalendarMapper.createDatedServiceJourneyCalendar(dsjById, opDaysById);

    // Then
    assertEquals(Set.of(new ServiceDate(D2020_10_21), new ServiceDate(D2020_12_24)), result.get(SJ_1));
  }


  @Test
  public void tripServiceAlterationsBySJId() {
    // Given
    var dsjById = new HierarchicalMapById<DatedServiceJourney>();

    // Map empty collection
    var result = CalendarMapper.tripServiceAlterationsBySJId(dsjById);
    assertTrue(result.isEmpty());

    // Add some data
    dsjById.add(createDatedServiceJourney("1", OP_DAY_1, SJ_1));
    dsjById.add(createDatedServiceJourney("2", OP_DAY_1, SJ_2, PLANNED));
    dsjById.add(createDatedServiceJourney("3", OP_DAY_1, SJ_3, REPLACED));
    dsjById.add(createDatedServiceJourney("4", OP_DAY_1, SJ_4, CANCELLATION));
    dsjById.add(createDatedServiceJourney("5", OP_DAY_1, SJ_5, EXTRA_JOURNEY));
    dsjById.add(createDatedServiceJourney("6", OP_DAY_1, SJ_6, PLANNED));
    dsjById.add(createDatedServiceJourney("7", OP_DAY_2, SJ_6));

    result = CalendarMapper.tripServiceAlterationsBySJId(dsjById);

    assertEquals(TripAlteration.planned, result.get(SJ_1));
    assertEquals(TripAlteration.planned, result.get(SJ_2));
    assertEquals(TripAlteration.replaced, result.get(SJ_3));
    assertEquals(TripAlteration.cancellation, result.get(SJ_4));
    assertEquals(TripAlteration.extraJourney, result.get(SJ_5));
    assertEquals(TripAlteration.planned, result.get(SJ_6));
  }

  @Test(expected = IllegalStateException.class)
  @Ignore
  public void tripServiceAlterationsBySJIdMixedAltNotAllowed() {
    var dsjById = new HierarchicalMapById<DatedServiceJourney>();
    dsjById.add(createDatedServiceJourney("1", OP_DAY_1, SJ_1));
    dsjById.add(createDatedServiceJourney("2", OP_DAY_1, SJ_1, REPLACED));
    CalendarMapper.tripServiceAlterationsBySJId(dsjById);
  }


  public static class IdProducer {
    int i = 0;
    public AgencyAndId newId() {
      return new AgencyAndId("F", Integer.toString(++i));
    }
  }
}