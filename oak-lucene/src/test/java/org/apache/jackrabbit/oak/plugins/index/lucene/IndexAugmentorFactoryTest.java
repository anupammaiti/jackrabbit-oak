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

package org.apache.jackrabbit.oak.plugins.index.lucene;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.FulltextQueryTermsProvider;
import org.apache.jackrabbit.oak.plugins.index.lucene.spi.IndexFieldProvider;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.whiteboard.DefaultWhiteboard;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class IndexAugmentorFactoryTest {
    private IndexAugmentorFactory indexAugmentorFactory;
    private Whiteboard whiteboard = new DefaultWhiteboard();

    @Before
    public void initializeFactory() {
        indexAugmentorFactory = new IndexAugmentorFactory(whiteboard);
    }

    @Test
    public void compositeIndexProvider()
    {
        final String typeA = "type:A";
        final String typeB = "type:B";
        final String typeC = "type:C";
        final String typeD = "type:D";

        new IdentifiableIndexFiledProvider("1", Sets.newHashSet(typeA, typeB));
        new IdentifiableIndexFiledProvider("2", Sets.newHashSet(typeC));
        new IdentifiableIndexFiledProvider("3", Sets.newHashSet(typeA, typeB));

        indexAugmentorFactory.refreshServices();

        validateComposedFields(typeA, "1", "3");
        validateComposedFields(typeC, "2");
        validateComposedFields(typeD);
    }

    @Test
    public void compositeQueryTermsProvider()
    {
        final String typeA = "type:A";
        final String typeB = "type:B";
        final String typeC = "type:C";
        final String typeD = "type:D";
        final String typeE = "type:E";

        new IdentifiableQueryTermsProvider("1", Sets.newHashSet(typeA, typeB));
        new IdentifiableQueryTermsProvider("2", Sets.newHashSet(typeC));
        new IdentifiableQueryTermsProvider("3", Sets.newHashSet(typeA, typeB));
        new IdentifiableQueryTermsProvider(null, Sets.newHashSet(typeE));

        indexAugmentorFactory.refreshServices();

        validateComposedQueryTerms(typeA, "1", "3");
        validateComposedQueryTerms(typeC, "2");
        validateComposedQueryTerms(typeD);
        validateComposedQueryTerms(typeE);
    }

    void validateComposedFields(String type, String ... expected) {
        IndexFieldProvider compositeIndexProvider = indexAugmentorFactory.getIndexFieldProvider(type);
        Iterable<Field> fields = compositeIndexProvider.getAugmentedFields(null, null, null);
        Set<String> ids = Sets.newHashSet();
        for (Field f : fields) {
            ids.add(f.stringValue());
        }

        assertEquals(expected.length, Iterables.size(ids));
        assertThat(ids, CoreMatchers.hasItems(expected));
    }

    void validateComposedQueryTerms(String type, String ... expected) {
        FulltextQueryTermsProvider compositeQueryTermsProvider = indexAugmentorFactory.getFulltextQueryTermsProvider(type);
        Query q = compositeQueryTermsProvider.getQueryTerm(null, null, null);
        if (q == null) {
            assertEquals("No query terms generated for " + type + ".", 0, expected.length);
        } else {
            Set<String> ids = Sets.newHashSet();
            if (q instanceof BooleanQuery) {
                BooleanQuery query = (BooleanQuery) q;
                List<BooleanClause> clauses = query.clauses();
                for (BooleanClause clause : clauses) {
                    assertEquals(SHOULD, clause.getOccur());

                    Query subQuery = clause.getQuery();
                    String subQueryStr = subQuery.toString();
                    ids.add(subQueryStr.substring(0, subQueryStr.indexOf(":1")));
                }
            } else {
                String subQueryStr = q.toString();
                ids.add(subQueryStr.substring(0, subQueryStr.indexOf(":1")));
            }

            assertEquals(expected.length, Iterables.size(ids));
            assertThat(ids, CoreMatchers.hasItems(expected));
        }
    }

    class IdentifiableIndexFiledProvider implements IndexFieldProvider {
        private final Field id;
        private final Set<String> nodeTypes;

        IdentifiableIndexFiledProvider(String id, Set<String> nodeTypes) {
            this.id = new StringField("id", id, Field.Store.NO);
            this.nodeTypes = nodeTypes;

            whiteboard.register(IndexFieldProvider.class, this, null);
        }

        @Nonnull
        @Override
        public Iterable<Field> getAugmentedFields(String path, NodeState document, NodeState indexDefinition) {
            return Lists.newArrayList(id);
        }

        @Nonnull
        @Override
        public Set<String> getSupportedTypes() {
            return nodeTypes;
        }
    }

    class IdentifiableQueryTermsProvider implements FulltextQueryTermsProvider {
        private final Query id;
        private final Set<String> nodeTypes;

        IdentifiableQueryTermsProvider(String id, Set<String> nodeTypes) {
            this.id = (id == null)?null:new TermQuery(new Term(id, "1"));
            this.nodeTypes = nodeTypes;

            whiteboard.register(FulltextQueryTermsProvider.class, this, null);
        }

        @Override
        public Query getQueryTerm(String text, Analyzer analyzer, NodeState indexDefinition) {
            return id;
        }

        @Nonnull
        @Override
        public Set<String> getSupportedTypes() {
            return nodeTypes;
        }
    }
}
