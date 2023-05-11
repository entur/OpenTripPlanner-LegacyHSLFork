package org.opentripplanner.standalone.server;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import org.opentripplanner.framework.application.RequestCorrelationID;

/**
 * Retrieve the {@code CorrelationId} from the HTTP request header and inject it in the
 * OTPServer context(see {@link RequestCorrelationID}). The correlation id is set on the
 * response before returning.
 */

public class CorrelationIdHeaderFilter implements ContainerRequestFilter, ContainerResponseFilter {

  /**
   * This can not be final since it is injected at startup time. The value must be set in
   * the router config {@code server.}.
   */
  private static String headerCorrelationID;

  public static void initCorrelationIdHeader(String value) {
    headerCorrelationID = value;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    RequestCorrelationID.initRequest(getHttpRequestCorrelationID(requestContext));
  }

  @Override
  public void filter(
    ContainerRequestContext requestContext,
    ContainerResponseContext responseContext
  ) throws IOException {
    responseContext.getHeaders().add(headerCorrelationID, RequestCorrelationID.get());
  }

  private String getHttpRequestCorrelationID(ContainerRequestContext requestContext) {
    return requestContext.getHeaderString(headerCorrelationID);
  }
}
