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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.score.definition.ScoreDefinitionType;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.random.RandomType;
import org.optaplanner.core.config.solver.termination.TerminationCompositionStyle;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.MeasureableSolver;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.SolverTimeMeasurement;
import com.github.rinde.rinsim.central.SolverValidator;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.rt.MeasurableRealtimeSolver;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.GraphHeuristics;
import com.github.rinde.rinsim.geom.Graphs.Heuristic;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 *
 * @author Rinde van Lon
 */
public final class OptaplannerSolvers {
  static final Logger LOGGER =
    LoggerFactory.getLogger(OptaplannerSolvers.class);

  static final Unit<Duration> TIME_UNIT = SI.MILLI(SI.SECOND);
  static final Unit<Velocity> SPEED_UNIT = NonSI.KILOMETERS_PER_HOUR;
  static final Unit<Length> DISTANCE_UNIT = SI.KILOMETER;
  static final String NAME_SEPARATOR = "-";

  // this is part of a while loop
  static final long WAIT_FOR_SOLVER_TERMINATION_PERIOD_MS = 5L;

  private OptaplannerSolvers() {}

  @CheckReturnValue
  public static Builder builder() {
    return Builder.defaultInstance();
  }

  static ImmutableMap<String, SolverConfig> getConfigsFromBenchmark(
      String xml) {
    final PlannerBenchmarkFactory plannerBenchmarkFactory =
      PlannerBenchmarkFactory
        .createFromFreemarkerXmlReader(new StringReader(xml));
    final PlannerBenchmark plannerBenchmark =
      plannerBenchmarkFactory.buildPlannerBenchmark();

    final PlannerBenchmarkRunner pbr =
      (PlannerBenchmarkRunner) plannerBenchmark;

    final ImmutableMap.Builder<String, SolverConfig> builder =
      ImmutableMap.builder();
    for (final SolverBenchmarkResult sbr : pbr.getPlannerBenchmarkResult()
      .getSolverBenchmarkResultList()) {
      builder.put(sbr.getName().replaceAll(" ", NAME_SEPARATOR),
        sbr.getSolverConfig());
    }
    return builder.build();
  }

  // public static OptaplannerFactory getFactoryFromBenchmark(String
  // xmlLocation) {
  // return new OptaplannerSolversFactory(xmlLocation);
  // }

  public static PDPSolution convert(GlobalStateObject state) {
    return convert(state, GraphHeuristics.euclidean());
  }

  @CheckReturnValue
  public static PDPSolution convert(GlobalStateObject state,
      Heuristic heuristic) {
    checkArgument(state.getTimeUnit().equals(TIME_UNIT));
    checkArgument(state.getSpeedUnit().equals(SPEED_UNIT));
    checkArgument(state.getDistUnit().equals(DISTANCE_UNIT));

    final PDPSolution problem = new PDPSolution(state.getTime());

    // final Set<Parcel> parcels = GlobalStateObjects.allParcels(state);

    final List<ParcelVisit> parcelList = new ArrayList<>();
    final Map<Parcel, ParcelVisit> pickups = new LinkedHashMap<>();
    final Map<Parcel, ParcelVisit> deliveries = new LinkedHashMap<>();

    final Set<ParcelVisit> unassignedPickups = new LinkedHashSet<>();

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
    unassignedPickups.addAll(pickups.values());

    final List<Vehicle> vehicleList = new ArrayList<>();
    for (int i = 0; i < state.getVehicles().size(); i++) {
      final VehicleStateObject vso = state.getVehicles().get(i);
      final Vehicle vehicle = new Vehicle(vso, state.getRoadModelSnapshot(),
        heuristic, state.getTimeUnit(), state.getSpeedUnit(), i);
      vehicleList.add(vehicle);

      final List<ParcelVisit> visits = new ArrayList<>();
      if (vso.getRoute().isPresent()) {
        final List<Parcel> route = vso.getRoute().get();

        for (final Parcel p : route) {
          // is it a pickup or a delivery?
          if (vso.getContents().contains(p) || !pickups.containsKey(p)) {
            final ParcelVisit delivery;
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
      }
      unassignedPickups.removeAll(visits);
      initRoute(vehicle, visits);
    }
    problem.parcelList = parcelList;
    problem.vehicleList = vehicleList;
    problem.unassignedPickups = unassignedPickups;
    return problem;
  }

  static org.optaplanner.core.api.solver.Solver createOptaplannerSolver(
      Builder builder, long seed) {
    final SolverConfig config = builder.getSolverConfig();

    config.setEntityClassList(
      ImmutableList.<Class<?>>of(ParcelVisit.class, Visit.class));
    config.setSolutionClass(PDPSolution.class);

    final TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig
      .setTerminationCompositionStyle(TerminationCompositionStyle.AND);
    terminationConfig.setBestScoreFeasible(true);

    if (builder.getUnimprovedMsLimit() > 0) {
      terminationConfig
        .setUnimprovedMillisecondsSpentLimit(builder.getUnimprovedMsLimit());
      config.setTerminationConfig(terminationConfig);
    } else if (builder.getUnimprovedStepCountLimit() > 0) {
      terminationConfig
        .setUnimprovedStepCountLimit(builder.getUnimprovedStepCountLimit());
      for (final PhaseConfig phase : config.getPhaseConfigList()) {
        if (phase instanceof LocalSearchPhaseConfig) {
          phase.setTerminationConfig(terminationConfig);
        }
      }
    }

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

    final SolverFactory<PDPSolution> factory = SolverFactory.createEmpty();
    factory.getSolverConfig().inherit(config);
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
  public abstract static class Builder implements Serializable {
    private static final long serialVersionUID = 20160425L;
    private static final String SINGLE_SOLVER_KEY = "single_solver";

    private static final String RESOURCE_DIR =
      "com/github/rinde/logistics/pdptw/solver/optaplanner/";

    private static final String FIRST_FIT_DECREASING =
      RESOURCE_DIR + "firstFitDecreasing.xml";
    private static final String CHEAPEST_INSERTION =
      RESOURCE_DIR + "cheapestInsertion.xml";
    private static final String FIRST_FIT_DECREASING_WITH_TABU =
      RESOURCE_DIR + "firstFitDecreasingWithTabu.xml";

    @Nullable
    private transient ImmutableMap<String, SolverConfig> configs;

    Builder() {}

    abstract boolean isValidated();

    abstract ObjectiveFunction getObjectiveFunction();

    abstract long getUnimprovedMsLimit();

    abstract int getUnimprovedStepCountLimit();

    // abstract String getSolverXmlResource();
    @Nullable
    abstract String getSolverXml();

    // name as it appears in benchmark xml
    @Nullable
    abstract String getSolverKey();

    abstract boolean isBenchmark();

    // @Nullable
    // abstract SolverConfig getSolverConfig();

    @Nullable
    abstract String getName();

    abstract boolean isTimeMeasuringEnabled();

    public abstract Heuristic getSolverHeuristic();

    @CheckReturnValue
    public Builder withValidated(boolean validate) {
      return create(validate, getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        getSolverXml(), getSolverKey(), isBenchmark(), getName(), configs,
        isTimeMeasuringEnabled(), getSolverHeuristic());
    }

    @CheckReturnValue
    public Builder withObjectiveFunction(ObjectiveFunction func) {
      return create(isValidated(), func, getUnimprovedMsLimit(),
        getUnimprovedStepCountLimit(), getSolverXml(), getSolverKey(),
        isBenchmark(), getName(), configs, isTimeMeasuringEnabled(),
        getSolverHeuristic());
    }

    /**
     * Sets the time limit (ms) that the solver should continue searching for an
     * improving solution. When using this option any previous call to
     * {@link #withUnimprovedStepCountLimit(int)} are ignored.
     * @param ms The unimproved time limit in milliseconds.
     * @return A new builder instance with the unimproved property changed.
     */
    @CheckReturnValue
    public Builder withUnimprovedMsLimit(long ms) {
      return create(isValidated(), getObjectiveFunction(), ms, -1,
        getSolverXml(), getSolverKey(), isBenchmark(), getName(), configs,
        isTimeMeasuringEnabled(), getSolverHeuristic());
    }

    /**
     * Sets the step count limit. This limit indicates the number of unimproving
     * steps that the solver will perform until it terminates. When using this
     * option any previous call to {@link #withUnimprovedMsLimit(long)} is
     * ignored.
     * @param count The number of steps.
     * @return A new builder instance with the unimproved property changed.
     */
    @CheckReturnValue
    public Builder withUnimprovedStepCountLimit(int count) {
      return create(isValidated(), getObjectiveFunction(), -1L, count,
        getSolverXml(), getSolverKey(), isBenchmark(), getName(), configs,
        isTimeMeasuringEnabled(), getSolverHeuristic());
    }

    @CheckReturnValue
    public Builder withSolverXmlResource(String solverXmlResource) {
      final String xml = resourceToString(solverXmlResource);
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        xml, SINGLE_SOLVER_KEY, false, getName(), null,
        isTimeMeasuringEnabled(), getSolverHeuristic()).interpretXml();
    }

    @CheckReturnValue
    public Builder withFirstFitDecreasingSolver() {
      return withSolverXmlResource(FIRST_FIT_DECREASING)
        .withName("first-fit-decreasing");
    }

    @CheckReturnValue
    public Builder withFirstFitDecreasingWithTabuSolver() {
      return withSolverXmlResource(FIRST_FIT_DECREASING_WITH_TABU)
        .withName("first-fit-decreasing-with-tabu");
    }

    @CheckReturnValue
    public Builder withCheapestInsertionSolver() {
      return withSolverXmlResource(CHEAPEST_INSERTION)
        .withName("cheapest-insertion");
    }

    @CheckReturnValue
    public Builder withSolverFromBenchmark(String benchmarkXmlResource,
        String solverKey) {
      final String xml = resourceToString(benchmarkXmlResource);
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        xml, solverKey, true, getName(), null, isTimeMeasuringEnabled(),
        getSolverHeuristic())
          .interpretXml();
    }

    @CheckReturnValue
    public Builder withSolverFromBenchmark(String benchmarkXmlResource) {
      final String xml = resourceToString(benchmarkXmlResource);
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        xml, null, true, getName(), null, isTimeMeasuringEnabled(),
        getSolverHeuristic())
          .interpretXml();
    }

    @CheckReturnValue
    public Builder withSolverKey(String key) {
      checkArgument(isBenchmark());
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        getSolverXml(), key, true, getName(), configs, isTimeMeasuringEnabled(),
        getSolverHeuristic())
          .interpretXml();
    }

    /**
     * Changes the name of the solvers that are created. The name can be used to
     * identify the solver in the logs and GUI. It is mandatory to call this
     * method.
     * @param name A non-null string that identifies the solvers created by this
     *          builder.
     * @return A new builder with the name property changed.
     */
    @CheckReturnValue
    public Builder withName(String name) {
      checkNotNull(name);
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        getSolverXml(), getSolverKey(), isBenchmark(), name, configs,
        isTimeMeasuringEnabled(), getSolverHeuristic());
    }

    /**
     * Enables/disables the usage of time measurements in the solver. If this is
     * disabled, {@link MeasureableSolver#getTimeMeasurements()} and
     * {@link MeasurableRealtimeSolver#getTimeMeasurements()} will throw an
     * {@link IllegalStateException}. If enabled these methods will return time
     * measurements.
     * @param enable <code>true</code> to enable, <code>false</code> to disable.
     *          Default value: <code>false</code>.
     * @return A new builder with the time measurement property changed.
     */
    public Builder withTimeMeasurementsEnabled(boolean enable) {
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        getSolverXml(), getSolverKey(), isBenchmark(), getName(), configs,
        enable, getSolverHeuristic());
    }

    /**
     * Configures the Solver to use a specific heuristic for the calculation of
     * optimal routes.
     * @param heuristic The heuristic to be used by the solver for determining
     *          routes for vehicles. By default this is
     *          {@link GraphHeuristics#euclidean()}}.
     * @return A new builder with the new heuristic configured.
     */
    public Builder withSolverHeuristic(Heuristic heuristic) {
      return create(isValidated(), getObjectiveFunction(),
        getUnimprovedMsLimit(), getUnimprovedStepCountLimit(),
        getSolverXml(), getSolverKey(), isBenchmark(), getName(), configs,
        isTimeMeasuringEnabled(), heuristic);
    }

    @CheckReturnValue
    public StochasticSupplier<Solver> buildSolverSupplier() {
      checkPreconditions();
      return new SimulatedTimeSupplier(this);
    }

    @CheckReturnValue
    public StochasticSupplier<RealtimeSolver> buildRealtimeSolverSupplier() {
      checkPreconditions();
      return new RealtimeSupplier(this);
    }

    @CheckReturnValue
    public ImmutableSet<String> getSupportedSolverKeys() {
      if (configs == null) {
        return ImmutableSet.of();
      }
      return verifyNotNull(configs).keySet();
    }

    void checkPreconditions() {
      checkArgument(getSolverXml() != null,
        "A solver config must be specified either via a benchmark xml or a "
          + "regular xml file.");
      checkArgument(getSolverKey() != null, "A solver key must be specified.");
      if (!isBenchmark()) {
        checkArgument(getName() != null, "A name must be specified.");
      }
    }

    Builder interpretXml() {
      if (isBenchmark()) {
        configs = getConfigsFromBenchmark(verifyNotNull(getSolverXml()));
      } else {
        final SolverFactory factory =
          SolverFactory.createFromXmlReader(new StringReader(getSolverXml()));
        configs = ImmutableMap.<String, SolverConfig>builder()
          .put(SINGLE_SOLVER_KEY, factory.getSolverConfig())
          .build();
      }
      if (getSolverKey() != null) {
        checkArgument(verifyNotNull(configs).containsKey(getSolverKey()));
      }
      return this;
    }

    String getFullName() {
      final StringBuilder sb = new StringBuilder();
      if (getName() == null) {
        checkState(isBenchmark());
        sb.append(getSolverKey());
      } else {
        sb.append(getName());
      }
      sb.append(NAME_SEPARATOR);
      if (getUnimprovedMsLimit() > 0) {
        return sb.append(getUnimprovedMsLimit())
          .append("ms")
          .toString();
      }
      return sb.append(getUnimprovedStepCountLimit())
        .append("steps")
        .toString();
    }

    SolverConfig getSolverConfig() {
      if (configs == null) {
        interpretXml();
      }
      return verifyNotNull(configs).get(getSolverKey());
    }

    static Builder defaultInstance() {
      return create(false, Gendreau06ObjectiveFunction.instance(), -1L, -1,
        null, null, false, null, null, false, GraphHeuristics.euclidean())
          .withSolverXmlResource(FIRST_FIT_DECREASING);
    }

    static Builder create(boolean validate, ObjectiveFunction func,
        long ms, int count, @Nullable String xml, @Nullable String key,
        boolean benchmark, @Nullable String name,
        @Nullable ImmutableMap<String, SolverConfig> map,
        boolean timeMeasuringEnabled, Heuristic heuristic) {
      final Builder b =
        new AutoValue_OptaplannerSolvers_Builder(validate, func, ms, count,
          xml, key, benchmark, name, timeMeasuringEnabled, heuristic);
      // copy the transient config map
      b.configs = map;
      return b;
    }

    static String resourceToString(String resourcePath) {
      try {
        final URL url = Resources.getResource(resourcePath);
        return Resources.toString(url, Charsets.UTF_8);
      } catch (final IOException e) {
        throw new IllegalArgumentException(
          "A problem occured while attempting to read: " + resourcePath, e);
      }
    }
  }

  static class OptaplannerSolver implements MeasureableSolver {
    @Nullable
    PDPSolution lastSolution;
    final ScoreCalculator scoreCalculator;
    final List<SolverTimeMeasurement> measurements;

    private final org.optaplanner.core.api.solver.Solver solver;
    private final String name;
    private long lastSoftScore;
    private final boolean isMeasuringEnabled;
    private final Heuristic routeHeuristic;

    OptaplannerSolver(Builder builder, long seed) {
      solver = createOptaplannerSolver(builder, seed);
      scoreCalculator = new ScoreCalculator();
      lastSolution = null;
      name = "OptaPlanner-" + verifyNotNull(builder.getFullName());
      isMeasuringEnabled = builder.isTimeMeasuringEnabled();
      measurements = new ArrayList<>();
      routeHeuristic = builder.getSolverHeuristic();
    }

    @Override
    public List<SolverTimeMeasurement> getTimeMeasurements() {
      checkState(isMeasuringEnabled, "Time measuring is not enabled.");
      return Collections.unmodifiableList(measurements);
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
        throws InterruptedException {
      final ImmutableList<ImmutableList<Parcel>> sol = doSolve(state);

      checkState(sol != null,
        "OptaPlanner didn't find a solution satisfying all hard constraints.");

      final PDPSolution solution = (PDPSolution) solver.getBestSolution();
      final HardSoftLongScore score = solution.getScore();
      lastSolution = solution;
      lastSoftScore = score.getSoftScore();
      return toSchedule(solution);
    }

    // actual solving, returns null when no valid solution was found
    @Nullable
    public ImmutableList<ImmutableList<Parcel>> doSolve(GlobalStateObject state)
        throws InterruptedException {
      final long start = System.nanoTime();
      // start solving
      final PDPSolution problem = convert(state, routeHeuristic);
      solver.solve(problem);
      // end solving
      if (isMeasuringEnabled) {
        final long duration = System.nanoTime() - start;
        measurements.add(SolverTimeMeasurement.create(state, duration));
      }

      final PDPSolution solution = (PDPSolution) solver.getBestSolution();
      final HardSoftLongScore score = solution.getScore();
      if (score.getHardScore() != 0) {
        return null;
      }
      return toSchedule(solution);
    }

    void addEventListener(SolverEventListener<PDPSolution> listener) {
      solver.addEventListener(listener);
    }

    boolean isSolving() {
      return solver.isSolving();
    }

    boolean isTerminateEarly() {
      return solver.isTerminateEarly();
    }

    void terminateEarly() {
      solver.terminateEarly();
    }

    @VisibleForTesting
    long getSoftScore() {
      return lastSoftScore;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  static class OptaplannerRTSolver implements MeasurableRealtimeSolver {
    final OptaplannerSolver solver;
    Optional<Scheduler> scheduler;
    @Nullable
    GlobalStateObject lastSnapshot;
    @Nullable
    ListenableFuture<ImmutableList<ImmutableList<Parcel>>> currentFuture;
    @Nullable
    ScheduleCallback currentScheduleCallback;
    private final String name;

    OptaplannerRTSolver(Builder b, long seed) {
      solver = new OptaplannerSolver(b, seed);
      scheduler = Optional.absent();
      name = "OptaplannerRT-" + verifyNotNull(b.getFullName());
    }

    @Override
    public List<SolverTimeMeasurement> getTimeMeasurements() {
      return solver.getTimeMeasurements();
    }

    @Override
    public void init(final Scheduler sched) {
      LOGGER.trace("OptaplannerRTSolver.init: {}", name);
      checkState(!scheduler.isPresent(),
        "Solver can be initialized only once.");
      scheduler = Optional.of(sched);

      final OptaplannerRTSolver ref = this;
      solver.addEventListener(new SolverEventListener<PDPSolution>() {
        @Override
        public void bestSolutionChanged(
            @SuppressWarnings("null") BestSolutionChangedEvent<PDPSolution> event) {
          if (event.isNewBestSolutionInitialized()
            && event.getNewBestSolution().getScore().getHardScore() == 0) {
            final ImmutableList<ImmutableList<Parcel>> schedule =
              toSchedule(event.getNewBestSolution());

            LOGGER.info("{} Found new best solution, update schedule. {}", ref,
              solver.isSolving());
            sched.updateSchedule(verifyNotNull(lastSnapshot), schedule);
          }
        }
      });
    }

    @Override
    public synchronized void problemChanged(final GlobalStateObject snapshot) {
      // the problem has changed so we should be computing (it doesn't matter if
      // we were already computing, that computation will be canceled).
      // permissionToRun.set(true);
      start(snapshot, true);
    }

    @Override
    public synchronized void receiveSnapshot(GlobalStateObject snapshot) {
      // this is the snapshot the solver is currently using
      final GlobalStateObject last = lastSnapshot;
      if (last == null
        || !isComputing()
        || last.getTime() > snapshot.getTime()) {
        return;
      }
      // if something significant happens -> restart solver
      boolean significantChangeDetected = false;
      for (int i = 0; i < snapshot.getVehicles().size(); i++) {
        // when a vehicle has a destination, it has committed to perform a
        // specific service operation, this has implications for the schedule:
        // this specific order can no longer be exchanged with other vehicles.
        // Therefore, when this is detected we want to restart the solver such
        // that it won't waste time trying to optimize based on outdated
        // assumptions. Note that we are only interested in events where a
        // vehicle takes upon a *new* commitment (not when it is finished with
        // an old commitment).
        if (snapshot.getVehicles().get(i).getDestination().isPresent()
          && !last.getVehicles().get(i).getDestination()
            .equals(snapshot.getVehicles().get(i).getDestination())) {
          significantChangeDetected = true;
          break;
        }
      }
      if (significantChangeDetected) {
        LOGGER.info(
          "Vehicle destination commitment change detected -> restart solver.");
        start(snapshot, false);
      }
    }

    @Override
    public synchronized void cancel() {
      doCancel(true);
    }

    synchronized void doCancel(boolean notify) {
      LOGGER.trace("{} cancel", this);
      if (isComputing()) {
        LOGGER.trace("{} is computing, cancel future");
        currentScheduleCallback.cancel();
        currentFuture.cancel(true);
        currentScheduleCallback = null;
        currentFuture = null;
        if (notify) {
          scheduler.get().doneForNow();
        }
      }
      if (solver.isSolving()) {
        LOGGER.trace("{} > terminate solver.", this);
        solver.terminateEarly();
        try {
          while (solver.isSolving()) {
            Thread.sleep(WAIT_FOR_SOLVER_TERMINATION_PERIOD_MS);
          }
          LOGGER.info("{} Solver terminated early.", this);
        } catch (final InterruptedException e) {
          // stop waiting upon interrupt
          LOGGER.warn("Interrupt while waiting for solver termination.");
        }
      }
    }

    synchronized void start(final GlobalStateObject snapshot,
        boolean notifyCancel) {
      checkState(scheduler.isPresent());
      doCancel(notifyCancel);
      checkState(currentFuture == null);
      checkState(currentScheduleCallback == null);

      lastSnapshot = snapshot;
      LOGGER.info("{} Start RT Optaplanner Solver.", this);
      currentFuture = scheduler.get().getSharedExecutor()
        .submit(new OptaplannerCallable(solver, snapshot));
      currentScheduleCallback = new ScheduleCallback(this);
      Futures.addCallback(currentFuture, currentScheduleCallback);
    }

    synchronized void handleSolverSuccess(
        @Nullable ImmutableList<ImmutableList<Parcel>> result) {
      if (result == null) {
        if (solver.isTerminateEarly() || currentFuture == null) {
          LOGGER.info("{} Solver was terminated early.", this);
        } else {
          scheduler.get().reportException(new IllegalArgumentException(
            "Solver.solve(..) must return a non-null result. Solver: "
              + solver));
        }
      } else {
        LOGGER.info("{} Computations finished, update schedule.", this);
        scheduler.get().updateSchedule(verifyNotNull(lastSnapshot), result);
        scheduler.get().doneForNow();
      }
      currentFuture = null;
      currentScheduleCallback = null;
    }

    synchronized void handleSolverFailure(Throwable t) {
      if (t instanceof CancellationException) {
        LOGGER.trace("{} Solver got cancelled.", this);
        return;
      }
      scheduler.get().reportException(t);
    }

    @Override
    public synchronized boolean isComputing() {
      return currentFuture != null && currentScheduleCallback != null;
    }

    @Override
    public String toString() {
      return name + NAME_SEPARATOR + Integer.toHexString(hashCode());
    }
  }

  static class ScheduleCallback
      implements FutureCallback<ImmutableList<ImmutableList<Parcel>>> {
    OptaplannerRTSolver reference;

    AtomicBoolean active;

    ScheduleCallback(OptaplannerRTSolver ref) {
      reference = ref;
      active = new AtomicBoolean(true);
    }

    void cancel() {
      synchronized (reference) {
        if (active.get()) {
          active.set(false);
        }
      }
    }

    @Override
    public void onSuccess(
        @Nullable ImmutableList<ImmutableList<Parcel>> result) {
      synchronized (reference) {
        if (active.get()) {
          reference.handleSolverSuccess(result);
          active.set(false);
        }
      }
    }

    @Override
    public void onFailure(Throwable t) {
      synchronized (reference) {
        if (active.get()) {
          reference.handleSolverFailure(t);
          active.set(false);
        }
      }
    }
  }

  static class OptaplannerCallable
      implements Callable<ImmutableList<ImmutableList<Parcel>>> {
    final OptaplannerSolver solver;
    final GlobalStateObject state;

    OptaplannerCallable(OptaplannerSolver solv, GlobalStateObject st) {
      verify(!solv.isSolving(), "Solver is already solving.");
      solver = solv;
      state = st;
    }

    @Nullable
    @Override
    public ImmutableList<ImmutableList<Parcel>> call() throws Exception {
      if (Thread.interrupted()) {
        LOGGER.trace("Stop computation before starting solver");
        return null;
      }
      verify(!solver.isSolving(), "Solver is already solving, this is a bug.");
      return solver.doSolve(state);
    }
  }

  static class SimulatedTimeSupplier
      implements StochasticSupplier<Solver>, Serializable {
    private static final long serialVersionUID = -6583451581964069388L;
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
      return "OptaPlannerST-" + builder.getName();
    }
  }

  static class RealtimeSupplier
      implements Serializable, StochasticSupplier<RealtimeSolver> {
    private static final long serialVersionUID = 4644145971526200115L;
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

    static final double MAX_SPEED_DIFF = .001;
    static final double SIXTY_SEC_IN_NS = 60000000000d;
    static final double TEN_SEC_IN_NS = 10000000000d;

    final OptaplannerSolver solver;
    Builder builder;

    Validator(Builder b, long seed) {
      solver = new OptaplannerSolver(b, seed);
      builder = b;
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
        throws InterruptedException {

      if (builder
        .getObjectiveFunction() instanceof Gendreau06ObjectiveFunction) {
        final Gendreau06ObjectiveFunction objFunc =
          (Gendreau06ObjectiveFunction) builder.getObjectiveFunction();

        checkState(Math.abs(state.getVehicles().get(0).getDto().getSpeed()
          - objFunc.getVehicleSpeed()) < MAX_SPEED_DIFF,
          "Speed of vehicle (%s) does not correspond with speed in objective"
            + " function (%s).",
          state.getVehicles().get(0).getDto().getSpeed(),
          objFunc.getVehicleSpeed());
      }

      final ImmutableList<ImmutableList<Parcel>> schedule = solver.solve(state);

      SolverValidator.validateOutputs(schedule, state);

      System.out.println(state);
      System.out.println("new schedule");
      System.out.println(Joiner.on("\n").join(schedule));

      final StatisticsDTO stats = Solvers.computeStats(state, schedule);

      // convert cost to nanosecond precision
      final double cost = builder.getObjectiveFunction().computeCost(stats)
        * SIXTY_SEC_IN_NS;

      final ScoreCalculator sc = solver.scoreCalculator;

      sc.resetWorkingSolution(solver.lastSolution);

      System.out.println(" === RinSim ===");
      System.out.println(
        builder.getObjectiveFunction().printHumanReadableFormat(stats));
      System.out.println(" === Optaplanner ===");
      System.out
        .println("Travel time: " + sc.getTravelTime() / SIXTY_SEC_IN_NS);
      System.out.println("Tardiness: " + sc.getTardiness() / SIXTY_SEC_IN_NS);
      System.out.println("Overtime: " + sc.getOvertime() / SIXTY_SEC_IN_NS);
      System.out.println(
        "Total: " + sc.calculateScore().getSoftScore() / -SIXTY_SEC_IN_NS);

      // optaplanner has nanosecond precision
      final double optaplannerCost = solver.getSoftScore() * -1d;

      final double difference = Math.abs(cost - optaplannerCost);
      // max 10 nanosecond deviation is allowed
      checkState(difference < TEN_SEC_IN_NS,
        "ObjectiveFunction cost (%s) must be equal to Optaplanner cost (%s),"
          + " the difference is %s.",
        cost, optaplannerCost, difference, builder.getObjectiveFunction());

      return schedule;
    }
  }
}
