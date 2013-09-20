package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

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
import rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.central.Central.SolverCreator;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.MultiVehicleSolverAdapter;
import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.central.arrays.SingleVehicleSolverAdapter;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.DynamicPDPTWScenario.ProblemClass;
import rinde.sim.pdptw.common.StatisticsDTO;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.experiment.MASConfigurator;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenarios;
import rinde.sim.pdptw.gendreau06.GendreauProblemClass;

import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public final class GendreauExperiments {

  private static final int THREADS = 2;
  private static final int REPETITIONS = 1;

  private GendreauExperiments() {}

  public static void main(String[] args) throws IOException {

    // final Gendreau06Scenario scenario = Gendreau06Parser.parse(
    // "files/scenarios/gendreau06/req_rapide_1_240_24", 10);

    final Gendreau06Scenario scenario = (Gendreau06Scenario) (new Gendreau06Scenarios(
        "files/scenarios/gendreau06/", false,
        GendreauProblemClass.LONG_LOW_FREQ)).provide().get(0);

    final MASConfiguration configuration = Central.solverConfigurator(
        new HeuristicSolverCreator(1, 20000), "-Offline").configure(
        -3577649692547979120L);

    // Central.solverConfigurator(
    // new RandomSolverCreator()).configure(6035094637740532013L);
    final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    final StatisticsDTO stats = Experiment.performSingleRun(scenario,
        configuration, objFunc, false);
    System.out.println(objFunc.printHumanReadableFormat(stats));

    // onlineExperiment();
    // offlineExperiment();
  }

  static void offlineExperiment() {
    System.out.println("offline");
    final Gendreau06Scenarios offlineScenarios = new Gendreau06Scenarios(
        "files/scenarios/gendreau06/", false, GendreauProblemClass.values());
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
        "files/scenarios/gendreau06/", true, GendreauProblemClass.values());
    final ExperimentResults onlineResults = Experiment
        .build(new Gendreau06ObjectiveFunction())
        .withRandomSeed(123)
        .repeat(REPETITIONS)
        .withThreads(THREADS)
        .addScenarioProvider(onlineScenarios)
        // .addConfigurator(new RandomBB())
        // .addConfigurator(new RandomAuctioneerHeuristicSolver())
        // .addConfigurator(new RandomRandom())
        // .addConfigurator(new HeuristicAuctioneerHeuristicSolver())
        // .addConfigurator(
        // Central.solverConfigurator(new RandomSolverCreator(), "-Online"))
        .addConfigurator(
            Central.solverConfigurator(new HeuristicSolverCreator(200, 5000),
                "-Online"))
        .addConfigurator(
            Central.solverConfigurator(new HeuristicSolverCreator(400, 10000),
                "-Online"))

        .perform();

    writeGendreauResults(onlineResults);
  }

  static void writeGendreauResults(ExperimentResults results) {

    final Table<MASConfigurator, ProblemClass, StringBuilder> table = HashBasedTable
        .create();

    checkArgument(results.objectiveFunction instanceof Gendreau06ObjectiveFunction);
    final Gendreau06ObjectiveFunction obj = (Gendreau06ObjectiveFunction) results.objectiveFunction;

    for (final SimulationResult r : results.results) {
      final MASConfigurator config = r.masConfigurator;
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
      sb.append(r.seed).append(",")/* seed */
      .append(r.scenario.getProblemInstanceId()).append(",")/* instance */
      .append(gpc.duration).append(",") /* duration */
      .append(gpc.frequency).append(",")/* frequency */
      .append(obj.computeCost(r.stats)).append(',')/* cost */
      .append(obj.tardiness(r.stats)).append(',')/* tardiness */
      .append(obj.travelTime(r.stats)).append(',')/* travelTime */
      .append(obj.overTime(r.stats)).append(',')/* overTime */
      .append(r.stats.computationTime) /* computation time */
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

  public static Solver wrapSafe(SingleVehicleArraysSolver solver,
      Unit<Duration> timeUnit) {
    return SolverValidator.wrap(new SingleVehicleSolverAdapter(
        ArraysSolverValidator.wrap(solver), timeUnit));
  }

  public static class HeuristicAuctioneerHeuristicSolver implements
      MASConfigurator {
    @Override
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);
      return new DefaultMASConfiguration() {
        @Override
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            @Override
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new SolverBidder(
                  ArraysSolverValidator.wrap(new HeuristicSolver(
                      new MersenneTwister(rng.nextLong()))));
              final RoutePlanner r = new SolverRoutePlanner(wrapSafe(
                  new HeuristicSolver(new MersenneTwister(rng.nextLong())),
                  SI.SECOND));
              return sim.register(new Truck(event.vehicleDTO, r, c));
            }
          };
        }

        @Override
        public ImmutableList<? extends Model<?>> getModels() {
          return ImmutableList.of(new AuctionCommModel());
        }
      };
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  // random auctioneer with random solver route planner
  public static class RandomAuctioneerHeuristicSolver implements
      MASConfigurator {

    @Override
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);
      return new DefaultMASConfiguration() {
        @Override
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            @Override
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new RandomBidder(rng.nextLong());
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

    @Override
    public String toString() {
      return getClass().getSimpleName();
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
  public static class RandomRandom implements MASConfigurator {
    @Override
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);

      return new DefaultMASConfiguration() {
        @Override
        public ImmutableList<? extends Model<?>> getModels() {
          return ImmutableList.of(new AuctionCommModel());
        }

        @Override
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            @Override
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new RandomBidder(rng.nextLong());
              return sim.register(new Truck(event.vehicleDTO,
                  new RandomRoutePlanner(rng.nextLong()), c));
            }
          };
        }
      };
    }

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  public static class HeuristicSolverCreator implements SolverCreator {

    private final int listLength;
    private final int maxIterations;

    public HeuristicSolverCreator(int listLength, int maxIterations) {
      this.listLength = listLength;
      this.maxIterations = maxIterations;
    }

    @Override
    public Solver create(long seed) {
      return SolverValidator
          .wrap(new MultiVehicleSolverAdapter(ArraysSolverValidator
              .wrap(new MultiVehicleHeuristicSolver(new MersenneTwister(seed),
                  listLength, maxIterations)), SI.SECOND));
    }

    @Override
    public String toString() {
      return new StringBuilder("Heuristic-").append(listLength).append("-")
          .append(maxIterations).toString();
    }
  }

  public static class RandomSolverCreator implements SolverCreator {

    public RandomSolverCreator() {}

    @Override
    public Solver create(long seed) {
      return new MultiVehicleSolverAdapter(
          ArraysSolverValidator.wrap(new RandomMVArraysSolver(
              new MersenneTwister(seed))), SI.SECOND);
    }

    @Override
    public String toString() {
      return "Random";
    }
  }

  public static class RandomBB implements MASConfigurator {
    @Override
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);

      return new DefaultMASConfiguration() {
        @Override
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            @Override
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new BlackboardUser();
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

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }
}
