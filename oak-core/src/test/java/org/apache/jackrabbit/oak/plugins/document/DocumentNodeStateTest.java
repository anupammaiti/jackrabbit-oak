/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocumentNodeStateTest {

    @Rule
    public DocumentMKBuilderProvider builderProvider = new DocumentMKBuilderProvider();

    @Test
    public void getMemory() {
        DocumentNodeStore store = builderProvider.newBuilder().getNodeStore();
        RevisionVector rv = new RevisionVector(Revision.newRevision(1));
        DocumentNodeState state = new DocumentNodeState(store, "/foo", rv);
        assertEquals(232, state.getMemory());
    }
}
