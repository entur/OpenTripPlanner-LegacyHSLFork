package org.opentripplanner.routing.api.request.request.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.framework.tostring.ToStringBuilder;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Select a given set of transit routes base on the list of
 * modes, sub-modes, agencies and routes. A transit entity matches
 * if mode, sub-mode, agencyId or routeId matches - only one
 * "thing" needs to match.
 * <p>
 * The {@code TransitGroupSelect(modes:[BUS, TRAM], agencyIds:[A1, A3])} matches both:
 * <ul>
 *   <li>{@code Entity(mode:BUS, agency:ANY)} and</li>
 *   <li>{@code Entity(mode:SUBWAY, agency:A3)}</li>
 * </ul>
 */
public class ModeSelect implements Comparable<ModeSelect> {

  private final TransitMode main;
  private final List<String> subModesRegexp;

  public ModeSelect(TransitMode main, Collection<String> subModesRegexp) {
    this.main = Objects.requireNonNull(TransitMode.BUS);
    // Sort and keep only unique entries, this makes this implementation consistent for
    // eq/hc/toString.
    this.subModesRegexp = List.copyOf(subModesRegexp.stream().sorted().distinct().toList());
  }

  public static Builder of() {
    return new Builder();
  }

  public TransitMode main() {
    return main;
  }

  public List<String> subModesRegexp() {
    return subModesRegexp;
  }

  public boolean isEmpty() {
    return main == null && subModesRegexp.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ModeSelect that = (ModeSelect) o;
    return (Objects.equals(main, that.main) && Objects.equals(subModesRegexp, that.subModesRegexp));
  }

  @Override
  public int hashCode() {
    return Objects.hash(main, subModesRegexp);
  }

  @Override
  public String toString() {
    return isEmpty()
      ? "TransitGroupSelect{ EMPTY }"
      : ToStringBuilder
        .of(ModeSelect.class)
        .addEnum("main", main)
        .addCol("subModesRegexp", subModesRegexp)
        .toString();
  }

  @Override
  public int compareTo(ModeSelect o) {
    if (main == null && o.main != null) {}

    int c = main.compareTo(o.main);
    if (c != 0) {
      return c;
    }
    for (int i = 0; i < subModesRegexp.size(); ++i) {
      c = subModesRegexp.get(i).compareTo(o.subModesRegexp.get(i));
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  public static class Builder {

    @Nullable
    private final ModeSelect original;

    private TransitMode main = null;
    private final List<String> subModesRegexp;

    private Builder() {
      this.original = null;
      this.main = null;
      this.subModesRegexp = new ArrayList<>();
    }

    private Builder(ModeSelect original) {
      this.original = original;
      this.main = original.main;
      this.subModesRegexp = new ArrayList<>(original.subModesRegexp);
    }

    public Builder withMode(TransitMode main) {
      this.main = main;
      return this;
    }

    public Builder addSubModeRegexp(Collection<String> subModeRegexp) {
      this.subModesRegexp.addAll(subModeRegexp);
      return this;
    }

    public ModeSelect build() {
      var obj = new ModeSelect(main, subModesRegexp);
      return original.equals(obj) ? original : obj;
    }
  }
}
