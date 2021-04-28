/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.SSLHandshakeException;
import ssg.lib.common.Config;
import static ssg.lib.common.net.NetTools.delay;
import ssg.lib.net.MCSListener.MCSListenerX;
import ssg.lib.net.stat.MCSStatistics;
import ssg.lib.net.stat.RunnerStatistics;

/**
 * MCS provides multi-CS (channel selector) instance with configurable number of
 * pure accept/data or just multiple accept/data selector handlers.
 *
 * Use listener() to register server listener with listener-specific
 * ConnectionIO.
 *
 * Use connect() to open socket channel for URI (host:port) with specific
 * ConnectionIO.
 *
 * @author 000ssg
 */
public class MCS implements MCSSelector {

    public static int defaultGuessedPoolSize = 100;

    /**
     * Processes (threads) provider.
     */
    //static ScheduledExecutorService executor = Executors.newScheduledThreadPool(100);
    // MCS title (for item/threads identification)
    String name;

    // threading
    ScheduledExecutorService executor;
    boolean ownExecutor = true;
    int guessedPoolSize = defaultGuessedPoolSize;

    // selector processing module
    private Runner[] dataHandlers;
    private int maxAcceptor;
    private int minHandler;
    private Integer acceptorIdx = 0;
    private Integer handlerIdx = 0;

    // networking
    // size of shared read buffer (per Runner)
    int readBufferSize = 1024 * 8;
    // check interval - connection verification period
    long checkInterval = 1000 * 5; // 5sec

    // statistics
    MCSStatistics stat;

    // listeners
    MCSListener[] listeners = new MCSListener[0];
    MCSListenerX[] xlisteners = new MCSListenerX[0];

    // exception hiding
    Collection<Class> noTraceException = new HashSet<Class>() {
        {
            add(SSLHandshakeException.class);
            add(IOException.class);
            add(ClosedSelectorException.class);
            add(CancelledKeyException.class);
        }
    };

    public MCS() {
        configureHandlers(1, 0);
    }

    public MCS(ScheduledExecutorService executor) {
        this.executor = executor;
        if (executor != null) {
            ownExecutor = false;
        }
        configureHandlers(1, 0);
    }

    public MCS(int acceptors, int dataHandlers) {
        configureHandlers(acceptors, dataHandlers);
    }

    public MCS(ScheduledExecutorService executor, int acceptors, int dataHandlers) {
        this.executor = executor;
        if (executor != null) {
            ownExecutor = false;
        }
        configureHandlers(acceptors, dataHandlers);
    }

    /**
     * Returns guessed threads pool size that is added to threads needed for
     * data handlers when creaing own executor.
     *
     * @return
     */
    public int guessPoolSize() {
        return guessedPoolSize > 1 && guessedPoolSize < 1000 ? guessedPoolSize : 100;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return executor;
    }

    public MCS configureName(String name) {
        this.name = name;
        return this;
    }

    public MCS configureStatistics(MCSStatistics stat) {
        this.stat = stat;
        return this;
    }

    public MCS configureHandlers(int acceptors, int dataHandlers) {
        if (this.dataHandlers == null || this.dataHandlers.length == 0 || this.dataHandlers[0] == null) {
            if (acceptors < 1) {
                acceptors = 1;
            } else if (acceptors > 10) {
                acceptors = 10;
            }
            if (dataHandlers < 0) {
                dataHandlers = 0;
            } else if (dataHandlers > 100) {
                dataHandlers = 100;
            }
            // [acceptors][dataHandlers]
            // 0         |            max
            this.dataHandlers = new Runner[acceptors + dataHandlers];
            maxAcceptor = acceptors - 1;
            minHandler = dataHandlers > 0 ? acceptors : 0;
            acceptorIdx = 0;
            handlerIdx = minHandler;

            if (executor == null) {
                executor = Executors.newScheduledThreadPool(this.dataHandlers.length + guessPoolSize());
            }
        }
        return this;
    }

    /**
     * Apply configuration for initial setup.
     *
     * @param configs
     * @return
     */
    public MCS configuration(Config... configs) throws IOException {
        if (configs != null) {
            for (Config config : configs) {
                if (config instanceof MCSConfig) {
                    MCSConfig mc = (MCSConfig) config;
                    // name
                    if (mc.name != null) {
                        configureName(mc.name);
                    }
                    // threading
                    if (mc.guessedProcessors != null) {
                        guessedPoolSize = mc.guessedProcessors;
                    }
                    if (mc.acceptors != null || mc.dataHandlers != null) {
                        configureHandlers(
                                mc.acceptors != null
                                        ? mc.acceptors
                                        : 1,
                                mc.dataHandlers != null
                                        ? mc.dataHandlers
                                        : 0);
                    }
                    if (mc.bufferSize != null && mc.bufferSize > 0 && mc.bufferSize < 1024 * 64) {
                        readBufferSize = mc.bufferSize;
                    }
                    if (mc.checkInterval != null && mc.checkInterval > 0) {
                        checkInterval = mc.checkInterval;
                    }
                    if (mc.noTrace != null) {
                        for (String s : mc.noTrace) {
                            if (s == null || s.trim().isEmpty()) {
                                continue;
                            }
                            String[] ss = s.split(",");
                            for (String si : ss) {
                                si = si.trim();
                                if (si.startsWith("-")) {
                                    if ("-all".equals(si)) {
                                        noTraceException.clear();
                                    } else {
                                        String cn = si.substring(1);
                                        try {
                                            Class cl = Class.forName(cn);
                                            if (cl != null && Throwable.class.isAssignableFrom(cl)) {
                                                noTraceException.remove(cl);
                                            }
                                        } catch (Throwable th) {
                                            th.printStackTrace();
                                        }
                                    }
                                } else {
                                    String cn = si.startsWith("+") ? si.substring(1) : si;
                                    try {
                                        if (cn.indexOf('.') == -1) {
                                            cn = "java.lang." + cn;
                                        }
                                        Class cl = Class.forName(cn);
                                        if (cl != null && Throwable.class.isAssignableFrom(cl)) {
                                            noTraceException.add(cl);
                                        }
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    /**
     * Returns selector for server-side connections listening and for pending
     * client connections.
     *
     * @return
     */
    Selector acceptor() {
        int idx = 0;
        synchronized (acceptorIdx) {
            idx = acceptorIdx++;
            if (acceptorIdx > maxAcceptor) {
                acceptorIdx = 0;
            }
        }
        //System.out.println("ACCEPTOR "+idx);
        return dataHandlers != null && dataHandlers.length > idx
                ? dataHandlers[idx] != null
                        ? dataHandlers[idx].selector
                        : null
                : null;
    }

    /**
     * Returns selector for I/O operations. may change from call to call to
     * evenly distribute connections between selectors.
     *
     * @return
     */
    Selector selector() {
        // if no separate data handlers - just use next acceptor
        if (handlerIdx == 0) {
            return acceptor();
        }
        int idx = minHandler;
        synchronized (handlerIdx) {
            idx = handlerIdx++;
            if (handlerIdx > dataHandlers.length - 1) {
                handlerIdx = minHandler;
            }
        }
        //System.out.println("SELECTOR "+idx);
        return dataHandlers != null && dataHandlers.length > idx
                ? dataHandlers[idx] != null
                        ? dataHandlers[idx].selector
                        : null
                : null;
    }

    /**
     * MCSSelector: returns acceptor() (has option OP_ACCEPT or OP_CONNECT) or
     * selector().
     *
     * @param options
     * @return
     */
    @Override
    public Selector selector(int options) {
        return (options & (OP_ACCEPT | OP_CONNECT)) != 0 ? acceptor() : selector();
    }

    public void listen(SocketAddress sa, ConnectionIO connectionHandler) {
        Selector acs = acceptor();
        try {
            ServerSocketChannel server = ServerSocketChannel.open();
            server.bind(sa);
            server.configureBlocking(false);
            server.register(acs, SelectionKey.OP_ACCEPT, connectionHandler);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public SocketChannel connect(URI uri, ConnectionIO connectionHandler) throws IOException {
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.register(acceptor(), SelectionKey.OP_CONNECT, connectionHandler);
        sc.connect(new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort()));
        if (stat != null) {
            stat.getRunnerStatistics().onConnect();
        }
        return sc;
    }

    /**
     * Returns default attachment if not defined otherwise.
     *
     * @param sc
     * @param attachment
     * @return
     */
    public RunnerIO attachmentFor(SocketChannel sc, Object attachment) {
        return attachment instanceof RunnerIO ? (RunnerIO) attachment : null;
    }

    public void waitInitialized() {
        long timeout = System.currentTimeMillis() + 1000 * 60;
        if (dataHandlers != null && dataHandlers.length > 0) {
            if (System.currentTimeMillis() >= timeout) {
                return;
            }
            if (dataHandlers[0] != null && dataHandlers[0].selector != null) {
                return;
            }
        }
    }

    public void start() throws IOException {
        if (dataHandlers == null || dataHandlers.length == 0 || dataHandlers[0] == null) {
            try {
                for (int i = 0; i <= maxAcceptor; i++) {
                    dataHandlers[i] = new Runner("acceptor" + (i > 0 ? "-" + i + "/" + (maxAcceptor + 1) : ""));
                }
                if (minHandler > 0) {
                    for (int i = minHandler; i < dataHandlers.length; i++) {
                        dataHandlers[i] = new Runner("data-handler-" + (i - minHandler) + "/" + (dataHandlers.length - minHandler));
                    }
                }
                for (Runner r : dataHandlers) {
                    if (r != null) {
                        executor.execute(r);
                    }
                }

                int ic = 0;
                while (ic < dataHandlers.length) {
                    ic = 0;
                    for (Runner r : dataHandlers) {
                        if (r.selector != null && r.selector.isOpen()) {
                            ic++;
                        }
                    }
                }
                for (MCSListener l : listeners) {
                    try {
                        l.onStarted(this);
                    } catch (Throwable th) {
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    public void stop() throws IOException {
        if (isRunning()) {
            try {
                if (dataHandlers != null) {
                    for (int i = 0; i < dataHandlers.length; i++) {
                        synchronized (dataHandlers) {
                            Runner r = dataHandlers[i];
                            try {
                                if (r != null && r.selector != null) {
                                    r.selector.close();
                                }
                            } catch (Throwable th) {
                            }
                        }
                    }
                }
                for (MCSListener l : listeners) {
                    try {
                        l.onStopped(this);
                    } catch (Throwable th) {
                    }
                }
            } catch (Throwable th) {
                for (MCSListener l : listeners) {
                    try {
                        l.onError(this, th);
                    } catch (Throwable th1) {
                    }
                }
                th.printStackTrace();
            } finally {
                if (ownExecutor) {
                    executor.shutdownNow();
                    executor = null;
                }
            }
        }
    }

    /**
     * "Preemptive" selector handling. If key is handled - return true,
     * otherwise standard handling is expected.
     *
     * No actions by default.
     *
     * @param runner
     * @param key
     * @return
     * @throws Exception
     */
    public boolean tryKeyHandling(Runner runner, SelectionKey key, RunnerStatistics stat) throws Exception {
        return false;
    }

    public boolean isRunning() {
        return dataHandlers != null && dataHandlers.length > 0 && dataHandlers[0] != null;
    }

    public RunnerIO onAccepted(Runner runner, SocketChannel ch, RunnerIO attachment) {
        return attachment;
    }

    public RunnerIO onConnected(Runner runner, SocketChannel ch, RunnerIO attachment) {
        return attachment;
    }

    public void onClosed(Runner runner, SelectionKey key, int keyOpt, RunnerIO attachment, Throwable th) {
    }

    public void onNoTraceException(Runner runner, Throwable th) {

    }

    public void addListener(MCSListener l) {
        if (l != null) {
            synchronized (listeners) {
                boolean ok = true;
                for (MCSListener li : listeners) {
                    if (l.equals(li)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    listeners = Arrays.copyOf(listeners, listeners.length + 1);
                    listeners[listeners.length - 1] = l;
                }
            }
            if (l instanceof MCSListenerX) {
                synchronized (xlisteners) {
                    boolean ok = true;
                    for (MCSListenerX li : xlisteners) {
                        if (l.equals(li)) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        xlisteners = Arrays.copyOf(xlisteners, xlisteners.length + 1);
                        xlisteners[xlisteners.length - 1] = (MCSListenerX) l;
                    }
                }
            }
        }
    }

    public void removeListener(MCSListener l) {
        if (l != null) {
            synchronized (listeners) {
                int idx = -1;
                for (int i = 0; i < listeners.length; i++) {
                    if (l.equals(listeners[i])) {
                        idx = i;
                        break;
                    }
                }
                if (idx != -1) {
                    for (int i = idx; i < listeners.length - 1; i++) {
                        listeners[i] = listeners[i + 1];
                    }
                    listeners = Arrays.copyOf(listeners, listeners.length - 1);
                }
            }
            if (l instanceof MCSListenerX) {
                synchronized (xlisteners) {
                    int idx = -1;
                    for (int i = 0; i < xlisteners.length; i++) {
                        if (l.equals(xlisteners[i])) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx != -1) {
                        for (int i = idx; i < xlisteners.length - 1; i++) {
                            xlisteners[i] = xlisteners[i + 1];
                        }
                        xlisteners = Arrays.copyOf(xlisteners, xlisteners.length - 1);
                    }
                }
            }
        }
    }

    public MCSListener[] listeners() {
        return listeners;
    }

    public MCSListenerX[] xlisteners() {
        return xlisteners;
    }

    /**
     * Runner closing helper: removes closed runner from service tables.
     *
     * @param runner
     * @return
     */
    boolean cleanup(Runner runner) {
        if (dataHandlers != null) {
            for (int i = 0; i < dataHandlers.length; i++) {
                if (dataHandlers[i] == runner) {
                    dataHandlers[i] = null;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("name=" + name);
        //sb.append(", active=" + active.size());
        sb.append(", dataHandlers=" + (dataHandlers != null ? dataHandlers.length : 0));
        sb.append(", maxAcceptor=" + maxAcceptor);
        sb.append(", minHandler=" + minHandler);
        sb.append(", acceptorIdx=" + acceptorIdx);
        sb.append(", handlerIdx=" + handlerIdx);
        sb.append(", readBufferSize=" + readBufferSize);
        sb.append(", checkInterval=" + checkInterval);
        sb.append(", no trace=" + noTraceException.size());
        if (!noTraceException.isEmpty()) {
            sb.append("\n  noTraceException[" + noTraceException.size() + "]:");
            for (Class cl : noTraceException) {
                sb.append("\n    " + cl.getCanonicalName());
            }
        }
        if (dataHandlers != null) {
            for (int i = 0; i < dataHandlers.length; i++) {
                sb.append("\n  [" + i + "] ");
                if (dataHandlers[i] != null) {
                    sb.append(dataHandlers[i].toString().replace("\n", "\n    "));
                } else {
                    sb.append("<none>");
                }
            }
        }
        if (stat != null) {
            sb.append("\n  stat=" + stat.dumpStatistics(false).replace("\n", "\n  "));
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    public class Runner implements Runnable {

        String title;
        Selector selector;
        Runnable handler;

        long started;
        RunnerStatistics stat;
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(readBufferSize);
        Map<SocketChannel, ByteBuffer> unread = new HashMap<>();
        Map<SocketChannel, ByteBuffer> unwritten = new HashMap<>();
        long nextCheck = checkInterval != 0 ? System.currentTimeMillis() + checkInterval : 0L;

        public Runner(
                String title
        ) {
            this.title = title;
            if (MCS.this.stat != null) {
                stat = MCS.this.stat.createChildRunnerStatistics(title);
            }
        }

        public String name() {
            return title;
        }

        public MCS mcs() {
            return MCS.this;
        }

        public List<Channel> channels() {
            List<Channel> r = new ArrayList<>();
            try {
                if (selector != null) synchronized (selector) {
                    Collection<SelectionKey> ks = new LinkedHashSet<>();
                    ks.addAll(selector.keys());
                    for (SelectionKey k : ks) {
                        r.add(k.channel());
                    }
                }
            } catch (Throwable th) {
                // ignore, we just need to fetch info...
            }
            return r;
        }

        /**
         * 0 - listeners, 1 - pending, 2 - connected, 3 - closed, 4- other, 5 -
         * error
         *
         * @return
         */
        public int[] channelStateInfo() {
            int[] r = new int[6];
            List<Channel> chs = channels();
            for (Channel ch : chs) {
                try {
                    if (ch instanceof SocketChannel) {
                        SocketChannel sch = (SocketChannel) ch;
                        if (sch.isConnectionPending()) {
                            r[1]++;
                        } else if (sch.isConnected()) {
                            r[2]++;
                        } else {
                            r[3]++;
                        }
                    } else if (ch instanceof ServerSocketChannel) {
                        ServerSocketChannel sch = (ServerSocketChannel) ch;
                        if (sch.isOpen()) {
                            r[0]++;
                        } else {
                            r[3]++;
                        }
                    } else {
                        r[4]++;
                    }
                } catch (Throwable th) {
                    r[5]++;
                }
            }
            return r;
        }

        @Override
        public void run() {
            String old = Thread.currentThread().getName();
            Thread.currentThread().setName(name + "-" + title);
            try {
                started = System.nanoTime();
                unread.clear();
                unwritten.clear();
                selector = Selector.open();
                while (selector != null && selector.isOpen()) {
                    long startedCycle = System.nanoTime();
                    int readyChannels = 0;
                    try {
                        readyChannels = selector.selectNow();
                    } catch (Throwable th) {
                    }
                    Collection<SelectionKey> skeys = new LinkedHashSet<>();
                    //if (readyChannels > 0)
                    synchronized (selector) {
                        try {
                            skeys.addAll(selector.selectedKeys());
                        } catch (Throwable th) {
                            onClosed(null, 0, null, th);
                            break;
                        }
                    }
                    Iterator<SelectionKey> keys = skeys.iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        int wip = 0;
                        try {
                            if (tryKeyHandling(this, key, stat)) {
                                // MCS-level handling for this key: for extensions...
                            } else {
                                if (key.isAcceptable()) {
                                    //////////////////////////////// ACCEPT
                                    if (stat != null) {
                                        stat.onAccept();
                                    }
                                    wip = SelectionKey.OP_ACCEPT;
                                    ServerSocketChannel srv = (ServerSocketChannel) key.channel();
                                    SocketChannel sc = srv.accept();
                                    if (sc != null) {
                                        sc.configureBlocking(false);
                                        if (stat != null) {
                                            stat.onConnected();
                                        }
                                        RunnerIO attachment = key.attachment() instanceof ConnectionIO
                                                ? ((ConnectionIO) key.attachment()).onConnected(sc, false)
                                                : attachmentFor(sc, key.attachment());
                                        attachment = MCS.this.onAccepted(this, sc, attachment);
                                        if (attachment != null) {
                                            attachment.onOpen(sc);
                                        }
                                        sc.register(selector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
                                    } else {
                                        // ignore key, may be next time it will be ok?
                                        continue;
                                    }
                                } else if (key.isConnectable()) {
                                    //////////////////////////////// CONNECT
                                    if (stat != null) {
                                        stat.onConnectable();
                                    }
                                    wip = SelectionKey.OP_CONNECT;
                                    SocketChannel sc = (SocketChannel) key.channel();
                                    if (stat != null) {
                                        stat.onConnected();
                                    }
                                    if (sc.isConnectionPending()) {
                                        sc.finishConnect();
                                    }
                                    RunnerIO attachment = key.attachment() instanceof ConnectionIO
                                            ? ((ConnectionIO) key.attachment()).onConnected(sc, true)
                                            : attachmentFor(sc, key.attachment());
                                    attachment = MCS.this.onConnected(this, sc, attachment);
                                    if (attachment != null) {
                                        attachment.onOpen(sc);
                                    }
                                    sc.register(selector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
                                }
                                if (key.isValid() && key.isReadable()) {
                                    //////////////////////////////// READ
                                    //// BUFFERED READING. IF CONSUMER CANT READ ALL - KEEP FOR NEXT CYCLE...
                                    wip = SelectionKey.OP_READ;
                                    SocketChannel sc = (key.channel() instanceof SocketChannel) ? (SocketChannel) key.channel() : null;
                                    if (sc != null && key.attachment() instanceof RunnerIO) {
                                        RunnerIO rw = (RunnerIO) key.attachment();
                                        ByteBuffer rb = unread.get(sc);
//                                    if (rb != null) {
//                                        System.out.println("HAS UNREAD: " + rb.remaining());
//                                    }
                                        if (rb != null && rb.hasRemaining()) {
                                            // read more if less than half buffer...
                                            if (rb.remaining() < readBufferSize / 2) {
                                                rb.compact();
                                                int c = sc.read(rb);
                                                rb.flip();
                                                if (c > 0) {
//                                                System.out.println("ADDED TO UNREAD: " + c + " -> " + rb.remaining());
                                                    if (stat != null) {
                                                        stat.onRead();
                                                    }
                                                }
                                            }
                                            for (MCSListenerX l : xlisteners) {
                                                try {
                                                    l.onRead(MCS.this, key, rb);
                                                } catch (Throwable th) {
                                                }
                                            }
                                            long c = rw.onRead(rb);
                                            if (!rb.hasRemaining()) {
                                                unread.remove(sc);
//                                            if (rb != null) {
//                                                System.out.println("REMOVED UNREAD");
//                                            }
                                                rb = null;
                                            } else {
//                                            System.out.println("KEEP UNREAD: " + rb.remaining());
                                            }
                                        }
                                        int c = rb == null ? sc.read(readBuffer) : 0;
                                        if (c > 0) {
//                                        System.out.println("READ: " + c);
                                            readBuffer.flip();
                                            for (MCSListenerX l : xlisteners) {
                                                try {
                                                    l.onRead(MCS.this, key, readBuffer);
                                                } catch (Throwable th) {
                                                }
                                            }
                                            rw.onRead(readBuffer);
                                            if (readBuffer.hasRemaining()) {
                                                this.unread.put(sc, readBuffer);
                                                readBuffer = ByteBuffer.allocateDirect(readBufferSize);
                                            } else {
                                                readBuffer.clear();
                                            }
                                        }
                                        if (c == -1 || !rw.isActive()) {
                                            unread.remove(sc);
                                            key.channel().close();
                                        } else if (c > 0) {
                                            if (stat != null) {
                                                stat.onRead();
                                            }
                                        }
                                    }
                                }
                                if (key.isValid() && key.isWritable()) {
                                    //////////////////////////////// WRITE
                                    wip = SelectionKey.OP_WRITE;
                                    SocketChannel sc = (key.channel() instanceof SocketChannel) ? (SocketChannel) key.channel() : null;
                                    if (sc != null && key.attachment() instanceof RunnerIO) {
                                        RunnerIO rw = (RunnerIO) key.attachment();

                                        // if have unread data - push
                                        if (true) {
                                            ByteBuffer rb = unread.get(sc);
                                            if (rb != null && rb.hasRemaining()) {
                                                for (MCSListenerX l : xlisteners) {
                                                    try {
                                                        l.onRead(MCS.this, key, rb);
                                                    } catch (Throwable th) {
                                                    }
                                                }
                                                long c = rw.onRead(rb);
                                                if (!rb.hasRemaining()) {
                                                    unread.remove(sc);
                                                    rb = null;
                                                }
                                            }
                                        }

                                        // if have unwritten data - write
                                        ByteBuffer rb = unwritten.get(sc);
                                        if (rb != null && rb.hasRemaining()) {
//                                        System.out.println("GOT UNWRITTEN: " + rb.remaining());
                                            long c = sc.write(rb);
                                            if (!rb.hasRemaining()) {
                                                unwritten.remove(sc);
//                                            System.out.println("REMOVED UNWRITTEN");
                                                rb = null;
                                            } else {
//                                            System.out.println("KEEP UNWRITTEN: " + rb.remaining());
                                            }
                                        }

                                        ByteBuffer buf = rb == null ? rw.onWrite((SocketChannel) key.channel()) : null;
                                        if (buf != null) {
                                            int cc = 0;
                                            int cycles = 3;
                                            for (MCSListenerX l : xlisteners) {
                                                try {
                                                    l.onWrite(MCS.this, key, Collections.singletonList(buf));
                                                } catch (Throwable th) {
                                                }
                                            }
                                            while (buf.hasRemaining()) {
                                                int c = sc.write(buf);
                                                if (c > 0) {
                                                    cc += c;
                                                }
                                                cycles--;
                                                if (cycles == 0) {
                                                    break;
                                                }
                                            }
                                            if (buf.hasRemaining()) {
//                                            System.out.println("KEEP UNWRITTEN: " + buf.remaining());
                                                unwritten.put(sc, buf);
                                            }
                                            if (cc > 0 && stat != null) {
                                                stat.onWrite();
                                            }
                                        }
                                        if (!rw.isActive()) {
                                            unwritten.remove(sc);
                                            key.channel().close();
                                        }
                                    }
                                }
                            }
                            if (!key.isValid()) {
                                if (key.attachment() instanceof RunnerIO) {
                                    for (MCSListenerX l : xlisteners) {
                                        try {
                                            l.onInvalid(MCS.this, key);
                                        } catch (Throwable th) {
                                        }
                                    }
                                    onClosed(key, wip, (RunnerIO) key.attachment(), null);
                                    if (stat != null) {
                                        stat.onClose();
                                    }
                                }
                            }
                        } catch (Throwable th) {
                            if (key.attachment() instanceof RunnerIO) {
                                onClosed(key, wip, (RunnerIO) key.attachment(), th);
                                if (stat != null) {
                                    stat.onClose();
                                }
                            }
                            // fix shared read buffer if error happened in READ operation
                            if (wip == SelectionKey.OP_READ) {
                                readBuffer.clear();
                            }

                            // report on stderr or invoke noTraceException()
                            if (!noTraceException.isEmpty()) {
                                boolean noTrace = false;
                                Class thCl = th.getClass();
                                try {
                                    for (Class cl : noTraceException) {
                                        if (cl.isAssignableFrom(thCl)) {
                                            noTrace = true;
                                            break;
                                        }
                                    }
                                } catch (Throwable th1) {
                                    // no need to handle notrace scanning...
                                }
                                if (noTrace) {
                                    MCS.this.onNoTraceException(this, th);
                                } else {
                                    th.printStackTrace();
                                }
                            } else {
                                th.printStackTrace();
                            }
                            try {
                                //key.cancel();
                                key.channel().close();
                            } catch (Throwable th1) {
                                th1.printStackTrace();
                            }
                        } finally {
                            try {
                                keys.remove();
                            } catch (ConcurrentModificationException cmex) {
                                MCS.this.onNoTraceException(this, cmex);
                                //break;
                            }
                        }
                    }

                    /////// check if there's unreadable open channel -> force close
                    if (checkInterval > 0 && System.currentTimeMillis() >= nextCheck) {
                        try {
                            if (stat != null) {
                                stat.onCheck();
                            }
                            SelectionKey[] sks = null;
                            synchronized (selector) {
                                sks = selector.keys().toArray(new SelectionKey[selector.keys().size()]);
                            }
                            if (sks != null) {
                                for (SelectionKey key : sks) {
                                    if (key.channel() instanceof SocketChannel) {
                                        SocketChannel sc = (SocketChannel) key.channel();
                                        if (sc.isConnected() && sc.read(ByteBuffer.wrap(new byte[0])) == -1) {
                                            unread.remove(sc);
                                            unwritten.remove(sc);
                                            try {
                                                key.cancel();
                                            } catch (Throwable th) {
                                            }
                                            try {
                                                sc.close();
                                            } catch (Throwable th) {
                                            }
                                            if (key.attachment() instanceof RunnerIO) {
                                                onClosed(key, 0, (RunnerIO) key.attachment(), null);
                                                if (stat != null) {
                                                    stat.onClose();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable th) {
                        } finally {
                            nextCheck = checkInterval > 0 ? System.currentTimeMillis() + checkInterval : 0L;
                        }
                    }
                    if (stat != null) {
                        stat.onDuration((System.nanoTime() - startedCycle));
                    }
                    delay(0);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                try {
                    onClosed(null, 0, null, null);
                } catch (IOException ioex) {
                    // TODO: ignore ???
                }
                handler = null;
                try {
                    if (selector != null && selector.isOpen()) {
                        selector.close();
                    }
                } catch (Throwable th) {
                    // 
                }
                selector = null;
                cleanup(this);
                Thread.currentThread().setName(old);
            }
        }

        void onClosed(SelectionKey key, int keyOpt, RunnerIO rw, Throwable th) throws IOException {
            if (th instanceof ClosedSelectorException) {
                // forced shutdown situation - do not spoil...
            } else if (th != null) {
                System.out.println("...  Error for " + (key != null ? key.channel() + ", " + opt2text(keyOpt) : " service") + ". Error: " + th);
            }
            if (rw != null) {
                rw.onClose(key != null && key.channel() instanceof SocketChannel ? (SocketChannel) key.channel() : null, th);
            }
            MCS.this.onClosed(this, key, keyOpt, rw, th);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
            sb.append('{');
            sb.append("title=" + title);
            sb.append(", selector=" + selector);
            sb.append(", handler=" + handler);
            sb.append(", started=" + started);
            sb.append(", readBufferSize=" + readBufferSize);
            sb.append(", readBuffer=" + readBuffer);
            sb.append(", unread=" + unread.size());
            sb.append(", unwritten=" + unwritten.size());
            sb.append(", checkInterval=" + checkInterval);
            sb.append(", nextCheck=" + nextCheck);
            // 0 - listeners, 1 - pending, 2 - connected, 3 - closed, 4- other, 5 -
            int[] chstat = this.channelStateInfo();
            sb.append("\n  (");
            sb.append("listeners=");
            sb.append(chstat[0]);
            sb.append(", pending=");
            sb.append(chstat[1]);
            sb.append(", connected=");
            sb.append(chstat[2]);
            sb.append(", closed=");
            sb.append(chstat[3]);
            sb.append(", other=");
            sb.append(chstat[4]);
            sb.append(", errors=");
            sb.append(chstat[5]);
            sb.append(")");
            if (stat != null) {
                sb.append("\n  stat=" + stat.dumpStatistics(false).replace("\n", "\n  "));
            }
            sb.append('\n');
            sb.append('}');
            return sb.toString();
        }
    }

    static String opt2text(int opt) {
        StringBuilder sb = new StringBuilder();
        if ((SelectionKey.OP_ACCEPT & opt) != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("OP_ACCEPT");
        }
        if ((SelectionKey.OP_CONNECT & opt) != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("OP_CONNECT");
        }
        if ((SelectionKey.OP_READ & opt) != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("OP_READ");
        }
        if ((SelectionKey.OP_WRITE & opt) != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("OP_WRITE");
        }
        return sb.toString();
    }

    /**
     *
     */
    public static interface ConnectionIO {

        RunnerIO onConnected(SocketChannel sc, boolean client) throws IOException;
    }

    /**
     * Represents abstraction for I/O in MCS runner
     */
    public static interface RunnerIO {

        /**
         * Prepare item for I/O
         *
         * @param sc
         * @throws IOException
         */
        default void onOpen(SocketChannel sc) throws IOException {
        }

        /**
         * Finalize item
         *
         * @param sc
         * @param th
         * @throws IOException
         */
        default void onClose(SocketChannel sc, Throwable th) throws IOException {
        }

        /**
         * If item is active. If not - connection is closed.
         *
         * @return
         * @throws IOException
         */
        boolean isActive();

        /**
         * Read data from channel
         *
         * @param sc
         * @return
         * @throws IOException
         */
        long onRead(ByteBuffer buf) throws IOException;

        /**
         * Get data to write to channel
         *
         * @param sc
         * @return
         * @throws IOException
         */
        ByteBuffer onWrite(SocketChannel sc) throws IOException;

    }

    public static class RunnerIOWrapper implements RunnerIO {

        Runner runner;
        RunnerIO base;

        public RunnerIOWrapper(RunnerIO base) {
            this.runner = runner;
            this.base = base;
        }

        public Runner runner() {
            return runner;
        }

        public RunnerIO base() {
            return base;
        }

        public RunnerIOWrapper configureRunner(Runner runner) {
            this.runner = runner;
            return this;
        }

        @Override
        public void onOpen(SocketChannel sc) throws IOException {
            base.onOpen(sc);
        }

        @Override
        public void onClose(SocketChannel sc, Throwable th) throws IOException {
            base.onClose(sc, th);
        }

        @Override
        public boolean isActive() {
            return base.isActive();
        }

        @Override
        public long onRead(ByteBuffer buf) throws IOException {
            return base.onRead(buf);
        }

        @Override
        public ByteBuffer onWrite(SocketChannel sc) throws IOException {
            return base.onWrite(sc);
        }

    }

    public static class MCSConfig extends Config {

        public MCSConfig() {
            super("net.mcs.base");
        }

        public MCSConfig(String base, String... args) {
            super(base, args);
        }

        @Description("MCS name")
        public String name;
        @Description("Number of acceptor threads")
        public Integer acceptors;
        @Description("Number of data handler threads. If none or 0 - all acceptor threads are also data handler threads.")
        public Integer dataHandlers;
        @Description("Expected number or data processing threads.")
        public Integer guessedProcessors;
        @Description("Input buffer size.")
        public Integer bufferSize;
        @Description("Interval between connection checks ('health check'?)")
        public Long checkInterval;
        @Description("If true, default statistics is created/assigned.")
        public Boolean statistics;
        @Description(
                value = "Exception classes that are not traced in stderr (assuming gracefully handled or handled on upper levels.",
                pattern = "[+]<name> to include, -<name> to exclude, may be comma separated values or multiple definitions. '-all' clears exclusions."
        )
        public String[] noTrace;
    }
}
