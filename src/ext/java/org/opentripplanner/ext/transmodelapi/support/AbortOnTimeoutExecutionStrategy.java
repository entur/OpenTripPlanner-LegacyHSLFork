package org.opentripplanner.ext.transmodelapi.support;

import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.schema.DataFetchingEnvironment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To abort fetching data when a timeout occurs we have to rethrow the time-out-exception.
 * This will prevent unresolved data-fetchers to be called. The exception is not handled
 * gracefully.
 */
public class AbortOnTimeoutExecutionStrategy extends AsyncExecutionStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(AbortOnTimeoutExecutionStrategy.class);

  private final AtomicInteger counter = new AtomicInteger(0);

  @Override
  protected <T> CompletableFuture<T> handleFetchingException(
    ExecutionContext executionContext,
    DataFetchingEnvironment environment,
    Throwable e
  ) {
    if (e instanceof OTPRequestTimeoutException te) {
      if (counter.incrementAndGet() % 10_000 == 0) {
        LOG.info(
          "{} - Number of timeouts for this request: {}",
          OTPRequestTimeoutException.MESSAGE,
          counter.get()
        );
      }
      throw te;
    }
    return super.handleFetchingException(executionContext, environment, e);
  }
}
