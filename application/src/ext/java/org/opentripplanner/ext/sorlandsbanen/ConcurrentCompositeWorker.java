package org.opentripplanner.ext.sorlandsbanen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorRouter;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorRouterResult;

class ConcurrentCompositeWorker<T extends RaptorTripSchedule> implements RaptorRouter<T> {

  private final RaptorRouter<T> mainWorker;
  private final RaptorRouter<T> alternativeWorker;

  ConcurrentCompositeWorker(RaptorRouter<T> mainWorker, RaptorRouter<T> alternativeWorker) {
    this.mainWorker = mainWorker;
    this.alternativeWorker = alternativeWorker;
  }

  @Override
  public RaptorRouterResult<T> route() {
    if (OTPFeature.ParallelRouting.isOn()) {
      var mainResultFuture = CompletableFuture.supplyAsync(mainWorker::route);
      var alternativeResultFuture = CompletableFuture.supplyAsync(alternativeWorker::route);

      try {
        return new RaptorWorkerResultComposite<>(
          mainResultFuture.get(),
          alternativeResultFuture.get()
        );
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    } else {
      var mainResult = mainWorker.route();
      var alternativeResult = alternativeWorker.route();
      return new RaptorWorkerResultComposite<>(mainResult, alternativeResult);
    }
  }
}
