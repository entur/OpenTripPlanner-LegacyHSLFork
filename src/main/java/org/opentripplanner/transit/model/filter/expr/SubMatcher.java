package org.opentripplanner.transit.model.filter.expr;

import static org.opentripplanner.transit.model.filter.expr.BinaryOperator.SUB;

import java.util.Arrays;
import org.opentripplanner.framework.lang.ArrayUtils;

/**
 * Takes a list of matchers and provide a single interface. All matchers in the list must match for
 * the composite matcher to return a match.
 */
public final class SubMatcher<T> implements Matcher<T> {

  private final Matcher<T> a;
  private final Matcher<T>[] b;

  private SubMatcher(Matcher<T> a, Matcher<T> b) {
    this.a = a;
    this.b = new Matcher[]{b};
  }

  private SubMatcher(Matcher<T> a, Matcher<T>[] list) {
    this.a = a;
    this.b = list;
  }


  public static <T> Matcher<T> subtract(Matcher<T> a, Matcher<T> b) {
    if(a instanceof SubMatcher<T> as) {
      var list = Arrays.copyOf(as.b, as.b.length + 1);
      list[list.length-1] = b;
      return new SubMatcher<>(as.a, list);
    }
    return new SubMatcher<>(a, b);
  }

  @Override
  public boolean match(T entity) {
    if(!a.match(entity)) {
      return false;
    }
    for (Matcher<T> it : b) {
      if (it.match(entity)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return  SUB.toString(a, b);
  }
}
