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

import static com.google.common.base.Verify.verifyNotNull;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.google.auto.value.AutoValue;

/**
 *
 * @author Rinde van Lon
 */
@AutoValue
public abstract class DoubleBid implements Bid<DoubleBid> {

  DoubleBid() {}

  @Override
  public abstract Bidder<DoubleBid> getBidder();

  public abstract double getCost();

  @Override
  public int compareTo(@Nullable DoubleBid o) {
    return Double.compare(getCost(), verifyNotNull(o).getCost());
  }

  public static DoubleBid create(long timeOfAuction, Bidder<DoubleBid> bidder,
      Parcel parcel, double cost) {
    return new AutoValue_DoubleBid(timeOfAuction, parcel, bidder, cost);
  }
}
