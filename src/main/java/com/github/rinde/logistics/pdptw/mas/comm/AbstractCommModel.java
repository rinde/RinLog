/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
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
import static com.google.common.collect.Lists.newArrayList;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.rinsim.core.AbstractModel;
import com.github.rinde.rinsim.core.ModelProvider;
import com.github.rinde.rinsim.core.ModelReceiver;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModelEvent;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.PDPModelEventType;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.google.common.base.Optional;

/**
 * This class provides a common base for classes that implement a communication
 * strategy between a set of {@link Communicator}s. There are currently two
 * implementations, blackboard communication ({@link BlackboardCommModel}) and
 * auctioning ({@link AuctionCommModel}).
 * @author Rinde van Lon 
 * @param <T> The type of {@link Communicator} this model expects.
 */
public abstract class AbstractCommModel<T extends Communicator> extends
    AbstractModel<T> implements ModelReceiver {
  /**
   * The logger.
   */
  protected static final Logger LOGGER = LoggerFactory
      .getLogger(AbstractCommModel.class);
  /**
   * The list of registered communicators.
   */
  protected List<T> communicators;

  /**
   * New instance.
   */
  protected AbstractCommModel() {
    communicators = newArrayList();
  }

  @Override
  public void registerModelProvider(ModelProvider mp) {
    final PDPModel pm = Optional.fromNullable(mp.getModel(PDPModel.class))
        .get();
    pm.getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        final PDPModelEvent event = (PDPModelEvent) e;
        checkArgument(event.parcel instanceof DefaultParcel,
            "This class is only compatible with DefaultParcel and subclasses.");
        final DefaultParcel dp = (DefaultParcel) event.parcel;
        receiveParcel(dp, event.time);
      }
    }, PDPModelEventType.NEW_PARCEL);
  }

  /**
   * Subclasses can define their own parcel handling strategy in this method.
   * @param p The new {@link DefaultParcel} that becomes available.
   * @param time The current time.
   */
  protected abstract void receiveParcel(DefaultParcel p, long time);

  @Override
  public boolean register(final T communicator) {
    LOGGER.trace("register {}", communicator);
    communicators.add(communicator);
    return true;
  }

  @Override
  public boolean unregister(T element) {
    throw new UnsupportedOperationException();
  }
}
