package org.opentripplanner.routing.api.request.request;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.routing.api.request.DebugRaptor;
import org.opentripplanner.routing.core.RouteMatcher;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;

// TODO VIA: Javadoc
public class TransitRequest implements Cloneable, Serializable {

  private List<FeedScopedId> whiteListedAgencies = List.of();
  private List<FeedScopedId> bannedAgencies = List.of();
  private List<FeedScopedId> preferredAgencies = List.of();
  private List<FeedScopedId> unpreferredAgencies = List.of();
  private RouteMatcher whiteListedRoutes = RouteMatcher.emptyMatcher();
  private RouteMatcher bannedRoutes = RouteMatcher.emptyMatcher();
  private List<FeedScopedId> preferredRoutes = List.of();
  private List<FeedScopedId> unpreferredRoutes = List.of();
  private List<FeedScopedId> bannedTrips = List.of();
  private DebugRaptor raptorDebugging = new DebugRaptor();

  public void setWhiteListedAgenciesFromSting(String s) {
    if (!s.isEmpty()) {
      whiteListedAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  public void setWhiteListedAgencies(List<FeedScopedId> whiteListedAgencies) {
    this.whiteListedAgencies = whiteListedAgencies;
  }

  public List<FeedScopedId> whiteListedAgencies() {
    return whiteListedAgencies;
  }

  public void setBannedAgenciesFromSting(String s) {
    if (!s.isEmpty()) {
      bannedAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  public void setBannedAgencies(List<FeedScopedId> bannedAgencies) {
    this.bannedAgencies = bannedAgencies;
  }

  public List<FeedScopedId> bannedAgencies() {
    return bannedAgencies;
  }

  public void setPreferredAgenciesFromString(String s) {
    if (!s.isEmpty()) {
      preferredAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  public void setPreferredAgencies(List<FeedScopedId> preferredAgencies) {
    this.preferredAgencies = preferredAgencies;
  }

  public List<FeedScopedId> preferredAgencies() {
    return preferredAgencies;
  }

  public void setUnpreferredAgenciesFromString(String s) {
    if (!s.isEmpty()) {
      unpreferredAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  public void setUnpreferredAgencies(List<FeedScopedId> unpreferredAgencies) {
    this.unpreferredAgencies = unpreferredAgencies;
  }

  public List<FeedScopedId> unpreferredAgencies() {
    return unpreferredAgencies;
  }

  public void setWhiteListedRoutesFromString(String s) {
    if (!s.isEmpty()) {
      whiteListedRoutes = RouteMatcher.parse(s);
    } else {
      whiteListedRoutes = RouteMatcher.emptyMatcher();
    }
  }

  public void setWhiteListedRoutes(RouteMatcher whiteListedRoutes) {
    this.whiteListedRoutes = whiteListedRoutes;
  }

  public RouteMatcher whiteListedRoutes() {
    return whiteListedRoutes;
  }

  public void setBannedRoutesFromString(String s) {
    if (!s.isEmpty()) {
      bannedRoutes = RouteMatcher.parse(s);
    } else {
      bannedRoutes = RouteMatcher.emptyMatcher();
    }
  }

  public void setBannedRoutes(RouteMatcher bannedRoutes) {
    this.bannedRoutes = bannedRoutes;
  }

  public RouteMatcher bannedRoutes() {
    return bannedRoutes;
  }

  public void setPreferredRoutesFromString(String s) {
    if (!s.isEmpty()) {
      preferredRoutes = List.copyOf(FeedScopedId.parseListOfIds(s));
    } else {
      preferredRoutes = List.of();
    }
  }

  public void setPreferredRoutes(List<FeedScopedId> preferredRoutes) {
    this.preferredRoutes = preferredRoutes;
  }

  public List<FeedScopedId> preferredRoutes() {
    return preferredRoutes;
  }

  public void setUnpreferredRoutesFromString(String s) {
    if (!s.isEmpty()) {
      unpreferredRoutes = List.copyOf(FeedScopedId.parseListOfIds(s));
    } else {
      unpreferredRoutes = List.of();
    }
  }

  public void setUnpreferredRoutes(List<FeedScopedId> unpreferredRoutes) {
    this.unpreferredRoutes = unpreferredRoutes;
  }

  public List<FeedScopedId> unpreferredRoutes() {
    return unpreferredRoutes;
  }

  public void setBannedTripsFromString(String ids) {
    if (!ids.isEmpty()) {
      bannedTrips = FeedScopedId.parseListOfIds(ids);
    }
  }

  public void setBannedTrips(List<FeedScopedId> bannedTrips) {
    this.bannedTrips = bannedTrips;
  }

  public List<FeedScopedId> bannedTrips() {
    return bannedTrips;
  }

  public void setRaptorDebugging(DebugRaptor raptorDebugging) {
    this.raptorDebugging = raptorDebugging;
  }

  public DebugRaptor raptorDebugging() {
    return raptorDebugging;
  }

  public TransitRequest clone() {
    try {
      var clone = (TransitRequest) super.clone();

      clone.whiteListedAgencies = List.copyOf(this.whiteListedAgencies);
      clone.bannedAgencies = List.copyOf(this.bannedAgencies);
      clone.preferredAgencies = List.copyOf(this.preferredAgencies);
      clone.unpreferredAgencies = List.copyOf(this.unpreferredAgencies);
      clone.whiteListedRoutes = this.whiteListedRoutes.clone();
      clone.bannedRoutes = this.bannedRoutes.clone();
      clone.preferredRoutes = List.copyOf(this.preferredRoutes);
      clone.unpreferredRoutes = List.copyOf(this.unpreferredRoutes);
      clone.bannedTrips = List.copyOf(this.bannedTrips);
      clone.raptorDebugging = new DebugRaptor(this.raptorDebugging);

      return clone;
    } catch (CloneNotSupportedException e) {
      /* this will never happen since our super is the cloneable object */
      throw new RuntimeException(e);
    }
  }
}
