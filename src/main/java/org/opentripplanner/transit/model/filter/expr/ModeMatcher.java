package org.opentripplanner.transit.model.filter.expr;

import java.util.List;
import java.util.function.Function;
import org.opentripplanner.transit.model.basic.TransitMode;

public final class ModeMatcher<T> implements Matcher<T> {

  private final TransitMode main;
  private final Function<T, TransitMode> toMainMode;
  private final Matcher<T> subModeMatchers;

  public ModeMatcher(
    TransitMode main,
    Function<T, TransitMode> toMainMode,
    List<String> subModeRegexps,
    Function<T, String> toSubmode
  ) {
    this.main = main;
    this.toMainMode = toMainMode;
    this.subModeMatchers =
      subModeRegexps.isEmpty()
        ? Matcher.everything()
        : OrMatcher.or(
          subModeRegexps
            .stream()
            .map(it -> (Matcher<T>) new RegExpMatcher<T>("SubModes", it, toSubmode))
            .toList()
        );
  }

  @Override
  public boolean match(T entity) {
    return main == toMainMode.apply(entity) && subModeMatchers.match(entity);
  }

  @Override
  public String toString() {
    return "Mode(" + main + ", " + subModeMatchers + ')';
  }
}
