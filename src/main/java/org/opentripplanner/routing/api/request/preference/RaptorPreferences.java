package org.opentripplanner.routing.api.request.preference;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentripplanner.routing.api.request.framework.Units;
import org.opentripplanner.transit.raptor.api.request.Optimization;
import org.opentripplanner.transit.raptor.api.request.RaptorProfile;
import org.opentripplanner.transit.raptor.api.request.SearchParams;
import org.opentripplanner.transit.raptor.api.transit.SearchDirection;
import org.opentripplanner.util.lang.ToStringBuilder;

/**
 * Set of optimizations to use with Raptor. These are available here for testing purposes.
 * <p>
 * THIS CLASS IS IMMUTABLE AND THREAD-SAFE.
 */
public final class RaptorPreferences implements Serializable {

  public static final RaptorPreferences DEFAULT = new RaptorPreferences();
  public static final double MAX_RELAX_COST_AT_DESTINATION_VALUE = 2.0;

  private final Set<Optimization> optimizations;

  private final RaptorProfile profile;

  private final SearchDirection searchDirection;

  private final Instant timeLimit;

  private final double relaxCostAtDestination;

  private RaptorPreferences() {
    this.optimizations = EnumSet.of(Optimization.PARETO_CHECK_AGAINST_DESTINATION);
    this.profile = RaptorProfile.MULTI_CRITERIA;
    this.searchDirection = SearchDirection.FORWARD;
    this.timeLimit = null;
    this.relaxCostAtDestination = SearchParams.NOT_SET;
  }

  private RaptorPreferences(RaptorPreferences.Builder builder) {
    this.optimizations = Collections.unmodifiableSet(builder.copyOptimizations());
    this.profile = Objects.requireNonNull(builder.profile);
    this.searchDirection = Objects.requireNonNull(builder.searchDirection);
    this.timeLimit = builder.timeLimit;
    this.relaxCostAtDestination =
      Units.reluctance(
        builder.relaxCostAtDestination,
        Double.NEGATIVE_INFINITY,
        MAX_RELAX_COST_AT_DESTINATION_VALUE
      );
  }

  public static Builder of() {
    return DEFAULT.copyOf();
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  public Set<Optimization> optimizations() {
    return optimizations;
  }

  public RaptorProfile profile() {
    return profile;
  }

  public SearchDirection searchDirection() {
    return searchDirection;
  }

  /**
   * If set this is used to set the latest-arrival-time in Raptor for arriveBy=false, and to set the
   * earliest-departure-time in Raptor for arriveBy=true.
   */
  @Nullable
  public Instant timeLimit() {
    return timeLimit;
  }

  public double relaxCostAtDestination() {
    return relaxCostAtDestination;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RaptorPreferences that = (RaptorPreferences) o;
    return (
      optimizations.equals(that.optimizations) &&
      profile == that.profile &&
      searchDirection == that.searchDirection &&
      Objects.equals(timeLimit, that.timeLimit) &&
      relaxCostAtDestination == that.relaxCostAtDestination
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(optimizations, profile, searchDirection, timeLimit, relaxCostAtDestination);
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(RaptorPreferences.class)
      .addCol("optimizations", optimizations, DEFAULT.optimizations)
      .addEnum("profile", profile, DEFAULT.profile)
      .addEnum("searchDirection", searchDirection, DEFAULT.searchDirection)
      // Ignore time limit if null (default value)
      .addDateTime("timeLimit", timeLimit)
      .addNum("relaxCostAtDestination", relaxCostAtDestination, DEFAULT.relaxCostAtDestination)
      .toString();
  }

  @SuppressWarnings("UnusedReturnValue")
  public static class Builder {

    private final RaptorPreferences original;
    private RaptorProfile profile;
    private SearchDirection searchDirection;
    private Set<Optimization> optimizations;
    private Instant timeLimit;
    private double relaxCostAtDestination;

    public Builder(RaptorPreferences original) {
      this.original = original;
      this.profile = original.profile;
      this.searchDirection = original.searchDirection;
      this.optimizations = null;
      this.timeLimit = original.timeLimit;
      this.relaxCostAtDestination = original.relaxCostAtDestination;
    }

    public RaptorPreferences original() {
      return original;
    }

    public Builder withOptimizations(Collection<Optimization> optimizations) {
      this.optimizations = EnumSet.copyOf(optimizations);
      return this;
    }

    public Builder withProfile(RaptorProfile profile) {
      this.profile = profile;
      return this;
    }

    public Builder withSearchDirection(SearchDirection searchDirection) {
      this.searchDirection = searchDirection;
      return this;
    }

    public Builder withTimeLimit(Instant timeLimit) {
      this.timeLimit = timeLimit;
      return this;
    }

    public Builder withRelaxCostAtDestination(double relaxCostAtDestination) {
      this.relaxCostAtDestination = relaxCostAtDestination;
      return this;
    }

    public RaptorPreferences build() {
      var value = new RaptorPreferences(this);
      return original.equals(value) ? original : value;
    }

    public Builder apply(Consumer<Builder> body) {
      body.accept(this);
      return this;
    }

    private Set<Optimization> copyOptimizations() {
      return optimizations == null ? original.optimizations : optimizations;
    }
  }
}
