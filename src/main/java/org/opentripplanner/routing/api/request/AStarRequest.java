package org.opentripplanner.routing.api.request;

import java.time.Instant;
import java.util.Set;
import org.opentripplanner.model.GenericLocation;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.api.request.request.VehicleParkingRequest;
import org.opentripplanner.routing.api.request.request.VehicleRentalRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.vehicle_rental.RentalVehicleType;

public class AStarRequest {

  private final GenericLocation from;
  private final GenericLocation to;

  private final Instant dateTime;

  private final boolean arriveBy;

  /**
   * The set of TraverseModes allowed when doing street routing.
   */
  private final TraverseModeSet streetSubRequestModes;
  private final StreetRequest streetRequest;

  private final VehicleRentalRequest vehicleRentalRequest;
  private final VehicleParkingRequest vehicleParkingRequest;
  private final RoutingPreferences preferences;

  /*
      Additional flags affecting mode transitions.
      This is a temporary solution, as it only covers parking and rental at the beginning of the trip.
    */
  private final boolean vehicleRental;
  private final boolean parkAndRide;
  private final boolean carPickup;
  private final Set<RentalVehicleType.FormFactor> allowedRentalFormFactors;

  public AStarRequest(
    GenericLocation from,
    GenericLocation to,
    Instant dateTime,
    boolean arriveBy,
    StreetRequest streetRequest,
    VehicleRentalRequest vehicleRentalRequest,
    VehicleParkingRequest vehicleParkingRequest,
    RoutingPreferences preferences
  ) {
    this.from = from;
    this.to = to;
    this.dateTime = dateTime;
    this.arriveBy = arriveBy;
    this.streetRequest = streetRequest;

    StreetMode mode = this.streetRequest.mode();
    this.streetSubRequestModes =
      switch (mode) {
        case NOT_SET -> throw new IllegalArgumentException("Street mode is not set");
        case WALK, FLEXIBLE -> new TraverseModeSet(TraverseMode.WALK);
        case BIKE -> new TraverseModeSet(TraverseMode.BICYCLE);
        case BIKE_TO_PARK, BIKE_RENTAL, SCOOTER_RENTAL -> new TraverseModeSet(
          TraverseMode.BICYCLE,
          TraverseMode.WALK
        );
        case CAR -> new TraverseModeSet(TraverseMode.CAR);
        case CAR_TO_PARK, CAR_PICKUP, CAR_RENTAL -> new TraverseModeSet(
          TraverseMode.CAR,
          TraverseMode.WALK
        );
      };
    this.vehicleRentalRequest = vehicleRentalRequest;
    this.vehicleParkingRequest = vehicleParkingRequest;
    this.preferences = preferences;

    parkAndRide =
      switch (mode) {
        case BIKE_TO_PARK, CAR_TO_PARK -> true;
        default -> false;
      };

    vehicleRental =
      switch (mode) {
        case BIKE_RENTAL, SCOOTER_RENTAL, CAR_RENTAL -> true;
        default -> false;
      };

    this.allowedRentalFormFactors =
      switch (mode) {
        case BIKE_RENTAL -> Set.of(RentalVehicleType.FormFactor.BICYCLE);
        case SCOOTER_RENTAL -> Set.of(RentalVehicleType.FormFactor.SCOOTER);
        case CAR_RENTAL -> Set.of(RentalVehicleType.FormFactor.CAR);
        default -> Set.of();
      };

    carPickup = mode == StreetMode.CAR_PICKUP;
  }

  public boolean arriveBy() {
    return arriveBy;
  }

  public RoutingPreferences preferences() {
    return preferences;
  }

  public TraverseModeSet streetSubRequestModes() {
    return streetSubRequestModes;
  }

  public boolean vehicleRental() {
    return vehicleRental;
  }

  public boolean parkAndRide() {
    return parkAndRide;
  }

  public boolean carPickup() {
    return carPickup;
  }

  public Set<RentalVehicleType.FormFactor> allowedRentalFormFactors() {
    return allowedRentalFormFactors;
  }

  public VehicleRentalRequest rental() {
    return vehicleRentalRequest;
  }

  public VehicleParkingRequest parking() {
    return vehicleParkingRequest;
  }

  public Instant dateTime() {
    return dateTime;
  }

  public StreetMode mode() {
    return streetRequest.mode();
  }

  public GenericLocation from() {
    return from;
  }

  public GenericLocation to() {
    return to;
  }

  public AStarRequest copyOfReversed() {
    return new AStarRequest(
      from,
      to,
      dateTime,
      !arriveBy,
      streetRequest,
      vehicleRentalRequest.clone(),
      vehicleParkingRequest.clone(),
      preferences.clone()
    );
  }
}
