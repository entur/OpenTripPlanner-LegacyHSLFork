package org.opentripplanner.street.model.vertex;

import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.StreetEdge;

/**
 * This vertex is created from all barrier tags.
 * <p>
 * Currently only barrier=bollard is supported. Node barrier=bollard implies access=no, foot=yes,
 * bicycle=yes
 * <p>
 * On this vertex geometry is split and two new edges are created.
 * <p>
 * If start/end vertex of {@link StreetEdge} is BarrierVertex
 * edge isn't traversable with CAR.
 */
public class BarrierVertex extends OsmVertex {

  public static final StreetTraversalPermission defaultBarrierPermissions =
    StreetTraversalPermission.ALL;
  private StreetTraversalPermission barrierPermissions;

  public BarrierVertex(double x, double y, long nodeId) {
    super(x, y, nodeId);
    barrierPermissions = defaultBarrierPermissions;
  }

  public StreetTraversalPermission getBarrierPermissions() {
    return barrierPermissions;
  }

  public void setBarrierPermissions(StreetTraversalPermission barrierPermissions) {
    this.barrierPermissions = barrierPermissions;
  }

  public void makeBarrierAtEndReachable() {
    var edgeCount = this.getDegreeOut() + this.getDegreeIn();
    var needsFix = false;
    if (edgeCount == 1) {
      needsFix = true;
    } else if (edgeCount == 2) {
      var out = this.getOutgoing();
      var in = this.getIncoming();
      if (
        out.isEmpty() ||
        in.isEmpty() ||
        out.iterator().next().getToVertex() == in.iterator().next().getFromVertex()
      ) {
        needsFix = true;
      }
    }
    if (needsFix) {
      this.barrierPermissions = StreetTraversalPermission.ALL;
    }
  }
}
