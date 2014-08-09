/**
 * 
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

import java.util.Set;

import com.github.rinde.rinsim.pdptw.common.DefaultParcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;

/**
 * This is an implementation of a blackboard communication model. It allows
 * {@link BlackboardUser}s to claim {@link DefaultParcel}s. Via the blackboard,
 * all other {@link BlackboardUser}s are notified of claims. With this
 * communication strategy race conditions between {@link BlackboardUser}s can be
 * prevented.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class BlackboardCommModel extends AbstractCommModel<BlackboardUser> {
  private final Set<DefaultParcel> unclaimedParcels;

  /**
   * New empty blackboard communication model.
   */
  public BlackboardCommModel() {
    unclaimedParcels = newLinkedHashSet();
  }

  /**
   * Lays a claim on the specified {@link DefaultParcel}. This means that this
   * parcel is no longer available to other {@link BlackboardUser}s.
   * @param claimer The user that claims the parcel.
   * @param p The parcel that is claimed.
   */
  public void claim(BlackboardUser claimer, DefaultParcel p) {
    LOGGER.trace("claim {} by {}", p, claimer);
    checkArgument(unclaimedParcels.contains(p), "Parcel %s must be unclaimed.",
        p);
    unclaimedParcels.remove(p);
    for (final BlackboardUser bu : communicators) {
      if (bu != claimer) {
        bu.update();
      }
    }
  }

  /**
   * Releases a claim on the specified {@link DefaultParcel}. This means that
   * this parcel become available again for other {@link BlackboardUser}s.
   * @param unclaimer The user that releases the claim.
   * @param p The parcel to release the claim for.
   */
  public void unclaim(BlackboardUser unclaimer, DefaultParcel p) {
    LOGGER.trace("unclaim {} by {}", p, unclaimer);
    checkArgument(!unclaimedParcels.contains(p), "Parcel %s must be claimed.",
        p);
    unclaimedParcels.add(p);
    for (final BlackboardUser bu : communicators) {
      if (bu != unclaimer) {
        bu.update();
      }
    }
  }

  @Override
  protected void receiveParcel(DefaultParcel p, long time) {
    unclaimedParcels.add(p);
    // notify all users of the new parcel
    for (final BlackboardUser bu : communicators) {
      bu.update();
    }
  }

  /**
   * @return All unclaimed parcels.
   */
  public Set<DefaultParcel> getUnclaimedParcels() {
    return unmodifiableSet(unclaimedParcels);
  }

  @Override
  public boolean register(BlackboardUser element) {
    super.register(element);
    element.init(this);
    return true;
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link BlackboardCommModel}
   *         instances.
   */
  public static StochasticSupplier<BlackboardCommModel> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<BlackboardCommModel>() {
      @Override
      public BlackboardCommModel get(long seed) {
        return new BlackboardCommModel();
      }
    };
  }
}
