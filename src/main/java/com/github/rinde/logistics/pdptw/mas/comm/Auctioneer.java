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

import com.github.rinde.rinsim.event.Listener;

/**
 *
 *
 * @author Rinde van Lon
 */
// Auctioneer ---- Parcel (one-to-one)
public interface Auctioneer<T extends Bid<T>> {

  void auctionParcel(Bidder<T> currentOwner, long time, T bidToBeat,
      Listener callback);

  void submit(T bid);

  Bidder<T> getWinner();

  boolean hasWinner();

  // -1L if the last auction was a success
  long getLastUnsuccessTime();

}
