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
package org.apache.jackrabbit.oak.jcr;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.apache.jackrabbit.oak.plugins.commit.JcrConflictHandler.createJcrConflictHandler;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nonnull;
import javax.jcr.Repository;

import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.ContentRepository;
import org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl;
import org.apache.jackrabbit.oak.plugins.atomic.AtomicCounterEditorProvider;
import org.apache.jackrabbit.oak.plugins.commit.ConflictValidatorProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.counter.NodeCounterEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.nodetype.NodeTypeIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.property.OrderedPropertyIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.property.PropertyIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.property.PropertyIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.reference.ReferenceEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.reference.ReferenceIndexProvider;
import org.apache.jackrabbit.oak.plugins.itemsave.ItemSaveValidatorProvider;
import org.apache.jackrabbit.oak.plugins.name.NameValidatorProvider;
import org.apache.jackrabbit.oak.plugins.name.NamespaceEditorProvider;
import org.apache.jackrabbit.oak.plugins.nodetype.TypeEditorProvider;
import org.apache.jackrabbit.oak.plugins.nodetype.write.InitialContent;
import org.apache.jackrabbit.oak.plugins.observation.CommitRateLimiter;
import org.apache.jackrabbit.oak.plugins.version.VersionEditorProvider;
import org.apache.jackrabbit.oak.query.QueryEngineSettings;
import org.apache.jackrabbit.oak.security.SecurityProviderImpl;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CompositeConflictHandler;
import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.jackrabbit.oak.spi.commit.EditorHook;
import org.apache.jackrabbit.oak.spi.commit.EditorProvider;
import org.apache.jackrabbit.oak.spi.commit.Observer;
import org.apache.jackrabbit.oak.spi.commit.PartialConflictHandler;
import org.apache.jackrabbit.oak.spi.lifecycle.RepositoryInitializer;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.state.Clusterable;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;

/**
 * Builder class which encapsulates the details of building a JCR
 * <tt>Repository</tt> backed by an Oak <tt>ContentRepository</tt> instance
 *
 * <p>The backing <tt>ContentRepository</tt> instance will be constructed with
 * reasonable defaults and additional components can be registered by calling
 * the <tt>with</tt> methods. Note that it is not possible to remove components
 * once registered.</p>
 *
 * <p>The Jcr builder is a lazy initializer, to have a working repository make sure
 * you call {@link Jcr#createContentRepository()} or
 * {@link Jcr#createRepository()}.</p>
 */
public class Jcr {
    public static final int DEFAULT_OBSERVATION_QUEUE_LENGTH = 1000;

    private final Oak oak;

    private final Set<RepositoryInitializer> repositoryInitializers = newLinkedHashSet();
    private final Set<QueryIndexProvider> queryIndexProviders = newLinkedHashSet();
    private final Set<CommitHook> commitHooks = newLinkedHashSet();
    private final Set<IndexEditorProvider> indexEditorProviders = newLinkedHashSet();
    private final Set<EditorProvider> editorProviders = newLinkedHashSet();
    private final Set<Editor> editors = newLinkedHashSet();
    private final Set<Observer> observers = newLinkedHashSet();

    private final CompositeConflictHandler conflictHandler = createJcrConflictHandler();
    private SecurityProvider securityProvider;
    private CommitRateLimiter commitRateLimiter;
    private ScheduledExecutorService scheduledExecutor;
    private Executor executor;
    private QueryEngineSettings queryEngineSettings;
    private String defaultWorkspaceName;
    private Whiteboard whiteboard;

    private int observationQueueLength = DEFAULT_OBSERVATION_QUEUE_LENGTH;
    private boolean fastQueryResultSize;

    private ContentRepository contentRepository;
    private Repository repository;
    
    private Clusterable clusterable;

    public Jcr(Oak oak) {
        this.oak = oak;

        with(new InitialContent());

        with(new EditorHook(new VersionEditorProvider()));

        with(new SecurityProviderImpl());

        with(new ItemSaveValidatorProvider());
        with(new NameValidatorProvider());
        with(new NamespaceEditorProvider());
        with(new TypeEditorProvider());
        with(new ConflictValidatorProvider());
        with(new AtomicCounterEditorProvider());
        with(new ReferenceEditorProvider());
        with(new ReferenceIndexProvider());

        with(new PropertyIndexEditorProvider());
        with(new NodeCounterEditorProvider());

        with(new PropertyIndexProvider());
        with(new NodeTypeIndexProvider());

        with(new OrderedPropertyIndexEditorProvider());
    }

    public Jcr() {
        this(new Oak());
    }

    public Jcr(NodeStore store) {
        this(new Oak(store));
    }

    @Nonnull
    public Jcr with(@Nonnull Clusterable c) {
        ensureRepositoryIsNotCreated();
        this.clusterable = checkNotNull(c);
        return this;
    }
    
    @Nonnull
    public final Jcr with(@Nonnull RepositoryInitializer initializer) {
        ensureRepositoryIsNotCreated();
        repositoryInitializers.add(checkNotNull(initializer));
        return this;
    }

    private void ensureRepositoryIsNotCreated() {
        checkState(repository == null && contentRepository == null,
                "Repository was already created");
    }

    @Nonnull
    public final Jcr with(@Nonnull QueryIndexProvider provider) {
        ensureRepositoryIsNotCreated();
        queryIndexProviders.add(checkNotNull(provider));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull IndexEditorProvider indexEditorProvider) {
        ensureRepositoryIsNotCreated();
        indexEditorProviders.add(checkNotNull(indexEditorProvider));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull CommitHook hook) {
        ensureRepositoryIsNotCreated();
        commitHooks.add(checkNotNull(hook));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull EditorProvider provider) {
        ensureRepositoryIsNotCreated();
        editorProviders.add(checkNotNull(provider));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull Editor editor) {
        ensureRepositoryIsNotCreated();
        editors.add(checkNotNull(editor));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull SecurityProvider securityProvider) {
        ensureRepositoryIsNotCreated();
        this.securityProvider = checkNotNull(securityProvider);
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull PartialConflictHandler conflictHandler) {
        ensureRepositoryIsNotCreated();
        this.conflictHandler.addHandler(checkNotNull(conflictHandler));
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull ScheduledExecutorService executor) {
        ensureRepositoryIsNotCreated();
        this.scheduledExecutor = checkNotNull(executor);
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull Executor executor) {
        ensureRepositoryIsNotCreated();
        this.executor = checkNotNull(executor);
        return this;
    }

    @Nonnull
    public final Jcr with(@Nonnull Observer observer) {
        ensureRepositoryIsNotCreated();
        observers.add(checkNotNull(observer));
        return this;
    }

    @Nonnull
    public Jcr withAsyncIndexing() {
        ensureRepositoryIsNotCreated();
        oak.withAsyncIndexing();
        return this;
    }

    @Nonnull
    public Jcr withObservationQueueLength(int observationQueueLength) {
        ensureRepositoryIsNotCreated();
        this.observationQueueLength = observationQueueLength;
        return this;
    }

    @Nonnull
    public Jcr with(@Nonnull CommitRateLimiter commitRateLimiter) {
        ensureRepositoryIsNotCreated();
        this.commitRateLimiter = checkNotNull(commitRateLimiter);
        return this;
    }

    @Nonnull
    public Jcr with(@Nonnull QueryEngineSettings qs) {
        ensureRepositoryIsNotCreated();
        this.queryEngineSettings = checkNotNull(qs);
        return this;
    }

    @Nonnull
    public Jcr withFastQueryResultSize(boolean fastQueryResultSize) {
        ensureRepositoryIsNotCreated();
        this.fastQueryResultSize = fastQueryResultSize;
        return this;
    }

    @Nonnull
    public Jcr with(@Nonnull String defaultWorkspaceName) {
        ensureRepositoryIsNotCreated();
        this.defaultWorkspaceName = checkNotNull(defaultWorkspaceName);
	return this;
    }

    @Nonnull
    public Jcr with(@Nonnull Whiteboard whiteboard) {
        ensureRepositoryIsNotCreated();
        this.whiteboard = checkNotNull(whiteboard);
        return this;
    }

    private void setUpOak() {
        // whiteboard
        if (whiteboard != null) {
            oak.with(whiteboard);
        }

        // repository initializers
        for (RepositoryInitializer repositoryInitializer : repositoryInitializers) {
            oak.with(repositoryInitializer);
        }

        // query index providers
        for (QueryIndexProvider queryIndexProvider : queryIndexProviders) {
            oak.with(queryIndexProvider);
        }

        // commit hooks
        for (CommitHook commitHook : commitHooks) {
            oak.with(commitHook);
        }

        // conflict handlers
        oak.with(conflictHandler);

        // index editor providers
        for (IndexEditorProvider indexEditorProvider : indexEditorProviders) {
            oak.with(indexEditorProvider);
        }

        // editors
        for (Editor editor : editors) {
            oak.with(editor);
        }

        // editor providers
        for (EditorProvider editorProvider : editorProviders) {
            oak.with(editorProvider);
        }

        // securityProvider
        oak.with(securityProvider);

        // executors
        if (scheduledExecutor != null) {
            oak.with(scheduledExecutor);
        }
        if (executor != null) {
            oak.with(executor);
        }

        // observers
        for (Observer observer : observers) {
            oak.with(observer);
        }

        // commit rate limiter
        if (commitRateLimiter != null) {
            oak.with(commitRateLimiter);
        }

        // query engine settings
        if (queryEngineSettings != null) {
            oak.with(queryEngineSettings);
        }

        // default workspace name
        if (defaultWorkspaceName != null) {
            oak.with(defaultWorkspaceName);
        }
        
        if (clusterable != null) {
            oak.with(clusterable);
        }
    }

    @Nonnull
    public ContentRepository createContentRepository() {
        if (contentRepository == null) {
            setUpOak();
            contentRepository = oak.createContentRepository();
        }
        return contentRepository;
    }

    @Nonnull
    public Repository createRepository() {
        if (repository == null) {
            repository = new RepositoryImpl(
                    createContentRepository(),
                    oak.getWhiteboard(),
                    securityProvider,
                    observationQueueLength,
                    commitRateLimiter,
                    fastQueryResultSize);
        }
        return repository;
    }

}
