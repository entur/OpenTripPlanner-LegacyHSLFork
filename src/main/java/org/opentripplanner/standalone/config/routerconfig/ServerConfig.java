package org.opentripplanner.standalone.config.routerconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_4;

import java.time.Duration;
import org.opentripplanner.framework.application.OtpAppException;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.standalone.server.OTPWebApplicationParameters;

public class ServerConfig implements OTPWebApplicationParameters {

  private final Duration apiProcessingTimeout;
  private final String httpCorrelationIDHeader;

  public ServerConfig(String parameterName, NodeAdapter root) {
    NodeAdapter c = root
      .of(parameterName)
      .since(V2_4)
      .summary("Configuration for router server.")
      .description(
        """
These parameters are used to configure the router server. Many parameters are specific to a 
domain, these are set tin the routing request.
        """
      )
      .asObject();

    this.apiProcessingTimeout =
      c
        .of("apiProcessingTimeout")
        .since(V2_4)
        .summary("Maximum processing time for an API request")
        .description(
          """
       This timeout limits the server-side processing time for a given API request.
       This does not include network latency nor waiting time in the HTTP server thread pool.
       The default value is `-1s` (no timeout).
       The timeout is applied to all APIs (REST, Transmodel , Legacy GraphQL).
        """
        )
        .asDuration(Duration.ofSeconds(-1));

    this.httpCorrelationIDHeader =
      c
        .of("httpCorrelationIDHeader")
        .since(V2_4)
        .summary("The HTTP Correlation-ID-Header to use")
        .description(
          """
          The HTTP request/response correlation-id header to use.The de-facto standard for this
          parameter is {@code "X-Correlation-ID"}, but OTP uses the value set in the config file,
          if no parameter is set not correlation-id is used.
          <p>
          If set OTP will fetch the correlation id from the http request headers and use it. If the
          header is missing ir no correlation-id value exist, a unique 6-character id is generated
          and used. The id is logged in every log message in the request scope and set on the http
          response with the given header name.
          <p>
          To disable this feature skip this parameter or set it to an empty string.
        """
        )
        .asString(null);
  }

  public Duration apiProcessingTimeout() {
    return apiProcessingTimeout;
  }

  public void validate(Duration streetRoutingTimeout) {
    if (
      !apiProcessingTimeout.isNegative() &&
      streetRoutingTimeout.toSeconds() > apiProcessingTimeout.toSeconds()
    ) {
      throw new OtpAppException(
        "streetRoutingTimeout (" +
        streetRoutingTimeout +
        ") must be shorter than apiProcessingTimeout (" +
        apiProcessingTimeout +
        ')'
      );
    }
  }

  @Override
  public String httpCorrelationIDHeader() {
    return httpCorrelationIDHeader;
  }
}
