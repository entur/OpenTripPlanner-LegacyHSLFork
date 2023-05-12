package org.opentripplanner.framework.application;

import java.security.SecureRandom;
import java.util.Random;
import javax.annotation.Nullable;
import org.slf4j.MDC;

/**
 * This class is responsible for generating the RequestCorrelationID[if not passed in] and
 * storing it in the ThreadLocal and in the slf4j log Mapped Diagnostic Context.
 * <p>
 * To output the {@code correlationID} in the logs use {@code %X{correlationID}} in the
 * log appender format.
 * <p>
 * If a correlation-id is not set in the request a 6 character long id is generated. This
 * should be more than enough to ensure uniqueness within a timeframe of 1 hour depending
 * on the number of requests processed. There are 36^6 ≈ 2*10^9 possible id.
 */
public class RequestCorrelationId {

  private static final String LOG_KEY = "correlationId";
  private static final String MIN_ID_BASE = "100000";
  private static final long MIN_ID = Long.parseLong(MIN_ID_BASE, Character.MAX_RADIX);
  private static final long MAX_ID = Long.parseLong(MIN_ID_BASE + "0", Character.MAX_RADIX);

  /**
   * The regular Random is good enough for generating new correlationIds, but we want
   * to properly seed it. Many otp instances might get started at the same time so using
   * the current time is not good enough. We do not use UUID because an 32 bit int is
   * enough and easier to read.
   */
  private static final Random ID_GEN = new Random(new SecureRandom().nextLong());

  private static boolean enabled = false;

  // private constructor to prevent creating new instances
  private RequestCorrelationId() {}

  /**
   * Enable CorrelationID in logs and in http request/response.
   */
  public static void enable() {
    RequestCorrelationId.enabled = true;
  }

  public static boolean isEnabled() {
    return RequestCorrelationId.enabled;
  }

  /**
   * Set the CorrelationID value in the local thread context.
   * (We delegate this to the slf4j logging framework, an alternative is to use ThreadLocal
   * instead.
   */
  public static void initRequest(String id) {
    set(initIdIfNotSet(id));
  }

  /**
   * Returns the current thread's unique CorrelationID
   */
  @Nullable
  public static String get() {
    return enabled ? MDC.get(LOG_KEY) : null;
  }

  /**
   * Set correlation ID in the slf4j Mapped Diagnostic Context
   */
  public static void setOnLocalThread(@Nullable String value) {
    set(value);
  }

  public static void clear() {
    if (enabled) {
      MDC.remove(LOG_KEY);
    }
  }

  private static void set(@Nullable String value) {
    if (enabled && value != null) {
      MDC.put(LOG_KEY, value);
    }
  }

  /**
   * Return the id [if not black] or generate a new one.
   */
  private static String initIdIfNotSet(String id) {
    if (id != null && !id.isBlank()) {
      return id;
    }
    long v = ID_GEN.nextLong(MIN_ID, MAX_ID);
    return Long.toString(v, Character.MAX_RADIX);
  }
}
