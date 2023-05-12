package org.opentripplanner.framework.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import org.opentripplanner.framework.application.OTPRequestTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will log {@link OTPRequestTimeoutException}. It will throttle the logging
 * to avoid spamming the logs. Only 1 of 10_000 sub-tasks in a request is logged. The
 * first timeout is always logged for a given http request.
 */
public class OtpTimeoutExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(OtpTimeoutExceptionHandler.class);
  private static final ThreadLocal<OtpTimeoutExceptionHandler> THREAD_LOCAL_EXCEPTION_HANDLER = ThreadLocal.withInitial(
    OtpTimeoutExceptionHandler::new
  );

  /** Log the first event, then every LOG_INTERVAL. */
  private static final int LOG_INTERVAL = 10_000;

  private final AtomicInteger errorCounter = new AtomicInteger(0);

  private OtpTimeoutExceptionHandler() {}

  /**
   * This method is used to propagate a request handler from a parent thread to a child.
   * The recommended way to do this is:
   * <pre>
   * // In constructor of Thread/Runnable
   * this.timeoutExceptionHandler = OtpTimeoutExceptionHandler.getLocalThreadHandler();
   *
   * public void run() {
   *   OtpTimeoutExceptionHandler.setOnLocalThread(timeoutExceptionHandler);
   *   :
   * }
   * </pre>
   * This assumes the runnable is created in the parent thread - witch is the normal case.
   */
  public static void setOnLocalThread(OtpTimeoutExceptionHandler it) {
    THREAD_LOCAL_EXCEPTION_HANDLER.set(it);
  }

  /**
   * Get the local thread exception handler. If it exists the existing one is returned, if not a
   * new one is created.
   */
  public static OtpTimeoutExceptionHandler getLocalThreadHandler() {
    return THREAD_LOCAL_EXCEPTION_HANDLER.get();
  }

  /**
   * Call reset if the thread is reused in multiple requests. This will only reset the exception
   * counter. If the counter get out of sync the only riskis that messages are not logged. If the
   * reset is called twice, two log messages will appear instead of one.
   */
  public void reset() {
    errorCounter.set(0);
  }

  /**
   * Process a timeout exception. This will throttle the logging to avoid spamming the logs
   * and decreasing the performance. A GraphQL query may have 50.000 sub-queries - witch
   * all is likely to timeout.
   */
  public void process(OTPRequestTimeoutException e) {
    int counter = errorCounter.getAndIncrement();
    if (counter % LOG_INTERVAL == 0) {
      LOG.warn("Task #{} aborted - {}", counter, e.getMessage());
    }
  }
}
