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
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.definition.ScoreDefinitionType;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.random.RandomType;
import org.optaplanner.core.config.solver.termination.TerminationConfig;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjects;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class OptaplannerSolver implements Solver {

  static final Unit<Duration> TIME_UNIT = SI.MILLI(SI.SECOND);
  static final Unit<Velocity> SPEED_UNIT = NonSI.KILOMETERS_PER_HOUR;
  static final Unit<Length> DISTANCE_UNIT = SI.KILOMETER;

  private final org.optaplanner.core.api.solver.Solver solver;
  private long lastSoftScore;

  OptaplannerSolver(long seed, boolean validated) {
    final SolverFactory factory = SolverFactory.createFromXmlResource(
      "com/github/rinde/logistics/pdptw/solver/optaplanner/solverConfig.xml");
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

    final TerminationConfig terminationConfig = new TerminationConfig();

    // terminationConfig.setStepCountLimit(10);
    terminationConfig.setUnimprovedSecondsSpentLimit(5L);
    config.setTerminationConfig(terminationConfig);

    final ScoreDirectorFactoryConfig scoreConfig =
      new ScoreDirectorFactoryConfig();
    scoreConfig.setScoreDefinitionType(ScoreDefinitionType.HARD_SOFT_LONG);
    scoreConfig.setIncrementalScoreCalculatorClass(ScoreCalculator.class);
    config.setScoreDirectorFactoryConfig(scoreConfig);

    config.setRandomSeed(seed);
    config.setRandomType(RandomType.MERSENNE_TWISTER);
    config.setEnvironmentMode(
      validated ? EnvironmentMode.FULL_ASSERT : EnvironmentMode.REPRODUCIBLE);

    solver = factory.buildSolver();
  }

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {

    final PDPSolution problem = convert(state);

    solver.solve(problem);

    final PDPSolution solution = (PDPSolution) solver.getBestSolution();

    final HardSoftLongScore score = solution.getScore();

    checkState(score.getHardScore() == 0);
    lastSoftScore = score.getSoftScore();

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

  @VisibleForTesting
  long getSoftScore() {
    return lastSoftScore;
  }

  static PDPSolution convert(GlobalStateObject state) {
    checkArgument(state.getTimeUnit().equals(TIME_UNIT));
    checkArgument(state.getSpeedUnit().equals(SPEED_UNIT));
    checkArgument(state.getDistUnit().equals(DISTANCE_UNIT));

    final PDPSolution problem = new PDPSolution(state.getTime());

    final Set<Parcel> parcels = GlobalStateObjects.allParcels(state);

    final List<ParcelVisit> parcelList = new ArrayList<>();
    for (final Parcel p : parcels) {
      final ParcelVisit pickup = new ParcelVisit(p, VisitType.PICKUP);
      final ParcelVisit delivery = new ParcelVisit(p, VisitType.DELIVER);
      pickup.setAssociation(delivery);
      delivery.setAssociation(pickup);
      parcelList.add(pickup);
      parcelList.add(delivery);
    }
    problem.parcelList = parcelList;

    final List<Vehicle> vehicleList = new ArrayList<>();
    for (final VehicleStateObject vso : state.getVehicles()) {
      vehicleList.add(new Vehicle(vso));
    }
    problem.vehicleList = vehicleList;

    // TODO fix the initial allocation
    final Vehicle vehicle = vehicleList.get(0);
    Visit prev = vehicle;
    for (final ParcelVisit pv : parcelList) {
      pv.setPreviousVisit(prev);
      pv.setVehicle(vehicle);
      prev.setNextVisit(pv);
      prev = pv;
    }
    return problem;
  }

  public static StochasticSupplier<Solver> supplier() {
    return new Sup();
  }

  public static StochasticSupplier<Solver> validatedSupplier(
      double vehicleSpeedKmH) {
    return new ValSup(Gendreau06ObjectiveFunction.instance(vehicleSpeedKmH));
  }

  static OptaplannerSolver instance() {
    return new OptaplannerSolver(123, false);
  }

  static Solver validatedInstance(long seed, ObjectiveFunction objFunc) {
    return new Validator(seed, objFunc);
  }

  static class Sup implements StochasticSupplier<Solver> {

    @Override
    public Solver get(long seed) {
      return new OptaplannerSolver(seed, false);
    }
  }

  static class ValSup implements StochasticSupplier<Solver> {
    final ObjectiveFunction objectiveFunction;

    ValSup(ObjectiveFunction objFunc) {
      objectiveFunction = objFunc;
    }

    @Override
    public Solver get(long seed) {
      return new Validator(seed, objectiveFunction);
    }
  }

  static class Validator implements Solver {
    final OptaplannerSolver solver;
    ObjectiveFunction objectiveFunction;

    Validator(long seed, ObjectiveFunction objFunc) {
      solver = new OptaplannerSolver(seed, true);
      objectiveFunction = objFunc;
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
        throws InterruptedException {

      final ImmutableList<ImmutableList<Parcel>> schedule = solver.solve(state);

      final double cost =
        objectiveFunction.computeCost(Solvers.computeStats(state, schedule))
            * 100000d;

      final double optaplannerCost = solver.getSoftScore() / 10d;
      checkState(Math.round(cost) == Math.round(optaplannerCost));

      return schedule;
    }
  }
}
