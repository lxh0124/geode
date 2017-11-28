/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.connectors.jdbc;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.RegionEvent;
import org.apache.geode.cache.SerializedCacheValue;
import org.apache.geode.connectors.jdbc.internal.SqlHandler;
import org.apache.geode.internal.cache.InternalRegion;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class JdbcWriterTest {

  private EntryEvent<Object, Object> entryEvent;
  private PdxInstance pdxInstance;
  private SqlHandler sqlHandler;
  private SerializedCacheValue<Object> serializedNewValue;
  private RegionEvent<Object, Object> regionEvent;

  private JdbcWriter<Object, Object> writer;

  @Before
  public void setUp() {
    entryEvent = mock(EntryEvent.class);
    pdxInstance = mock(PdxInstance.class);
    sqlHandler = mock(SqlHandler.class);
    serializedNewValue = mock(SerializedCacheValue.class);
    regionEvent = mock(RegionEvent.class);

    when(entryEvent.getRegion()).thenReturn(mock(InternalRegion.class));
    when(entryEvent.getSerializedNewValue()).thenReturn(serializedNewValue);
    when(serializedNewValue.getDeserializedValue()).thenReturn(pdxInstance);

    writer = new JdbcWriter<>(sqlHandler);
  }

  @Test
  public void beforeUpdateWithPdxInstanceWritesToSqlHandler() {
    writer.beforeUpdate(entryEvent);

    verify(sqlHandler, times(1)).write(any(), any(), any(), eq(pdxInstance));
  }

  @Test
  public void beforeUpdateWithoutPdxInstanceWritesToSqlHandler() {
    when(serializedNewValue.getDeserializedValue()).thenReturn(new Object());

    assertThatThrownBy(() -> writer.beforeUpdate(entryEvent))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void beforeCreateWithPdxInstanceWritesToSqlHandler() {
    writer.beforeCreate(entryEvent);

    verify(sqlHandler, times(1)).write(any(), any(), any(), eq(pdxInstance));
  }

  @Test
  public void beforeDestroyWithPdxInstanceWritesToSqlHandler() {
    writer.beforeDestroy(entryEvent);

    verify(sqlHandler, times(1)).write(any(), any(), any(), eq(pdxInstance));
  }

  @Test
  public void beforeRegionDestroyDoesNotWriteToSqlHandler() {
    writer.beforeRegionDestroy(mock(RegionEvent.class));

    verifyZeroInteractions(sqlHandler);
  }

  @Test
  public void beforeRegionClearDoesNotWriteToSqlHandler() {
    writer.beforeRegionClear(mock(RegionEvent.class));

    verifyZeroInteractions(sqlHandler);
  }
}
