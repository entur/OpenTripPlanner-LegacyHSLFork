package org.opentripplanner.standalone.config.routerequest;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_3;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.api.request.request.TransitRequest;
import org.opentripplanner.routing.api.request.request.filter.TransitPriorityGroupSelect;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.standalone.config.framework.json.OtpVersion;
import org.opentripplanner.transit.model.basic.TransitMode;

public class TransitPriorityGroupConfig {

  public static void mapTransitRequest(NodeAdapter root, TransitRequest transit) {
    var c = root
      .of("transitPriorityGroups")
      .since(OtpVersion.V2_3)
      .summary("Transit priority groups configuration")
      .description(
        """
        Use this to group transit patterns into groups. Each group will be given priority
        over others groups in the trip search. Hence; To paths with a different different
        set of groups will BOTH be returned unless the cost is worse then the relaxation
        specified in the `` parameter . 
        
        """.stripIndent()
      )
      .asObject();

    transit.addPriorityGroupsBase(
      TransitPriorityGroupConfig.mapList(
        c,
        "base",
        "All groups in base get the same group-id(GROUP_ZERO). Normally you will put all " +
        "local-traffic and other 'none-problematic' services here."
      )
    );
    transit.addPriorityGroupsByAgency(
      TransitPriorityGroupConfig.mapList(
        c,
        "byAgency",
        "All groups here are split by agency. For example if you list mode " +
        "[RAIL, COACH] then all rail and coach services run by an agency get the same " +
        "group-id."
      )
    );
    transit.addPriorityGroupsGlobal(
      TransitPriorityGroupConfig.mapList(
        c,
        "global",
        "All services matching a 'global' group will get the same group-id. Use this " +
        "to assign the same id to a specific mode/sub-mode/route."
      )
    );
  }

  private static Collection<TransitPriorityGroupSelect> mapList(
    NodeAdapter root,
    String parameterName,
    String description
  ) {
    return root
      .of(parameterName)
      .since(V2_3)
      .summary("Configuration for transit priority groups.")
      .description(description + " The max total number of group-ids are 32, so be careful.")
      .asObjects(TransitPriorityGroupConfig::mapTransitGroupSelect);
  }

  private static TransitPriorityGroupSelect mapTransitGroupSelect(NodeAdapter c) {
    return TransitPriorityGroupSelect
      .of()
      .addModes(
        c
          .of("modes")
          .since(V2_3)
          .summary("List all modes to select for this group.")
          .asEnumSet(TransitMode.class)
      )
      .addSubModeRegexp(
        c
          .of("subModes")
          .since(V2_3)
          .summary("List a set of regular expressions for matching sub-modes.")
          .asStringList(List.of())
      )
      .addAgencyIds(
        c.of("agencies").since(V2_3).summary("List agency ids to match.").asFeedScopedIds(List.of())
      )
      .addRouteIds(
        c.of("routes").since(V2_3).summary("List route ids to match.").asFeedScopedIds(List.of())
      )
      .build();
  }
}