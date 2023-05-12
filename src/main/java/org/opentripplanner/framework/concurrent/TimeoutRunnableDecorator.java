package org.opentripplanner.framework.concurrent;

import org.opentripplanner.framework.application.OTPRequestTimeoutException;

/**
 * This decorator inject otp specific code around all Runnable tasks.Currently, it handles timeout
 * exceptions and make sure it is logged for the first task witch time out, and then for every
 * 10_000 timeout in ONE request.
 * <p>
 * To do this the decorator copy the timeout handler form the parent thread(where this is created)
 * and set it in the thread local of the child thread - propagating the handler to all child
 * threads.
 */
class TimeoutRunnableDecorator implements Runnable {

  private final Runnable delegate;
  private final OtpTimeoutExceptionHandler timeoutExceptionHandler;

  TimeoutRunnableDecorator(Runnable delegate) {
    this.timeoutExceptionHandler = OtpTimeoutExceptionHandler.getLocalThreadHandler();
    this.delegate = delegate;
  }

  @Override
  public void run() {
    try {
      // Allow handler to propagate to this thread´s children(if any)
      OtpTimeoutExceptionHandler.setOnLocalThread(timeoutExceptionHandler);
      delegate.run();
    } catch (OTPRequestTimeoutException e) {
      timeoutExceptionHandler.process(e);
    }
  }
}
