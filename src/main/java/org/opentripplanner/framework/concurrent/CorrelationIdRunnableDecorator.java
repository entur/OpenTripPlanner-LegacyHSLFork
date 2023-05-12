package org.opentripplanner.framework.concurrent;

import org.opentripplanner.framework.application.RequestCorrelationId;

class CorrelationIdRunnableDecorator implements Runnable {

  private final Runnable delegate;
  private final String parentCorrelationID;

  public CorrelationIdRunnableDecorator(Runnable delegate) {
    this.parentCorrelationID = RequestCorrelationId.get();
    this.delegate = delegate;
  }

  @Override
  public void run() {
    try {
      RequestCorrelationId.setInChildThread(parentCorrelationID);
      delegate.run();
    } finally {
      RequestCorrelationId.clear();
    }
  }
}
