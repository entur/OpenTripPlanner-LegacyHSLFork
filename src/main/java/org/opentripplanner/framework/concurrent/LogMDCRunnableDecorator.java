package org.opentripplanner.framework.concurrent;

import java.util.Map;
import org.opentripplanner.framework.application.LogMDCSupport;

class LogMDCRunnableDecorator implements Runnable {

  private final Runnable delegate;
  private final Map<String, String> parentLogContext;

  LogMDCRunnableDecorator(Runnable delegate) {
    this.delegate = delegate;
    this.parentLogContext = LogMDCSupport.getContext();
  }

  @Override
  public void run() {
    try {
      LogMDCSupport.setLocal(parentLogContext);
      delegate.run();
    } finally {
      LogMDCSupport.clearLocal();
    }
  }
}
