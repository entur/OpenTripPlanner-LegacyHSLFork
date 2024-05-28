package org.opentripplanner.ext.flex.template;

import static org.opentripplanner.model.StopTime.MISSING_VALUE;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opentripplanner.ext.flex.FlexPathDurations;
import org.opentripplanner.ext.flex.edgetype.FlexTripEdge;
import org.opentripplanner.ext.flex.flexpathcalculator.FlexPathCalculator;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.framework.time.TimeUtils;
import org.opentripplanner.model.PathTransfer;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.state.EdgeTraverser;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;

class FlexAccessTemplate extends AbstractFlexTemplate {

  FlexAccessTemplate(
    FlexTrip<?, ?> trip,
    NearbyStop boardStop,
    int boardStopPosition,
    StopLocation alightStop,
    int alightStopPosition,
    FlexServiceDate date,
    FlexPathCalculator calculator,
    Duration maxTransferDuration
  ) {
    super(
      trip,
      boardStop,
      alightStop,
      boardStopPosition,
      alightStopPosition,
      date,
      calculator,
      maxTransferDuration
    );
  }

  Optional<DirectFlexPath> createDirectGraphPath(
    NearbyStop egress,
    boolean arriveBy,
    int requestedDepartureTime
  ) {
    List<Edge> egressEdges = egress.edges;

    Vertex flexToVertex = egress.state.getVertex();

    if (!isRouteable(flexToVertex)) {
      return Optional.empty();
    }

    var flexEdge = getFlexEdge(flexToVertex, egress.stop);

    if (flexEdge == null) {
      return Optional.empty();
    }

    final State[] afterFlexState = flexEdge.traverse(accessEgress.state);

    var finalStateOpt = EdgeTraverser.traverseEdges(afterFlexState[0], egressEdges);

    if (finalStateOpt.isEmpty()) {
      return Optional.empty();
    }

    var finalState = finalStateOpt.get();
    var flexDurations = calculateFlexPathDurations(flexEdge, finalState);

    int timeShift;

    if (arriveBy) {
      int lastStopArrivalTime = flexDurations.mapToFlexTripArrivalTime(requestedDepartureTime);
      int latestArrivalTime = trip.latestArrivalTime(
        lastStopArrivalTime,
        fromStopIndex,
        toStopIndex,
        flexDurations.trip()
      );

      if (latestArrivalTime == MISSING_VALUE) {
        return Optional.empty();
      }

      // No need to time-shift latestArrivalTime for meeting the min-booking notice restriction,
      // the time is already as-late-as-possible
      var bookingInfo = RoutingBookingInfo.of(trip.getPickupBookingInfo(fromStopIndex));
      if (!bookingInfo.isThereEnoughTimeToBookForArrival(latestArrivalTime, requestedBookingTime)) {
        return Optional.empty();
      }

      // Shift from departing at departureTime to arriving at departureTime
      timeShift = flexDurations.mapToRouterArrivalTime(latestArrivalTime) - flexDurations.total();
    } else {
      int firstStopDepartureTime = flexDurations.mapToFlexTripDepartureTime(requestedDepartureTime);

      // Time-shift departure so the minimum-booking-notice restriction is honored. This is not
      //  necessary in for access/egress since in practice Raptor will do this for us.
      // TODO get routing booking info
      var bookingInfo = trip.getPickupBookingInfo(fromStopIndex);
      if (bookingInfo != null) {
        var minNotice = bookingInfo.getMinimumBookingNotice();
        if (minNotice != null && requestedBookingTime != RoutingBookingInfo.NOT_SET) {
          int firstBookableDepartureTime = requestedBookingTime + (int) minNotice.toSeconds();
          if (firstBookableDepartureTime > firstStopDepartureTime) {
            firstStopDepartureTime = firstBookableDepartureTime;
          }
        }
      }

      int earliestDepartureTime = trip.earliestDepartureTime(
        firstStopDepartureTime,
        fromStopIndex,
        toStopIndex,
        flexDurations.trip()
      );

      if (earliestDepartureTime == MISSING_VALUE) {
        return Optional.empty();
      }

      var routingBookingInfo = RoutingBookingInfo.of(bookingInfo);
      if (
        !routingBookingInfo.isThereEnoughTimeToBookForDeparture(
          earliestDepartureTime,
          requestedBookingTime
        )
      ) {
        return Optional.empty();
      }

      timeShift = flexDurations.mapToRouterDepartureTime(earliestDepartureTime);

      System.out.println(
        "requestedDepartureTime .. : " + TimeUtils.timeToStrLong(requestedDepartureTime)
      );
      System.out.println(
        "requestedBookingTime .... : " + TimeUtils.timeToStrLong(requestedBookingTime)
      );
      System.out.println(
        "EDT ..................... : " + TimeUtils.timeToStrLong(earliestDepartureTime)
      );
      System.out.println("BookingInfo ............. : " + bookingInfo);
    }

    return Optional.of(new DirectFlexPath(timeShift, finalState));
  }

  protected List<Edge> getTransferEdges(PathTransfer transfer) {
    return transfer.getEdges();
  }

  protected RegularStop getFinalStop(PathTransfer transfer) {
    return transfer.to instanceof RegularStop ? (RegularStop) transfer.to : null;
  }

  protected Collection<PathTransfer> getTransfersFromTransferStop(
    FlexAccessEgressCallbackAdapter callback
  ) {
    return callback.getTransfersFromStop(transferStop);
  }

  protected Vertex getFlexVertex(Edge edge) {
    return edge.getFromVertex();
  }

  protected FlexPathDurations calculateFlexPathDurations(FlexTripEdge flexEdge, State state) {
    int preFlexTime = (int) accessEgress.state.getElapsedTimeSeconds();
    int edgeTimeInSeconds = flexEdge.getTimeInSeconds();
    int postFlexTime = (int) state.getElapsedTimeSeconds() - preFlexTime - edgeTimeInSeconds;
    return new FlexPathDurations(
      preFlexTime,
      edgeTimeInSeconds,
      postFlexTime,
      secondsFromStartOfTime
    );
  }

  protected FlexTripEdge getFlexEdge(Vertex flexToVertex, StopLocation transferStop) {
    var flexPath = calculator.calculateFlexPath(
      accessEgress.state.getVertex(),
      flexToVertex,
      fromStopIndex,
      toStopIndex
    );

    if (flexPath == null) {
      return null;
    }

    return new FlexTripEdge(
      accessEgress.state.getVertex(),
      flexToVertex,
      accessEgress.stop,
      transferStop,
      trip,
      fromStopIndex,
      toStopIndex,
      serviceDate,
      flexPath
    );
  }

  protected boolean isRouteable(Vertex flexVertex) {
    if (accessEgress.state.getVertex() == flexVertex) {
      return false;
    } else return (
      calculator.calculateFlexPath(
        accessEgress.state.getVertex(),
        flexVertex,
        fromStopIndex,
        toStopIndex
      ) !=
      null
    );
  }
}
