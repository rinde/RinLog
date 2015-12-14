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

import static com.google.common.base.MoreObjects.toStringHelper;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableGraphType;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 *
 * @author Rinde van Lon
 */
@PlanningEntity
public class ParcelVisit implements Visit {

  // problem facts
  private Parcel parcel;
  private VisitType visitType;
  private Point position;
  private TimeWindow timeWindow;
  private long serviceDuration;
  private long latestStartTime;

  // planning variables
  Visit previousVisit;

  // shadow variables
  ParcelVisit nextVisit;

  ParcelVisit() {}

  ParcelVisit(Parcel p, VisitType t) {
    parcel = p;
    visitType = t;
    if (visitType == VisitType.DELIVER) {
      position = parcel.getDeliveryLocation();
      timeWindow = parcel.getDeliveryTimeWindow();
      serviceDuration = parcel.getDeliveryDuration();
    } else {
      position = parcel.getPickupLocation();
      timeWindow = parcel.getPickupTimeWindow();
      serviceDuration = parcel.getPickupDuration();
    }
    latestStartTime = timeWindow.end() - serviceDuration;
  }

  enum VisitType {
    PICKUP, DELIVER;
  }

  public Parcel getParcel() {
    return parcel;
  }

  public VisitType getVisitType() {
    return visitType;
  }

  @PlanningVariable(valueRangeProviderRefs = {"parcelRange", "vehicleRange"
  }, graphType = PlanningVariableGraphType.CHAINED)
  // @Override
  public Visit getPreviousVisit() {
    return previousVisit;
  }

  // @Override
  public void setPreviousVisit(Visit v) {
    previousVisit = v;
  }

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
    return position;
  }

  public long computeServiceStartTime(long timeOfArrival) {
    return Math.max(timeWindow.begin(), timeOfArrival);
  }

  // computes tardiness cost if servicing would commence at timeOfArrival
  public long computeTardiness(long timeOfArrival) {
    return Math.max(0L, timeOfArrival - latestStartTime);
  }

  public long getServiceDuration() {
    return serviceDuration;
  }

  @Override
  public String toString() {
    return toStringHelper(getClass().getSimpleName())
        .add("Parcel", parcel)
        .addValue(visitType)
        .toString();
  }

}
