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

import org.junit.Test;
import org.optaplanner.core.api.score.Score;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;

/**
 *
 * @author Rinde van Lon
 */
public class ScoreCalculatorTest {

  @Test
  public void test() {

    final ScoreCalculator sc = new ScoreCalculator();

    final GlobalStateObject state = GlobalStateObjectBuilder.globalBuilder()
        .addAvailableParcel(
          Parcel.builder(new Point(0, 0), new Point(0, 1)).build())
        .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
            .setVehicleDTO(VehicleDTO.builder()
                .speed(1d)
                .build())
            .build())
        .build();

    final PDPSolution sol = OptaplannerSolver.convert(state);

    sc.resetWorkingSolution(sol);

    final Score score = sc.calculateScore();
    System.out.println(score);

    sc.beforeVariableChanged(sol.vehicleList.get(0).getNextVisit(),
      "previousVisit");

    // final Score score2 = sc.calculateScore();
    // System.out.println(score2);

    sc.afterVariableChanged(sol.vehicleList.get(0).getNextVisit(),
      "previousVisit");

    final Score score3 = sc.calculateScore();
    System.out.println(score3);

  }
}
