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
/**
 *
 */
package org.apache.geode.cache.query.internal.index;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.query.IndexType;
import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.data.Portfolio;
import org.apache.geode.cache.query.internal.DefaultQuery;
import org.apache.geode.cache.query.internal.IndexTrackingQueryObserver;
import org.apache.geode.cache.query.internal.IndexTrackingQueryObserver.IndexInfo;
import org.apache.geode.cache.query.internal.QueryObserver;
import org.apache.geode.cache.query.internal.QueryObserverHolder;
import org.apache.geode.cache30.CacheTestCase;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.PartitionedRegionQueryEvaluator.TestHook;
import org.apache.geode.test.dunit.Assert;
import org.apache.geode.test.dunit.AsyncInvocation;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.LogWriterUtils;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.Wait;
import org.apache.geode.test.dunit.WaitCriterion;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.geode.test.dunit.internal.JUnit4DistributedTestCase;
import org.apache.geode.test.junit.categories.DistributedTest;

/**
 *
 */
@Category(DistributedTest.class)
public class IndexTrackingQueryObserverDUnitTest extends JUnit4CacheTestCase {

  private final int NUM_BKTS = 10;
  private static final String queryStr = "select * from /portfolio where ID >= 0";
  protected static final int TOTAL_OBJECTS = 1000;
  public static final String INDEX_NAME = "keyIndex1";

  public IndexTrackingQueryObserverDUnitTest() {
    super();
  }


  @Ignore("Disabled for bug 52321")
  @Test
  public void testIndexInfoOnRemotePartitionedRegion() throws Exception {
    final Host host = Host.getHost(0);
    VM ds0 = host.getVM(0);
    VM ds1 = host.getVM(1);

    ds0.invoke(new SerializableRunnable("Set system property") {
      public void run() {
        DefaultQuery.QUERY_VERBOSE = true;
      }
    });
    ds1.invoke(new SerializableRunnable("Set system property") {
      public void run() {
        DefaultQuery.QUERY_VERBOSE = true;
      }
    });

    createPR(ds0);
    createPR(ds1);

    createQueryIndex(ds0, true);
    createQueryIndex(ds1, false);

    // Populate region.
    initializeRegion(ds0);

    // Check query verbose on both VMs
    AsyncInvocation async1 = verifyQueryVerboseData(ds0, TOTAL_OBJECTS / 2);
    AsyncInvocation async2 = verifyQueryVerboseData(ds1, TOTAL_OBJECTS / 2);

    // Run query on one vm only.
    runQuery(ds1);

    async1.join();
    async2.join();

    ds0.invoke(new SerializableRunnable("Test Query Verbose Data") {
      public void run() {
        // Reset the observer.
        QueryObserverHolder.reset();
        // Reset System Property
        DefaultQuery.QUERY_VERBOSE = false;
      }
    });
    ds1.invoke(new SerializableRunnable("Test Query Verbose Data") {

      public void run() {
        // Reset the observer.
        QueryObserverHolder.reset();
        // Reset System Property
        DefaultQuery.QUERY_VERBOSE = false;
      }
    });

    if (async1.exceptionOccurred()) {
      Assert.fail("", async1.getException());
    }

    if (async1.exceptionOccurred()) {
      Assert.fail("", async1.getException());
    }
  }

  /**
   * CReates a PR on a VM with NUM_BKTS buckets.
   *
   * @param vm
   */
  private void createPR(VM vm) {

    SerializableRunnable createDS = new SerializableRunnable("Creating PR Datastore") {

      public void run() {

        QueryObserver observer = QueryObserverHolder.setInstance(new IndexTrackingQueryObserver());

        // Create Partition Region
        PartitionAttributesFactory paf = new PartitionAttributesFactory();
        paf.setTotalNumBuckets(NUM_BKTS);
        AttributesFactory af = new AttributesFactory();
        af.setPartitionAttributes(paf.create());

        Region region = getCache().createRegion("portfolio", af.create());

      }
    };

    vm.invoke(createDS);
  }

  private void initializeRegion(VM vm) {

    SerializableRunnable initRegion = new SerializableRunnable("Initialize the PR") {

      public void run() {

        Region region = getCache().getRegion("portfolio");

        if (region.size() == 0) {
          for (int i = 0; i < TOTAL_OBJECTS; i++) {
            region.put(Integer.toString(i), new Portfolio(i, i));
          }
        }
        assertEquals(TOTAL_OBJECTS, region.size());

      }
    };
    vm.invoke(initRegion);
  }

  private void createQueryIndex(VM vm, final boolean create) {

    SerializableRunnable createIndex = new SerializableRunnable("Create index on PR") {

      public void run() {

        // Query VERBOSE has to be true for the test
        assertTrue(DefaultQuery.QUERY_VERBOSE);

        QueryService qs = getCache().getQueryService();

        Index keyIndex1 = null;
        try {
          if (create) {
            keyIndex1 = (IndexProtocol) qs.createIndex(INDEX_NAME, IndexType.FUNCTIONAL, "ID",
                "/portfolio ");
            assertNotNull(keyIndex1);
            assertTrue(keyIndex1 instanceof PartitionedIndex);
          }
        } catch (Exception e) {
          Assert.fail("While creating Index on PR", e);
        }
        Region region = getCache().getRegion("portfolio");
        // Inject TestHook in QueryObserver before running query.
        IndexTrackingTestHook th = new IndexTrackingTestHook(region, NUM_BKTS / 2);
        QueryObserver observer = QueryObserverHolder.getInstance();
        assertTrue(QueryObserverHolder.hasObserver());

        ((IndexTrackingQueryObserver) observer).setTestHook(th);
      }
    };

    vm.invoke(createIndex);
  }

  private void runQuery(VM vm) {

    SerializableRunnable runQuery = new SerializableRunnable("Run Query on PR") {

      public void run() {

        QueryService qs = getCache().getQueryService();
        Query query = qs.newQuery(queryStr);
        Region region = getCache().getRegion("portfolio");

        SelectResults results = null;
        try {
          results = (SelectResults) query.execute();
        } catch (Exception e) {
          Assert.fail("While running query on PR", e);
        }

        // The query should return all elements in region.
        assertEquals(region.size(), results.size());
      }
    };
    vm.invoke(runQuery);
  }

  private AsyncInvocation verifyQueryVerboseData(VM vm, final int results) {

    SerializableRunnable testQueryVerbose = new SerializableRunnable("Test Query Verbose Data") {

      public void run() {
        // Query VERBOSE has to be true for the test
        assertTrue(DefaultQuery.QUERY_VERBOSE);

        // Get TestHook from observer.
        QueryObserver observer = QueryObserverHolder.getInstance();
        assertTrue(QueryObserverHolder.hasObserver());

        final IndexTrackingTestHook th =
            (IndexTrackingTestHook) ((IndexTrackingQueryObserver) observer).getTestHook();

        Wait.waitForCriterion(new WaitCriterion() {

          public boolean done() {
            if (th.getRegionMap() != null) {
              return th.getRegionMap().getResults() != null;
            }
            return false;
          }

          public String description() {
            return null;
          }
        }, 60 * 1000, 200, true);

        IndexInfo regionMap = th.getRegionMap();

        Collection<Integer> rslts = regionMap.getResults().values();
        int totalResults = 0;
        for (Integer i : rslts) {
          totalResults += i.intValue();
        }

        LogWriterUtils.getLogWriter().fine("Index Info result size is " + totalResults);
        assertEquals(results, totalResults);
      }
    };
    AsyncInvocation asyncInv = vm.invokeAsync(testQueryVerbose);
    return asyncInv;
  }

  /**
   * TODO: Not implemented fully for all the hooks.
   *
   */
  public static class IndexTrackingTestHook implements TestHook {
    IndexInfo rMap;
    Region regn;
    int bkts;

    public IndexTrackingTestHook(Region region, int bukts) {
      this.regn = region;
      this.bkts = bukts;
    }


    public void hook(int spot) throws RuntimeException {

      QueryObserver observer = QueryObserverHolder.getInstance();
      assertTrue(observer instanceof IndexTrackingQueryObserver);
      IndexTrackingQueryObserver gfObserver = (IndexTrackingQueryObserver) observer;

      if (spot == 1) { // before index lookup
      } else if (spot == 2) { // before key range index lookup
      } else if (spot == 3) { // End of afterIndexLookup call
      } else if (spot == 4) { // Before resetting indexInfoMap
        Map map = gfObserver.getUsedIndexes();
        assertEquals(1, map.size());

        assertTrue(map.get(INDEX_NAME) instanceof IndexInfo);
        rMap = (IndexInfo) map.get(INDEX_NAME);

        if (this.regn instanceof PartitionedRegion) {
          assertEquals(1, rMap.getResults().size());
        } else if (this.regn instanceof LocalRegion) {
          assertEquals(1, rMap.getResults().size());
        }
      }
    }

    public IndexInfo getRegionMap() {
      return rMap;
    }
  }
}
