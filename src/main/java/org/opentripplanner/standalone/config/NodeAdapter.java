package org.opentripplanner.standalone.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.routing.api.request.RequestFunctions;
import org.opentripplanner.routing.api.request.RequestModes;
import org.opentripplanner.routing.api.request.request.JourneyRequest;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.util.OtpAppException;
import org.opentripplanner.util.time.DurationUtils;
import org.slf4j.Logger;

/**
 * This class wrap a {@link JsonNode} and decorate it with type-safe parsing of types used in OTP
 * like enums, date, time, URIs and so on. By wrapping the JsonNode we get consistent parsing rules
 * and the possibility to log unused parameters when the end of parsing a file. Also the
 * configuration POJOs become cleaner because they do not have any parsing logic in them any more.
 * <p>
 * This class have 100% test coverage - keep it that way, for the individual configuration POJOs a
 * smoke test is good enough.
 */
public class NodeAdapter {

  private final JsonNode json;

  /**
   * The source is the origin of the configuration. The source can be "DEFAULT", the name of the
   * JSON source files or the "SerializedGraph".
   */
  private final String source;

  /**
   * This class wrap a {@link JsonNode} which might be a child of another node. We keep the path
   * string for logging and debugging purposes
   */
  private final String contextPath;

  /**
   * This parameter is used internally in this class to be able to produce a list of parameters
   * which is NOT requested.
   */
  private final List<String> parameterNames = new ArrayList<>();

  /**
   * The collection of children is used to be able to produce a list of unused parameters for all
   * children.
   */
  private final List<NodeAdapter> children = new ArrayList<>();

  public NodeAdapter(@Nonnull JsonNode node, String source) {
    this(node, source, null);
  }

  /**
   * Constructor for nested configuration nodes.
   */
  private NodeAdapter(@Nonnull JsonNode node, String source, String contextPath) {
    this.json = node;
    this.source = source;
    this.contextPath = contextPath;
  }

  public List<NodeAdapter> asList() {
    List<NodeAdapter> result = new ArrayList<>();

    // Count elements starting at 1
    int i = 1;
    for (JsonNode node : json) {
      String pName = "[" + i + "]";
      NodeAdapter child = new NodeAdapter(node, source, fullPath(pName));
      children.add(child);
      result.add(child);
      ++i;
    }
    return result;
  }

  public boolean isNonEmptyArray() {
    return json.isArray() && json.size() > 0;
  }

  public boolean isObject() {
    return json.isObject() && json.size() > 0;
  }

  public String getSource() {
    return source;
  }

  public boolean isEmpty() {
    return json.isMissingNode();
  }

  public NodeAdapter path(String paramName) {
    NodeAdapter child = new NodeAdapter(param(paramName), source, fullPath(paramName));

    if (!child.isEmpty()) {
      parameterNames.add(paramName);
      children.add(child);
    }
    return child;
  }

  /** Delegates to {@link JsonNode#has(String)} */
  public boolean exist(String paramName) {
    return json.has(paramName);
  }

  public Boolean asBoolean(String paramName, boolean defaultValue) {
    return param(paramName).asBoolean(defaultValue);
  }

  /**
   * Get a required parameter as a boolean value.
   *
   * @throws OtpAppException if parameter is missing.
   */
  public boolean asBoolean(String paramName) {
    assertRequiredFieldExist(paramName);
    return param(paramName).asBoolean();
  }

  public double asDouble(String paramName, double defaultValue) {
    return param(paramName).asDouble(defaultValue);
  }

  public double asDouble(String paramName) {
    assertRequiredFieldExist(paramName);
    return param(paramName).asDouble();
  }

  public Optional<Double> asDoubleOptional(String paramName) {
    JsonNode node = param(paramName);
    if (node.isMissingNode()) {
      return Optional.empty();
    }
    return Optional.of(node.asDouble());
  }

  public List<Double> asDoubles(String paramName, List<Double> defaultValue) {
    if (!exist(paramName)) return defaultValue;
    return arrayAsList(paramName, JsonNode::asDouble);
  }

  public int asInt(String paramName, int defaultValue) {
    return param(paramName).asInt(defaultValue);
  }

  public int asInt(String paramName) {
    assertRequiredFieldExist(paramName);
    return param(paramName).asInt();
  }

  public long asLong(String paramName, long defaultValue) {
    return param(paramName).asLong(defaultValue);
  }

  public long asLong(String paramName) {
    assertRequiredFieldExist(paramName);
    return param(paramName).asLong();
  }

  public String asText(String paramName, String defaultValue) {
    return param(paramName).asText(defaultValue);
  }

  public String asText() {
    return json.asText();
  }

  public Set<String> asTextSet(String paramName, Set<String> defaultValue) {
    if (!exist(paramName)) return defaultValue;
    return new HashSet<>(arrayAsList(paramName, JsonNode::asText));
  }

  public RequestModes asRequestModes(String paramName, RequestModes defaultValue) {
    var node = param(paramName);
    return node == null || node.asText().isBlank()
      ? defaultValue
      : new QualifiedModeSet(node.asText()).getRequestModes();
  }

  /**
   * Get a required parameter as a text String value.
   *
   * @throws OtpAppException if parameter is missing.
   */
  public String asText(String paramName) {
    assertRequiredFieldExist(paramName);
    return param(paramName).asText();
  }

  /** Get required enum value. Parser is not case sensitive. */
  public <T extends Enum<T>> T asEnum(String paramName, Class<T> ofType) {
    return asEnum(paramName, asText(paramName), ofType);
  }

  /** Get optional enum value. Parser is not case sensitive. */
  @SuppressWarnings("unchecked")
  public <T extends Enum<T>> T asEnum(String paramName, T defaultValue) {
    var value = asText(paramName, defaultValue.name());
    return asEnum(paramName, value, (Class<T>) defaultValue.getClass());
  }

  /**
   * Get a map of enum values listed in the config like this: (This example have Boolean values)
   * <pre>
   * key : {
   *   A : true,  // turned on
   *   B : false  // turned off
   *   // Commented out to use default value
   *   // C : true
   * }
   * </pre>
   *
   * @param <E>    The enum type
   * @param <T>    The map value type.
   * @param mapper The function to use to map a node in the JSON tree into a value of type T. The
   *               second argument to the function is the enum NAME(String).
   * @return a map of listed enum values as keys with value, or an empty map if not set.
   */
  public <T, E extends Enum<E>> Map<E, T> asEnumMap(
    String paramName,
    Class<E> enumClass,
    BiFunction<NodeAdapter, String, T> mapper
  ) {
    return localAsEnumMap(paramName, enumClass, mapper, false);
  }

  /**
   * Get a map of enum values listed in the config like the {@link #asEnumMap(String, Class,
   * BiFunction)}, but verify that all enum keys are listed. This can be used for settings where
   * there is appropriate no default value. Note! This method return {@code null} if the given
   * parameter is not present.
   */
  public <T, E extends Enum<E>> Map<E, T> asEnumMapAllKeysRequired(
    String paramName,
    Class<E> enumClass,
    BiFunction<NodeAdapter, String, T> mapper
  ) {
    Map<E, T> map = localAsEnumMap(paramName, enumClass, mapper, true);
    return map.isEmpty() ? null : map;
  }

  public <T extends Enum<T>> Set<T> asEnumSet(String paramName, Class<T> enumClass) {
    if (!exist(paramName)) {
      return Set.of();
    }

    Set<T> result = EnumSet.noneOf(enumClass);

    JsonNode param = param(paramName);
    if (param.isArray()) {
      for (JsonNode it : param) {
        result.add(Enum.valueOf(enumClass, it.asText()));
      }
    }
    // Assume all values is concatenated in one string separated by ','
    else {
      String[] values = asText(paramName).split("[,\\s]+");
      for (String value : values) {
        if (value.isBlank()) {
          continue;
        }
        try {
          result.add(Enum.valueOf(enumClass, value));
        } catch (IllegalArgumentException e) {
          throw new OtpAppException(
            "The parameter '" +
            fullPath(paramName) +
            "': '" +
            value +
            "' is not an enum value of " +
            enumClass.getSimpleName() +
            ". Source: " +
            source +
            "."
          );
        }
      }
    }
    return result;
  }

  public FeedScopedId asFeedScopedId(String paramName, FeedScopedId defaultValue) {
    if (!exist(paramName)) {
      return defaultValue;
    }
    return FeedScopedId.parseId(asText(paramName));
  }

  public List<FeedScopedId> asFeedScopedIds(String paramName, List<FeedScopedId> defaultValues) {
    JsonNode array = param(paramName);

    if (array.isMissingNode()) {
      return defaultValues;
    }
    assertIsArray(paramName, array);

    List<FeedScopedId> ids = new ArrayList<>();
    for (JsonNode it : array) {
      ids.add(FeedScopedId.parseId(it.asText()));
    }
    return ids;
  }

  public List<FeedScopedId> asFeedScopedIdList(String paramName, List<FeedScopedId> defaultValues) {
    return List.copyOf(asFeedScopedIds(paramName, List.copyOf(defaultValues)));
  }

  public Locale asLocale(String paramName, Locale defaultValue) {
    if (!exist(paramName)) {
      return defaultValue;
    }
    String[] parts = asText(paramName).split("[-_ ]+");
    if (parts.length == 1) {
      return new Locale(parts[0]);
    }
    if (parts.length == 2) {
      return new Locale(parts[0], parts[1]);
    }
    if (parts.length == 3) {
      return new Locale(parts[0], parts[1], parts[2]);
    }
    throw new OtpAppException(
      "The parameter: '" +
      fullPath(paramName) +
      "' is not recognized as a valid Locale. Use: <Language>[_<country>[_<variant>]]. " +
      "Source: " +
      source +
      "."
    );
  }

  public LocalDate asDateOrRelativePeriod(String paramName, String defaultValue) {
    String text = asText(paramName, defaultValue);
    try {
      if (text == null || text.isBlank()) {
        return null;
      }
      if (text.startsWith("-") || text.startsWith("P")) {
        return LocalDate.now().plus(Period.parse(text));
      } else {
        return LocalDate.parse(text);
      }
    } catch (DateTimeParseException e) {
      throw new OtpAppException(
        "The parameter '" +
        fullPath(paramName) +
        "': '" +
        text +
        "' is not a Period or LocalDate. " +
        "Source: " +
        source +
        ". Details: " +
        e.getLocalizedMessage()
      );
    }
  }

  public Duration asDuration(String paramName, Duration defaultValue) {
    return exist(paramName) ? DurationUtils.duration(param(paramName).asText()) : defaultValue;
  }

  public Duration asDuration(String paramName) {
    assertRequiredFieldExist(paramName);
    return DurationUtils.duration(param(paramName).asText());
  }

  public List<Duration> asDurations(String paramName, List<Duration> defaultValues) {
    JsonNode array = param(paramName);

    if (array.isMissingNode()) {
      return defaultValues;
    }
    assertIsArray(paramName, array);

    List<Duration> durations = new ArrayList<>();
    for (JsonNode it : array) {
      durations.add(DurationUtils.duration(it.asText()));
    }
    return durations;
  }

  public Pattern asPattern(String paramName, String defaultValue) {
    String regex = asText(paramName, defaultValue);
    if (regex == null) {
      return null;
    }
    return Pattern.compile(regex);
  }

  public List<URI> asUris(String paramName) {
    List<URI> uris = new ArrayList<>();
    JsonNode array = param(paramName);

    if (array.isMissingNode()) {
      return uris;
    }
    assertIsArray(paramName, array);

    for (JsonNode it : array) {
      uris.add(uriFromString(paramName, it.asText()));
    }
    return uris;
  }

  public URI asUri(String paramName) {
    assertRequiredFieldExist(paramName);
    return asUri(paramName, null);
  }

  public URI asUri(String paramName, String defaultValue) {
    return uriFromString(paramName, asText(paramName, defaultValue));
  }

  public DoubleFunction<Double> asLinearFunction(
    String paramName,
    DoubleFunction<Double> defaultValue
  ) {
    String text = param(paramName).asText();
    if (text == null || text.isBlank()) {
      return defaultValue;
    }
    try {
      return RequestFunctions.parse(text);
    } catch (Exception e) {
      throw new OtpAppException(
        "Unable to parse parameter '" +
        fullPath(paramName) +
        "'. The value '" +
        text +
        "' is not a valid function on the form \"a + b x\" (\"2.0 + 7.1 x\")." +
        "Source: " +
        source +
        "."
      );
    }
  }

  public ZoneId asZoneId(String paramName, ZoneId defaultValue) {
    if (!exist(paramName)) {
      return defaultValue;
    }
    final String zoneId = param(paramName).asText();
    try {
      return ZoneId.of(zoneId);
    } catch (DateTimeException e) {
      throw new OtpAppException(
        "Unable to parse parameter '" +
        fullPath(paramName) +
        "'. The value '" +
        zoneId +
        "' is is not a valid Zone ID, it should be parsable by java.time.ZoneId class. " +
        "Source: " +
        source +
        "."
      );
    }
  }

  /**
   * Log unused parameters for the entire configuration file/noe tree. Call this method for thew
   * root adapter for each config file read.
   */
  public void logAllUnusedParameters(Logger log) {
    for (String p : unusedParams()) {
      log.warn("Unexpected config parameter: '{}' in '{}'. Is the spelling correct?", p, source);
    }
  }

  public <T> Map<String, T> asMap(String paramName, BiFunction<NodeAdapter, String, T> mapper) {
    NodeAdapter node = path(paramName);

    if (node.isEmpty()) {
      return Map.of();
    }

    Map<String, T> result = new HashMap<>();

    Iterator<String> names = node.json.fieldNames();
    while (names.hasNext()) {
      String key = names.next();
      result.put(key, mapper.apply(node, key));
    }
    return result;
  }

  JsonNode asRawNode(String paramName) {
    return param(paramName);
  }

  private <T extends Enum<T>> T asEnum(String paramName, String value, Class<T> ofType) {
    var upperCaseValue = value.toUpperCase();
    return Stream
      .of(ofType.getEnumConstants())
      .filter(it -> it.name().toUpperCase().equals(upperCaseValue))
      .findFirst()
      .orElseThrow(() -> {
        List<T> legalValues = List.of(ofType.getEnumConstants());
        throw new OtpAppException(
          "The parameter '" +
          fullPath(paramName) +
          "': '" +
          value +
          "' is not in legal. Expected one of " +
          legalValues +
          ". Source: " +
          source +
          "."
        );
      });
  }

  /* private methods */

  /**
   * This method list all unused parameters(full path), also nested ones. It uses recursion to get
   * child nodes.
   */
  private List<String> unusedParams() {
    List<String> unusedParams = new ArrayList<>();
    Iterator<String> it = json.fieldNames();

    while (it.hasNext()) {
      String fieldName = it.next();
      if (!parameterNames.contains(fieldName)) {
        unusedParams.add(fullPath(fieldName) + ":" + json.get(fieldName));
      }
    }

    for (NodeAdapter c : children) {
      // Recursive call to get child unused parameters
      unusedParams.addAll(c.unusedParams());
    }
    unusedParams.sort(String::compareTo);
    return unusedParams;
  }

  private JsonNode param(String paramName) {
    parameterNames.add(paramName);
    return json.path(paramName);
  }

  private String fullPath(String paramName) {
    return contextPath == null ? paramName : concatPath(contextPath, paramName);
  }

  private String concatPath(String a, String b) {
    return a + "." + b;
  }

  private URI uriFromString(String paramName, String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return new URI(text);
    } catch (URISyntaxException e) {
      throw new OtpAppException(
        "Unable to parse parameter '" +
        fullPath(paramName) +
        "'. The value '" +
        text +
        "' is is not a valid URI, it should be parsable by java.net.URI class. " +
        "Source: " +
        source +
        "."
      );
    }
  }

  private <T> List<T> arrayAsList(String paramName, Function<JsonNode, T> parse) {
    List<T> values = new ArrayList<>();
    for (JsonNode node : param(paramName)) {
      values.add(parse.apply(node));
    }
    return values;
  }

  private void assertRequiredFieldExist(String paramName) {
    if (!exist(paramName)) {
      throw requiredFieldMissingException(paramName);
    }
  }

  private OtpAppException requiredFieldMissingException(String paramName) {
    return new OtpAppException(
      "Required parameter '" + fullPath(paramName) + "' not found in '" + source + "'."
    );
  }

  private void assertIsArray(String paramName, JsonNode array) {
    if (!array.isArray()) {
      throw new OtpAppException(
        "Unable to parse parameter '" +
        fullPath(paramName) +
        "': '" +
        array.asText() +
        "' expected an ARRAY. Source: " +
        source +
        "."
      );
    }
  }

  private <T, E extends Enum<E>> EnumMap<E, T> localAsEnumMap(
    String paramName,
    Class<E> enumClass,
    BiFunction<NodeAdapter, String, T> mapper,
    boolean requireAllValues
  ) {
    NodeAdapter node = path(paramName);

    EnumMap<E, T> result = new EnumMap<>(enumClass);

    if (node.isEmpty()) {
      return result;
    }

    for (E v : enumClass.getEnumConstants()) {
      if (node.exist(v.name())) {
        result.put(v, mapper.apply(node, v.name()));
      } else if (requireAllValues) {
        throw requiredFieldMissingException(concatPath(paramName, v.name()));
      }
    }
    return result;
  }
}
