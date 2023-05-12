package org.opentripplanner.framework.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;
import org.opentripplanner.framework.application.RequestCorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This thread pool factory should be used to create all threads handling user requests in OTP. It
 * allows OTP to decorate threads and tasks. It should not be used for updater threads.
 * <p>
 * This factory inject the RequestCorrelationID into the child threads.
 */
public class OtpRequestThreadFactory implements ThreadFactory {

  private static final Logger LOG = LoggerFactory.getLogger(OtpRequestThreadFactory.class);

  private final ThreadFactory delegate;

  private OtpRequestThreadFactory(ThreadFactory delegate) {
    this.delegate = delegate;
  }

  public static ThreadFactory of(String nameFormat) {
    var defaultFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();

    if (RequestCorrelationId.isEnabled()) {
      LOG.info("Creating thread-factory w/log correlationId with format: " + nameFormat);
      return new OtpRequestThreadFactory(defaultFactory);
    } else {
      LOG.info("Creating default thread-factory with format: " + nameFormat);
      return defaultFactory;
    }
  }

  @Override
  public Thread newThread(@Nonnull Runnable r) {
    return delegate.newThread(new CorrelationIdRunnableDecorator(r));
  }
}
