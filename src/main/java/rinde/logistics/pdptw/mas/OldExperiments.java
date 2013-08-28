/**
 * 
 */
package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.logistics.pdptw.mas.GSimulation.Configurator;
import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.BlackboardCommModel;
import rinde.logistics.pdptw.mas.comm.BlackboardUser;
import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.RandomBidder;
import rinde.logistics.pdptw.mas.comm.SolverBidder;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.logistics.pdptw.solver.HeuristicSolver;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.central.arrays.SingleVehicleSolverAdapter;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.DynamicPDPTWScenario.ProblemClass;
import rinde.sim.pdptw.common.StatsTracker.StatisticsDTO;
import rinde.sim.pdptw.experiments.DefaultMASConfiguration;
import rinde.sim.pdptw.experiments.ExperimentUtil;
import rinde.sim.pdptw.experiments.Experiments;
import rinde.sim.pdptw.experiments.Experiments.ExperimentResults;
import rinde.sim.pdptw.experiments.Experiments.Result;
import rinde.sim.pdptw.experiments.MASConfiguration;
import rinde.sim.pdptw.experiments.MASConfigurator;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenarios;
import rinde.sim.pdptw.gendreau06.GendreauProblemClass;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class OldExperiments {

  public static void main(String[] args) {

    // fullExperiment(new HeuristicAuctioneerHeuristicSolver(), 123, 1,
    // GendreauProblemClass.SHORT_LOW_FREQ);
    // fullAgentExperiment(new RandomBB(), 123, 10,
    // GendreauProblemClass.SHORT_LOW_FREQ);

    // experiments(123, 1, new RandomAuctioneerHeuristicSolver());

    final ExperimentResults results = Experiments
        .experiment(new Gendreau06ObjectiveFunction())
        .addScenarioProvider(new Gendreau06Scenarios(
            "files/scenarios/gendreau06/", GendreauProblemClass.SHORT_LOW_FREQ))
        .addSolution(new RandomBB()) //
        .addSolution(new RandomAuctioneerHeuristicSolver()) //
        .withRandomSeed(123) //
        .repeat(1) //
        .perform();

    writeGendreauResults(results);

    System.out.println(results);

  }

  static void agentExperiments(long masterSeed, int repetitions,
      Configurator... configurators) {
    for (final Configurator c : configurators) {
      fullAgentExperiment(c, masterSeed, repetitions, GendreauProblemClass.values());
    }
  }

  static void fullAgentExperiment(Configurator c, long masterSeed,
      int repetitions, GendreauProblemClass... claz) {
    for (final GendreauProblemClass ec : claz) {
      fullAgentExperiment(c, masterSeed, repetitions, ec);
    }
  }

  static void writeGendreauResults(ExperimentResults results) {

    final Table<MASConfigurator, ProblemClass, StringBuilder> table = LinkedHashBasedTable
        .create();

    checkArgument(results.objectiveFunction instanceof Gendreau06ObjectiveFunction);
    final Gendreau06ObjectiveFunction obj = (Gendreau06ObjectiveFunction) results.objectiveFunction;

    for (final Result r : results.results) {
      final MASConfigurator config = r.masConfigurator;
      final ProblemClass pc = r.scenario.getProblemClass();

      if (!table.contains(config, pc)) {
        table
            .put(config, pc, new StringBuilder(
                "seed,instance,duration,frequency,cost,tardiness,travelTime,overTime\n"));
      }
      final StringBuilder sb = table.get(config, pc);

      final GendreauProblemClass gpc = (GendreauProblemClass) pc;
      sb.append(r.seed).append(",")/* seed */
      .append(r.scenario.getProblemInstanceId()).append(",")/* instance */
      .append(gpc.duration).append(",") /* duration */
      .append(gpc.frequency).append(",")/* frequency */
      .append(obj.computeCost(r.stats)).append(',')/* cost */
      .append(obj.tardiness(r.stats)).append(',')/* tardiness */
      .append(obj.travelTime(r.stats)).append(',')/* travelTime */
      .append(obj.overTime(r.stats))/* overTime */
      .append("\n");

    }

    final Set<Cell<MASConfigurator, ProblemClass, StringBuilder>> set = table
        .cellSet();

    for (final Cell<MASConfigurator, ProblemClass, StringBuilder> cell : set) {

      try {
        final File dir = new File("files/results/gendreau"
            + cell.getColumnKey().getId());
        if (!dir.exists() || !dir.isDirectory()) {
          Files.createParentDirs(dir);
          dir.mkdir();
        }
        final File file = new File(dir.getPath() + "/"
            + cell.getRowKey().getClass().getSimpleName() + "_"
            + results.masterSeed + cell.getColumnKey().getId() + ".txt");
        if (file.exists()) {
          file.delete();
        }

        Files.write(cell.getValue().toString(), file, Charsets.UTF_8);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }

  }

  static void fullAgentExperiment(Configurator c, long masterSeed,
      int repetitions, GendreauProblemClass claz) {
    final List<String> files = ExperimentUtil
        .getFilesFromDir("files/scenarios/gendreau06/", claz.fileId);
    final RandomGenerator rng = new MersenneTwister(masterSeed);
    final long[] seeds = new long[repetitions];
    for (int i = 0; i < repetitions; i++) {
      seeds[i] = rng.nextLong();
    }
    final StringBuilder sb = new StringBuilder();
    sb.append("seed,instance,duration,frequency,cost,tardiness,travelTime,overTime\n");
    for (final String file : files) {
      for (int i = 0; i < repetitions; i++) {
        if (c instanceof RandomSeed) {
          ((RandomSeed) c).setSeed(seeds[i]);
        }
        final StatisticsDTO stats = GSimulation
            .simulate(file, claz.vehicles, c, false);
        final Gendreau06ObjectiveFunction obj = new Gendreau06ObjectiveFunction();
        checkState(obj.isValidResult(stats));

        // example: req_rapide_1_240_24
        final String[] name = new File(file).getName().split("_");

        final int instanceNumber = Integer.parseInt(name[2]);
        final int duration = Integer.parseInt(name[3]);
        final int frequency = Integer.parseInt(name[4]);
        checkArgument(duration == claz.duration);
        checkArgument(frequency == claz.frequency);

        sb.append(seeds[i]).append(",")/* seed */
        .append(instanceNumber).append(",")/* instance */
        .append(duration).append(",") /* duration */
        .append(frequency).append(",")/* frequency */
        .append(obj.computeCost(stats)).append(',')/* cost */
        .append(obj.tardiness(stats)).append(',')/* tardiness */
        .append(obj.travelTime(stats)).append(',')/* travelTime */
        .append(obj.overTime(stats))/* overTime */
        .append("\n");
      }
    }
    try {
      final File dir = new File("files/results/gendreau" + claz.fileId);
      if (!dir.exists() || !dir.isDirectory()) {
        Files.createParentDirs(dir);
        dir.mkdir();
      }
      final File file = new File(dir.getPath() + "/"
          + c.getClass().getSimpleName() + "_" + masterSeed + claz.fileId
          + ".txt");
      if (file.exists()) {
        file.delete();
      }

      Files.write(sb.toString(), file, Charsets.UTF_8);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  interface RandomSeed {
    void setSeed(long seed);
  }

  public static Solver wrapSafe(SingleVehicleArraysSolver solver,
      Unit<Duration> timeUnit) {
    return SolverValidator.wrap(new SingleVehicleSolverAdapter(
        ArraysSolverValidator.wrap(solver), timeUnit));
  }

  public static class HeuristicAuctioneerHeuristicSolver implements
      Configurator, RandomSeed {

    private final RandomGenerator rng;

    public HeuristicAuctioneerHeuristicSolver() {
      rng = new MersenneTwister(0L);
    }

    public boolean create(Simulator sim, AddVehicleEvent event) {
      final Communicator c = new SolverBidder(
          ArraysSolverValidator.wrap(new HeuristicSolver(new MersenneTwister(
              rng.nextLong()))));

      sim.register(c);

      final RoutePlanner r = new SolverRoutePlanner(
          wrapSafe(new HeuristicSolver(new MersenneTwister(rng.nextLong())), SI.SECOND));

      return sim.register(new Truck(event.vehicleDTO, r, c));
    }

    public void setSeed(long seed) {
      rng.setSeed(seed);
    }

    public Model<?>[] createModels() {
      return new Model<?>[] { new AuctionCommModel() };
    }
  }

  // random auctioneer with random solver route planner
  public static class RandomAuctioneerHeuristicSolver implements
      MASConfigurator {

    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);
      return new DefaultMASConfiguration() {
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new RandomBidder(rng.nextLong());
              sim.register(c);
              return sim
                  .register(new Truck(event.vehicleDTO,
                      new SolverRoutePlanner(SolverValidator
                          .wrap(new SingleVehicleSolverAdapter(
                              ArraysSolverValidator.wrap(new HeuristicSolver(
                                  new MersenneTwister(rng.nextLong()))),
                              SI.SECOND))), c));
            }
          };
        }

        @Override
        public ImmutableList<? extends Model<?>> getModels() {
          return ImmutableList.of(new AuctionCommModel());
        }
      };
    }
  }

  /**
   * <ul>
   * <li>RandomRoutePlanner</li>
   * <li>AuctionCommModel</li>
   * <li>RandomBidder</li>
   * </ul>
   * 
   * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
   */
  public static class RandomRandom implements Configurator, RandomSeed {
    protected final RandomGenerator rng;

    public RandomRandom() {
      rng = new MersenneTwister(0L);
    }

    public boolean create(Simulator sim, AddVehicleEvent event) {
      final Communicator c = new RandomBidder(rng.nextLong());
      sim.register(c);
      return sim.register(new Truck(event.vehicleDTO, new RandomRoutePlanner(
          rng.nextLong()), c));
    }

    public Model<?>[] createModels() {
      return new Model<?>[] { new AuctionCommModel() };
    }

    public void setSeed(long seed) {
      rng.setSeed(seed);
    }

  }

  public static class RandomBB implements MASConfigurator {
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);

      return new DefaultMASConfiguration() {
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new BlackboardUser();
              sim.register(c);
              return sim.register(new Truck(event.vehicleDTO,
                  new RandomRoutePlanner(rng.nextLong()), c));
            }
          };
        }

        @Override
        public ImmutableList<? extends Model<?>> getModels() {
          return ImmutableList.of(new BlackboardCommModel());
        }
      };
    }
  }

}
