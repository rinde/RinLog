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

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class SolverTest {

  @Test
  public void test() throws InterruptedException {

    final OptaplannerSolver s = new OptaplannerSolver(123, true);

    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
        .addAvailableParcel(
          Parcel.builder(new Point(0, 0), new Point(2, 0)).build())
        .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
            .setLocation(new Point(5, 5))
            .setRoute(ImmutableList.<Parcel>of())
            .build())
        .build();

    // final StatisticsDTO stats = Solvers.computeStats(gso, null);

    // final double cost =
    // Gendreau06ObjectiveFunction.instance(50d).computeCost(stats);
    // System.out.println(cost * 60000);

    final ImmutableList<ImmutableList<Parcel>> schedule = s.solve(gso);
    System.out.println(s.getSoftScore());
    System.out.println(schedule);

    final double cost = Gendreau06ObjectiveFunction.instance(50d)
        .computeCost(Solvers.computeStats(gso, schedule));
    System.out.println("cost: " + cost * 60000d);
    // TODO create integration test that verifies score

    // cases that are currently not covered:
    // - partially (un)loaded parcel
  }

}
