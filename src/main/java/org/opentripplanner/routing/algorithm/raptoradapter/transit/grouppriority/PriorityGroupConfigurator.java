package org.opentripplanner.routing.algorithm.raptoradapter.transit.grouppriority;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import org.opentripplanner.framework.lang.ArrayUtils;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.cost.grouppriority.TransitGroupPriority32n;
import org.opentripplanner.transit.model.filter.expr.Matcher;
import org.opentripplanner.transit.model.filter.transit.TripPatternMatcherFactory;
import org.opentripplanner.routing.api.request.request.filter.TransitGroupSelect;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.network.TripPattern;

/**
 * This class dynamically builds an index of transit-group-ids from the
 * provided {@link TransitGroupSelect}s while serving the caller with
 * group-ids for each requested pattern. It is made for optimal
 * performance, since it is used in request scope.
 * <p>
 * THIS CLASS IS NOT THREAD-SAFE.
 */
public class PriorityGroupConfigurator {

  /**
   * There are two ways we can treat the base (local-traffic) transit priority group:
   * <ol>
   *   <li> We can assign group id 1 (one) to the base group and it will be treated as any other group.
   *   <li> We can assign group id 0 (zero) to the base and it will not be added to the set of groups
   *   a given path has.
   * </ol>
   * When we compare paths we compare sets of group ids. A set is dominating another set if it is
   * a smaller subset or different from the other set.
   * <p>
   * <b>Example - base-group-id = 0 (zero)</b>
   * <p>
   * Let B be the base and G be concrete group. Then: (B) dominates (G), (G) dominates (B), (B)
   * dominates (BG), but (G) does not dominate (BG). In other words, paths with only agency
   * X (group G) is not given an advantage in the routing over paths with a combination of agency
   * X (group G) and local traffic (group B).
   * <p>
   * TODO: Experiment with base-group-id=0 and make it configurable.
   */
  private static final int GROUP_INDEX_COUNTER_START = 1;

  private final int baseGroupId = TransitGroupPriority32n.groupId(GROUP_INDEX_COUNTER_START);
  private int groupIndexCounter = GROUP_INDEX_COUNTER_START;
  private final boolean enabled;
  private final Matcher<TripPattern>[] agencyMatchers;
  private final Matcher<TripPattern>[] globalMatchers;

  // Index matchers and ids
  private final List<MatcherWithGroupId> matchersWithIds;

  private PriorityGroupConfigurator() {
    this.enabled = false;
    this.agencyMatchers = null;
    this.globalMatchers = null;
    this.matchersWithIds = List.of();
  }

  private PriorityGroupConfigurator(
    Collection<TransitGroupSelect> byAgency,
    Collection<TransitGroupSelect> global
  ) {
    this.agencyMatchers = TripPatternMatcherFactory.of(byAgency);
    this.globalMatchers = TripPatternMatcherFactory.of(global);
    this.enabled = Stream.of(agencyMatchers, globalMatchers).anyMatch(ArrayUtils::hasContent);

    var list = new ArrayList<MatcherWithGroupId>();
    list.addAll(
      Arrays.stream(globalMatchers).map(m -> new MatcherAndId(m, nextGroupId())).toList()
    );
    list.addAll(Arrays.stream(agencyMatchers).map(MatcherAgencyAndIds::new).toList());
    this.matchersWithIds = List.copyOf(list);
  }

  public static PriorityGroupConfigurator empty() {
    return new PriorityGroupConfigurator();
  }

  public static PriorityGroupConfigurator of(
    Collection<TransitGroupSelect> byAgency,
    Collection<TransitGroupSelect> global
  ) {
    if (Stream.of(byAgency, global).allMatch(Collection::isEmpty)) {
      return empty();
    }
    return new PriorityGroupConfigurator(byAgency, global);
  }

  /**
   * Fetch/lookup the transit-group-id for the given pattern.
   * <p>
   * @throws IllegalArgumentException if more than 32 group-ids are requested.
   */
  public int lookupTransitGroupPriorityId(TripPattern tripPattern) {
    if (!enabled || tripPattern == null) {
      return baseGroupId;
    }

    for (var it : matchersWithIds) {
      if (it.matcher().match(tripPattern)) {
        var agencyId = tripPattern.getRoute().getAgency().getId();
        return it.groupId(agencyId, this::nextGroupId);
      }
    }

    // Fallback to base-group-id
    return baseGroupId;
  }

  public int baseGroupId() {
    return baseGroupId;
  }

  private int nextGroupId() {
    return TransitGroupPriority32n.groupId(++groupIndexCounter);
  }

  /**
   * A matcher with the associated priority group ids.
   */
  interface MatcherWithGroupId {
    Matcher<TripPattern> matcher();
    int groupId(FeedScopedId agencyId, IntSupplier nextGroupId);
  }

  /** Pair of matcher and groupId. Used only inside this class. */
  private record MatcherAndId(Matcher<TripPattern> matcher, int groupId)
    implements MatcherWithGroupId {
    @Override
    public int groupId(FeedScopedId agencyId, IntSupplier nextGroupId) {
      return groupId;
    }
  }

  /** Matcher with a map of group ids by agency. */
  private record MatcherAgencyAndIds(Matcher<TripPattern> matcher, TObjectIntMap<FeedScopedId> ids)
    implements MatcherWithGroupId {
    MatcherAgencyAndIds(Matcher<TripPattern> matcher) {
      this(
        matcher,
        new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1)
      );
    }

    @Override
    public int groupId(FeedScopedId agencyId, IntSupplier nextGroupId) {
      int groupId = ids.get(agencyId);
      if (groupId < 0) {
        groupId = nextGroupId.getAsInt();
        ids.put(agencyId, groupId);
      }
      return groupId;
    }
  }
}
