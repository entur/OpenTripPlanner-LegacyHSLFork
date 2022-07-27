package org.opentripplanner;

import static org.opentripplanner.standalone.configure.OTPAppConstruction.creatTransitLayerForRaptor;

import io.micrometer.core.instrument.Metrics;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.standalone.api.OtpServerContext;
import org.opentripplanner.standalone.config.RouterConfig;
import org.opentripplanner.standalone.server.DefaultServerContext;
import org.opentripplanner.transit.raptor.configure.RaptorConfig;
import org.opentripplanner.transit.service.TransitModel;
import org.opentripplanner.transit.service.TransitModelIndex;

public class TestServerContext {

  private TestServerContext() {}

  /** Create a context for unit testing, using the default RoutingRequest. */
  public static OtpServerContext createServerContext(Graph graph, TransitModel transitModel) {
    transitModel.setTransitModelIndex(new TransitModelIndex(transitModel));
    DefaultServerContext context = DefaultServerContext.create(
      RouterConfig.DEFAULT,
      new RaptorConfig<>(RouterConfig.DEFAULT.raptorTuningParameters()),
      graph,
      transitModel,
      Metrics.globalRegistry,
      false
    );
    creatTransitLayerForRaptor(context.transitModel(), context.routerConfig());
    return context;
  }
}
