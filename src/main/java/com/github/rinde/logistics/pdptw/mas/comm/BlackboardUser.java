/**
 * 
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

import java.util.Collection;
import java.util.Set;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventDispatcher;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;

/**
 * This {@link Communicator} implementation allows communication via a
 * blackboard system. It requires the {@link BlackboardCommModel}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class BlackboardUser implements Communicator {

  private Optional<BlackboardCommModel> bcModel;
  private final EventDispatcher eventDispatcher;
  private final Set<DefaultParcel> claimedParcels;

  /**
   * Constructor.
   */
  public BlackboardUser() {
    eventDispatcher = new EventDispatcher(CommunicatorEventType.values());
    bcModel = Optional.absent();
    claimedParcels = newLinkedHashSet();
  }

  /**
   * @param model Injects the {@link BlackboardCommModel}.
   */
  public void init(BlackboardCommModel model) {
    bcModel = Optional.of(model);
  }

  @Override
  public void waitFor(DefaultParcel p) {}

  /**
   * Lay a claim on the specified {@link DefaultParcel}.
   * @param p The parcel to claim.
   */
  @Override
  public void claim(DefaultParcel p) {
    checkArgument(!claimedParcels.contains(p), "Parcel %s is already claimed.",
        p);
    // forward call to model
    bcModel.get().claim(this, p);
    claimedParcels.add(p);
  }

  @Override
  public void unclaim(DefaultParcel p) {
    checkArgument(claimedParcels.contains(p), "Parcel %s is not claimed.", p);
    bcModel.get().unclaim(this, p);
    claimedParcels.remove(p);
  }

  @Override
  public void done() {
    claimedParcels.clear();
  }

  /**
   * Notifies this blackboard user of a change in the environment.
   */
  public void update() {
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  @Override
  public void addUpdateListener(Listener l) {
    eventDispatcher.addListener(l, CommunicatorEventType.CHANGE);
  }

  @Override
  public Collection<DefaultParcel> getParcels() {
    return Sets.union(bcModel.get().getUnclaimedParcels(), claimedParcels);
  }

  @Override
  public Collection<DefaultParcel> getClaimedParcels() {
    return unmodifiableSet(claimedParcels);
  }

  @Override
  public String toString() {
    return toStringHelper(this).addValue(Integer.toHexString(hashCode()))
        .toString();
  }

  // not needed
  @Override
  public void init(RoadModel rm, PDPModel pm, DefaultVehicle v) {}

  /**
   * @return A {@link StochasticSupplier} that supplies {@link BlackboardUser}
   *         instances.
   */
  public static StochasticSupplier<BlackboardUser> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<BlackboardUser>() {
      @Override
      public BlackboardUser get(long seed) {
        return new BlackboardUser();
      }
    };
  }

}