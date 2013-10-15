/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomAdaptor;

import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;

/**
 * A {@link RoutePlanner} implementation that creates random routes.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class RandomRoutePlanner extends AbstractRoutePlanner {

  private Queue<DefaultParcel> assignedParcels;
  private final Random rng;

  /**
   * Creates a random route planner using the specified random seed.
   * @param seed The random seed.
   */
  public RandomRoutePlanner(long seed) {
    rng = new RandomAdaptor(new MersenneTwister(seed));
    assignedParcels = newLinkedList();
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Collection<DefaultParcel> inCargo = Collections.checkedCollection(
        (Collection) pdpModel.get().getContents(vehicle.get()),
        DefaultParcel.class);
    if (onMap.isEmpty() && inCargo.isEmpty()) {
      assignedParcels.clear();
    } else {
      final List<DefaultParcel> ps = newArrayListWithCapacity((onMap.size() * 2)
          + inCargo.size());
      // Parcels on map need to be visited twice, once for pickup, once
      // for delivery.
      ps.addAll(onMap);
      ps.addAll(onMap);
      ps.addAll(inCargo);
      Collections.shuffle(ps, rng);
      assignedParcels = newLinkedList(ps);
    }
  }

  @Override
  public void nextImpl(long time) {
    assignedParcels.poll();
  }

  @Override
  public boolean hasNext() {
    return !assignedParcels.isEmpty();
  }

  @Override
  public Optional<DefaultParcel> current() {
    return Optional.fromNullable(assignedParcels.peek());
  }

  public static SupplierRng<RandomRoutePlanner> supplier() {
    return new DefaultSupplierRng<RandomRoutePlanner>() {
      @Override
      public RandomRoutePlanner get(long seed) {
        return new RandomRoutePlanner(seed);
      }
    };
  }
}
