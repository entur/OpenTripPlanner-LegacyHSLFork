package org.opentripplanner.raptor.api.request;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.api.model.RelaxFunction;

/**
 * Parameters to configure the multi-criteria search.
 * <p>
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public class MultiCriteriaRequest<T extends RaptorTripSchedule> {

  @Nullable
  private final RelaxFunction relaxArrivalTime;

  @Nullable
  private final RelaxFunction relaxC1;

  @Nullable
  private final Double relaxCostAtDestination;

  private MultiCriteriaRequest() {
    this.relaxArrivalTime = null;
    this.relaxC1 = null;
    this.relaxCostAtDestination = null;
  }

  public MultiCriteriaRequest(Builder<T> builder) {
    this.relaxArrivalTime = builder.relaxArrivalTime();
    this.relaxC1 = builder.relaxC1();
    this.relaxCostAtDestination = builder.relaxCostAtDestination();
  }

  public static <S extends RaptorTripSchedule> Builder<S> of() {
    return new Builder<S>(new MultiCriteriaRequest<>());
  }

  public Builder<T> copyOf() {
    return new Builder<>(this);
  }

  /**
   * Whether to accept non-optimal trips if they are close enough with respect to
   * arrival-time. In other words this relaxes the pareto comparison at each stop
   * and at the destination.
   * <p>
   * Let {@code c} be the existing minimum pareto optimal cost to beat. Then a trip
   * with cost {@code c'} is accepted if the following is true:
   * <pre>
   * c' < RelaxFunction.relax(c)
   * </pre>
   * The default is {@code empty}, not set.
   */
  @Nullable
  public Optional<RelaxFunction> relaxArrivalTime() {
    return Optional.ofNullable(relaxArrivalTime);
  }

  /**
   * Whether to accept non-optimal trips if they are close enough with respect to
   * c1(generalized-cost). In other words this relaxes the pareto comparison at
   * each stop and at the destination.
   * <p>
   * Let {@code c} be the existing minimum pareto optimal cost to beat. Then a trip
   * with cost {@code c'} is accepted if the following is true:
   * <pre>
   * c' < RelaxFunction.relax(c)
   * </pre>
   * The default is {@code empty}, not set.
   */
  @Nullable
  public Optional<RelaxFunction> relaxC1() {
    return Optional.ofNullable(relaxC1);
  }

  /**
   * Whether to accept non-optimal trips if they are close enough - if and only if they represent an
   * optimal path for their given iteration. In other words this slack only relaxes the pareto
   * comparison at the destination.
   * <p>
   * Let {@code c} be the existing minimum pareto optimal cost to beat. Then a trip with cost
   * {@code c'} is accepted if the following is true:
   * <pre>
   * c' < Math.round(c * relaxCostAtDestination)
   * </pre>
   * If the value is less than 1.0 a normal '<' comparison is performed.
   * <p>
   * The default is not set.
   * <p>
   * @deprecated This parameter only relax the cost at the destination, not at each stop. This
   * is replaced by {@link #relaxC1()}. This parameter is ignored if {@link #relaxC1()} exist.
   */
  @Deprecated
  public Optional<Double> relaxCostAtDestination() {
    return Optional.ofNullable(relaxCostAtDestination);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultiCriteriaRequest<?> that = (MultiCriteriaRequest<?>) o;
    return (
      Objects.equals(relaxArrivalTime, that.relaxArrivalTime) &&
      Objects.equals(relaxC1, that.relaxC1) &&
      Objects.equals(relaxCostAtDestination, that.relaxCostAtDestination)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(relaxArrivalTime, relaxC1, relaxCostAtDestination);
  }

  @Override
  public String toString() {
    return ToStringBuilder
      .of(MultiCriteriaRequest.class)
      .addObj("relaxArrivalTime", relaxArrivalTime)
      .addObj("relaxC1", relaxC1)
      .addNum("relaxCostAtDestination", relaxCostAtDestination)
      .toString();
  }

  public static class Builder<T extends RaptorTripSchedule> {

    private final MultiCriteriaRequest<T> original;

    private RelaxFunction relaxArrivalTime = null;
    private RelaxFunction relaxC1 = null;
    private Double relaxCostAtDestination = null;

    public Builder(MultiCriteriaRequest<T> original) {
      this.original = original;
    }

    @Nullable
    public RelaxFunction relaxArrivalTime() {
      return relaxArrivalTime;
    }

    public Builder<T> withRelaxArrivalTime(RelaxFunction relaxArrivalTime) {
      this.relaxArrivalTime = relaxArrivalTime;
      return this;
    }

    @Nullable
    public RelaxFunction relaxC1() {
      return relaxC1;
    }

    public Builder<T> withRelaxC1(RelaxFunction relaxC1) {
      this.relaxC1 = relaxC1;
      return this;
    }

    @Nullable
    @Deprecated
    public Double relaxCostAtDestination() {
      return relaxCostAtDestination;
    }

    @Deprecated
    public Builder<T> withRelaxCostAtDestination(Double value) {
      relaxCostAtDestination = value;
      return this;
    }

    public MultiCriteriaRequest<T> build() {
      var newInstance = new MultiCriteriaRequest<T>(this);
      return original.equals(newInstance) ? original : newInstance;
    }

    @Override
    public String toString() {
      return ToStringBuilder
        .of(MultiCriteriaRequest.Builder.class)
        .addObj("relaxArrivalTime", relaxArrivalTime)
        .addObj("relaxC1", relaxC1)
        .addNum("relaxCostAtDestination", relaxCostAtDestination)
        .toString();
    }
  }
}
