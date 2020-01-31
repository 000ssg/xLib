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
package ssg.lib.wamp.nodes;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import ssg.lib.wamp.util.WAMPTools;

/**
 * Local clients helper used to enable regular messages processing.
 *
 * @author 000ssg
 */
public class WAMPClients implements Runnable {

    public long stopTimeout = 1000 * 15; // 15 seconds for graceful stop...

    List<WAMPClient> clients = WAMPTools.createSynchronizedList();
    private ExecutorService es; // shared executor service, if none - initiates new thread
    private Boolean interrupt;

    public WAMPClients() {
    }

    public WAMPClients(ExecutorService es) {
        this.es = es;
    }

    public boolean isRunning() {
        return interrupt != null;
    }

    public void start() {
        if (interrupt == null) {
            interrupt = false;
            if (es == null) {
                Thread th = new Thread(this);
                th.setDaemon(true);
                th.start();
            } else {
                es.execute(this);
            }
        }
    }

    public void run() {
        String old = Thread.currentThread().getName();
        try {
            String cn = (getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName();
            Thread.currentThread().setName(cn + ".wamp.runner");
            while (interrupt != null && !interrupt) {
                runCycle();
                Thread.sleep(5);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                Thread.currentThread().setName(old);
            }
            interrupt = null;
        }
    }

    public void addClient(WAMPClient client) {
        clients.add(client);
    }

    public void removeClient(WAMPClient client) {
        if (client != null && clients.contains(client)) {
            clients.remove(client);
        }
    }

    public int runCycle() {
        int processed = 0;
        // publishers
        WAMPClient[] entries = null;
        while (entries == null) {
            try {
                synchronized (clients) {
                    entries = clients.toArray(new WAMPClient[clients.size()]);
                }
            } catch (Throwable th) {
            }
        }
        if (entries != null && entries.length > 0) {
            for (WAMPClient client : entries) {
                if (client.isConnected()) {
                    try {
                        client.runCycle();
                        processed++;
                    } catch (Throwable th) {
                    }
                } else {
                    processed++;
                    removeClient(client);
                }
            }
        }

        processed += runCycleExtra();

        return processed;
    }

    /**
     * Use this to perform regular action within run cycle. Return positive
     * value if has more to do.
     *
     * @return
     */
    public int runCycleExtra() {
        return 0;
    }

    public void stop(String reason) {
        if (interrupt != null && !interrupt) {
            interrupt = true;
            if (!clients.isEmpty()) {
                for (WAMPClient client : clients) {
                    if (client != null && client.isConnected() && client.isSessionEstablished()) {
                        try {
                            client.disconnect(reason);
                        } catch (Throwable th) {
                        }
                    }
                }
            }
            long timeout = System.currentTimeMillis() + stopTimeout;
            while (System.currentTimeMillis() < timeout) {
                try {
                    if (runCycle() > 0) {
                        continue;
                    }
                } catch (Throwable th) {
                }
                break;
            }
        }
    }

    public Collection<WAMPClient> getClients() {
        List<WAMPClient> r = WAMPTools.createList();
        r.addAll(clients);
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("stopTimeout=");
        sb.append(stopTimeout);
        sb.append(", clients=");
        sb.append(clients.size());
        sb.append(", es=");
        sb.append(es != null);
        if (interrupt == null) {
            sb.append(", idle");
        } else if (interrupt) {
            sb.append(", stopping");
        } else {
            sb.append(", running");
        }
        sb.append('}');
        return sb.toString();
    }

}
