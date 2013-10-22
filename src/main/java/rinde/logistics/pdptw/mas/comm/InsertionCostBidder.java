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
import rinde.sim.pdptw.central.Solvers.SimulationConverter;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.central.Solvers.StateContext;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A bidder implementation that creates bids based on an 'insertion cost'
 * heuristic. This heuristic simply generates all possible insertions for the
 * new parcel in the current schedule. For each parcel the cost is calculated
 * and the cheapest insertion is selected. The bid value returned by
 * {@link #getBidFor(DefaultParcel, long)} is
 * <code>cheapestInsertion - baseline</code>, where <code>baseline</code> is the
 * cost of the current plan without the new parcel.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class InsertionCostBidder extends AbstractBidder implements
    SimulatorUser {

  private final ObjectiveFunction objectiveFunction;
  private Optional<SimulationConverter> converter;
  private Optional<SimulatorAPI> simulator;

  /**
   * @param objFunc The objective function used to calculate the bid value.
   */
  public InsertionCostBidder(ObjectiveFunction objFunc) {
    objectiveFunction = objFunc;
    converter = Optional.absent();
    simulator = Optional.absent();
  }

  @Override
  public double getBidFor(DefaultParcel p, long time) {
    final Set<DefaultParcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(p);
    final ImmutableList<ParcelDTO> dtoRoute = Solvers
        .toDtoList(((Truck) vehicle.get()).getRoute());
    final StateContext context = converter.get().convert(
        SolveArgs.create().useParcels(parcels).noCurrentRoutes());
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
        context.state, ImmutableList.of(dtoRoute)));

    // if there is already a current destination we have to service that first
    // so we don't have to consider insertions before the first
    final int startIndex = roadModel.get().getDestination(vehicle.get()) == null ? 0
        : 1;

    final boolean isCommitted = context.state.vehicles.get(0).destination != null;
    final List<ImmutableList<ParcelDTO>> routes = plusTwoInsertions(dtoRoute,
        p.dto, startIndex);

    double cheapestInsertion = Double.POSITIVE_INFINITY;
    for (int i = 0; i < routes.size(); i++) {
      if (!(i == 0 && isCommitted)) {
        final ImmutableList<ParcelDTO> r = routes.get(i);
        final double cost = objectiveFunction.computeCost(Solvers.computeStats(
            context.state, ImmutableList.of(r)));
        if (cost < cheapestInsertion) {
          cheapestInsertion = cost;
        }
      }
    }
    return cheapestInsertion - baseline;
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
        && !converter.isPresent()) {
      converter = Optional.of(Solvers.converterBuilder().with(roadModel.get())
          .with(pdpModel.get()).with(simulator.get()).with(vehicle.get())
          .buildSingle());
    }
  }

  /**
   * @param objFunc The objective function used to calculate the bid value.
   * @return A {@link SupplierRng} that supplies {@link InsertionCostBidder}
   *         instances.
   */
  public static SupplierRng<InsertionCostBidder> supplier(
      final ObjectiveFunction objFunc) {
    return new DefaultSupplierRng<InsertionCostBidder>() {
      @Override
      public InsertionCostBidder get(long seed) {
        return new InsertionCostBidder(objFunc);
      }
    };
  }
}
