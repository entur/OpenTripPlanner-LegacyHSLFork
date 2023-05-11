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

  // TODO - Is there a de-facto standard for this, what should we use?
  //        "X-Correlation-ID", "Correlation-ID", "X-Request-ID" ?
  private static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    RequestCorrelationID.initRequest(getHttpRequestCorrelationID(requestContext));
  }

  @Override
  public void filter(
    ContainerRequestContext requestContext,
    ContainerResponseContext responseContext
  ) throws IOException {
    responseContext.getHeaders().add(HEADER_CORRELATION_ID, RequestCorrelationID.get());
  }

  private String getHttpRequestCorrelationID(ContainerRequestContext requestContext) {
    return requestContext.getHeaderString(HEADER_CORRELATION_ID);
  }
}
