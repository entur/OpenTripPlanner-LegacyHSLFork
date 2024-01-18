package org.opentripplanner.transit.model.filter.expr;

import java.util.function.Function;
import java.util.regex.Pattern;

public final class RegExpMatcher<T> implements Matcher<T> {

  private final String typeName;
  private final Pattern regexps;
  private final Function<T, String> toValue;

  public RegExpMatcher(String typeName, String regexp, Function<T, String> toValue) {
    this.typeName = typeName;
    this.regexps = Pattern.compile(regexp);
    this.toValue = toValue;
  }

  @Override
  public boolean match(T entity) {
    var value = toValue.apply(entity);
    for (Pattern p : regexps) {
      if (p.matcher(value).matches()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public String toString() {
    return typeName + "/" + regexps + '/';
  }
}
