package org.opentripplanner.ext.transmodelapi.support;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;

/**
 * Return the GraphQl execution result and a flag to indicate the
 * execution failed due to a timeout.
 */
public record OtpExecutionResult(ExecutionResult result, boolean timeout) {
  public static OtpExecutionResult of(ExecutionResult result) {
    boolean isTimeout =
      result.getErrors() != null &&
      result.getErrors().stream().anyMatch(OTPProcessingTimeoutGraphQLException.class::isInstance);
    return new OtpExecutionResult(result, isTimeout);
  }

  public static OtpExecutionResult ofTimeout() {
    return new OtpExecutionResult(
      ExecutionResult
        .newExecutionResult()
        .addError(GraphQLError.newError().message(OTPRequestTimeoutException.MESSAGE).build())
        .build(),
      true
    );
  }
}
