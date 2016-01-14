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
import static com.google.common.base.Verify.verifyNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.optaplanner.benchmark.api.PlannerBenchmark;
import org.optaplanner.benchmark.api.PlannerBenchmarkFactory;
import org.optaplanner.benchmark.impl.PlannerBenchmarkRunner;
import org.optaplanner.benchmark.impl.result.SolverBenchmarkResult;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.event.BestSolutionChangedEvent;
import org.optaplanner.core.api.solver.event.SolverEventListener;
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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 *
 * @author Rinde van Lon
 */
public final class OptaplannerSolvers {
  static final Unit<Duration> TIME_UNIT = SI.MILLI(SI.SECOND);
  static final Unit<Velocity> SPEED_UNIT = NonSI.KILOMETERS_PER_HOUR;
  static final Unit<Length> DISTANCE_UNIT = SI.KILOMETER;

  private OptaplannerSolvers() {}

  @CheckReturnValue
  public static Builder builder() {
    return Builder.defaultInstance();
  }

  public static Map<String, SolverConfig> getConfigsFromBenchmark(
      String xmlLocation) {
    final PlannerBenchmarkFactory plannerBenchmarkFactory =
      PlannerBenchmarkFactory.createFromFreemarkerXmlResource(xmlLocation);
    final PlannerBenchmark plannerBenchmark =
      plannerBenchmarkFactory.buildPlannerBenchmark();

    final PlannerBenchmarkRunner pbr =
      (PlannerBenchmarkRunner) plannerBenchmark;

    final ImmutableMap.Builder<String, SolverConfig> builder =
      ImmutableMap.builder();
    for (final SolverBenchmarkResult sbr : pbr.getPlannerBenchmarkResult()
        .getSolverBenchmarkResultList()) {
      builder.put(sbr.getName().replaceAll(" ", "-"), sbr.getSolverConfig());
    }
    return builder.build();
  }

  @CheckReturnValue
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

  static org.optaplanner.core.api.solver.Solver createOptaplannerSolver(
      Builder builder, long seed) {

    final SolverFactory factory;
    if (builder.getSolverConfig() != null) {
      factory = SolverFactory.createEmpty();
      factory.getSolverConfig().inherit(builder.getSolverConfig());
    } else {
      factory =
        SolverFactory.createFromXmlResource(builder.getSolverXmlResource());
    }
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

  @AutoValue
  public abstract static class Builder {

    static final String DEFAULT_SOLVER_XML_RESOURCE =
      "com/github/rinde/logistics/pdptw/solver/optaplanner/solverConfig.xml";

    Builder() {}

    abstract boolean isValidated();

    abstract Gendreau06ObjectiveFunction getObjectiveFunction();

    abstract long getUnimprovedMsLimit();

    abstract String getSolverXmlResource();

    @Nullable
    abstract SolverConfig getSolverConfig();

    @CheckReturnValue
    public Builder withValidated(boolean validate) {
      return create(validate, getObjectiveFunction(),
        getUnimprovedMsLimit(), getSolverXmlResource(), getSolverConfig());
    }

    @CheckReturnValue
    public Builder withObjectiveFunction(Gendreau06ObjectiveFunction func) {
      return create(isValidated(), func, getUnimprovedMsLimit(),
        getSolverXmlResource(), getSolverConfig());
    }

    @CheckReturnValue
    public Builder withUnimprovedMsLimit(long ms) {
      return create(isValidated(), getObjectiveFunction(), ms,
        getSolverXmlResource(), getSolverConfig());
    }

    @CheckReturnValue
    public Builder withSolverXmlResource(String solverXmlResource) {
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), solverXmlResource, getSolverConfig());
    }

    // takes precedence over xml
    @CheckReturnValue
    public Builder withSolverConfig(SolverConfig solverConfig) {
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getSolverXmlResource(), solverConfig);
    }

    @CheckReturnValue
    public StochasticSupplier<Solver> buildSolver() {
      return new SimulatedTimeSupplier(this);
    }

    @CheckReturnValue
    public StochasticSupplier<RealtimeSolver> buildRealtimeSolver() {
      return new RealtimeSupplier(this);
    }

    static Builder defaultInstance() {
      return create(false, Gendreau06ObjectiveFunction.instance(), 1L,
        DEFAULT_SOLVER_XML_RESOURCE, null);
    }

    static Builder create(boolean validate, Gendreau06ObjectiveFunction func,
        long sec, String resource, @Nullable SolverConfig config) {
      return new AutoValue_OptaplannerSolvers_Builder(validate, func, sec,
          resource, config);
    }
  }

  static class OptaplannerSolver implements Solver {
    private final org.optaplanner.core.api.solver.Solver solver;
    private long lastSoftScore;
    @Nullable
    PDPSolution lastSolution;
    final ScoreCalculator scoreCalculator;

    OptaplannerSolver(Builder builder, long seed) {
      solver = createOptaplannerSolver(builder, seed);
      scoreCalculator = new ScoreCalculator();
      lastSolution = null;
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
        throws InterruptedException {
      final PDPSolution problem = convert(state);
      solver.solve(problem);

      final PDPSolution solution = (PDPSolution) solver.getBestSolution();
      lastSolution = solution;

      final HardSoftLongScore score = solution.getScore();

      checkState(score.getHardScore() == 0,
        "Optaplanner didn't find a solution satisfying all hard constraints.");
      lastSoftScore = score.getSoftScore();
      return toSchedule(solution);
    }

    void addEventListener(SolverEventListener<PDPSolution> listener) {
      solver.addEventListener(listener);
    }

    boolean isSolving() {
      return solver.isSolving();
    }

    void terminateEarly() {
      solver.terminateEarly();
    }

    @VisibleForTesting
    long getSoftScore() {
      return lastSoftScore;
    }
  }

  static class OptaplannerRTSolver implements RealtimeSolver {
    final OptaplannerSolver solver;
    Optional<Scheduler> scheduler;
    @Nullable
    GlobalStateObject lastSnapshot;

    OptaplannerRTSolver(Builder b, long seed) {
      solver = new OptaplannerSolver(b, seed);
      scheduler = Optional.absent();
    }

    @Override
    public void init(final Scheduler sched) {
      checkState(!scheduler.isPresent(),
        "Solver can be initialized only once.");
      scheduler = Optional.of(sched);

      solver.addEventListener(new SolverEventListener<PDPSolution>() {
        @Override
        public void bestSolutionChanged(
            @SuppressWarnings("null") BestSolutionChangedEvent<PDPSolution> event) {
          if (event.isNewBestSolutionInitialized()
              && event.getNewBestSolution().getScore().getHardScore() == 0) {
            final ImmutableList<ImmutableList<Parcel>> schedule =
              toSchedule(event.getNewBestSolution());
            sched.updateSchedule(verifyNotNull(lastSnapshot), schedule);
          }
        }
      });
    }

    @Override
    public void problemChanged(final GlobalStateObject snapshot) {
      checkState(scheduler.isPresent());
      cancel();
      lastSnapshot = snapshot;

      final ListenableFuture<ImmutableList<ImmutableList<Parcel>>> future =
        scheduler.get().getSharedExecutor()
            .submit(Solvers.createSolverCallable(solver, snapshot));

      Futures.addCallback(future,
        new FutureCallback<ImmutableList<ImmutableList<Parcel>>>() {

          @Override
          public void onSuccess(
              @Nullable ImmutableList<ImmutableList<Parcel>> result) {
            if (result == null) {
              scheduler.get().reportException(
                new IllegalArgumentException("Solver.solve(..) must return a "
                    + "non-null result. Solver: " + solver));
            } else {
              scheduler.get().updateSchedule(snapshot, result);
              scheduler.get().doneForNow();
            }
          }

          @Override
          public void onFailure(Throwable t) {
            if (t instanceof CancellationException) {
              return;
            }
            scheduler.get().reportException(t);
          }
        });
    }

    @Override
    public void receiveSnapshot(GlobalStateObject snapshot) {}

    @Override
    public void cancel() {
      if (isComputing()) {
        solver.terminateEarly();
        while (solver.isSolving()) {
          try {
            Thread.sleep(5L);
          } catch (final InterruptedException e) {
            // stop waiting upon interrupt
            break;
          }
        }
      }
    }

    @Override
    public boolean isComputing() {
      return solver.isSolving();
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

    @Override
    public String toString() {
      return "OptaplannerST";
    }
  }

  static class RealtimeSupplier implements StochasticSupplier<RealtimeSolver> {
    final Builder builder;

    RealtimeSupplier(Builder b) {
      builder = b;
    }

    @Override
    public RealtimeSolver get(long seed) {
      return new OptaplannerRTSolver(builder, seed);
    }

    @Override
    public String toString() {
      return "OptaplannerRT";
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

      final ScoreCalculator sc = solver.scoreCalculator;

      sc.resetWorkingSolution(solver.lastSolution);

      System.out.println(" === RinSim ===");
      System.out.println(
        builder.getObjectiveFunction().printHumanReadableFormat(stats));
      System.out.println(" === Optaplanner ===");
      System.out
          .println("Travel time: " + sc.getTravelTime() / 60000000000d);
      System.out.println("Tardiness: " + sc.getTardiness() / 60000000000d);
      System.out.println("Overtime: " + sc.getOvertime() / 60000000000d);
      System.out.println(
        "Total: " + sc.calculateScore().getSoftScore() / -60000000000d);

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
