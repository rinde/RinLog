/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomAdaptor;

import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;
import rinde.sim.util.StochasticSuppliers.AbstractStochasticSupplier;

import com.google.common.base.Optional;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

/**
 * A {@link RoutePlanner} implementation that creates random routes.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class RandomRoutePlanner extends AbstractRoutePlanner {

  private final Multiset<DefaultParcel> assignedParcels;
  private Optional<DefaultParcel> current;
  private final Random rng;

  /**
   * Creates a random route planner using the specified random seed.
   * @param seed The random seed.
   */
  public RandomRoutePlanner(long seed) {
    LOGGER.info("constructor {}", seed);
    assignedParcels = LinkedHashMultiset.create();
    current = Optional.absent();
    rng = new RandomAdaptor(new MersenneTwister(seed));
  }

  @Override
  protected final void doUpdate(Collection<DefaultParcel> onMap, long time) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Collection<DefaultParcel> inCargo = Collections.checkedCollection(
        (Collection) pdpModel.get().getContents(vehicle.get()),
        DefaultParcel.class);
    assignedParcels.clear();
    for (final DefaultParcel dp : onMap) {
      assignedParcels.add(dp, 2);
    }
    assignedParcels.addAll(inCargo);
    updateCurrent();
  }

  private void updateCurrent() {
    if (assignedParcels.isEmpty()) {
      current = Optional.absent();
    } else {
      final List<DefaultParcel> list = newArrayList(assignedParcels
          .elementSet());
      current = Optional.of(list.get(rng.nextInt(list.size())));
    }
  }

  @Override
  public final void nextImpl(long time) {
    LOGGER.trace("current {}", current);
    if (current.isPresent()) {
      checkArgument(assignedParcels.remove(current.get()));
    }
    updateCurrent();
  }

  @Override
  public final boolean hasNext() {
    return !assignedParcels.isEmpty();
  }

  @Override
  public final Optional<DefaultParcel> current() {
    return current;
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link RandomRoutePlanner}
   *         instances.
   */
  public static StochasticSupplier<RandomRoutePlanner> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomRoutePlanner>() {
      @Override
      public RandomRoutePlanner get(long seed) {
        return new RandomRoutePlanner(seed);
      }
    };
  }
}
