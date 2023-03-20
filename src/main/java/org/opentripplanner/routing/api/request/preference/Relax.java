package org.opentripplanner.routing.api.request.preference;

public record Relax(double ratio, int slack) {
  public static final Relax NORMAL = new Relax(1.0, 0);
}
