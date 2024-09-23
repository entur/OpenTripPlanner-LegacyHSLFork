package org.opentripplanner.ext.sorlandsbanen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.raptor.api.model.RaptorTripSchedule;
import org.opentripplanner.raptor.rangeraptor.RangeRaptor;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorRouter;
import org.opentripplanner.raptor.rangeraptor.internalapi.RaptorWorkerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurrentRangeRaptor<T extends RaptorTripSchedule> implements RaptorRouter<T> {

  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentRangeRaptor.class);

  private final RangeRaptor<T> mainWorker;
  private final RangeRaptor<T> alternativeWorker;

  ConcurrentRangeRaptor(RangeRaptor<T> mainWorker, RangeRaptor<T> alternativeWorker) {
    this.mainWorker = mainWorker;
    this.alternativeWorker = alternativeWorker;
  }

  public RaptorWorkerResult<T> route() {
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
