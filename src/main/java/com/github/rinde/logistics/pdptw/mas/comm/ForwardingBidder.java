/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import java.util.Set;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.Listener;

/**
 * Forwarding decorator implementation of {@link Bidder}.
 * @author Rinde van Lon
 * @param <T> The bid type.
 */
public abstract class ForwardingBidder<T extends Bid<T>> implements Bidder<T> {

  /**
   * @return The delegate that is decorated.
   */
  protected abstract Bidder<T> delegate();

  @Override
  public void init(RoadModel rm, PDPModel pm, Vehicle v) {
    delegate().init(rm, pm, v);
  }

  @Override
  public void addUpdateListener(Listener l) {
    delegate().addUpdateListener(l);
  }

  @Override
  public void waitFor(Parcel p) {
    delegate().waitFor(p);
  }

  @Override
  public void claim(Parcel p) {
    delegate().claim(p);
  }

  @Override
  public void unclaim(Parcel p) {
    delegate().unclaim(p);
  }

  @Override
  public void done() {
    delegate().done();
  }

  @Override
  public Set<Parcel> getParcels() {
    return delegate().getParcels();
  }

  @Override
  public Set<Parcel> getClaimedParcels() {
    return delegate().getClaimedParcels();
  }

  @Override
  public void callForBids(Auctioneer<T> auctioneer, Parcel p, long time) {
    delegate().callForBids(auctioneer, p, time);
  }

  @Override
  public void endOfAuction(Auctioneer<T> auctioneer, Parcel p,
      long auctionStartTime) {
    delegate().endOfAuction(auctioneer, p, auctionStartTime);
  }

  @Override
  public void receiveParcel(Auctioneer<T> auctioneer, Parcel p,
      long auctionStartTime) {
    delegate().receiveParcel(auctioneer, p, auctionStartTime);
  }

  @Override
  public boolean releaseParcel(Parcel p) {
    return delegate().releaseParcel(p);
  }
}
