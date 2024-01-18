package org.opentripplanner.transit.model.filter.expr;

import java.util.List;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;

public class AbstractMatcherFactory<T> {

  private Matcher<T> root;

  @SuppressWarnings("unchecked")

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

  static Matcher<TripPattern> sub(List<Matcher<TripPattern>> matchers) {
    return OrMatcher.or(matchers);
  }

}
