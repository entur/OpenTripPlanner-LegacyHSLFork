package org.opentripplanner.ext.transmodelapi.support;

import graphql.ExceptionWhileDataFetching;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;
import org.opentripplanner.framework.concurrent.OtpTimeoutExceptionHandler;

public class OtpSimpleDataFetcherExceptionHandler extends SimpleDataFetcherExceptionHandler {

  public OtpSimpleDataFetcherExceptionHandler() {
    OtpTimeoutExceptionHandler.getLocalThreadHandler().reset();
  }

  @Override
  protected void logException(ExceptionWhileDataFetching error, Throwable exception) {
    if (exception instanceof OTPRequestTimeoutException e) {
      OtpTimeoutExceptionHandler.getLocalThreadHandler().process(e);
    } else {
      super.logException(error, exception);
    }
  }
}
