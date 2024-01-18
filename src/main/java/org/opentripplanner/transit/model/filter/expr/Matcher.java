package org.opentripplanner.transit.model.filter.expr;

/**
 * Generic matcher interface - this is the root of the matcher type hierarchy.
 * <p/>
 * @param <T> Domain type to match.
 */
@FunctionalInterface
public interface Matcher<T> {
  boolean match(T entity);

  public static <T> Matcher<T> everything() {
    return e -> true;
  }

  public static <T> Matcher<T> nothing() {
    return e -> false;
  }
}
