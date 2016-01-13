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
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.github.rinde.rinsim.central.SolverValidator;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
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
  PDPSolution lastSolution;
  final ScoreCalculator scoreCalculator;

  OptaplannerSolver(Builder builder, long seed) {
    solver = createOptaplannerSolver(builder, seed);
    scoreCalculator = new ScoreCalculator();
  }

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {

    final PDPSolution problem = convert(state);

    solver.solve(problem);

    lastSolution = (PDPSolution) solver.getBestSolution();

    final HardSoftLongScore score = lastSolution.getScore();

    // System.out.println(state);
    checkState(score.getHardScore() == 0,
      "Optaplanner didn't find a solution satisfying all hard constraints.");
    lastSoftScore = score.getSoftScore();

    return toSchedule(lastSolution);
  }

  static org.optaplanner.core.api.solver.Solver createOptaplannerSolver(
      Builder builder, long seed) {
    final SolverFactory factory = SolverFactory.createFromXmlResource(
      builder.getSolverXmlResource());
    final SolverConfig config = factory.getSolverConfig();
    config.setEntityClassList(
      ImmutableList.<Class<?>>of(
        ParcelVisit.class,
        Visit.class));
    config.setSolutionClass(PDPSolution.class);

    final TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig
        .setUnimprovedMillisecondsSpentLimit(builder.getUnimprovedMsLimit());
    config.setTerminationConfig(terminationConfig);

    final ScoreDirectorFactoryConfig scoreConfig =
      new ScoreDirectorFactoryConfig();
    scoreConfig.setScoreDefinitionType(ScoreDefinitionType.HARD_SOFT_LONG);
    scoreConfig.setIncrementalScoreCalculatorClass(ScoreCalculator.class);
    config.setScoreDirectorFactoryConfig(scoreConfig);

    config.setRandomSeed(seed);
    config.setRandomType(RandomType.MERSENNE_TWISTER);
    config.setEnvironmentMode(
      builder.isValidated() ? EnvironmentMode.FULL_ASSERT
          : EnvironmentMode.REPRODUCIBLE);

    return factory.buildSolver();
  }

  static ImmutableList<ImmutableList<Parcel>> toSchedule(PDPSolution solution) {
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

  long getTardiness() {
    return scoreCalculator.getTardiness();
  }

  long getTravelTime() {
    return scoreCalculator.getTravelTime();
  }

  long getOvertime() {
    return scoreCalculator.getOvertime();
  }

  public static PDPSolution convert(GlobalStateObject state) {
    checkArgument(state.getTimeUnit().equals(TIME_UNIT));
    checkArgument(state.getSpeedUnit().equals(SPEED_UNIT));
    checkArgument(state.getDistUnit().equals(DISTANCE_UNIT));

    final PDPSolution problem = new PDPSolution(state.getTime());

    // final Set<Parcel> parcels = GlobalStateObjects.allParcels(state);

    final List<ParcelVisit> parcelList = new ArrayList<>();
    final Map<Parcel, ParcelVisit> pickups = new LinkedHashMap<>();
    final Map<Parcel, ParcelVisit> deliveries = new LinkedHashMap<>();

    for (final Parcel p : state.getAvailableParcels()) {
      final ParcelVisit pickup = new ParcelVisit(p, VisitType.PICKUP);
      final ParcelVisit delivery = new ParcelVisit(p, VisitType.DELIVER);
      pickups.put(p, pickup);
      deliveries.put(p, delivery);
      pickup.setAssociation(delivery);
      delivery.setAssociation(pickup);
      parcelList.add(pickup);
      parcelList.add(delivery);
    }

    boolean firstVehicle = true;

    final List<Vehicle> vehicleList = new ArrayList<>();
    for (int i = 0; i < state.getVehicles().size(); i++) {
      final VehicleStateObject vso = state.getVehicles().get(i);
      final Vehicle vehicle = new Vehicle(vso, i);
      vehicleList.add(vehicle);

      final List<ParcelVisit> visits = new ArrayList<>();
      if (vso.getRoute().isPresent()) {
        List<Parcel> route = vso.getRoute().get();

        if (firstVehicle) {
          route = new ArrayList<>(route);
          final Set<Parcel> unassigned =
            GlobalStateObjects.unassignedParcels(state);
          route.addAll(unassigned);
          route.addAll(unassigned);
          firstVehicle = false;
        }

        for (final Parcel p : route) {
          // is it a pickup or a delivery?
          if (vso.getContents().contains(p) || !pickups.containsKey(p)) {
            ParcelVisit delivery;
            if (deliveries.containsKey(p)) {
              delivery = deliveries.remove(p);
            } else {
              delivery = new ParcelVisit(p, VisitType.DELIVER);
              parcelList.add(delivery);
            }
            visits.add(delivery);
          } else {
            visits.add(checkNotNull(pickups.remove(p)));
          }
        }
      } else {
        throw new IllegalArgumentException();
        // add destination
        //
        // final List<Parcel> route = new ArrayList<>();
        // route.addAll(vso.getDestination().asSet());

        // add contents
      }
      initRoute(vehicle, visits);
    }

    problem.parcelList = parcelList;
    problem.vehicleList = vehicleList;

    // System.out.println("**** INITIAL ****");
    // System.out.println(problem);

    return problem;
  }

  static void initRoute(Vehicle vehicle, List<ParcelVisit> visits) {
    final Visit last = vehicle.getLastVisit();
    // attach to tail
    Visit prev = last == null ? vehicle : last;
    for (final ParcelVisit pv : visits) {
      pv.setPreviousVisit(prev);
      pv.setVehicle(vehicle);
      prev.setNextVisit(pv);
      prev = pv;
    }
  }

  // public static StochasticSupplier<Solver> supplier(
  // long unimprovedSecondsLimit) {
  // return new Sup(unimprovedSecondsLimit);
  // }
  //
  // public static StochasticSupplier<Solver> validatedSupplier(
  // long unimprovedSecondsLimit,
  // double vehicleSpeedKmH) {
  // return new ValSup(Gendreau06ObjectiveFunction.instance(vehicleSpeedKmH),
  // unimprovedSecondsLimit);
  // }
  //
  // static OptaplannerSolver instance() {
  // return new OptaplannerSolver(123, false, 1);
  // }
  //
  // static Solver validatedInstance(long seed,
  // Gendreau06ObjectiveFunction objFunc, long unimprovedSecondsLimit) {
  // return new Validator(seed, objFunc, unimprovedSecondsLimit);
  // }

  // static class Sup implements StochasticSupplier<Solver> {
  //
  // long usl;
  //
  // Sup(long unimprovedSecondsLimit) {
  // usl = unimprovedSecondsLimit;
  // }
  //
  // @Override
  // public Solver get(long seed) {
  // return new OptaplannerSolver(seed, false, usl);
  // }
  // }
  //
  // static class ValSup implements StochasticSupplier<Solver> {
  // final Gendreau06ObjectiveFunction objectiveFunction;
  // long usl;
  //
  // ValSup(Gendreau06ObjectiveFunction objFunc, long unimprovedSecondsLimit) {
  // objectiveFunction = objFunc;
  // usl = unimprovedSecondsLimit;
  // }
  //
  // @Override
  // public Solver get(long seed) {
  // return new Validator(seed, objectiveFunction, usl);
  // }
  // }

  public static Builder builder() {
    return Builder.defaultInstance();
  }

  @AutoValue
  public abstract static class Builder {

    static final String DEFAULT_SOLVER_XML_RESOURCE =
      "com/github/rinde/logistics/pdptw/solver/optaplanner/solverConfig.xml";

    abstract boolean isValidated();

    abstract Gendreau06ObjectiveFunction getObjectiveFunction();

    abstract long getUnimprovedMsLimit();

    abstract String getSolverXmlResource();

    public Builder setValidated(boolean validate) {
      return create(validate, getObjectiveFunction(),
        getUnimprovedMsLimit(), getSolverXmlResource());
    }

    public Builder setObjectiveFunction(Gendreau06ObjectiveFunction func) {
      return create(isValidated(), func, getUnimprovedMsLimit(),
        getSolverXmlResource());
    }

    public Builder setUnimprovedMsLimit(long ms) {
      return create(isValidated(), getObjectiveFunction(), ms,
        getSolverXmlResource());
    }

    public Builder setSolverXmlResource(String solverXmlResource) {
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), solverXmlResource);
    }

    public StochasticSupplier<Solver> buildSolver() {
      return new SimulatedTimeSupplier(this);
    }

    public StochasticSupplier<RealtimeSolver> buildRealtimeSolver() {
      return new RealtimeSupplier(this);
    }

    static Builder defaultInstance() {
      return create(false, Gendreau06ObjectiveFunction.instance(), 1L,
        DEFAULT_SOLVER_XML_RESOURCE);
    }

    static Builder create(boolean validate, Gendreau06ObjectiveFunction func,
        long sec, String resource) {
      return new AutoValue_OptaplannerSolver_Builder(validate, func, sec,
          resource);
    }
  }

  static class RTSolver implements RealtimeSolver {

    @Override
    public void init(Scheduler scheduler) {
      // TODO Auto-generated method stub

    }

    @Override
    public void problemChanged(GlobalStateObject snapshot) {
      // TODO Auto-generated method stub

    }

    @Override
    public void receiveSnapshot(GlobalStateObject snapshot) {
      // TODO Auto-generated method stub

    }

    @Override
    public void cancel() {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean isComputing() {
      // TODO Auto-generated method stub
      return false;
    }

  }

  static class SimulatedTimeSupplier implements StochasticSupplier<Solver> {
    final Builder builder;

    SimulatedTimeSupplier(Builder b) {
      builder = b;
    }

    @Override
    public Solver get(long seed) {
      if (builder.isValidated()) {
        return new Validator(builder, seed);
      }
      return new OptaplannerSolver(builder, seed);
    }
  }

  static class RealtimeSupplier implements StochasticSupplier<RealtimeSolver> {
    final Builder builder;

    RealtimeSupplier(Builder b) {
      builder = b;
    }

    @Override
    public RealtimeSolver get(long seed) {
      // TODO Auto-generated method stub
      return null;
    }
  }

  static class Validator implements Solver {
    final OptaplannerSolver solver;
    Builder builder;

    Validator(Builder b, long seed) {
      solver = new OptaplannerSolver(b, seed);
      builder = b;
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
        throws InterruptedException {
      checkState(
        Math.abs(state.getVehicles().get(0).getDto().getSpeed() -
            builder.getObjectiveFunction().getVehicleSpeed()) < 0.001);

      final ImmutableList<ImmutableList<Parcel>> schedule = solver.solve(state);

      SolverValidator.validateOutputs(schedule, state);

      System.out.println(state);
      System.out.println("new schedule");
      System.out.println(Joiner.on("\n").join(schedule));

      final StatisticsDTO stats = Solvers.computeStats(state, schedule);

      // convert cost to nanosecond precision
      final double cost = builder.getObjectiveFunction().computeCost(stats)
          * 60000000000d;

      solver.scoreCalculator.resetWorkingSolution(solver.lastSolution);

      System.out.println(" === RinSim ===");
      System.out.println(
        builder.getObjectiveFunction().printHumanReadableFormat(stats));
      System.out.println(" === Optaplanner ===");
      System.out
          .println("Travel time: " + solver.getTravelTime() / 60000000000d);
      System.out.println("Tardiness: " + solver.getTardiness() / 60000000000d);
      System.out.println("Overtime: " + solver.getOvertime() / 60000000000d);
      System.out.println("Total: " + solver.getSoftScore() / -60000000000d);

      // optaplanner has nanosecond precision
      final double optaplannerCost = solver.getSoftScore() * -1d;

      final double difference = Math.abs(cost - optaplannerCost);
      // max 10 nanosecond deviation is allowed
      checkState(
        difference < 10000000000d,
        "ObjectiveFunction cost (%s) must be equal to Optaplanner cost (%s),"
            + " the difference is %s.",
        cost, optaplannerCost, difference);

      return schedule;
    }
  }
}
