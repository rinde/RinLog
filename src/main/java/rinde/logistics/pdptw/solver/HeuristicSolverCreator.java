package rinde.logistics.pdptw.solver;

import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;

import rinde.sim.pdptw.central.Central.SolverCreator;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.MultiVehicleSolverAdapter;

/**
 * A {@link SolverCreator} that creates {@link MultiVehicleHeuristicSolver}s.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class HeuristicSolverCreator implements SolverCreator {

  private final int listLength;
  private final int maxNrOfNonImprovements;
  private final boolean debug;
  private final boolean strictMode;

  /**
   * Create a new instance with the specified list length and maximum number of
   * non-improvements.
   * @param pListLength see {@link MultiVehicleHeuristicSolver}.
   * @param pMaxNrOfNonImprovements see {@link MultiVehicleHeuristicSolver}.
   */
  public HeuristicSolverCreator(int pListLength, int pMaxNrOfNonImprovements) {
    this(pListLength, pMaxNrOfNonImprovements, false, false);
  }

  public HeuristicSolverCreator(int pListLength, int pMaxNrOfNonImprovements,
      boolean pDebug, boolean pStrictMode) {
    listLength = pListLength;
    maxNrOfNonImprovements = pMaxNrOfNonImprovements;
    debug = pDebug;
    strictMode = pStrictMode;
  }

  @Override
  public Solver create(long seed) {
    return SolverValidator.wrap(new MultiVehicleSolverAdapter(
        ArraysSolverValidator.wrap(new MultiVehicleHeuristicSolver(
            new MersenneTwister(seed), listLength, maxNrOfNonImprovements,
            debug, strictMode)), SI.SECOND));
  }

  @Override
  public String toString() {
    return new StringBuilder("Heuristic-").append(listLength).append("-")
        .append(maxNrOfNonImprovements).toString();
  }
}
