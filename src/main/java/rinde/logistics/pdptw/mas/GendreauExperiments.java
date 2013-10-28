package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.NegotiatingBidder;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.common.DynamicPDPTWScenario.ProblemClass;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenarios;
import rinde.sim.pdptw.gendreau06.GendreauProblemClass;

import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

/**
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class GendreauExperiments {

  private static final String SCENARIOS_PATH = "files/scenarios/gendreau06/";

  private static final int THREADS = 1;
  private static final int REPETITIONS = 1;
  private static final long SEED = 123L;

  private GendreauExperiments() {}

  public static void main(String[] args) throws IOException {

    onlineExperiment();
    // offlineExperiment();
  }

  static void offlineExperiment() {
    System.out.println("offline");
    final Gendreau06Scenarios offlineScenarios = new Gendreau06Scenarios(
        SCENARIOS_PATH, false, GendreauProblemClass.values());
    final ExperimentResults offlineResults = Experiment
        .build(new Gendreau06ObjectiveFunction())
        .addScenarioProvider(offlineScenarios)
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
    final ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();

    final Gendreau06Scenarios onlineScenarios = new Gendreau06Scenarios(
        SCENARIOS_PATH, true, GendreauProblemClass.SHORT_LOW_FREQ);
    final ExperimentResults onlineResults = Experiment.build(objFunc)
        .withRandomSeed(SEED).repeat(REPETITIONS).withThreads(THREADS)
        .addScenarioProvider(onlineScenarios)

        // .showGui()

        /*
         * AUCTIONS
         */

        // .addConfiguration(
        // new TruckConfiguration(RandomRoutePlanner.supplier(), RandomBidder
        // .supplier(), ImmutableList.of(AuctionCommModel.supplier())))
        //
        // .addConfiguration(
        // new TruckConfiguration(RandomRoutePlanner.supplier(),
        // InsertionCostBidder.supplier(objFunc), ImmutableList
        // .of(AuctionCommModel.supplier())))

        // .addConfiguration(
        // new TruckConfiguration(RandomRoutePlanner.supplier(), SolverBidder
        // .supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(50, 100)),
        // ImmutableList.of(AuctionCommModel.supplier())))

        //
        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(50, 100)),
        // SolverBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(50, 100)),
        // ImmutableList.of(AuctionCommModel.supplier())))

        .addConfiguration(
            new TruckConfiguration(SolverRoutePlanner
                .supplier(MultiVehicleHeuristicSolver.supplier(50, 100)),
                NegotiatingBidder.supplier(objFunc,
                    MultiVehicleHeuristicSolver.supplier(50, 100),
                    MultiVehicleHeuristicSolver.supplier(50, 100)),
                ImmutableList.of(AuctionCommModel.supplier())))

        //

        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(50, 1000)),
        // SolverBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(50, 100)),
        // ImmutableList.of(AuctionCommModel.supplier())))
        //
        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(200, 50000)),
        // SolverBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(50, 100)),
        // ImmutableList.of(AuctionCommModel.supplier())))
        //
        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(200, 50000)),
        // SolverBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(20, 5000)),
        // ImmutableList.of(AuctionCommModel.supplier())))

        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(50, 100)),
        // SolverBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(100, 50)),
        // ImmutableList.of(AuctionCommModel.supplier())))

        /*
         * BLACKBOARD
         */

        // .addConfiguration(
        // new TruckConfiguration(GotoClosestRoutePlanner.supplier(),
        // BlackboardUser.supplier(), ImmutableList.of(BlackboardCommModel
        // .supplier())))
        //
        // .addConfiguration(
        // new TruckConfiguration(RandomRoutePlanner.supplier(),
        // BlackboardUser.supplier(), ImmutableList.of(BlackboardCommModel
        // .supplier())))

        /*
         * CENTRAL
         */

        // .addConfiguration(
        // Central.solverConfiguration(
        // MultiVehicleHeuristicSolver.supplier(500, 10000), "-Online"))

        .perform();

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
