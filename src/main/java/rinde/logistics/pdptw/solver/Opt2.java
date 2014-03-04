package rinde.logistics.pdptw.solver;

import rinde.opt.localsearch.Swaps;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.GlobalStateObject.VehicleStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.collect.ImmutableList;

public class Opt2 implements Solver {

  final Solver delegate;
  final ParcelRouteEvaluator evaluator;

  public Opt2(Solver deleg, ObjectiveFunction objFunc) {
    delegate = deleg;
    evaluator = new ParcelRouteEvaluator(objFunc);
  }

  // cheapest insertion with random restarts?

  // depth first search
  // uses the first improving of k-interchanges

  // breadth first search
  // finds best of possible k-interchanges (swaps) and uses that for next
  // iteration

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    final ImmutableList<ImmutableList<ParcelDTO>> schedule = delegate
        .solve(state);
    final ImmutableList.Builder<Integer> indexBuilder = ImmutableList.builder();
    for (final VehicleStateObject vso : state.vehicles) {
      indexBuilder.add(vso.destination == null ? 0 : 1);
    }

    final ImmutableList<ImmutableList<ParcelDTO>> routes = Swaps.opt2(schedule,
        indexBuilder.build(), state, evaluator);

    // System.out.println(routes);
    // System.out.println(state);

    return routes;
  }

  public static SupplierRng<Solver> supplier(
      final SupplierRng<Solver> delegate, final ObjectiveFunction objFunc) {
    return new DefaultSupplierRng<Solver>() {
      @Override
      public Solver get(long seed) {
        return new Opt2(delegate.get(seed), objFunc);
      }
    };
  }
}
