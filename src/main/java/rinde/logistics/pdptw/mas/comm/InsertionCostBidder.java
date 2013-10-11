/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.List;
import java.util.Set;

import rinde.logistics.pdptw.mas.Truck;
import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SVSolverHandle;
import rinde.sim.pdptw.central.Solvers.StateContext;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class InsertionCostBidder extends AbstractBidder implements
    SimulatorUser {

  private final ObjectiveFunction objectiveFunction;
  private Optional<SVSolverHandle> solverHandle;
  private Optional<SimulatorAPI> simulator;

  /**
   * @param sol
   */
  public InsertionCostBidder(ObjectiveFunction objFunc) {
    objectiveFunction = objFunc;
    solverHandle = Optional.absent();
    simulator = Optional.absent();
  }

  @Override
  public double getBidFor(DefaultParcel p, long time) {
    final Set<DefaultParcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(p);
    final ImmutableList<ParcelDTO> dtoRoute = Solvers
        .toDtoList(((Truck) vehicle.get()).getRoute());
    final StateContext context = solverHandle.get().convert(parcels, null);
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
        context.state, ImmutableList.of(dtoRoute)));

    // if there is already a current destination we have to service that first
    // so we don't have to consider insertions before the first
    final int startIndex = roadModel.get().getDestination(vehicle.get()) == null ? 0
        : 1;

    final boolean isCommitted = context.state.vehicles.get(0).destination != null;
    final List<ImmutableList<ParcelDTO>> routes = plusTwoInsertions(dtoRoute,
        p.dto, startIndex);

    double lowestCost = Double.POSITIVE_INFINITY;
    for (int i = 0; i < routes.size(); i++) {
      if (!(i == 0 && isCommitted)) {
        final ImmutableList<ParcelDTO> r = routes.get(i);
        final double cost = objectiveFunction.computeCost(Solvers.computeStats(
            context.state, ImmutableList.of(r)));
        if (cost < lowestCost) {
          lowestCost = cost;
        }
      }
    }
    return lowestCost - baseline;
  }

  static <T> ImmutableList<ImmutableList<T>> plusOneInsertions(
      ImmutableList<T> list, T item) {
    return plusOneInsertions(list, item, 0);
  }

  static <T> ImmutableList<ImmutableList<T>> plusOneInsertions(
      ImmutableList<T> list, T item, int startIndex) {
    final ImmutableList.Builder<ImmutableList<T>> inserted = ImmutableList
        .builder();
    for (int i = startIndex; i < list.size() + 1; i++) {
      final ImmutableList<T> firstHalf = list.subList(0, i);
      final ImmutableList<T> secondHalf = list.subList(i, list.size());
      inserted.add(ImmutableList.<T> builder().addAll(firstHalf).add(item)
          .addAll(secondHalf).build());
    }
    return inserted.build();
  }

  static <T> ImmutableList<ImmutableList<T>> plusTwoInsertions(
      ImmutableList<T> list, T item, int startIndex) {
    final ImmutableList<ImmutableList<T>> plusOneInsertions = plusOneInsertions(
        list, item, startIndex);
    final ImmutableList.Builder<ImmutableList<T>> plusTwoInsertions = ImmutableList
        .builder();
    for (final ImmutableList<T> l : plusOneInsertions) {
      plusTwoInsertions.addAll(plusOneInsertions(l, item, l.indexOf(item) + 1));
    }
    return plusTwoInsertions.build();
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    simulator = Optional.of(api);
    initSolver();
  }

  @Override
  protected void afterInit() {
    initSolver();
  }

  private void initSolver() {
    if (simulator.isPresent() && roadModel.isPresent()
        && !solverHandle.isPresent()) {
      solverHandle = Optional.of(Solvers.singleVehicleSolver(null,
          roadModel.get(), pdpModel.get(), simulator.get(), vehicle.get()));
    }
  }
}
