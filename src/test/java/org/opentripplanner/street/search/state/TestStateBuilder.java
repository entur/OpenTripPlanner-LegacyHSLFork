package org.opentripplanner.street.search.state;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import org.opentripplanner.framework.i18n.NonLocalizedString;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.service.vehiclerental.model.RentalVehicleType;
import org.opentripplanner.service.vehiclerental.model.VehicleRentalStation;
import org.opentripplanner.service.vehiclerental.street.StreetVehicleRentalLink;
import org.opentripplanner.service.vehiclerental.street.VehicleRentalEdge;
import org.opentripplanner.service.vehiclerental.street.VehicleRentalPlaceVertex;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.street.model._data.StreetModelForTest;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.transit.model.framework.FeedScopedId;

/**
 * Builds up a state chain for use in tests.
 */
public class TestStateBuilder {

  private static final Instant DEFAULT_START_TIME = OffsetDateTime
    .parse("2023-04-18T12:00:00+02:00")
    .toInstant();
  private int count = 1;

  private State currentState;

  private TestStateBuilder(StreetMode mode) {
    currentState =
      new State(
        StreetModelForTest.intersectionVertex(count, count),
        StreetSearchRequest.of().withMode(mode).withStartTime(DEFAULT_START_TIME).build()
      );
  }

  /**
   * Create an initial state that starts walking.
   */
  public static TestStateBuilder ofWalking() {
    return new TestStateBuilder(StreetMode.WALK);
  }

  /**
   * Create an initial state that start in a car.
   */
  public static TestStateBuilder ofDriving() {
    return new TestStateBuilder(StreetMode.CAR);
  }

  public static TestStateBuilder ofCarRental() {
    return new TestStateBuilder(StreetMode.CAR_RENTAL);
  }

  /**
   * Traverse a very plain street edge with no special characteristics.
   */
  public TestStateBuilder streetEdge() {
    count++;
    var from = (StreetVertex) currentState.vertex;
    var to = StreetModelForTest.intersectionVertex(count, count);

    var edge = StreetModelForTest.streetEdge(from, to);
    var states = edge.traverse(currentState);
    if (states.length != 1) {
      throw new IllegalStateException("Only single state transitions are supported.");
    }
    currentState = states[0];
    return this;
  }

  /**
   * Traverse a street edge and switch to Car mode
   */
  public TestStateBuilder pickUpCar() {
    count++;

    var station = new VehicleRentalStation();
    var stationName = "FooStation";
    var networkName = "bar";
    var vehicleType = new RentalVehicleType(
      new FeedScopedId(networkName, "car"),
      "car",
      RentalFormFactor.CAR,
      RentalVehicleType.PropulsionType.ELECTRIC,
      100000d
    );
    station.id = new FeedScopedId(networkName, stationName);
    station.name = new NonLocalizedString(stationName);
    station.latitude = count;
    station.longitude = count;
    station.vehiclesAvailable = 10;
    station.spacesAvailable = 10;
    station.vehicleTypesAvailable = Map.of(vehicleType, 10);
    station.vehicleSpacesAvailable = Map.of(vehicleType, 10);
    station.isRenting = true;
    station.isReturning = true;
    station.realTimeData = true;

    VehicleRentalPlaceVertex vertex = new VehicleRentalPlaceVertex(null, station);
    var link = new StreetVehicleRentalLink((StreetVertex) currentState.vertex, vertex);

    currentState = link.traverse(currentState)[0];

    var edge = new VehicleRentalEdge(vertex, RentalFormFactor.CAR);

    State[] traverse = edge.traverse(currentState);
    currentState =
      Arrays
        .stream(traverse)
        .filter(it -> it.getNonTransitMode() == TraverseMode.CAR)
        .findFirst()
        .get();

    return this;
  }

  public State build() {
    return currentState;
  }
}
