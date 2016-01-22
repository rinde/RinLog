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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 * @author Rinde van Lon
 */
public final class SetFactories {
  SetFactories() {}

  public interface SetFactory {
    <V> Set<V> create();
  }

  public static SetFactory linkedHashSet() {
    return DefaultFactories.LINKED_HASH_SET;
  }

  public static SetFactory synchronizedFactory(SetFactory factory) {
    return new SynchronizedSetFactory(factory);
  }

  enum DefaultFactories implements SetFactory {
    LINKED_HASH_SET {
      @Override
      public <V> Set<V> create() {
        return new LinkedHashSet<>();
      }
    }
  }

  static class SynchronizedSetFactory implements SetFactory {
    private final SetFactory factory;

    SynchronizedSetFactory(SetFactory fac) {
      factory = fac;
    }

    @Override
    public <V> Set<V> create() {
      return Collections.synchronizedSet(factory.<V>create());
    }
  }
}
