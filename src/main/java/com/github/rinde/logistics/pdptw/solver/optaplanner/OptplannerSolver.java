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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.definition.ScoreDefinitionType;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.random.RandomType;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjects;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class OptplannerSolver implements Solver {

  private final org.optaplanner.core.api.solver.Solver solver;

  OptplannerSolver() {
    final SolverFactory factory = SolverFactory.createEmpty();
    final SolverConfig config = factory.getSolverConfig();

    // final ScanAnnotatedClassesConfig scan = new ScanAnnotatedClassesConfig();
    // scan.setPackageIncludeList(asList(getClass().getPackage().getName()));
    // config.setScanAnnotatedClassesConfig(scan);
    config.setEntityClassList(
      ImmutableList.<Class<?>>of(
        ParcelVisit.class,
        // Vehicle.class,
        Visit.class));
    config.setSolutionClass(PDPSolution.class);
    // final TerminationConfig terminationConfig =
    // config.getTerminationConfig();

    final ScoreDirectorFactoryConfig scoreConfig =
      new ScoreDirectorFactoryConfig();
    scoreConfig.setScoreDefinitionType(ScoreDefinitionType.HARD_SOFT_LONG);
    scoreConfig.setIncrementalScoreCalculatorClass(ScoreCalculator.class);
    config.setScoreDirectorFactoryConfig(scoreConfig);

    config.setRandomSeed(123L);
    config.setRandomType(RandomType.MERSENNE_TWISTER);
    config.setEnvironmentMode(EnvironmentMode.FULL_ASSERT);

    solver = factory.buildSolver();
  }

  static final Unit<Duration> TIME_UNIT = SI.MILLI(SI.SECOND);
  static final Unit<Velocity> SPEED_UNIT = NonSI.KILOMETERS_PER_HOUR;
  static final Unit<Length> DISTANCE_UNIT = SI.KILOMETER;

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {

    checkArgument(state.getTimeUnit().equals(TIME_UNIT));
    checkArgument(state.getSpeedUnit().equals(SPEED_UNIT));
    checkArgument(state.getDistUnit().equals(DISTANCE_UNIT));

    final PDPSolution problem = new PDPSolution(state.getTime());

    final Set<Parcel> parcels = GlobalStateObjects.allParcels(state);

    final List<ParcelVisit> parcelList = new ArrayList<>();
    for (final Parcel p : parcels) {
      parcelList.add(new ParcelVisit(p, VisitType.PICKUP));
      parcelList.add(new ParcelVisit(p, VisitType.DELIVER));
    }
    problem.parcelList = parcelList;

    final List<Vehicle> vehicleList = new ArrayList<>();
    for (final VehicleStateObject vso : state.getVehicles()) {
      vehicleList.add(new Vehicle(vso));
    }
    problem.vehicleList = vehicleList;

    // TODO fix the initial allocation
    // Visit prev = vehicleList.get(0);
    // for (final ParcelVisit pv : parcelList) {
    // pv.setPreviousVisit(prev);
    // prev = pv;
    // }

    solver.solve(problem);

    final PDPSolution solution = (PDPSolution) solver.getBestSolution();

    final ImmutableList.Builder<ImmutableList<Parcel>> scheduleBuilder =
      ImmutableList.builder();
    for (final Vehicle v : solution.vehicleList) {
      final ImmutableList.Builder<Parcel> routeBuilder =
        ImmutableList.builder();
      ParcelVisit pv = v.getNextVisit();
      while (pv != null) {
        routeBuilder.add(pv.getParcel());
        pv = pv.getNextVisit();
      }
      scheduleBuilder.add(routeBuilder.build());
    }
    return scheduleBuilder.build();
  }
}
