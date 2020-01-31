/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author sesidoro
 */
public interface TaskExecutor {

    void execute(Object identifier, Runnable... run);

    void execute(Object identifier, Collection<Runnable>... run);

    void shutdown();

    void onSubmitted(Object identifier, Runnable run, long submittedAt);

    void onCompleted(Object identifier, Runnable run, long submittedAt, long startedAt, long durationNano, Throwable error);

    void addTaskExecutorListener(TaskExecutorListener l);

    void removeTaskExecutorListener(TaskExecutorListener l);

    public static interface TaskExecutorListener {

        void onSubmitted(Object identifier, Runnable run, long submittedAt);

        void onCompleted(Object identifier, Runnable run, long submittedAt, long startedAt, long durationNano, Throwable error);
    }

    public static abstract class TaskExecutorBase implements TaskExecutor {

        TaskExecutorListener[] listeners = null;

        @Override
        public void onSubmitted(Object identifier, Runnable run, long submittedAt) {
            if (listeners != null) {
                for (TaskExecutorListener l : listeners) {
                    if (l != null) {
                        try {
                            l.onSubmitted(identifier, run, submittedAt);
                        } catch (Throwable th) {
                        }
                    }
                }
            }
        }

        @Override
        public void onCompleted(Object identifier, Runnable run, long submittedAt, long startedAt, long durationNano, Throwable error) {
            if (listeners != null) {
                for (TaskExecutorListener l : listeners) {
                    if (l != null) {
                        try {
                            l.onCompleted(identifier, run, submittedAt, startedAt, durationNano, error);
                        } catch (Throwable th) {
                        }
                    }
                }
            }
        }

        @Override
        public void addTaskExecutorListener(TaskExecutorListener l) {
            if (l != null) {
                synchronized (this) {
                    boolean found = false;
                    if (listeners != null) {
                        for (Object o : listeners) {
                            if (l.equals(o)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        if (listeners == null) {
                            listeners = new TaskExecutorListener[]{l};
                        } else {
                            listeners = Arrays.copyOf(listeners, listeners.length + 1);
                            listeners[listeners.length - 1] = l;
                        }
                    }
                }
            }
        }

        @Override
        public void removeTaskExecutorListener(TaskExecutorListener l) {
            if (l == null || listeners == null) {
                return;
            }
            int idx = -1;
            for (int i = 0; i < listeners.length; i++) {
                if (l.equals(listeners[i])) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                synchronized (this) {
                    if (listeners.length == 1) {
                        listeners = null;
                    } else {
                        TaskExecutorListener[] old = listeners;
                        listeners = new TaskExecutorListener[listeners.length - 1];
                        int off = 0;
                        for (int i = 0; i < old.length; i++) {
                            if (i != idx) {
                                listeners[off++] = old[i];
                            }
                        }
                    }
                }
            }
        }

        public Runnable wrap(final Object identifier, final Runnable r) {
            final long submittedAt = System.currentTimeMillis();
            onSubmitted(identifier, r, submittedAt);
            return new Runnable() {

                @Override
                public void run() {
                    long startedAt = System.currentTimeMillis();
                    long started = System.nanoTime();
                    Throwable error = null;
                    try {
                        r.run();
                    } catch (Throwable th) {
                        error = th;
                    } finally {
                        TaskExecutorBase.this.onCompleted(identifier, r, submittedAt, startedAt, System.nanoTime() - started, error);
                    }
                }
            };
        }
    }

    public static class TaskExecutorSimple extends TaskExecutorBase {

        @Override
        public void execute(Object identifier, Collection<Runnable>... run) {
            if (run != null) {
                int c = 0;
                for (Collection<Runnable> rc : run) {
                    if (rc != null) {
                        c += rc.size();
                    }
                }
                if (c > 0) {
                    Runnable[] rs = new Runnable[c];
                    c = 0;
                    for (Collection<Runnable> rc : run) {
                        if (rc != null) {
                            for (Runnable r : rc) {
                                rs[c++] = r;
                            }
                        }
                    }
                    execute(identifier, rs);
                }
            }
        }

        @Override
        public void execute(Object identifier, Runnable... run) {
            if (run != null) {
                for (int i = 0; i < run.length; i++) {
                    if (run[i] == null) {
                        continue;
                    }
                    Thread thread = new Thread(wrap(identifier, run[i]));
                    thread.setDaemon(true);
                    thread.setName("TaskExecutor: " + identifier + "[" + i + "]");
                    thread.start();
                }
            }
        }

        @Override
        public void shutdown() {
        }
    }

    public static class TaskExecutorPool extends TaskExecutorBase {

        ExecutorService es;
//        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(100);

        public TaskExecutorPool() {
            es=Executors.newScheduledThreadPool(5);
//            es = new ThreadPoolExecutor(
//                    50, // int corePoolSize,
//                    150, // int maximumPoolSize,
//                    60, // long keepAliveTime,
//                    TimeUnit.SECONDS, // TimeUnit unit,
//                    workQueue // BlockingQueue<Runnable> workQueue
//            );
        }
        public TaskExecutorPool(
                TaskExecutorListener... listeners
        ) {
            this.listeners=listeners;
            es=Executors.newScheduledThreadPool(5);
//            es = new ThreadPoolExecutor(
//                    50, // int corePoolSize,
//                    150, // int maximumPoolSize,
//                    60, // long keepAliveTime,
//                    TimeUnit.SECONDS, // TimeUnit unit,
//                    workQueue // BlockingQueue<Runnable> workQueue
//            );
        }

        public TaskExecutorPool(
                int corePoolSize,
                int maximumPoolSize
        ) {
            es=Executors.newScheduledThreadPool(5);
//            es = new ThreadPoolExecutor(
//                    corePoolSize,
//                    maximumPoolSize,
//                    60, // long keepAliveTime,
//                    TimeUnit.SECONDS, // TimeUnit unit,
//                    workQueue // BlockingQueue<Runnable> workQueue
//
//            );
        }
        public TaskExecutorPool(
                int corePoolSize,
                int maximumPoolSize,
                TaskExecutorListener... listeners
        ) {
            this.listeners=listeners;
            es=Executors.newScheduledThreadPool(5);            
//            es = new ThreadPoolExecutor(
//                    corePoolSize,
//                    maximumPoolSize,
//                    60, // long keepAliveTime,
//                    TimeUnit.SECONDS, // TimeUnit unit,
//                    workQueue // BlockingQueue<Runnable> workQueue
//
//            );
        }

        @Override
        public void execute(Object identifier, Runnable... run) {
            if (run != null) {
                for (Runnable r : run) {
                    if (r != null) {
                        es.submit(wrap(identifier, r));
                    }
                }
            }
        }

        @Override
        public void execute(Object identifier, Collection<Runnable>... run) {
            if (run != null) {
                for (Collection<Runnable> rs : run) {
                    for (Runnable r : rs) {
                        if (r != null) {
                            es.submit(wrap(identifier, r));
                        }
                    }
                }
            }
        }

        public void shutdown() {
            es.shutdown();
        }
    }
}
