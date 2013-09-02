/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Set;

import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.unit.SI;

import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.StateContext;
import rinde.sim.pdptw.central.arrays.ArraysSolvers;
import rinde.sim.pdptw.central.arrays.ArraysSolvers.ArraysObject;
import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.central.arrays.SolutionObject;
import rinde.sim.pdptw.common.DefaultParcel;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class SolverBidder extends AbstractBidder {

  protected final SingleVehicleArraysSolver solver;

  public SolverBidder(SingleVehicleArraysSolver sol) {
    solver = sol;
  }

  // TODO figure out what happens when the vehicle is moving towards a parcel
  // at the moment this method is called?
  public double getBidFor(DefaultParcel p, long time) {

    // TODO optimize baseline, baseline can be remembered from last assigned
    // parcel

    // TODO coordinate with route planner if it uses the same solver?
    // or create cached solver wrapper?

    // compute insertion cost
    final double baseline = computeBid(null, time);
    final double additional = computeBid(p, time);
    return additional - baseline;
  }

  private double computeBid(@Nullable DefaultParcel p, long time) {
    final Set<DefaultParcel> availableParcels = newLinkedHashSet(assignedParcels);
    if (p != null) {
      availableParcels.add(p);
    }
    if (availableParcels.isEmpty()) {
      return 0;
    }
    final StateContext sc = Solvers.convert(roadModel.get(), pdpModel.get(),
        vehicle.get(), availableParcels,
        Measure.valueOf(time, SI.MILLI(SI.SECOND)));
    final ArraysObject ao = ArraysSolvers.toSingleVehicleArrays(sc.state,
        SI.MILLI(SI.SECOND));
    final SolutionObject so = solver.solve(ao.travelTime, ao.releaseDates,
        ao.dueDates, ao.servicePairs, ao.serviceTimes);
    return so.objectiveValue;
  }
}
