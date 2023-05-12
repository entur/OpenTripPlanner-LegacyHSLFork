package org.opentripplanner.framework.concurrent;

import org.opentripplanner.framework.application.RequestCorrelationId;

class CorrelationIdAndTimeoutRunnableDecorator extends TimeoutRunnableDecorator {

  private final String parentCorrelationID;

  CorrelationIdAndTimeoutRunnableDecorator(Runnable delegate) {
    super(delegate);
    this.parentCorrelationID = RequestCorrelationId.get();
  }

  @Override
  public void run() {
    try {
      RequestCorrelationId.setOnLocalThread(parentCorrelationID);
      super.run();
    } finally {
      RequestCorrelationId.clear();
    }
  }
}
