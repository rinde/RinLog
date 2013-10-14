package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;

import rinde.logistics.pdptw.mas.comm.InsertionCostBidder;
import rinde.logistics.pdptw.mas.comm.RandomBidder;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.logistics.pdptw.solver.HeuristicSolverCreator;
import rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.MultiVehicleSolverAdapter;
import rinde.sim.pdptw.common.DynamicPDPTWScenario.ProblemClass;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenarios;
import rinde.sim.pdptw.gendreau06.GendreauProblemClass;
import rinde.sim.util.SupplierRng;

import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

/**
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class GendreauExperiments {

  private static final String SCENARIOS_PATH = "files/scenarios/gendreau06/";

  private static final int THREADS = 16;
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
        .addConfigurator(
            Central.solverConfigurator(new HeuristicSolverCreator(6000,
                20000000), "-Offline"))
        .addConfigurator(
            Central.solverConfigurator(new HeuristicSolverCreator(8000,
                200000000), "-Offline")).perform();
    writeGendreauResults(offlineResults);
  }

  static void onlineExperiment() {
    System.out.println("online");
    final Gendreau06Scenarios onlineScenarios = new Gendreau06Scenarios(
        SCENARIOS_PATH, true, GendreauProblemClass.values());
    final ExperimentResults onlineResults = Experiment
        .build(new Gendreau06ObjectiveFunction())
        .withRandomSeed(SEED)
        .repeat(REPETITIONS)
        .withThreads(THREADS)
        .addScenarioProvider(onlineScenarios)

        // .showGui()
        // .addConfigurator(new RandomBB())
        // .addConfigurator(new RandomAuctioneerHeuristicSolver())
        // .addConfigurator(new RandomRandom())

        .addConfigurator(
            new BlackboardMASSupplier(new SolverRoutePlannerSupplier(50, 100)))

        .addConfigurator(
            new BlackboardMASSupplier(new RandomRoutePlannerSupplier()))

        .addConfigurator(
            new AuctionMASSupplier(new SolverRoutePlannerSupplier(50, 100),
                new InsertionCostBidderSupplier()))

        .addConfigurator(
            new AuctionMASSupplier(new SolverRoutePlannerSupplier(50, 100),
                new RandomBidderSupplier()))

        // .addConfigurator(new InsertionCostAuctioneerHeuristicSolver(500,
        // 10000))
        // .addConfigurator(
        // new InsertionCostAuctioneerHeuristicSolver(500, 100000))
        // .addConfigurator(
        // Central.solverConfigurator(new RandomSolverCreator(), "-Online"))
        // .addConfigurator(
        // Central.solverConfigurator(
        // new HeuristicSolverCreator(500, 1000000), "-Online"))
        // .addConfigurator(
        // Central.solverConfigurator(new HeuristicSolverCreator(400, 10000),
        // "-Online"))

        .perform();

    writeGendreauResults(onlineResults);
  }

  static void writeGendreauResults(ExperimentResults results) {

    final Table<SupplierRng<MASConfiguration>, ProblemClass, StringBuilder> table = HashBasedTable
        .create();

    checkArgument(results.objectiveFunction instanceof Gendreau06ObjectiveFunction);
    final Gendreau06ObjectiveFunction obj = (Gendreau06ObjectiveFunction) results.objectiveFunction;

    for (final SimulationResult r : results.results) {
      final SupplierRng<MASConfiguration> config = r.masConfigurator;
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

    final Set<Cell<SupplierRng<MASConfiguration>, ProblemClass, StringBuilder>> set = table
        .cellSet();

    for (final Cell<SupplierRng<MASConfiguration>, ProblemClass, StringBuilder> cell : set) {

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

  static SolverRoutePlanner createSolverRoutePlanner(long seed, int listLength,
      int maxNrOfImprovements) {
    return new SolverRoutePlanner(
        SolverValidator.wrap(new MultiVehicleSolverAdapter(
            ArraysSolverValidator.wrap(new MultiVehicleHeuristicSolver(
                new MersenneTwister(seed), listLength, maxNrOfImprovements)),
            SI.SECOND)));
  }

  static class InsertionCostBidderSupplier implements
      SupplierRng<InsertionCostBidder> {
    @Override
    public InsertionCostBidder get(long seed) {
      return new InsertionCostBidder(new Gendreau06ObjectiveFunction());
    }

    @Override
    public String toString() {
      return "Insertion-Cost-Bidder";
    }
  }

  static class RandomBidderSupplier implements SupplierRng<RandomBidder> {
    @Override
    public RandomBidder get(long seed) {
      return new RandomBidder(seed);
    }

    @Override
    public String toString() {
      return "Random-Bidder";
    }
  }

  static class SolverRoutePlannerSupplier implements
      SupplierRng<SolverRoutePlanner> {
    final int listLength;
    final int maxNrOfImprovements;

    SolverRoutePlannerSupplier(int ll, int maxImprovements) {
      listLength = ll;
      maxNrOfImprovements = maxImprovements;
    }

    @Override
    public SolverRoutePlanner get(long seed) {
      return createSolverRoutePlanner(seed, listLength, maxNrOfImprovements);
    }

    @Override
    public String toString() {
      return "Solver-Route-Planner-" + listLength + "-" + maxNrOfImprovements;
    }
  }

  static class RandomRoutePlannerSupplier implements
      SupplierRng<RandomRoutePlanner> {
    @Override
    public RandomRoutePlanner get(long seed) {
      return new RandomRoutePlanner(seed);
    }

    @Override
    public String toString() {
      return "Random-Route-Planner";
    }
  }
}
