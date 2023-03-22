package org.opentripplanner.routing.api.request.request;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.model.modes.ExcludeAllTransitFilter;
import org.opentripplanner.routing.api.request.DebugRaptor;
import org.opentripplanner.routing.api.request.request.filter.AllowAllTransitFilter;
import org.opentripplanner.routing.api.request.request.filter.TransitFilter;
import org.opentripplanner.routing.api.request.request.filter.TransitPriorityGroupSelect;
import org.opentripplanner.transit.model.framework.FeedScopedId;

// TODO VIA: Javadoc
public class TransitRequest implements Cloneable, Serializable {

  private List<FeedScopedId> bannedTrips = new ArrayList<>();

  private List<TransitFilter> filters = List.of(AllowAllTransitFilter.of());

  @Deprecated
  private List<FeedScopedId> preferredAgencies = List.of();

  private List<FeedScopedId> unpreferredAgencies = List.of();

  /**
   * @deprecated TODO OTP2 Needs to be implemented
   */
  @Deprecated
  private List<FeedScopedId> preferredRoutes = List.of();

  private List<FeedScopedId> unpreferredRoutes = List.of();

  private List<TransitPriorityGroupSelect> priorityGroupsBase = new ArrayList<>();
  private List<TransitPriorityGroupSelect> priorityGroupsByAgency = new ArrayList<>();
  private List<TransitPriorityGroupSelect> priorityGroupsGlobal = new ArrayList<>();
  private DebugRaptor raptorDebugging = new DebugRaptor();

  public void setBannedTripsFromString(String ids) {
    if (!ids.isEmpty()) {
      this.bannedTrips = FeedScopedId.parseListOfIds(ids);
    }
  }

  public void setBannedTrips(List<FeedScopedId> bannedTrips) {
    this.bannedTrips = bannedTrips;
  }

  public List<FeedScopedId> bannedTrips() {
    return bannedTrips;
  }

  public List<TransitFilter> filters() {
    return filters;
  }

  public void setFilters(List<TransitFilter> filters) {
    this.filters = filters;
  }

  /**
   * All transit patterns matching one of the {@link TransitPriorityGroupSelect}s is assigned the
   * BASE-GROUP-ID. This is normally EVERYTHING including local-traffic, that does not
   * need to be threaded in a special way.
   * <p>
   * Note! Entities that do not mach any of the three sets({@code #priorityGroupsBase()},
   * {@link #priorityGroupsByAgency} and {@link #priorityGroupsGlobal()})
   * will also be put in this group.
   */
  public List<TransitPriorityGroupSelect> priorityGroupsBase() {
    return priorityGroupsBase;
  }

  public void addPriorityGroupsBase(Collection<TransitPriorityGroupSelect> priorityGroupsBase) {
    this.priorityGroupsBase.addAll(priorityGroupsBase);
  }

  /**
   * A unique group-id is assigned all patterns grouped by matching select and agency.
   * In other words, two patterns matching the same select and with the same agency-id
   * will get the same group-id.
   */
  public List<TransitPriorityGroupSelect> priorityGroupsByAgency() {
    return priorityGroupsByAgency;
  }

  /**
   * All patterns matching the same select will be assigned the same group-id.
   */
  public void addPriorityGroupsByAgency(
    Collection<TransitPriorityGroupSelect> priorityGroupsByAgency
  ) {
    this.priorityGroupsByAgency.addAll(priorityGroupsByAgency);
  }

  public List<TransitPriorityGroupSelect> priorityGroupsGlobal() {
    return priorityGroupsGlobal;
  }

  public void addPriorityGroupsGlobal(Collection<TransitPriorityGroupSelect> priorityGroupsGlobal) {
    this.priorityGroupsGlobal.addAll(priorityGroupsGlobal);
  }

  @Deprecated
  public void setPreferredAgenciesFromString(String s) {
    if (!s.isEmpty()) {
      preferredAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  @Deprecated
  public void setPreferredAgencies(List<FeedScopedId> preferredAgencies) {
    this.preferredAgencies = preferredAgencies;
  }

  /**
   * List of preferred agencies by user.
   */
  @Deprecated
  public List<FeedScopedId> preferredAgencies() {
    return preferredAgencies;
  }

  public void setUnpreferredAgenciesFromString(String s) {
    if (!s.isEmpty()) {
      unpreferredAgencies = FeedScopedId.parseListOfIds(s);
    }
  }

  /**
   * List of unpreferred agencies for given user.
   */
  public void setUnpreferredAgencies(List<FeedScopedId> unpreferredAgencies) {
    this.unpreferredAgencies = unpreferredAgencies;
  }

  public List<FeedScopedId> unpreferredAgencies() {
    return unpreferredAgencies;
  }

  @Deprecated
  public void setPreferredRoutesFromString(String s) {
    if (!s.isEmpty()) {
      preferredRoutes = List.copyOf(FeedScopedId.parseListOfIds(s));
    } else {
      preferredRoutes = List.of();
    }
  }

  @Deprecated
  public void setPreferredRoutes(List<FeedScopedId> preferredRoutes) {
    this.preferredRoutes = preferredRoutes;
  }

  /**
   * Set of preferred routes by user and configuration.
   */
  @Deprecated
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

  /**
   * Set of unpreferred routes for given user and configuration.
   */
  public List<FeedScopedId> unpreferredRoutes() {
    return unpreferredRoutes;
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

      clone.preferredAgencies = List.copyOf(this.preferredAgencies);
      clone.unpreferredAgencies = List.copyOf(this.unpreferredAgencies);
      clone.preferredRoutes = List.copyOf(this.preferredRoutes);
      clone.unpreferredRoutes = List.copyOf(this.unpreferredRoutes);
      clone.raptorDebugging = new DebugRaptor(this.raptorDebugging);
      clone.priorityGroupsBase = new ArrayList<>(this.priorityGroupsBase);
      clone.priorityGroupsByAgency = new ArrayList<>(this.priorityGroupsByAgency);
      clone.priorityGroupsGlobal = new ArrayList<>(this.priorityGroupsGlobal);

      // filters are immutable
      clone.setFilters(this.filters);

      return clone;
    } catch (CloneNotSupportedException e) {
      /* this will never happen since our super is the cloneable object */
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns whether it is requested to run the transit search for this request.
   */
  public boolean enabled() {
    return filters.stream().noneMatch(ExcludeAllTransitFilter.class::isInstance);
  }

  /**
   * Disables the transit search for this request, for example when you only want bike routes.
   */
  public void disable() {
    this.filters = List.of(ExcludeAllTransitFilter.of());
  }
}
