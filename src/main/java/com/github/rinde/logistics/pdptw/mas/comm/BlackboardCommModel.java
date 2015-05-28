/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

import java.io.Serializable;
import java.util.Set;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.google.auto.value.AutoValue;

/**
 * This is an implementation of a blackboard communication model. It allows
 * {@link BlackboardUser}s to claim {@link Parcel}s. Via the blackboard, all
 * other {@link BlackboardUser}s are notified of claims. With this communication
 * strategy race conditions between {@link BlackboardUser}s can be prevented.
 * @author Rinde van Lon
 */
public class BlackboardCommModel extends AbstractCommModel<BlackboardUser> {
  private final Set<Parcel> unclaimedParcels;

  /**
   * New empty blackboard communication model.
   */
  public BlackboardCommModel() {
    unclaimedParcels = newLinkedHashSet();
  }

  /**
   * Lays a claim on the specified {@link Parcel}. This means that this parcel
   * is no longer available to other {@link BlackboardUser}s.
   * @param claimer The user that claims the parcel.
   * @param p The parcel that is claimed.
   */
  public void claim(BlackboardUser claimer, Parcel p) {
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
   * Releases a claim on the specified {@link Parcel}. This means that this
   * parcel become available again for other {@link BlackboardUser}s.
   * @param unclaimer The user that releases the claim.
   * @param p The parcel to release the claim for.
   */
  public void unclaim(BlackboardUser unclaimer, Parcel p) {
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
  protected void receiveParcel(Parcel p, long time) {
    unclaimedParcels.add(p);
    // notify all users of the new parcel
    for (final BlackboardUser bu : communicators) {
      bu.update();
    }
  }

  /**
   * @return All unclaimed parcels.
   */
  public Set<Parcel> getUnclaimedParcels() {
    return unmodifiableSet(unclaimedParcels);
  }

  @Override
  public boolean register(BlackboardUser element) {
    super.register(element);
    element.init(this);
    return true;
  }

  /**
   * @return A new {@link Builder} instance.
   */
  public static Builder builder() {
    return Builder.create();
  }

  /**
   * Builder for {@link BlackboardCommModel}.
   * @author Rinde van Lon
   */
  @AutoValue
  public static class Builder extends
    AbstractModelBuilder<BlackboardCommModel, BlackboardUser> implements
    Serializable {

    private static final long serialVersionUID = 7935679222838329436L;

    @Override
    public BlackboardCommModel build(DependencyProvider dependencyProvider) {
      return new BlackboardCommModel();
    }

    static Builder create() {
      return new AutoValue_BlackboardCommModel_Builder();
    }
  }
}
