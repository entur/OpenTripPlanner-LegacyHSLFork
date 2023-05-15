package org.opentripplanner.framework.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;
import org.opentripplanner.framework.application.LogMDCSupport;

/**
 * This thread pool factory should be used to create all threads handling user requests in OTP. It
 * allows OTP to decorate threads and tasks. It should not be used for updater threads.
 */
public class OtpRequestThreadFactory implements ThreadFactory {

  private final ThreadFactory delegate;

  private OtpRequestThreadFactory(ThreadFactory delegate) {
    this.delegate = delegate;
  }

  public static ThreadFactory of(String nameFormat) {
    var defaultFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
    return new OtpRequestThreadFactory(defaultFactory);
  }

  @Override
  public Thread newThread(@Nonnull Runnable r) {
    if (LogMDCSupport.isRequestTracingInLoggingEnabled()) {
      return delegate.newThread(new LogMDCRunnableDecorator(r));
    }
    return delegate.newThread(r);
  }
}
