/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import java.math.RoundingMode;

import javax.annotation.Nullable;

import org.optaplanner.core.api.domain.entity.PlanningEntity;

import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.DoubleMath;

/**
 *
 * @author Rinde van Lon
 */
@PlanningEntity
public class Vehicle implements Visit {

  // planning variables
  @Nullable
  final Visit previousVisit = null;

  // shadow variables
  @Nullable
  ParcelVisit nextVisit;

  // problem facts
  private final VehicleStateObject vehicle;
  private final long endTime;
  private final long remainingServiceTime;

  Vehicle() {
    vehicle = null;
    endTime = -1;
    remainingServiceTime = -1;
  }

  Vehicle(VehicleStateObject vso) {
    vehicle = vso;
    endTime = Util.msToNs(vso.getDto().getAvailabilityTimeWindow()).end();
    remainingServiceTime = vso.getRemainingServiceTime() > 0
        ? Util.msToNs(vso.getRemainingServiceTime()) : 0;
  }

  // @PlanningVariable(valueRangeProviderRefs = {"parcelRange", "vehicleRange"
  // }, graphType = PlanningVariableGraphType.CHAINED)
  // @Override
  // public Visit getPreviousVisit() {
  // return previousVisit;
  // }
  //
  // @Override
  // public void setPreviousVisit(Visit v) {
  // previousVisit = v;
  // }

  // @InverseRelationShadowVariable(sourceVariableName = "previousVisit")
  @Nullable
  @Override
  public ParcelVisit getNextVisit() {
    return nextVisit;
  }

  @Override
  public void setNextVisit(@Nullable final ParcelVisit v) {
    nextVisit = v;
  }

  @Override
  public Vehicle getVehicle() {
    return this;
  }

  @Nullable
  @Override
  public ParcelVisit getLastVisit() {
    if (nextVisit == null) {
      return null;
    }
    return nextVisit.getLastVisit();
  }

  @Override
  public void setVehicle(Vehicle v) {}

  @Override
  public Point getPosition() {
    return vehicle.getLocation();
  }

  public Optional<Parcel> getDestination() {
    return vehicle.getDestination();
  }

  public ImmutableSet<Parcel> getContents() {
    return vehicle.getContents();
  }

  public Point getDepotLocation() {
    return vehicle.getDto().getStartPosition();
  }

  public long getRemainingServiceTime() {
    return remainingServiceTime;
  }

  public long computeDepotTardiness(long timeOfArrival) {
    return Math.max(0L, timeOfArrival - endTime);
  }

  public long computeTravelTime(Point from, Point to) {
    final double speedKMH = vehicle.getDto().getSpeed();

    final double distKM = Point.distance(from, to);

    final double travelTimeH = distKM / speedKMH;
    // convert to nanoseconds
    return DoubleMath.roundToLong(travelTimeH * 3600000000000d,
      RoundingMode.HALF_DOWN);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + Integer.toHexString(hashCode());
  }
}
