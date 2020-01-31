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
package ssg.lib.service;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Provider statistics represents accumulated info on service processing per
 * provider.
 *
 * Provider is stored as descriptive text (e.g. provider.toString()).
 *
 * Processing is a set of request/response cycles with related optional
 * description and optional error. Each cycle has set of long values as:
 * [start,duration,readBytes,writtenBytes].
 *
 * taskCounters provide per-request/response: [submitted,started,duration]
 */
public class ProviderStatistics implements Serializable, Cloneable {

    String provider;
    long opened; // provider opened (if available)
    long started = System.currentTimeMillis(); // time of starting statistics gathering
    long closed; // time of provider clsoing
    byte[] errCounts;
    long[][] counters;
    String[] counterDescriptions;
    String[] counterErrors;
    long[][][] taskCounters;
    String[][] taskErrors;

    public ProviderStatistics(String provider) {
        this.provider = provider;
    }

    public void setOpened(long opened) {
        this.opened = opened;
    }

    public void close() {
        if (closed == 0) {
            closed = System.currentTimeMillis();
        }
    }

    /**
     * type: 0 - addCounter, 1 - updateCounter, 2 - addTask, 3 - updateTask, 4 -
     * overflow (some error exceeds bmax byte), 5 - invalid type
     *
     * @param type
     */
    void addErrCount(int type) {
        if (type < 0 || type > 5) {
            addErrCount(5);
        }
        if (errCounts == null) {
            errCounts = new byte[6];
        }
        if (errCounts[type] == Byte.MAX_VALUE) {
            if (type < 4 && errCounts[type] < Byte.MAX_VALUE) {
                errCounts[4]++;
            }
        } else {
            errCounts[type]++;
        }
    }

    public void addCounter(String description, Throwable error, long... values) {
        if (closed > 0) {
            addErrCount(0);
            return;
        }
        synchronized (this) {
            if (counters == null) {
                counters = new long[1][4];
                counters[0][0] = System.currentTimeMillis();
                if (values != null) {
                    for (int i = 0; i < Math.min(counters[0].length, values.length); i++) {
                        counters[0][i] = values[i];
                    }
                }
                counterDescriptions = new String[]{description};
                counterErrors = new String[]{error2string(error)};
                taskCounters = new long[1][][];
                taskErrors = new String[1][];
            } else {
                int idx = counters.length;
                counters = Arrays.copyOf(counters, counters.length + 1);
                counterDescriptions = Arrays.copyOf(counterDescriptions, counterDescriptions.length + 1);
                counterErrors = Arrays.copyOf(counterErrors, counterErrors.length + 1);
                taskCounters = Arrays.copyOf(taskCounters, taskCounters.length + 1);
                taskErrors = Arrays.copyOf(taskErrors, taskErrors.length + 1);
                counters[idx] = new long[4];
                counterDescriptions[idx] = description;
                counterErrors[idx] = error2string(error);
                counters[idx][0] = System.currentTimeMillis();
                if (values != null) {
                    for (int i = 0; i < Math.min(counters[idx].length, values.length); i++) {
                        counters[idx][i] = values[i];
                    }
                }
            }
        }
    }

    public void updateCounter(String description, Throwable error, long duration, long readBytes, long writeBytes) {
        if (counters == null || closed > 0) {
            if(closed>0)addErrCount(1);
            return;
        }
        synchronized (this) {
            int idx = counters.length - 1;
            if (description != null) {
                counterDescriptions[idx] = description;
            }
            if (error != null) {
                counterErrors[idx] = error2string(error);
            }
            if (duration != 0) {
                counters[idx][1] = duration;
            } else {
                counters[idx][1] = System.currentTimeMillis() - counters[idx][0];
            }
            if (readBytes > 0) {
                counters[idx][2] += readBytes;
            }
            if (writeBytes > 0) {
                counters[idx][3] += writeBytes;
            }
        }
    }

    public void updateRAMCounter() {
        if (counters == null || closed > 0) {
            return;
        }
        synchronized (this) {
            int idx = counters.length - 1;
            if (counters[idx].length < 7) {
                counters[idx] = Arrays.copyOf(counters[idx], 7);
            }
            Runtime rt = Runtime.getRuntime();
            counters[idx][4] = rt.freeMemory();
            counters[idx][5] = rt.totalMemory();
            counters[idx][6] = rt.maxMemory();
        }
    }

    public void addTask(long submittedAt) {
        if (closed > 0) {
            addErrCount(2);
            return;
        }
        if (taskCounters != null) {
            long[][] tc = taskCounters[taskCounters.length - 1];
            if (tc == null) {
                tc = new long[1][3];
                taskCounters[taskCounters.length - 1] = tc;
            } else {
                int idx = taskCounters.length - 1;
                tc = taskCounters[idx];
                tc = Arrays.copyOf(tc, tc.length + 1);
                tc[tc.length - 1] = new long[3];
                taskCounters[idx] = tc;
                taskErrors[idx] = Arrays.copyOf(taskErrors[idx], taskErrors[idx].length + 1);
            }
            tc[tc.length - 1][0] = submittedAt;
        }
    }

    public void updateTask(long startedAt, long duration, Throwable error) {
        if (taskCounters == null || closed > 0) {
            if(closed>0)addErrCount(3);
            return;
        }
        long[][] tc = taskCounters[taskCounters.length - 1];
        String[] te = taskErrors[taskErrors.length - 1];
        if (tc != null && tc.length > 0) {
            int idx = tc.length - 1;
            tc[idx][1] = startedAt;
            tc[idx][2] = duration;
            if (te == null) {
                te = new String[]{error2string(error)};
                taskErrors[taskErrors.length - 1] = te;
            } else {
                if (te.length <= idx) {
                    te = Arrays.copyOf(te, idx + 1);
                    taskErrors[taskErrors.length - 1] = te;
                    te[idx] = error2string(error);
                }
            }
        }
    }

    String error2string(Throwable th) {
        if (th == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(th.getClass().getName());
        sb.append(": ");
        sb.append(th.getMessage());
        for (StackTraceElement ste : th.getStackTrace()) {
            sb.append("\n  ");
            sb.append(ste.getClassName());
            sb.append(".");
            sb.append(ste.getMethodName());
            sb.append(": ");
            sb.append(ste.getFileName());
            sb.append("(");
            sb.append(ste.getLineNumber());
            sb.append(")");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{" + "provider=" + provider);
        DateFormat tf = new SimpleDateFormat("HH:mm:ss.SSS");
        if (opened > 0) {
            sb.append(", opened=" + tf.format(new Date(opened)));
        }
        sb.append(", started=" + tf.format(new Date(started)));
        if (closed > 0) {
            sb.append(", closed=" + tf.format(new Date(closed)));
            sb.append(", period=" + (closed - started));
            if (opened > 0) {
                sb.append("/" + (closed - opened));
            }
            sb.append("ms");
        } else {
            long t = System.currentTimeMillis();
            sb.append(", wip=" + (t - started));
            if (opened > 0) {
                sb.append("/" + (t - opened));
            }
            sb.append("ms");
        }
        if(errCounts!=null) {
            sb.append(", errCount=");
            if(errCounts[0]>0) sb.append(" addCounter:"+errCounts[0]);
            if(errCounts[1]>0) sb.append(" updateCounter:"+errCounts[1]);
            if(errCounts[2]>0) sb.append(" addTask:"+errCounts[2]);
            if(errCounts[3]>0) sb.append(" updateTask:"+errCounts[3]);
            if(errCounts[4]>0) sb.append(" overflow:"+errCounts[4]);
            if(errCounts[5]>0) sb.append(" unknown:"+errCounts[5]);
        }
        if (counters != null) {
            sb.append(", counters=" + counters.length);
            for (int i = 0; i < counters.length; i++) {
                long[] ll = counters[i];
                sb.append("\n  [");
                sb.append(tf.format(new Date(ll[0])));
                sb.append(", ");
                sb.append(ll[1]); // / 1000000f);
                sb.append("ms]");
                sb.append(", read=");
                sb.append(ll[2]);
                sb.append(", write=");
                sb.append(ll[3]);
                if (ll.length > 6) {
                    sb.append(", RAM(f/t/m)=");
                    sb.append(ll[4] / 1024 / 1024f);
                    sb.append("/");
                    sb.append(ll[5] / 1024 / 1024f);
                    sb.append("/");
                    sb.append(ll[6] / 1024 / 1024f);
                    sb.append("MB");
                }
                sb.append(", ");
                sb.append(counterDescriptions[i]);
                if (counterErrors[i] != null) {
                    sb.append("\n  ERROR:");
                    sb.append(counterErrors[i].replace("\n", "\n  "));
                }
                if (taskCounters[i] != null) {
                    sb.append("\n TASKS[" + taskCounters[i].length + "]: submitted/started (+ms)/duration");
                    for (int j = 0; j < taskCounters[i].length; j++) {
                        long[] tcs = taskCounters[i][j];
                        sb.append("\n    ");
                        sb.append(tf.format(new Date(tcs[0])));
                        sb.append("/+");
                        sb.append(tcs[1] - tcs[0]);
                        sb.append("/");
                        sb.append(tcs[2] / 1000000f);
                        sb.append("ms");
                        if (taskErrors[i]!=null && taskErrors[i][j] != null) {
                            sb.append("\n    ERROR: ");
                            sb.append(taskErrors[i][j].replace("\n", "\n      "));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

}
