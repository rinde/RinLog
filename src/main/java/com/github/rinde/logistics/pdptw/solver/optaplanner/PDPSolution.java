/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

/**
 *
 * @author Rinde van Lon
 */
@PlanningSolution
public class PDPSolution implements Solution<HardSoftLongScore> {
  static final String PARCEL_RANGE = "parcelRange";
  static final String VEHICLE_RANGE = "vehicleRange";

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = PARCEL_RANGE)
  List<ParcelVisit> parcelList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = VEHICLE_RANGE)
  List<Vehicle> vehicleList;

  HardSoftLongScore score;

  private long startTime;

  PDPSolution() {}

  PDPSolution(long st) {
    startTime = Util.msToNs(st);
  }

  @Override
  public HardSoftLongScore getScore() {
    return score;
  }

  @Override
  public void setScore(HardSoftLongScore s) {
    score = s;
  }

  public long getStartTime() {
    return startTime;
  }

  @Override
  public Collection<? extends Object> getProblemFacts() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (final Vehicle v : vehicleList) {
      sb.append(v);
      ParcelVisit next = v.getNextVisit();
      while (next != null) {
        sb.append("->").append(next);
        next = next.getNextVisit();
      }
      if (vehicleList.get(vehicleList.size() - 1) != v) {
        sb.append(System.lineSeparator());
      }
    }
    return sb.toString();
  }

  public static boolean equal(PDPSolution lsol, PDPSolution rsol) {
    checkNotNull(lsol);
    checkNotNull(rsol);

    if (lsol.parcelList.size() != rsol.parcelList.size()) {
      return false;
    }
    if (lsol.vehicleList.size() != rsol.vehicleList.size()) {
      return false;
    }
    if (lsol.startTime != rsol.startTime) {
      return false;
    }
    for (int i = 0; i < lsol.parcelList.size(); i++) {
      if (!ParcelVisit.equalProblemFacts(lsol.parcelList.get(i),
        rsol.parcelList.get(i))) {
        return false;
      }
    }
    for (int i = 0; i < lsol.vehicleList.size(); i++) {
      if (!Vehicle.scheduleEqual(lsol.vehicleList.get(i),
        rsol.vehicleList.get(i))) {
        return false;
      }
    }
    return true;
  }
}
