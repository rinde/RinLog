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

import org.optaplanner.core.api.domain.entity.PlanningEntity;

import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.math.DoubleMath;

/**
 *
 * @author Rinde van Lon
 */
@PlanningEntity
public class Vehicle implements Visit {

  // planning variables
  final Visit previousVisit = null;

  // shadow variables
  ParcelVisit nextVisit;

  // problem facts
  final VehicleStateObject vehicle;

  Vehicle() {
    vehicle = null;
  }

  Vehicle(VehicleStateObject vso) {
    vehicle = vso;
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
  @Override
  public ParcelVisit getNextVisit() {
    return nextVisit;
  }

  @Override
  public void setNextVisit(ParcelVisit v) {
    nextVisit = v;
  }

  @Override
  public Point getPosition() {
    return vehicle.getLocation();
  }

  public Point getDepotLocation() {
    return vehicle.getDto().getStartPosition();
  }

  public long computeDepotTardiness(long timeOfArrival) {
    return Math.max(0L,
      timeOfArrival - vehicle.getDto().getAvailabilityTimeWindow().end());
  }

  public long computeTravelTime(Point from, Point to) {
    final double speedKMH = vehicle.getDto().getSpeed();

    final double distKM = Point.distance(from, to);

    final double travelTimeH = distKM / speedKMH;

    return DoubleMath.roundToLong(travelTimeH * 3600000d,
      RoundingMode.HALF_DOWN);
  }
}
