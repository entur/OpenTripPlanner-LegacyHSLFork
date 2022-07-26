package org.opentripplanner.transit.model.site;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.transit.model.basic.I18NString;
import org.opentripplanner.transit.model.basic.WgsCoordinate;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.framework.TransitEntity2;

/**
 * A group of stopLocations, which can share a common Stoptime
 */
public class FlexLocationGroup
  extends TransitEntity2<FlexLocationGroup, FlexLocationGroupBuilder>
  implements StopLocation {

  private final Set<StopLocation> stopLocations = new HashSet<>();
  private final I18NString name;
  private GeometryCollection geometry = new GeometryCollection(
    null,
    GeometryUtils.getGeometryFactory()
  );

  private WgsCoordinate centroid;

  FlexLocationGroup(FlexLocationGroupBuilder builder) {
    super(builder.getId());
    this.name = builder.name();
  }

  public static FlexLocationGroupBuilder of(FeedScopedId id) {
    return new FlexLocationGroupBuilder(id);
  }

  @Override
  public I18NString getName() {
    return name;
  }

  @Override
  public I18NString getDescription() {
    return null;
  }

  @Override
  @Nullable
  public I18NString getUrl() {
    return null;
  }

  @Override
  public String getFirstZoneAsString() {
    return null;
  }

  /**
   * Returns the centroid of all stops and areas belonging to this location group.
   */
  @Override
  @Nonnull
  public WgsCoordinate getCoordinate() {
    return centroid;
  }

  @Override
  public Geometry getGeometry() {
    return geometry;
  }

  @Override
  public boolean isPartOfStation() {
    return false;
  }

  @Override
  public boolean isPartOfSameStationAs(StopLocation alternativeStop) {
    return false;
  }

  /**
   * Adds a new location to the location group. This should ONLY be used during the graph build
   * process.
   */
  public void addLocation(StopLocation location) {
    stopLocations.add(location);

    int numGeometries = geometry.getNumGeometries();
    Geometry[] newGeometries = new Geometry[numGeometries + 1];
    for (int i = 0; i < numGeometries; i++) {
      newGeometries[i] = geometry.getGeometryN(i);
    }
    if (location instanceof Stop) {
      WgsCoordinate coordinate = location.getCoordinate();
      Envelope envelope = new Envelope(coordinate.asJtsCoordinate());
      double xscale = Math.cos(coordinate.latitude() * Math.PI / 180);
      envelope.expandBy(100 / xscale, 100);
      newGeometries[numGeometries] = GeometryUtils.getGeometryFactory().toGeometry(envelope);
    } else if (location instanceof FlexStopLocation) {
      newGeometries[numGeometries] = location.getGeometry();
    } else {
      throw new RuntimeException("Unknown location type");
    }
    geometry = new GeometryCollection(newGeometries, GeometryUtils.getGeometryFactory());
    centroid = new WgsCoordinate(geometry.getCentroid().getY(), geometry.getCentroid().getX());
  }

  /**
   * Returns all the locations belonging to this location group.
   */
  public Set<StopLocation> getLocations() {
    return stopLocations;
  }

  @Override
  public boolean sameAs(@Nonnull FlexLocationGroup other) {
    return (
      getId().equals(other.getId()) &&
      Objects.equals(name, other.getName()) &&
      Objects.equals(geometry, other.getGeometry())
    );
  }

  @Override
  @Nonnull
  public FlexLocationGroupBuilder copy() {
    return new FlexLocationGroupBuilder(this);
  }
}
