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

import java.util.Comparator;
import java.util.List;

/**
 * Task provider returns list of tasks per phase.
 *
 * Tasks may be prioritized.
 *
 *
 * @author sesidoro
 */
public interface TaskProvider {

    public static enum TaskPhase {
        initial,
        regular,
        terminal
    }

    List<Task> getTasks(TaskPhase... phases);

    public static Comparator<Task> getTaskComparator(final boolean startedLast) {
        return new Comparator<Task>() {
            long t = System.currentTimeMillis();

            @Override
            public int compare(Task o1, Task o2) {
                if (startedLast) {
                    if (o1.started != 0 && o2.started == 0) {
                        return -1;
                    }
                    if (o2.started != 0 && o1.started == 0) {
                        return 1;
                    }
                    if (o2.started != 0 && o1.started != 0) {
                        return 0;
                    }
                }
                Long l1 = o1.getStartAt();
                Long l2 = o2.getStartAt();
                if (l1 != 0 && l1 < t) {
                    l1 = 0L;
                }
                if (l2 != 0 && l2 < t) {
                    l2 = 0L;
                }
                int c = l1.compareTo(l2);
                if (c == 0) {
                    Integer i1 = o1.getPriority().order();
                    c = i1.compareTo(o2.getPriority().order());
                }
                return c;
            }
        };
    }

    public static class Task implements Runnable {

        public static enum TaskPriority {
            top,
            high,
            normal,
            medium,
            low;

            public int order() {
                return order(this);
            }

            public static int order(TaskPriority tp) {
                if (tp == null) {
                    return -1;
                }
                switch (tp) {
                    case top:
                        return 0;
                    case high:
                        return 1;
                    case normal:
                        return 2;
                    case medium:
                        return 3;
                    case low:
                        return 4;
                    default:
                        return -1;
                }
            }
        }

        public static enum TaskState {
            initial,
            queued,
            running,
            completed,
            failed
        }
        private long startAt;
        private long started;
        private long completed;
        TaskPriority priority = TaskPriority.normal;
        private TaskState state = TaskState.initial;
        private Runnable runnable;
        private Throwable error;

        public Task(Runnable run) {
            this.runnable = run;
            if (run == null) {
                setState(TaskState.failed);
                started = System.currentTimeMillis();
                completed = started;
            }
        }

        public Task(Runnable run, TaskPriority priority) {
            this.runnable = run;
            this.priority = priority;
            if (run == null) {
                setState(TaskState.failed);
                started = System.currentTimeMillis();
                completed = started;
            }
        }

        @Override
        public void run() {
            if (started > 0) {
                return;
            }
            try {
                started = System.currentTimeMillis();
                setState(TaskState.running);
                getRunnable().run();
                state = TaskState.completed;
            } catch (Throwable th) {
                error = th;
                state = TaskState.failed;
            } finally {
                completed = System.currentTimeMillis();
            }
        }

        /**
         * @return the startAt
         */
        public long getStartAt() {
            return startAt;
        }

        /**
         * @param startAt the startAt to set
         */
        public void setStartAt(long startAt) {
            if (started == 0) {
                this.startAt = startAt;
            }
        }

        /**
         * @return the started
         */
        public long getStarted() {
            return started;
        }

        /**
         * @return the completed
         */
        public long getCompleted() {
            return completed;
        }

        /**
         * @return the state
         */
        public TaskState getState() {
            return state;
        }

        /**
         * @return the state
         */
        public void setState(TaskState state) {
            if (state != null) {
                this.state = state;
            }
        }

        /**
         * @return the runnable
         */
        public Runnable getRunnable() {
            return runnable;
        }

        /**
         * @return the error
         */
        public Throwable getError() {
            return error;
        }

        public TaskPriority getPriority() {
            return priority;
        }

        public long getDuration() {
            if (started > 0) {
                if (completed > 0) {
                    return completed - started;
                } else {
                    return System.currentTimeMillis() - started;
                }
            }
            return 0;
        }
    }
}
