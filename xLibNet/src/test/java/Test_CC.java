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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network server/client prototyping.
 *
 * Both ends use Selector to optimize I/O.
 *
 * Same "engine" is used where connection establish may be executed separately
 * from data I/O operations and data operations may be parallelized into several
 * threads.
 *
 * "Server" is echo producer, that reads input and replies it with "REPLY"
 * prefix. It reconnizes "end of communication" if received text ends with
 * "Bye.\n".
 *
 * "Client" pretends to play in QA game, sending texts and waiting for replies.
 * Reply must end with "\n".
 *
 * It is expected that both client and server do not preform "long" read/write
 * (do not block I/O with them). That is possible by separating those operations
 * with RW buffering component. "Business logic" is provided as RW listeners.
 *
 *
 *
 * @author 000ssg
 */
public class Test_CC {

    /**
     * Processes (threads) provider.
     */
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(100);

    public static void main(String... args) throws Exception {
        int serverRunners = 2;
        int clientRunners = 2;

        Server server = new Server("server", rw -> {
            List<byte[]> data = rw.readIn();
            if (!data.isEmpty()) {
                data.add(0, "REPLY:".getBytes());
                rw.writeOut(data.toArray(new byte[data.size()][]));
                for (byte[] bb : data) {
                    if (new String(bb).endsWith("Byte.\n")) {
                        rw.active = false;
                    }
                }
            }
        }, serverRunners);
        server.commonHandler = new RWDebug();
        server.start();

        Client client = new Client("client", clientRunners);
        client.commonHandler = new RWDebug();
        client.start();

        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            if (i > 0 && i % 20 == 0) {
                delay(10);
            }
            final String pre = "" + i + ".";
            SocketChannel cl1 = client.connect(server.getServerURI(), new RWListener() {
                List<byte[]> questions = new ArrayList<byte[]>() {
                    {
                        add((pre + "Hello!").getBytes());
                        add((pre + "How do you do?").getBytes());
                        add((pre + "To be or not to be...").getBytes());
                        add((pre + "Nothing compares to...").getBytes());
                        add((pre + "Bye.").getBytes());
                    }
                };
                boolean waiting = false;

                @Override
                public void onOpen(RW rw) {
                    // start dialog...
                    movement(rw);
                }

                @Override
                public void onRead(RW rw) {
                    List<byte[]> resp = rw.readIn();
                    if (resp.isEmpty()) {
                        return;
                    }
                    waiting = false;
                    for (byte[] buf : resp) {
                        if (buf.length > 0 && buf[buf.length - 1] == '\n') {
                            // next phrase reply ends with "\n".
                            movement(rw);
                        }
                    }
                }

                public void movement(RW rw) {
                    if (waiting) {
                        return;
                    }
                    if (questions.isEmpty()) {
                        rw.active = false;
                    } else {
                        waiting = true;
                        byte[] buf = questions.remove(0);
                        rw.writeOut(buf, new byte[]{'\n'});
                    }
                }
            });
        }

        // wait max 20 seconds...
        long timeout = System.currentTimeMillis() + 1000 * 20;
        while ((!client.handlers.isEmpty() || client.pending() > 0) && System.currentTimeMillis() < timeout) {
            delay(100);
            System.out.println("still " + client.handlers.size() + " client handlers...");
        }
        long duration = System.nanoTime() - started;

        if (!(System.currentTimeMillis() < timeout)) {
            System.out.println("TIMEOUT");
        }
        System.out.println("Run summary   : duration=" + (duration / 1000000f) + "ms");
        System.out.println("Client summary: all=" + client.channels.size() + ", pending=" + client.pending() + ", connected=" + client.connected() + ", not connected=" + client.notConnected());

        client.stop();
        server.stop();
        executor.shutdownNow();

        delay(500);
    }

    static void delay(long duration) {
        try {
            Thread.sleep(duration > 0 ? duration : 1);
        } catch (Throwable th) {
        }
    }

    static class Service {

        String name;
        Collection<SocketChannel> active = new HashSet<>();
        Map<SocketChannel, RW> handlers = new LinkedHashMap<>();
        RWListener commonHandler;
        int maxHandlers = 3;

        Runner[] dataHandlers;
        AtomicInteger nextHandlerIdx = new AtomicInteger(1);

        Service() {
        }

        void waitInitialized() {
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

        /**
         * Returns selector for server-side connections listening and for
         * pending client connections.
         *
         * @return
         */
        Selector acceptor() {
            return dataHandlers != null && dataHandlers.length > 0 ? dataHandlers[0].selector : null;
        }

        /**
         * Returns selector for I/O operations. may change from call to call to
         * evenly distribute connections between selectors.
         *
         * @return
         */
        Selector nextDataSelector() {
            synchronized (this) {
                if (dataHandlers == null || dataHandlers.length == 0) {
                    return null;
                }
                if (dataHandlers.length == 1) {
                    return dataHandlers[0] != null ? dataHandlers[0].selector : null;
                }
                int idx = nextHandlerIdx.getAndIncrement();
                if (idx == dataHandlers.length - 1) {
                    nextHandlerIdx.set(1);
                }
                return dataHandlers[idx].selector;
            }
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

        class Runner implements Runnable {

            String title;
            Service svc;
            Selector selector;
            Runnable handler;
            RWListener commonHandler;
            AtomicInteger acceptCount = new AtomicInteger();
            AtomicInteger connectableCount = new AtomicInteger();
            AtomicInteger connCount = new AtomicInteger();
            AtomicInteger readCount = new AtomicInteger();
            AtomicInteger writeCount = new AtomicInteger();
            AtomicLong cpu = new AtomicLong();
            long started;

            public Runner(
                    String title,
                    Service svc,
                    RWListener commonHandler
            ) {
                this.title = title;
                this.svc = svc;
                this.commonHandler = commonHandler;
            }

            @Override
            public void run() {
                String old = Thread.currentThread().getName();
                Thread.currentThread().setName(name + "-" + title);
                try {
                    started = System.nanoTime();
                    selector = Selector.open();
                    while (selector != null && selector.isOpen()) {
                        long startedCycle = System.nanoTime();
                        int readyChannels = selector.selectNow();
                        Collection<SelectionKey> skeys = null;
                        synchronized (selector) {
                            try {
                                skeys = selector.selectedKeys();
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
                                if (key.isAcceptable()) {
                                    acceptCount.getAndIncrement();
                                    wip = SelectionKey.OP_ACCEPT;
                                    ServerSocketChannel srv = (ServerSocketChannel) key.channel();
                                    SocketChannel sc = srv.accept();
                                    sc.configureBlocking(false);
                                    RW rw = new RW();
                                    rw.name = "S:" + sc;
                                    if (commonHandler != null) {
                                        rw.addRWListener(commonHandler);
                                    }
                                    if (key.attachment() instanceof RWListener) {
                                        rw.addRWListener((RWListener) key.attachment());
                                    }
                                    rw.onOpen();
                                    connCount.getAndIncrement();
                                    sc.register(nextDataSelector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE, rw);
                                    onAccepted(sc, rw);
                                } else if (key.isConnectable()) {
                                    connectableCount.getAndIncrement();
                                    wip = SelectionKey.OP_CONNECT;
                                    SocketChannel sc = (SocketChannel) key.channel();
                                    RW rw = new RW();
                                    rw.name = "C:" + sc;
                                    if (commonHandler != null) {
                                        rw.addRWListener(commonHandler);
                                    }
                                    if (key.attachment() instanceof RWListener) {
                                        rw.addRWListener((RWListener) key.attachment());
                                    }
                                    if (sc.isConnectionPending()) {
                                        sc.finishConnect();
                                    }
                                    rw.onOpen();
                                    connCount.getAndIncrement();
                                    sc.register(nextDataSelector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE, rw);
                                    onConnected(sc, rw);
                                }
                                if (key.isValid() && key.isReadable()) {
                                    wip = SelectionKey.OP_READ;
                                    if (key.attachment() instanceof RW) {
                                        RW rw = (RW) key.attachment();
                                        rw.read((SocketChannel) key.channel());
                                        if (!rw.active) {
                                            key.channel().close();
                                        } else {
                                            readCount.getAndIncrement();
                                        }
                                    }
                                }
                                if (key.isValid() && key.isWritable()) {
                                    wip = SelectionKey.OP_WRITE;
                                    if (key.attachment() instanceof RW) {
                                        RW rw = (RW) key.attachment();
                                        int c = rw.write((SocketChannel) key.channel());
                                        if (!rw.active) {
                                            key.channel().close();
                                        }
                                        if (c > 0) {
                                            writeCount.getAndIncrement();
                                        }
                                    }
                                }
                                if (!key.isValid()) {
                                    if (key.attachment() instanceof RW) {
                                        onClosed(key, wip, (RW) key.attachment(), null);
                                    }
                                }
                            } catch (Throwable th) {
                                if (key.attachment() instanceof RW) {
                                    onClosed(key, wip, (RW) key.attachment(), th);
                                }
                                th.printStackTrace();
                                try {
                                    key.cancel();
                                    key.channel().close();
                                } catch (Throwable th1) {
                                    th1.printStackTrace();
                                }
                            } finally {
                                if (key.isValid()) {
                                    keys.remove();
                                }
                            }
                        }
                        cpu.getAndAdd(System.nanoTime() - startedCycle);
                        delay(0);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    onClosed(null, 0, null, null);
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

            void onAccepted(SocketChannel ch, RW rw) {
                handlers.put(ch, rw);
                svc.onAccepted(ch, rw);
            }

            void onConnected(SocketChannel ch, RW rw) {
                handlers.put(ch, rw);
                svc.onConnected(ch, rw);
            }

            void onClosed(SelectionKey key, int keyOpt, RW rw, Throwable th) {
                if (th instanceof ClosedSelectorException) {
                    // forced shutdown situation - do not spoil...
                } else if (th != null) {
                    System.out.println("...  Error for " + (key != null ? key.channel() + ", " + opt2text(keyOpt) : " service") + ". Error: " + th);
                }
                if (rw != null) {
                    rw.onClose();
                }
                if (key != null) {
                    handlers.remove(key.channel());
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Shutting down " + name + '-' + title + ". Dropping " + handlers.size() + " connection handlers."
                            + " TOTALS: "
                            + "a/c=" + this.acceptCount.get() + "/" + this.connectableCount.get()
                            + ", connections=" + connCount.get()
                            + ", reads=" + readCount.get()
                            + ", writes=" + writeCount.get()
                            + ", up time=" + ((System.nanoTime() - started) / 1000000f) + "ms"
                            + ", cpu=" + (cpu.get() / 1000000f) + "ms");
                    for (Entry<SocketChannel, RW> e : handlers.entrySet().toArray(new Entry[handlers.size()])) {
                        sb.append("\n  " + (e.getKey() + " -> " + e.getValue()).replace("\n", "\n    "));
                    }
                    System.out.println(sb.toString());
                    handlers.clear();
                }
                svc.onClosed(key, keyOpt, rw, th);
            }
        }

        void start() {
            if (dataHandlers == null || dataHandlers.length == 0 || dataHandlers[0] == null) {
                try {
                    dataHandlers = new Runner[this.maxHandlers];
                    for (int i = 0; i < dataHandlers.length; i++) {
                        dataHandlers[i] = new Runner(i == 0 ? "acceptor" : "dh+" + i, this, commonHandler);
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
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        void stop() {
            if (dataHandlers != null && dataHandlers.length > 0 && dataHandlers[0] != null) {
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
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        void onAccepted(SocketChannel ch, RW rw) {
        }

        void onConnected(SocketChannel ch, RW rw) {
        }

        void onClosed(SelectionKey key, int keyOpt, RW rw, Throwable th) {
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
    }

    static class Server extends Service {

        int port = 30051;
        ServerSocketChannel server;
        RWListener serverHandler;

        Server(String name, RWListener serverHandler, int runners) {
            this.name = name;
            this.serverHandler = serverHandler;
            this.maxHandlers = runners > 0 && runners < 100 ? runners : 5;
        }

        @Override
        void start() {
            super.start();
            Selector acs = acceptor();
            try {
                server = ServerSocketChannel.open();
                server.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port));
                server.configureBlocking(false);
                server.register(acs, SelectionKey.OP_ACCEPT, serverHandler);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        @Override
        void stop() {
            try {
                server.close();
            } catch (Throwable th) {
            } finally {
                server = null;
            }
            super.stop();
        }

        URI getServerURI() {
            if (dataHandlers != null && dataHandlers.length > 0 && dataHandlers[0] != null)
            try {
                return new URI("protocol://localhost:" + port);
            } catch (Throwable th) {
            }
            return null;
        }
    }

    static class Client extends Service {

        int connectCount = 0;
        List<SocketChannel> channels = new ArrayList<>();

        Client(String name, int runners) throws IOException {
            this.name = name;
            this.maxHandlers = runners > 0 && runners < 100 ? runners : 1;
        }

        SocketChannel connect(URI uri, RWListener handler) throws IOException {
            connectCount++;
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            sc.register(acceptor(), SelectionKey.OP_CONNECT, handler);
            sc.connect(new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort()));
            channels.add(sc);
            return sc;
        }

        public int pending() {
            int r = 0;
            for (SocketChannel sc : channels) {
                if (sc.isConnectionPending()) {
                    r++;
                }
            }
            return r;
        }

        public int connected() {
            int r = 0;
            for (SocketChannel sc : channels) {
                if (sc.isConnected()) {
                    r++;
                }
            }
            return r;
        }

        public int notConnected() {
            int r = 0;
            for (SocketChannel sc : channels) {
                if (!sc.isConnected()) {
                    r++;
                }
            }
            return r;
        }

    }

    /**
     * Intermediary for socket operations: read/write - socket I/O,
     * writeOut/readIn - consumer I/O.
     */
    static class RW {

        String name;
        boolean active = true;
        List<byte[]> input = new ArrayList<>();
        List<byte[]> output = new ArrayList<>();
        List<RWListener> listeners = new ArrayList<>();

        void read(SocketChannel sc) throws IOException {
            byte[] buf = new byte[1024];
            long cc = 0;
            int c = 0;
            while ((c = sc.read(ByteBuffer.wrap(buf))) > 0) {
                synchronized (input) {
                    input.add(Arrays.copyOf(buf, c));
                }
                cc += c;
            }
            if (c < 0) {
                sc.close();
            }
            if (cc > 0) {
                onRead();
            }
        }

        int write(SocketChannel sc) throws IOException {
            int r = 0;
            byte[] buf = null;
            synchronized (output) {
                if (!output.isEmpty()) {
                    buf = output.remove(0);
                }
            }
            if (buf != null) {
                int c = sc.write(ByteBuffer.wrap(buf));
                if (c < 0) {
                    sc.close();
                } else if (c < buf.length) {
                    synchronized (output) {
                        output.add(0, Arrays.copyOfRange(buf, c, buf.length - c));
                    }
                }
                if (c > 0) {
                    r = c;
                }
            }
            return r;
        }

        public List<byte[]> readIn() {
            List<byte[]> r = new ArrayList<>();
            synchronized (input) {
                r.addAll(input);
                input.clear();
            }
            return r;
        }

        public void writeOut(byte[]  
            ... bufs) {
            if (bufs != null) {
                synchronized (output) {
                    for (byte[] buf : bufs) {
                        if (buf != null) {
                            output.add(buf);
                        }
                    }
                }
            }
        }

        void onOpen() {
            if (!listeners.isEmpty()) {
                RWListener[] ls = null;
                synchronized (listeners) {
                    ls = listeners.toArray(new RWListener[listeners.size()]);
                }
                for (RWListener l : ls) {
                    try {
                        l.onOpen(this);
                    } catch (Throwable th) {
                    }
                }
            }
        }

        void onRead() {
            if (!listeners.isEmpty()) {
                RWListener[] ls = null;
                synchronized (listeners) {
                    ls = listeners.toArray(new RWListener[listeners.size()]);
                }
                for (RWListener l : ls) {
                    try {
                        l.onRead(this);
                    } catch (Throwable th) {
                    }
                }
            }
        }

        void onClose() {
            if (1 == 1) {
                active = false;
                if (!listeners.isEmpty()) {
                    RWListener[] ls = null;
                    synchronized (listeners) {
                        ls = listeners.toArray(new RWListener[listeners.size()]);
                    }
                    for (RWListener l : ls) {
                        try {
                            l.onClose(this);
                        } catch (Throwable th) {
                        }
                    }
                }
            }
        }

        void addRWListener(RWListener l) {
            if (l != null && !listeners.contains(l)) {
                synchronized (listeners) {
                    listeners.add(l);
                }
            }
        }

        void removeRWListener(RWListener l) {
            if (l != null && listeners.contains(l)) {
                synchronized (listeners) {
                    listeners.remove(l);
                }
            }
        }

    }

    static interface RWListener {

        default void onOpen(RW rw) {
        }

        default void onClose(RW rw) {
        }

        void onRead(RW rw);

    }

    static class RWDebug implements RWListener {

        @Override
        public void onOpen(RW rw) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].onOpen (" + rw.name + "]");
        }

        @Override
        public void onRead(RW rw) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].onRead (" + rw.name + "]");
        }

        @Override
        public void onClose(RW rw) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].onClose(" + rw.name + "]");
        }

    }
}
