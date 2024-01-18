package org.opentripplanner.transit.model.filter.transit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.transit.model.filter.expr.AndMatcher;
import org.opentripplanner.transit.model.filter.expr.IdMatcher;
import org.opentripplanner.transit.model.filter.expr.Matcher;
import org.opentripplanner.transit.model.filter.expr.ModeMatcher;
import org.opentripplanner.transit.model.filter.expr.OrMatcher;
import org.opentripplanner.routing.api.request.request.filter.TransitGroupSelect;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;

public class TripPatternMatcherFactory {

  @SuppressWarnings("unchecked")
  public static Matcher<TripPattern>[] of(Collection<TransitGroupSelect> selectors) {
    return selectors
      .stream()
      .map(TripPatternMatcherFactory::of)
      .toArray(Matcher[]::new);
  }


  public static Matcher<TripPattern> of(TransitGroupSelect select) {
    if (select.isEmpty()) {
      return Matcher.everything();
    }
    List<Matcher<TripPattern>> list = new ArrayList<>();

    if (!select.modes().isEmpty()) {
      list.add(
        or(select.modes().stream().map(it -> mode(it.main(), it.subModesRegexp())).toList())
      );
    }

    if (!select.routeIds().isEmpty()) {
      list.add(
        or(select.routeIds().stream().map(TripPatternMatcherFactory::routeId).toList())
      );
    }

    if (!select.agencyIds().isEmpty()) {
      list.add(
        or(select.agencyIds().stream().map(TripPatternMatcherFactory::routeId).toList())
      );
    }
    return and(list);
  }

  static Matcher<TripPattern> mode(TransitMode main, List<String> subModeRegexps) {
    return new ModeMatcher<>(
      main,
      TripPattern::getMode,
      subModeRegexps,
      p -> p.getNetexSubmode().name()
    );
  }

  static Matcher<TripPattern> agencyId(FeedScopedId id) {
    return new IdMatcher<>("agency", id, p -> p.getRoute().getAgency().getId());
  }

  static Matcher<TripPattern> routeId(FeedScopedId id) {
    return new IdMatcher<>("agency", id, p -> p.getRoute().getId());
  }

  static Matcher<TripPattern> and(List<Matcher<TripPattern>> matchers) {
    return AndMatcher.of(matchers);
  }

  static Matcher<TripPattern> or(List<Matcher<TripPattern>> matchers) {
    return OrMatcher.or(matchers);
  }
}
