/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.fixture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.oak.commons.FixturesHelper.Fixture;
import org.apache.jackrabbit.oak.spi.state.NodeStore;

/**
 * NodeStore fixture for parametrized tests.
 */
public abstract class NodeStoreFixture {

    public static final NodeStoreFixture MEMORY_NS = new MemoryFixture();

    public static final NodeStoreFixture SEGMENT_MK = new SegmentFixture();

    public static final NodeStoreFixture DOCUMENT_NS = new DocumentMongoFixture();

    public static final NodeStoreFixture DOCUMENT_RDB = new DocumentRdbFixture();

    /**
     * Creates a new empty {@link NodeStore} instance. An implementation must
     * ensure the returned node store is indeed empty and is independent from
     * instances returned from previous calls to this method.
     *
     * @return a new node store instance.
     */
    public abstract NodeStore createNodeStore();

    /**
     * Create a new cluster node that is attached to the same backend storage.
     * 
     * @param clusterNodeId the cluster node id
     * @return the node store, or null if clustering is not supported
     */
    public NodeStore createNodeStore(int clusterNodeId) {
        return null;
    }

    public void dispose(NodeStore nodeStore) {
    }

    public boolean isAvailable() {
        return true;
    }

    public static Collection<Object[]> asJunitParameters(Set<Fixture> fixtures) {
        List<NodeStoreFixture> configuredFixtures = new ArrayList<NodeStoreFixture>();
        if (fixtures.contains(Fixture.DOCUMENT_NS)) {
            configuredFixtures.add(DOCUMENT_NS);
        }
        if (fixtures.contains(Fixture.SEGMENT_MK)) {
            configuredFixtures.add(SEGMENT_MK);
        }
        if (fixtures.contains(Fixture.MEMORY_NS)) {
            configuredFixtures.add(MEMORY_NS);
        }
        if (fixtures.contains(Fixture.DOCUMENT_RDB)) {
            configuredFixtures.add(DOCUMENT_RDB);
        }

        Collection<Object[]> result = new ArrayList<Object[]>();
        for (NodeStoreFixture f : configuredFixtures) {
            if (f.isAvailable()) {
                result.add(new Object[]{f});
            }
        }
        return result;
    }
}