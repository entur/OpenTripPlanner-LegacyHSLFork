package org.opentripplanner.raptor.rangeraptor.internalapi;

import org.opentripplanner.raptor.api.model.RaptorTripSchedule;

public interface RaptorRouter<T extends RaptorTripSchedule> {
  RaptorWorkerResult<T> route();
}
