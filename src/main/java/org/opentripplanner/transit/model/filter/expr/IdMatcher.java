package org.opentripplanner.transit.model.filter.expr;

import java.util.function.Function;
import org.opentripplanner.transit.model.filter.expr.Matcher;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public final class IdMatcher<T> implements Matcher<T> {

  private final String typeName;
  private final FeedScopedId id;
  private final Function<T, FeedScopedId> idProvider;

  public IdMatcher(String typeName, FeedScopedId id, Function<T, FeedScopedId> idProvider) {
    this.typeName = typeName;
    this.id = id;
    this.idProvider = idProvider;
  }

  @Override
  public boolean match(T entity) {
    return id.equals(idProvider.apply(entity));
  }

  @Override
  public String toString() {
    return typeName + "Id(" + id + ')';
  }
}
