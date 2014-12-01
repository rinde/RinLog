/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
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
package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.NegotiatingBidder;
import com.github.rinde.logistics.pdptw.mas.comm.NegotiatingBidder.SelectNegotiatorsHeuristic;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import com.github.rinde.rinsim.central.Central;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.experiment.Experiment;
import com.github.rinde.rinsim.pdptw.experiment.ExperimentResults;
import com.github.rinde.rinsim.pdptw.experiment.MASConfiguration;
import com.github.rinde.rinsim.pdptw.experiment.Experiment.SimulationResult;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.pdptw.gendreau06.GendreauProblemClass;
import com.github.rinde.rinsim.scenario.Scenario.ProblemClass;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

/**
 * 
 * @author Rinde van Lon 
 */
public final class GendreauExperiments {

  private static final String SCENARIOS_PATH = "files/scenarios/gendreau06/";

  private static final int THREADS = 5;
  private static final int REPETITIONS = 5;
  private static final long SEED = 123L;

  private GendreauExperiments() {}

  /**
   * Executes the experiment.
   * @param args The args (ignored).
   */
  public static void main(String[] args) {
    onlineExperiment();
  }

  static void offlineExperiment() {
    System.out.println("offline");

    final List<Gendreau06Scenario> offlineScenarios = Gendreau06Parser.parser()
        .addDirectory(SCENARIOS_PATH)
        .offline()
        .filter(GendreauProblemClass.values())
        .parse();

    final ExperimentResults offlineResults = Experiment
        .build(Gendreau06ObjectiveFunction.instance())
        .addScenarios(offlineScenarios)
        .withRandomSeed(321)
        .repeat(REPETITIONS)
        .withThreads(THREADS)
        .addConfiguration(
            Central.solverConfiguration(
                MultiVehicleHeuristicSolver.supplier(6000, 20000000),
                "-Offline"))
        .addConfiguration(
            Central.solverConfiguration(
                MultiVehicleHeuristicSolver.supplier(8000, 200000000),
                "-Offline")).perform();
    writeGendreauResults(offlineResults);
  }

  static void onlineExperiment() {
    System.out.println("online");
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();

    final List<Gendreau06Scenario> onlineScenarios = Gendreau06Parser.parser()
        .allowDiversion()
        .addDirectory(SCENARIOS_PATH)
        // .setNumVehicles(1)
        .filter(GendreauProblemClass.SHORT_LOW_FREQ)
        .parse();

    final Experiment.Builder builder = Experiment.build(objFunc)
        .withRandomSeed(SEED)
        .repeat(REPETITIONS)
        .withThreads(THREADS)
        .addScenarios(onlineScenarios);

    final StochasticSupplier<? extends RoutePlanner> routePlannerSupplier = SolverRoutePlanner
        .supplier(MultiVehicleHeuristicSolver.supplier(200, 50000));

    final StochasticSupplier<? extends Communicator> communicatorSupplier = NegotiatingBidder
        .supplier(objFunc, MultiVehicleHeuristicSolver.supplier(20, 10000),
            MultiVehicleHeuristicSolver.supplier(200, 50000), 2,
            SelectNegotiatorsHeuristic.FIRST_DESTINATION_POSITION);

    builder.addConfiguration(new TruckConfiguration(routePlannerSupplier,
        communicatorSupplier, ImmutableList.of(AuctionCommModel.supplier())));

    final ExperimentResults onlineResults = builder.perform();
    writeGendreauResults(onlineResults);
  }

  static void writeGendreauResults(ExperimentResults results) {

    final Table<MASConfiguration, ProblemClass, StringBuilder> table = HashBasedTable
        .create();

    checkArgument(results.objectiveFunction instanceof Gendreau06ObjectiveFunction);
    final Gendreau06ObjectiveFunction obj = (Gendreau06ObjectiveFunction) results.objectiveFunction;

    for (final SimulationResult r : results.results) {
      final MASConfiguration config = r.masConfiguration;
      final ProblemClass pc = r.scenario.getProblemClass();

      if (!table.contains(config, pc)) {
        table
            .put(
                config,
                pc,
                new StringBuilder(
                    "seed,instance,duration,frequency,cost,tardiness,travelTime,overTime,computationTime\n"));
      }
      final StringBuilder sb = table.get(config, pc);

      final GendreauProblemClass gpc = (GendreauProblemClass) pc;
      /* seed */
      sb.append(r.seed).append(",")
          /* instance */
          .append(r.scenario.getProblemInstanceId()).append(",")
          /* duration */
          .append(gpc.duration).append(",")
          /* frequency */
          .append(gpc.frequency).append(",")
          /* cost */
          .append(obj.computeCost(r.stats)).append(',')
          /* tardiness */
          .append(obj.tardiness(r.stats)).append(',')
          /* travelTime */
          .append(obj.travelTime(r.stats)).append(',')
          /* overTime */
          .append(obj.overTime(r.stats)).append(',')
          /* computation time */
          .append(r.stats.computationTime).append("\n");
    }

    final Set<Cell<MASConfiguration, ProblemClass, StringBuilder>> set = table
        .cellSet();
    for (final Cell<MASConfiguration, ProblemClass, StringBuilder> cell : set) {
      try {
        final File dir = new File("files/results/gendreau"
            + cell.getColumnKey().getId());
        if (!dir.exists() || !dir.isDirectory()) {
          Files.createParentDirs(dir);
          dir.mkdir();
        }
        final File file = new File(dir.getPath() + "/"
            + cell.getRowKey().toString() + "_" + results.masterSeed
            + cell.getColumnKey().getId() + ".txt");
        if (file.exists()) {
          file.delete();
        }

        Files.write(cell.getValue().toString(), file, Charsets.UTF_8);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
