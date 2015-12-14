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

  // problem facts
  long startTime;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "parcelRange")
  List<ParcelVisit> parcelList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "vehicleRange")
  List<Vehicle> vehicleList;

  HardSoftLongScore score;

  PDPSolution() {}

  PDPSolution(long st) {
    startTime = st;
  }

  @Override
  public HardSoftLongScore getScore() {
    return score;
  }

  @Override
  public void setScore(HardSoftLongScore s) {
    score = s;
  }

  @Override
  public Collection<? extends Object> getProblemFacts() {
    // TODO Auto-generated method stub
    return null;
  }

}
