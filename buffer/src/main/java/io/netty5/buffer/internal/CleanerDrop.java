/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.buffer.internal;

import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A drop implementation that delegates to another drop instance, either when called directly, or when it becomes
 * cleanable. This ensures that objects are dropped even if they leak.
 */
public final class CleanerDrop<T extends Buffer> implements Drop<T> {
    private Cleaner.Cleanable cleanable;
    private GatedRunner<T> runner;

    /**
     * Wrap the given drop instance, and produce a new drop instance that will also call the delegate drop instance if
     * it becomes cleanable.
     */
    public static <T extends Buffer> Drop<T> wrap(Drop<T> drop, MemoryManager manager) {
        return innerWrap(drop, manager, true);
    }

    /**
     * Wrap the given drop instance, and produce a new drop instance that will also call the delegate drop instance if
     * it becomes cleanable.
     * <p>
     * The produced drop will not report to the {@link LeakDetection}, but {@linkplain #fork() forks} of the drop will.
     * This is used by the {@linkplain BufferAllocator#constBufferSupplier(byte[]) const-buffer suppliers}, which don't
     * expose any way for them to be closed.
     */
    public static <T extends Buffer> Drop<T> wrapWithoutLeakDetection(Drop<T> drop, MemoryManager manager) {
        return innerWrap(drop, manager, false);
    }

    private static <T extends Buffer> CleanerDrop<T> innerWrap(
            Drop<T> drop, MemoryManager manager, boolean detectLeaks) {
        CleanerDrop<T> cleanerDrop = new CleanerDrop<>();
        GatedRunner<T> runner = new GatedRunner<>(drop, manager, detectLeaks);
        cleanerDrop.cleanable = InternalBufferUtils.getCleaner().register(cleanerDrop, runner);
        cleanerDrop.runner = runner;
        return cleanerDrop;
    }

    private CleanerDrop() {
    }

    @Override
    public void attach(T obj) {
        runner.prepareRecover(obj);
        runner.drop.attach(obj);
    }

    @Override
    public void drop(T obj) {
        runner.dropping = true;
        runner.set(obj);
        cleanable.clean();
    }

    @Override
    public Drop<T> fork() {
        CleanerDrop<T> drop = innerWrap(runner.drop.fork(), runner.manager, true);
        drop.runner.tracerFromSplitParent = true;
        drop.runner.tracer = runner.tracer;
        return drop;
    }

    @Override
    public String toString() {
        return "CleanerDrop(" + runner.drop + ')';
    }

    private static final class GatedRunner<T extends Buffer> extends AtomicReference<Object> implements Runnable {
        private static final NoOpAllocatorControl ALLOC_CONTROL = new NoOpAllocatorControl();
        private static final long serialVersionUID = 2685535951915798850L;
        final Drop<T> drop;
        final MemoryManager manager;
        final boolean detectLeaks;
        volatile boolean dropping;
        volatile boolean tracerFromSplitParent;
        LifecycleTracer tracer;

        private GatedRunner(Drop<T> drop, MemoryManager manager, boolean detectLeaks) {
            this.drop = drop;
            this.manager = manager;
            this.detectLeaks = detectLeaks;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Object obj = getAndSet(null); // Make absolutely sure we only delegate once.
            if (obj != null) {
                if (dropping) {
                    drop.drop((T) obj);
                } else {
                    try (Buffer recoveredBuffer = manager.recoverMemory(ALLOC_CONTROL, obj, (Drop<Buffer>) drop)) {
                        if (detectLeaks) {
                            LeakDetection.reportLeak(tracer, "buffer (" + recoveredBuffer.capacity() + " bytes)");
                        }
                    }
                }
            }
        }

        public void prepareRecover(T obj) {
            LifecycleTracer recoveredTracer = ResourceSupport.getTracer((ResourceSupport<?, ?>) obj);
            if (tracerFromSplitParent) {
                tracerFromSplitParent = false;
                tracer.splitTo(recoveredTracer);
            }
            tracer = recoveredTracer;
            set(manager.unwrapRecoverableMemory(obj));
        }
    }

    private static final class NoOpAllocatorControl implements AllocatorControl {
        @Override
        public BufferAllocator getAllocator() {
            throw new UnsupportedOperationException();
        }
    }
}
