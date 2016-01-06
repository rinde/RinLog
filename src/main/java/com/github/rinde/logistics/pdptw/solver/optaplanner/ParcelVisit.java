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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nullable;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.solution.cloner.DeepPlanningClone;
import org.optaplanner.core.api.domain.variable.AnchorShadowVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableGraphType;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitStrengthComparator;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

/**
 *
 * @author Rinde van Lon
 */
@PlanningEntity(difficultyComparatorClass = VisitStrengthComparator.class)
public class ParcelVisit implements Visit {
  // this variable should be the same value as the name of the field in this
  // class
  static final String PREV_VISIT = "previousVisit";

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
  @Nullable
  ParcelVisit nextVisit;
  Vehicle vehicle;

  // helper variable
  @Nullable
  ParcelVisit associated;

  // Difficulty should be implemented ascending: easy entities are lower,
  // difficult entities are higher.
  public static class VisitStrengthComparator implements Comparator<Visit> {

    public VisitStrengthComparator() {}

    @Override
    public int compare(Visit o1, Visit o2) {

      final boolean is1pv = o1 instanceof ParcelVisit;
      final boolean is2pv = o2 instanceof ParcelVisit;

      if (!is1pv && !is2pv) {
        return 0;
      } else if (!is1pv) {
        return -1;
      } else if (!is2pv) {
        return 1;
      }
      // is1pv && is2pv
      final ParcelDTO p1 = ((ParcelVisit) o1).getParcel().getDto();
      final ParcelDTO p2 = ((ParcelVisit) o2).getParcel().getDto();

      if (p1.equals(p2)) {
        return 0;
      }

      return ComparisonChain.start()
          // small time windows are more difficult, therefore: reverse ordering
          .compare(
            p1.getPickupTimeWindow().length()
                + p1.getDeliveryTimeWindow().length(),
            p2.getPickupTimeWindow().length()
                + p2.getDeliveryTimeWindow().length(),
            Ordering.natural().reverse())
          .result();
    }

  }

  ParcelVisit() {}

  ParcelVisit(Parcel p, VisitType t) {
    parcel = p;
    visitType = t;
    if (visitType == VisitType.DELIVER) {
      position = parcel.getDeliveryLocation();
      timeWindow = Util.msToNs(parcel.getDeliveryTimeWindow());
      serviceDuration = Util.msToNs(parcel.getDeliveryDuration());
    } else {
      position = parcel.getPickupLocation();
      timeWindow = Util.msToNs(parcel.getPickupTimeWindow());
      serviceDuration = Util.msToNs(parcel.getPickupDuration());
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

  @Nullable
  @PlanningVariable(strengthComparatorClass = VisitStrengthComparator.class,
                    valueRangeProviderRefs = {
                        PDPSolution.PARCEL_RANGE,
                        PDPSolution.VEHICLE_RANGE
                    },
                    graphType = PlanningVariableGraphType.CHAINED)
  public Visit getPreviousVisit() {
    return previousVisit;
  }

  public void setPreviousVisit(@Nullable Visit v) {
    previousVisit = v;
  }

  // @InverseRelationShadowVariable(sourceVariableName = "previousVisit")
  @Nullable
  @Override
  public ParcelVisit getNextVisit() {
    return nextVisit;
  }

  @Override
  public void setNextVisit(@Nullable ParcelVisit v) {
    nextVisit = v;
  }

  @AnchorShadowVariable(sourceVariableName = ParcelVisit.PREV_VISIT)
  @Nullable
  @Override
  public Vehicle getVehicle() {
    return vehicle;
  }

  @Override
  public void setVehicle(@Nullable Vehicle v) {
    vehicle = v;
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

    return visitType + "-" + parcel.toString();
    //
    // return toStringHelper(getClass().getSimpleName())
    // .add("Parcel", parcel)
    // .addValue(visitType)
    // .toString();
  }

  @Nullable
  @Override
  public ParcelVisit getLastVisit() {
    if (nextVisit == null) {
      return this;
    }
    return nextVisit.getLastVisit();
  }

  public boolean isBefore(ParcelVisit pv) {
    checkArgument(getVehicle() != null);
    checkArgument(Objects.equals(getVehicle(), pv.getVehicle()));
    ParcelVisit next = getNextVisit();
    while (next != null) {
      if (next.equals(pv)) {
        return true;
      }
      next = next.getNextVisit();
    }
    return false;
  }

  /**
   * @param delivery
   */
  public void setAssociation(ParcelVisit pv) {
    checkArgument(pv.getParcel().equals(getParcel()));
    checkArgument(pv.getVisitType() != getVisitType());
    associated = pv;
  }

  @DeepPlanningClone
  @Nullable
  public ParcelVisit getAssociation() {
    return associated;
  }

  static boolean equalProblemFacts(ParcelVisit lpv, ParcelVisit rpv) {
    checkNotNull(lpv);
    checkNotNull(rpv);
    return Objects.equals(lpv.getVisitType(), rpv.getVisitType())
        && Objects.equals(lpv.getParcel(), rpv.getParcel())
        && Objects.equals(lpv.getPosition(), rpv.getPosition())
        && Objects.equals(lpv.getServiceDuration(), rpv.getServiceDuration())
        && Objects.equals(lpv.timeWindow, rpv.timeWindow)
        && Objects.equals(lpv.latestStartTime, rpv.latestStartTime);
  }
}
