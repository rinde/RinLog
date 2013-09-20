/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nullable;

import rinde.logistics.pdptw.mas.Truck;
import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.common.DefaultParcel;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class SolverBidder extends AbstractBidder {

  private final SingleVehicleArraysSolver solver;

  public SolverBidder(SingleVehicleArraysSolver sol) {
    solver = sol;
  }

  // TODO figure out what happens when the vehicle is moving towards a parcel
  // at the moment this method is called?
  @Override
  public double getBidFor(DefaultParcel p, long time) {

    ((Truck) vehicle.get()).getRoute();

    // TODO optimize baseline, baseline can be remembered from last assigned
    // parcel

    // TODO coordinate with route planner if it uses the same solver?
    // or create cached solver wrapper?

    // compute insertion cost
    final double baseline = computeBid(null, time);
    final double additional = computeBid(p, time);
    return additional - baseline;
  }

  double computeInsertionCost(Collection<DefaultParcel> route,
      DefaultParcel newParcel) {
    return 0;

    // find cheapest insertion point

  }

  private double computeBid(@Nullable DefaultParcel p, long time) {
    final Set<DefaultParcel> availableParcels = newLinkedHashSet(assignedParcels);
    if (p != null) {
      availableParcels.add(p);
    }
    if (availableParcels.isEmpty()) {
      return 0;
    }
    // final StateContext sc = Solvers.convert(roadModel.get(), pdpModel.get(),
    // vehicle.get(), availableParcels,
    // Measure.valueOf(time, SI.MILLI(SI.SECOND)), null);
    // final ArraysObject ao = ArraysSolvers.toSingleVehicleArrays(sc.state,
    // SI.MILLI(SI.SECOND));
    // final SolutionObject so = solver.solve(ao.travelTime, ao.releaseDates,
    // ao.dueDates, ao.servicePairs, ao.serviceTimes, null);
    return 0;// so.objectiveValue;
  }
}
