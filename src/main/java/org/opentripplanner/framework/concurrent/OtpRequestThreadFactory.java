package org.opentripplanner.framework.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;
import org.opentripplanner.framework.application.RequestCorrelationId;

/**
 * This thread pool factory should be used to create all threads handling user requests in OTP. It
 * allows OTP to decorate threads and tasks. It should not be used for updater threads.
 * <p>
 * This factory inject the RequestCorrelationID into the child threads.
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
    if (RequestCorrelationId.isEnabled()) {
      return delegate.newThread(new CorrelationIdAndTimeoutRunnableDecorator(r));
    } else {
      return delegate.newThread(new TimeoutRunnableDecorator(r));
    }
  }
}
