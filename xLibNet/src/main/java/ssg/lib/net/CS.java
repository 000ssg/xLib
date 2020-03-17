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
package ssg.lib.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.di.DI;
import ssg.lib.di.DM;

/**
 *
 * @author 000ssg
 */
public class CS implements Runnable {

    public static int scheduledPoolSize = 5;

    private static final CSListener[] noListeners = new CSListener[0];
    private ScheduledExecutorService scheduler;
    private boolean ownedScheduler = false;
    String title;
    Selector selector;
    ScheduledFuture<?> executor;
    private boolean executorIsActive = false;
    List<CSListener> listeners = new ArrayList<>();
    List<CSGroup> groups = new ArrayList<>();
    Map<Handler, Boolean> registered = Collections.synchronizedMap(new HashMap<>());
    Map<Object, ByteBuffer[]> unwritten = new ConcurrentHashMap<>();// Collections.synchronizedMap(new HashMap<>()); // keep fetched but not written data...
    long inactivityTimeout = 1000 * 60 * 5;

    public CS() {
    }

    public CS(String title) {
        this.title = title;
    }

    public CS(String title, ScheduledExecutorService scheduler) {
        this.title = title;
        this.scheduler = scheduler;
        ownedScheduler = false;
    }

    public synchronized void start() throws IOException {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(scheduledPoolSize);
            ownedScheduler = true;
        }
        selector = Selector.open();
        executor = scheduler.schedule(this, 1, TimeUnit.NANOSECONDS);
    }

    public boolean isRunning() {
        return executor != null && selector != null;
    }

    public void onStopped(Throwable error) {
        if (error != null) {
            error.printStackTrace();
        }
    }

    public synchronized void stop() throws IOException {
        for (CSGroup l : groups) {
            try {
                l.onStop(this);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (executor != null) {
            executor.cancel(false);
        }
        executor = null;
        if (!registered.isEmpty()) {
            for (Entry<Handler, Boolean> h : registered.entrySet()) {
                if (h.getValue()) {
                    h.setValue(Boolean.FALSE);
                    h.getKey().unregister(selector);
                }
            }
        }
        if (selector != null) {
            selector.close();
        }
        selector = null;
        for (CSListener l : listeners()) {
            l.onStopped(this);
        }
    }

    public <Z extends CS> Z addCSListener(CSListener... ls) {
        if (ls != null && ls.length > 0) {
            for (CSListener l : ls) {
                if (l == null || listeners.contains(l)) {
                    continue;
                }
                listeners.add(l);
            }
        }
        return (Z) this;
    }

    public void removeCSListener(CSListener... ls) {
        if (ls != null && ls.length > 0) {
            for (CSListener l : ls) {
                if (l != null && listeners.contains(l)) {
                    listeners.remove(l);
                }
            }
        }
    }

    public <Z extends CS> Z addCSGroup(CSGroup... ls) {
        if (ls != null && ls.length > 0) {
            for (CSGroup l : ls) {
                if (l == null || groups.contains(l)) {
                    continue;
                }
                groups.add(l);
                if (isRunning()) {
                    l.onStarted(this);
                }
            }
        }
        return (Z) this;
    }

    public void removeCSGroup(CSGroup... ls) {
        if (ls != null && ls.length > 0) {
            for (CSGroup l : ls) {
                if (l != null && groups.contains(l)) {
                    groups.remove(l);
                    l.onStop(this);
                }
            }
        }
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduler;
    }

    CSListener[] listeners() {
        return (listeners.isEmpty()) ? noListeners : listeners.toArray(new CSListener[listeners.size()]);
    }

    @Override
    public void run() {
        if (executorIsActive) {
            throw new RuntimeException("Failed to re-run already active " + getClass().getName());
        }

        executorIsActive = true;

        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName("CS: " + ((title != null) ? title : System.identityHashCode(this)));

        Throwable error = null;
        try {
            for (CSGroup l : groups) {
                l.onStarted(this);
            }
            for (CSListener l : listeners()) {
                l.onStarted(this);
            }

            Handler handler = null;
            try {
                if (!registered.isEmpty()) {
                    for (Entry<Handler, Boolean> h : registered.entrySet()) {
                        handler = h.getKey();
                        if (!h.getValue()) {
                            handler.register(selector);
                            h.setValue(Boolean.TRUE);
                        }
                    }
                }
            } catch (Throwable th) {
                onError("Failed to register some handler(s): " + handler, th);
            }

            /////////////////////////////////////// cyclic channel verification
            // timestamps for last channel non-idle activity
            Map<SelectionKey, Long> lastIO = new HashMap<>();
            // new keys recognition support: if accept connection -> pre/post keys mismatch
            Set<SelectionKey> preKeys = new HashSet();
            // timestampe for next check,1st check on 1st run
            long nextCheck = System.currentTimeMillis();
            long nextHealthCheck = nextCheck - (long) (inactivityTimeout / 2.3);

            while (true) {
                long ts = System.currentTimeMillis();
                try {
                    if (selector == null) {
                        break;
                    }
                    // NOTE: need to invoke selectNow to prepare selected keys
                    int readyChannels = selector.selectNow();
                    if (readyChannels == 0) {
                        // no activities for registered channels... -> release 1ms...
                        try {
                            Thread.sleep(1);
                        } catch (Throwable th) {
                            break;
                        }
                    } else {
                        Set<SelectionKey> skeys = selector.selectedKeys();
                        Iterator<SelectionKey> keys = skeys.iterator();
                        while (keys.hasNext()) {
                            SelectionKey key = keys.next();
                            try {
                                SelectableChannel sc = key.channel();
                                if (key.attachment() instanceof Handler) {
                                    SelectionKey[] newKeys = ((Handler) key.attachment()).onHandle(key);
                                    if (newKeys != null && newKeys.length > 0) {
                                        // new channel -> initialize I/O timestamp
                                        for (SelectionKey sk : newKeys) {
                                            lastIO.put(sk, ts);
                                        }
                                    }
                                } else if (key.attachment() instanceof DI) {
                                    DI<ByteBuffer, Channel> di = (DI<ByteBuffer, Channel>) key.attachment();
                                    if (key.isReadable()) {
                                        long c = 0;
                                        if (di.isReady(sc)) {
                                            ByteBuffer buf = read(sc);
                                            if (buf != null && buf.hasRemaining()) {
                                                lastIO.put(key, ts);
                                                for (CSListener l : listeners()) {
                                                    l.onRead(this, key, buf);
                                                }
                                                c += di.write(sc, Collections.singletonList(buf));
                                            } else if (buf == null) {
                                                di.onProviderEvent(sc, DM.PN_INPUT_CLOSED);
                                                // EOF -> close?
                                                int a = 0;
                                            } else {
                                                lastIO.put(key, ts);
                                            }
//                                    if (c > 0) {
//                                        System.out.println("CS.read : " + c);
//                                    }
                                        }
                                    }
                                    if (key.isWritable()) {
                                        ByteBuffer[] bbs = unwritten.remove(key);
                                        long bbsSize = BufferTools.getRemaining(bbs);
//                                        if (bbsSize == 0) {
//                                            unwritten.remove(key);
//                                        }
                                        boolean processedNewData = false;
                                        boolean addedUnwritten = false;
                                        boolean actualWrite = false;
                                        while (!(processedNewData || addedUnwritten)) {
                                            if (bbsSize > 0) {
                                                //unwritten.remove(key);
                                                //System.out.println("READ from UNREAD[" + System.identityHashCode(sc) + "]: " + bbsSize);
                                                bbsSize = 0;
                                            } else {
                                                processedNewData = true;
                                                List<ByteBuffer> bufs = ((DI<ByteBuffer, Channel>) key.attachment()).read(sc);
                                                if (bufs != null && !bufs.isEmpty()) {
                                                    for (CSListener l : listeners()) {
                                                        l.onWrite(this, key, bufs);
                                                    }
                                                    bbs = bufs.toArray(new ByteBuffer[bufs.size()]);
                                                }
                                            }

                                            if (bbs != null && bbs.length > 0 && bbs[0] != null) {
                                                long c0 = BufferTools.getRemaining(bbs);
                                                long c = write(key.channel(), bbs); //  ((SocketChannel) key.channel()).write(bbs);

                                                if (c < c0) {
                                                    // keep unwritten output for next write session...
                                                    addedUnwritten = true;
                                                    int len = bbs.length;
                                                    bbs = BufferTools.getNonEmpties(bbs);
                                                    unwritten.put(key, bbs);
                                                    //System.out.println("DO        UNREAD[" + System.identityHashCode(sc) + "]: " + BufferTools.getRemaining(bbs) + " [" + c + "/" + c0 + "; " + bbs.length + "/" + len + "]");
                                                }
                                                if (c > 0 || c0 > 0) {
                                                    actualWrite = true;
                                                }
//                                        if (c > 0) {
//                                            System.out.println("CS.write: " + c + "/" + c0);
//                                        }
                                            }
                                        }
                                        if (actualWrite) {
                                            lastIO.put(key, ts);
                                        }
                                    }
                                } else {
                                    if (key.isReadable()) {
                                        lastIO.put(key, ts);
                                    }
                                }
                                if (!key.isValid()) {
                                    for (CSListener l : listeners()) {
                                        l.onInvalid(this, key);
                                    }
                                    key.cancel();
                                    if (unwritten.containsKey(key)) {
                                        unwritten.remove(key);
                                    }
                                }
                            } catch (IOException ioex) {
                                //ioex.printStackTrace();
                                if (key.attachment() instanceof DM) {
                                    ((DM) key.attachment()).delete(key.channel());
                                }
                                try {
                                    lastIO.remove(key);
                                    key.channel().close();
                                } catch (Throwable th) {
                                    // just ensure it is closed
                                }
                                if (unwritten.containsKey(key)) {
                                    unwritten.remove(key);
                                }
                            } catch (Throwable th) {
                                if (th instanceof CancelledKeyException) {
                                    // do not dump cancelled keys: no extra
                                } else {
                                    th.printStackTrace();
                                }
                                try {
                                    //System.err.println(getClass().getSimpleName() + ".run: trying to delete/close " + key.channel() + ", " + key.attachment() + " beacaue of " + th);
                                    if (key.attachment() instanceof DM) {
                                        ((DM) key.attachment()).delete(key.channel());
                                    }
                                    try {
                                        key.channel().close();
                                        lastIO.remove(key);
                                    } catch (Throwable th1) {
                                    }
                                    if (unwritten.containsKey(key)) {
                                        unwritten.remove(key);
                                    }
                                } catch (Throwable th1) {
                                }

                            } finally {
                                keys.remove();
                            }
                        }
                        if (executor == null || executor.isCancelled()) {
                            break;
                        }
                    }
                } catch (ClosedSelectorException csex) {
                    break;
                } catch (ConcurrentModificationException cmex) {
                    break;
                } catch (Throwable th) {
                    if (th instanceof ClosedByInterruptException) {
                        int a = 0;
                    } else {
                        error = th;
                        //th.printStackTrace();
                    }
                    break;
                }

                if (ts >= nextHealthCheck) {
                    for (SelectionKey key : selector.keys()) {
                        if (key.attachment() instanceof DM) {
                            try {
                                ((DM) key.attachment()).healthCheck(key.channel());
                                lastIO.put(key, ts);
                            } catch (Throwable th) {
                                try {
                                    ((DM) key.attachment()).delete(key.channel());
                                } catch (Throwable th1) {
                                }
                                try {
                                    lastIO.remove(key);
                                    key.channel().close();
                                } catch (Throwable th1) {
                                    // just ensure it is closed
                                }
                                if (unwritten.containsKey(key)) {
                                    unwritten.remove(key);
                                }
                            }
                        }
                    }
                    nextHealthCheck = nextCheck + 1;//ts - (long) (inactivityTimeout / 2.3);
                }
                if (ts >= nextCheck) {
                    if (lastIO.isEmpty()) {
                        nextCheck = ts + inactivityTimeout / 2;
                        nextHealthCheck = nextCheck - inactivityTimeout / 3;
                    } else {
                        try {
                            // force close channels with no activity
                            Set<SelectionKey> close = new HashSet<>();
                            // drop missing
                            Set<SelectionKey> postKeys = selector.keys();
                            for (SelectionKey sk : lastIO.keySet().toArray(new SelectionKey[lastIO.size()])) {
                                if (!postKeys.contains(sk)) {
                                    lastIO.remove(sk);
                                }
                            }
                            // check timeouts
                            for (Entry<SelectionKey, Long> ske : lastIO.entrySet()) {
                                if ((ts - ske.getValue()) >= inactivityTimeout) {
                                    close.add(ske.getKey());
                                }
                            }
                            if (!close.isEmpty()) {
                                for (SelectionKey key : close) {
                                    lastIO.remove(key);
                                    try {
                                        try {
                                            if (key.attachment() instanceof DM) {
                                                ((DM) key.attachment()).delete(key.channel());
                                            }
                                        } catch (Throwable th1) {
                                        }
                                        key.channel().close();
                                    } catch (Throwable th2) {
                                        // just ensure closing error does not prevent subsequent close operations.
                                    }
                                }
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        } finally {
                            nextCheck = ts + inactivityTimeout;
                            nextHealthCheck = nextCheck - (long) (inactivityTimeout / 2.3);
                        }
                    }
                }
            }
        } finally {
            try {
                onStopped(error);
            } finally {
                executorIsActive = false;
                Thread.currentThread().setName(oldName);
                if (scheduler != null && ownedScheduler) {
                    scheduler.shutdown();
                    scheduler = null;
                }
            }
        }
    }

    public void add(Handler handler) throws IOException {
        if (handler != null) {
            if (selector != null) {
                handler.register(selector);
                registered.put(handler, true);
            } else {
                registered.put(handler, false);
            }
        }
        for (CSListener l : listeners()) {
            l.onAdded(this, handler);
        }
    }

    public void remove(Handler handler) throws IOException {
        if (handler != null) {
            if (registered.containsKey(handler)) {
                registered.remove(handler);
            }
            handler.unregister(selector);
        }
        for (CSListener l : listeners()) {
            l.onRemoved(this, handler);
        }
    }

    public boolean hasTCPHandler(int port) {
        if (port <= 0 || port > 0xFFFF) {
            return false;
        }
        for (Handler h : registered.keySet()) {
            if (h instanceof TCPHandler) {
                TCPHandler tcp = (TCPHandler) h;
                SocketAddress[] saddrs = tcp.getAddresses();
                if (saddrs != null) {
                    for (SocketAddress saddr : saddrs) {
                        if (NetTools.getPort(saddr) == port) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean hasUDPHandler(int port) {
        if (port <= 0 || port > 0xFFFF) {
            return false;
        }
        for (Handler h : registered.keySet()) {
            if (h instanceof UDPHandler) {
                UDPHandler udp = (UDPHandler) h;
                SocketAddress saddr = udp.getAddress();
                if (NetTools.getPort(saddr) == port) {
                    return true;
                }
            }
        }
        return false;
    }

    public ByteBuffer read(Channel sc) throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(1024 * 16);
        int c = (sc instanceof ByteChannel)
                ? ((ByteChannel) sc).read(buf)
                : readNotByteChannel(sc, buf);
        if (c == -1) {
            return null;
        }
        if (c > 0) {
            ((Buffer) buf).flip();
        }
        return buf;
    }

    /**
     * Channel reader to enable non-ByteChannel channels. by default throws
     * exception.
     *
     * @param sc
     * @param buf
     * @return
     * @throws IOException
     */
    public int readNotByteChannel(Channel sc, ByteBuffer buf) throws IOException {
        throw new IOException("Unsupported channel: " + sc + ". Need to return ByteBuffer on read operation.");
    }

    public long write(Channel sc, ByteBuffer... bbs) throws IOException {
        return ((SocketChannel) sc).write(bbs);
    }

    public void onError(String message, Throwable th) {
        System.err.println(message);
        if (th != null) {
            th.printStackTrace();
        }
    }

    public Selector selector() {
        return selector;
    }

}
