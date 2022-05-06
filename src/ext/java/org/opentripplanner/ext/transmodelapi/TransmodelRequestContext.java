package org.opentripplanner.ext.transmodelapi;

import org.opentripplanner.routing.RoutingService;
import org.opentripplanner.standalone.server.Router;

public class TransmodelRequestContext {

  private final Router router;
  private final RoutingService routingService;
  private final String etClientName;

  public TransmodelRequestContext(
    Router router,
    RoutingService routingService,
    String etClientName
  ) {
    this.router = router;
    this.routingService = routingService;
    this.etClientName = etClientName;
  }

  public Router getRouter() {
    return router;
  }

  public RoutingService getRoutingService() {
    return routingService;
  }

  public String getEtClientName() {
    return etClientName;
  }
}
