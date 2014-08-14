/**
 * 
 */
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;

import com.github.rinde.logistics.pdptw.mas.route.AbstractRoutePlanner;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon 
 * 
 */
public class TestRoutePlanner extends AbstractRoutePlanner {

  protected final Queue<DefaultParcel> route;

  public TestRoutePlanner() {
    route = newLinkedList();
  }

  @Override
  public Optional<DefaultParcel> current() {
    return Optional.fromNullable(route.peek());
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Collection<DefaultParcel> inCargo = Collections.checkedCollection(
        (Collection) pdpModel.get().getContents(vehicle.get()),
        DefaultParcel.class);
    route.clear();
    route.addAll(onMap);
    route.addAll(inCargo);
    route.addAll(onMap);
  }

  @Override
  protected void nextImpl(long time) {
    route.poll();
  }

  public static StochasticSupplier<TestRoutePlanner> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<TestRoutePlanner>() {
      @Override
      public TestRoutePlanner get(long seed) {
        return new TestRoutePlanner();
      }
    };
  }
}
