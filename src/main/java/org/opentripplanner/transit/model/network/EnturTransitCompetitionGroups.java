package org.opentripplanner.transit.model.network;

import static org.opentripplanner.transit.model.basic.TransitMode.AIRPLANE;
import static org.opentripplanner.transit.model.basic.TransitMode.BUS;
import static org.opentripplanner.transit.model.basic.TransitMode.CABLE_CAR;
import static org.opentripplanner.transit.model.basic.TransitMode.CARPOOL;
import static org.opentripplanner.transit.model.basic.TransitMode.COACH;
import static org.opentripplanner.transit.model.basic.TransitMode.FUNICULAR;
import static org.opentripplanner.transit.model.basic.TransitMode.GONDOLA;
import static org.opentripplanner.transit.model.basic.TransitMode.MONORAIL;
import static org.opentripplanner.transit.model.basic.TransitMode.RAIL;
import static org.opentripplanner.transit.model.basic.TransitMode.SUBWAY;
import static org.opentripplanner.transit.model.basic.TransitMode.TAXI;
import static org.opentripplanner.transit.model.basic.TransitMode.TRAM;
import static org.opentripplanner.transit.model.basic.TransitMode.TROLLEYBUS;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority.TransitPriorityGroup32n;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnturTransitCompetitionGroups {

  private static final Logger LOG = LoggerFactory.getLogger(EnturTransitCompetitionGroups.class);

  private static final Set<TransitMode> LOCAL_TRAFFIC_MODE = EnumSet.of(
    BUS,
    SUBWAY,
    TRAM,
    CABLE_CAR,
    GONDOLA,
    FUNICULAR,
    TROLLEYBUS,
    MONORAIL,
    CARPOOL,
    TAXI
  );

  private int groupIndex = -1;

  private final int localTrafficGroupId = nextGroupId();
  private final int airplaneGroupId = nextGroupId();
  private final int airportLinkBusGroupId = nextGroupId();
  private final Map<FeedScopedId, Integer> authorityGroupIds = new HashMap<>();
  private final Set<String> list = new HashSet<>();

  private int nextGroupId() {
    return TransitPriorityGroup32n.groupId(++groupIndex);
  }

  private int groupId(FeedScopedId authorityId) {
    return authorityGroupIds.computeIfAbsent(authorityId, fId -> nextGroupId());
  }

  public int resolveCompetitionGroup(TransitMode mode, SubMode subMode, FeedScopedId id) {
    int groupId = findCompetitionGroup(mode, subMode, id);
    String description = "%08x %-10s %-28s %s".formatted(groupId, mode, subMode, id);
    list.add(description);

    if (groupId < 0) {
      printList();
      throw new IllegalStateException(description);
    }
    return groupId;
  }

  public void printList() {
    System.out.println("--------------------------------");
    list.stream().sorted().forEach(System.out::println);
    System.out.println("Total number of group ids: " + (groupIndex - 1));
    System.out.println("--------------------------------");
  }

  private int findCompetitionGroup(TransitMode mode, SubMode subMode, FeedScopedId id) {
    String subModeName = subMode == null ? "unknown" : subMode.name();

    if (
      subModeName.contains("local") ||
      subModeName.equals("sightseeingService") ||
      subModeName.equals("touristRailway")
    ) {
      return localTrafficGroupId;
    }
    if (subModeName.equals("airportLinkBus")) {
      return airportLinkBusGroupId;
    }
    if (subModeName.equals("railReplacementBus")) {
      return groupId(id);
    }
    if (LOCAL_TRAFFIC_MODE.contains(mode)) {
      return localTrafficGroupId;
    }
    if (mode == AIRPLANE) {
      return airplaneGroupId;
    }
    if (mode == RAIL || mode == COACH) {
      return groupId(id);
    }
    LOG.error("Unexpected mode/submode/authority: " + mode + "/" + subModeName + "/" + id);
    return localTrafficGroupId;
  }
}
