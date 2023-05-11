package org.opentripplanner.standalone.server;

import org.opentripplanner.framework.lang.StringUtils;

public interface OTPWebApplicationParameters {
  /**
   * The HTTP request/response correlation-id header to use. This is optional and the default is
   * {@code null}/not set. The de-facto standard for this parameter is {@code "X-Correlation-ID"},
   * but OTP uses the value set in the config file, if no parameter is set not correlation-id is
   * used. For more information, see the configuration documentation.
   */
  String httpCorrelationIDHeader();

  default boolean correlationIDEnabled() {
    return StringUtils.hasValue(httpCorrelationIDHeader());
  }
}
