package org.opentripplanner.routing.algorithm.raptoradapter.transit.grouppriority;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;

public class TransitGroupPriorityReport {

  public static String buildReport(List<TripPatternForDates> patterns) {
    var map = new TreeMap<Integer, DebugEntity>();
    for (var it : patterns) {
      var pattern = it.getTripPattern().getPattern();
      var de = map.computeIfAbsent(
        it.priorityGroupId(),
        i -> new DebugEntity(it.priorityGroupId())
      );
      de.add(
        it.route().getAgency().getId().toString(),
        pattern.getMode().name(),
        pattern.getNetexSubmode().name()
      );
    }
    return (
      "TRANSIT GROUPS PRIORITY" +
      map.values().stream().map(DebugEntity::toString).sorted().collect(Collectors.joining(""))
    );
  }

  private static class DebugEntity {

    private final int groupId;
    private final TreeMap<String, AgencyEntry> agencies = new TreeMap<>();

    public DebugEntity(int groupId) {
      this.groupId = groupId;
    }

    void add(String agency, String mode, String submode) {
      agencies.computeIfAbsent(agency, AgencyEntry::new).add(mode, submode);
    }

    @Override
    public String toString() {
      var buf = new StringBuilder("\n  %#010x".formatted(groupId));
      for (var it : agencies.values()) {
        buf.append("\n    ").append(it.toString());
      }
      return buf.toString();
    }
  }

  private record AgencyEntry(String agency, TreeMap<String, TreeSet<String>> modes) {
    private AgencyEntry(String agency) {
      this(agency, new TreeMap<>());
    }

    void add(String mode, String submode) {
      modes.computeIfAbsent(mode, m -> new TreeSet<>()).add(submode);
    }

    @Override
    public String toString() {
      var buf = new StringBuilder();
      for (var it : modes.entrySet()) {
        buf.append(", ");
        buf.append(it.getKey());
        if (!it.getValue().isEmpty()) {
          buf.append(" (").append(String.join(", ", it.getValue())).append(")");
        }
      }
      return agency + " ~ " + buf.substring(2);
    }
  }
}
