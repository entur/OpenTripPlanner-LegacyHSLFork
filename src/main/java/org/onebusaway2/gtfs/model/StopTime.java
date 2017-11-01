/*
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway2.gtfs.model;

import org.onebusaway2.gtfs.model.calendar.TimeToStringConverter;

import java.util.Objects;

public final class StopTime extends IdentityBean<AgencyAndId> implements Comparable<StopTime> {

    private static final long serialVersionUID = 1L;

    public static final int MISSING_VALUE = -999;

    private AgencyAndId id;

    private Trip trip;

    private Stop stop;

    private int arrivalTime = MISSING_VALUE;

    private int departureTime = MISSING_VALUE;

    private int timepoint = MISSING_VALUE;

    private int stopSequence;

    private String stopHeadsign;

    private String routeShortName;

    private int pickupType;

    private int dropOffType;

    private double shapeDistTraveled = MISSING_VALUE;

    /** This is a Conveyal extension to the GTFS spec to support Seattle on/off peak fares. */
    private String farePeriodId;

    public StopTime() {

    }

    public StopTime(StopTime st) {
        this.arrivalTime = st.arrivalTime;
        this.departureTime = st.departureTime;
        this.dropOffType = st.dropOffType;
        this.id = st.id;
        this.pickupType = st.pickupType;
        this.routeShortName = st.routeShortName;
        this.shapeDistTraveled = st.shapeDistTraveled;
        this.stop = st.stop;
        this.stopHeadsign = st.stopHeadsign;
        this.stopSequence = st.stopSequence;
        this.timepoint = st.timepoint;
        this.trip = st.trip;
    }

    public AgencyAndId getId() {
        return id;
    }

    public void setId(AgencyAndId id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(int stopSequence) {
        this.stopSequence = stopSequence;
    }

    public Stop getStop() {
        return stop;
    }

    public void setStop(Stop stop) {
        this.stop = stop;
    }

    public boolean isArrivalTimeSet() {
        return arrivalTime != MISSING_VALUE;
    }

    /**
     * @return arrival time, in seconds since midnight
     */
    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public void clearArrivalTime() {
        this.arrivalTime = MISSING_VALUE;
    }

    public boolean isDepartureTimeSet() {
        return departureTime != MISSING_VALUE;
    }

    /**
     * @return departure time, in seconds since midnight
     */
    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public void clearDepartureTime() {
        this.departureTime = MISSING_VALUE;
    }

    public boolean isTimepointSet() {
        return timepoint != MISSING_VALUE;
    }

    /**
     * @return 1 if the stop-time is a timepoint location
     */
    public int getTimepoint() {
        return timepoint;
    }

    public void setTimepoint(int timepoint) {
        this.timepoint = timepoint;
    }

    public void clearTimepoint() {
        this.timepoint = MISSING_VALUE;
    }

    public String getStopHeadsign() {
        return stopHeadsign;
    }

    public void setStopHeadsign(String headSign) {
        this.stopHeadsign = headSign;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public void setRouteShortName(String routeShortName) {
        this.routeShortName = routeShortName;
    }

    public int getPickupType() {
        return pickupType;
    }

    public void setPickupType(int pickupType) {
        this.pickupType = pickupType;
    }

    public int getDropOffType() {
        return dropOffType;
    }

    public void setDropOffType(int dropOffType) {
        this.dropOffType = dropOffType;
    }

    public boolean isShapeDistTraveledSet() {
        return shapeDistTraveled != MISSING_VALUE;
    }

    public double getShapeDistTraveled() {
        return shapeDistTraveled;
    }

    public void setShapeDistTraveled(double shapeDistTraveled) {
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public void clearShapeDistTraveled() {
        this.shapeDistTraveled = MISSING_VALUE;
    }

    public String getFarePeriodId() {
        return farePeriodId;
    }

    public void setFarePeriodId(String farePeriodId) {
        this.farePeriodId = farePeriodId;
    }

    public int compareTo(StopTime o) {
        return this.getStopSequence() - o.getStopSequence();
    }

    @Override
    public String toString() {
        return "StopTime(seq=" + getStopSequence() + " stop=" + getStop().getId() + " trip="
                + getTrip().getId() + " times=" + TimeToStringConverter.toHH_MM_SS(getArrivalTime())
                + "-" + TimeToStringConverter.toHH_MM_SS(getDepartureTime()) + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StopTime other = (StopTime) obj;
        if (!trip.getId().toString().equals(other.getTrip().getId().toString()))
            return false;
        if (!stop.getId().toString().equals(other.getStop().getId().toString()))
            return false;
        if (pickupType != other.pickupType)
            return false;
        if (dropOffType != other.dropOffType)
            return false;
        if (arrivalTime != other.arrivalTime)
            return false;
        if (departureTime != other.departureTime)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trip.getId().toString(), stop.getId().toString(), pickupType, dropOffType, arrivalTime, departureTime);
    }
}
