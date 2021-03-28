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
package ssg.lib.websocket;

import ssg.lib.http.base.Head;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;
import ssg.lib.websocket.extensions.WebSocketExtensionGZip;
import ssg.lib.websocket.extensions.WebSocketExtensionTimestamp;
import ssg.lib.websocket.extensions.WebSocketExtensions;
import ssg.lib.websocket.tools.BufferingWebSocketMessageHandler;
import java.io.IOException;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.CommonTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.websocket.WebSocketProcessor.PongMessageListener;
import ssg.lib.websocket.impl.WebSocketProtocolHandler;

/**
 * Basic WebSocket implementation common fro both server and client sides.
 *
 * @author 000ssg
 */
public abstract class WebSocket implements WebSocketConstants {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    // available protocols/extensions
    public static final Collection<String> allProtocols = new HashSet<>();
    public static final Map<String, WebSocketExtension> allExtensions = new LinkedHashMap<String, WebSocketExtension>() {
        {
            for (WebSocketExtension wse : new WebSocketExtension[]{
                new WebSocketExtensionTimestamp(),
                new WebSocketExtensionGZip()
            }) {
                put(wse.getName(), wse);
            }
        }
    };

    public final int id = NEXT_ID.getAndIncrement();
    // additional websocket info for initialization/runtime extensions supports
    private WebSocketAddons addOns;
    // instance protocol/extensions set
    private boolean initialized = false;
    private boolean client = false;
    String protocol;
    WebSocketExtensions extensions = new WebSocketExtensions();
    Collection<WebSocketLifecycleListener> wsListeners = new LinkedHashSet<>();

    // limitations: frame, timeout and any other stoppers...
    private int maxFrameSize = 100000; // frame size. If exceeds -> frames are automatically fragmented.
    private int maxTimeout = 1000000; // default timeout. E.g. used when ping is waiting for pong.

    // payload messages: queue, buffer for partials, processor, etc.
    private List messages = Collections.synchronizedList(new LinkedList());
    private WebSocketMessageHandler handler = new BufferingWebSocketMessageHandler();
    private WebSocketProcessor processor;
    private WebSocketProtocolHandler protocolHandler;
    // debug support
    private FrameMonitor frameMonitor;

    // closing support
    private boolean remoteClosed = false;
    private boolean localClosed = false;
    private short closeCode;
    private byte[] closeData;

    // owner (creation context
    transient Object owner;
    // optional remote
    URI uri;
    // handshaking...
    WebSocketHandshake handshake;
    String path;
    // input candidate is used to cache full frame before adding to input.
    ByteBuffer inputCandidate = ByteBuffer.allocate(getMaxFrameSize() + 2 + 4 + 4);
    int inputCandidateLeft = -1;
    List<WebSocketFrame> input = Collections.synchronizedList(new ArrayList<>());
    List<ByteBuffer> output = Collections.synchronizedList(new ArrayList<>());
    private WebSocketMessageListener defaultMessageListener;

    public WebSocket() {
    }

    public WebSocket(URI uri) {
        this.uri = uri;
    }

    public WebSocket(WebSocketAddons addOns) {
        this.addOns = addOns;
    }

    public WebSocket(URI uri, WebSocketAddons addOns) {
        this.uri = uri;
        this.addOns = addOns;
    }

    /**
     * set owner once!
     *
     * @param obj
     */
    public void owner(Object obj) {
        if (owner == null && obj != null) {
            owner = obj;
        }
    }

    public <T> T owner() {
        return (T) owner;
    }

    public WebSocketAddons getAddOns() {
        return addOns;
    }

    public String getPath() {
        return path;
    }

    public String getProtocol() {
        return protocol;
    }

    public boolean isClient() {
        return client;
    }

    public void handshake(Head head) throws IOException {
        if (handshake == null) {
            client = false; // TODO: check if this is server case...
            handshake = new WebSocketHandshake(this, head);
            path = head.getProtocolInfo()[1];
        } else {
            throw new IOException("Cannot set handshake head: handshake already initialized.");
        }
    }

    // handshake as client
    public void handshake(
            String version,
            String path,
            String host,
            String origin,
            String[] proposedProtocols,
            String[] proposedExtensions,
            Integer wsVersion,
            Map<String, String> httpHeaders
    ) throws IOException {
        client = true;
        handshake = new WebSocketHandshake(
                this,
                version,
                path,
                host,
                origin,
                proposedProtocols,
                proposedExtensions,
                wsVersion,
                httpHeaders);
        this.path = path;
    }

    /**
     * Handshake as server
     *
     * @throws IOException
     */
    public void handshake() throws IOException {
        client = false;
        fetch();
        write(null);
    }

    private int maskSize = 0;
    private int len = 0;
    private int lenExtraSize = 0;

    public long add(Collection<ByteBuffer>... bbs) throws IOException {
        { //synchronized (inputCandidate) {
            long c = BufferTools.getRemaining(bbs);
            if (bbs == null || bbs.length == 0) {
                return c;
            }
            if (!isInitialized()) {
                if (handshake == null) {
                    handshake = new WebSocketHandshake(this);
                }
                for (Collection<ByteBuffer> bs : bbs) {
                    handshake.add(bs);
                }
            }
            if (BufferTools.getRemaining(bbs) > 0) {
                // add data for frames...
                for (Collection<ByteBuffer> bs : bbs) {
                    if (bs == null || bs.isEmpty()) {
                        continue;
                    }
                    for (ByteBuffer bb : bs) {
                        if (bb == null || !bb.hasRemaining()) {
                            continue;
                        }
                        while (inputCandidateLeft == -1 || inputCandidateLeft > 0) {
                            byte b = bb.get();
                            inputCandidate.put(b);
                            if (inputCandidateLeft == -1) {
                                if (inputCandidate.position() == 2) {
                                    if ((b & 0x80) != 0) {
                                        maskSize = 4;
                                    } else {
                                        maskSize = 0;
                                    }
                                    len = 0x7F & b;
                                    if (len == 127) {
                                        lenExtraSize = 8;
                                    } else if (len == 126) {
                                        lenExtraSize = 2;
                                    } else {
                                        lenExtraSize = 0;
                                        inputCandidateLeft = len + maskSize;
                                        maskSize = 0;
                                    }
                                    len = 0;
                                } else if (lenExtraSize > 0) {
                                    len = (len << 8) + (0xFF & b);
                                    lenExtraSize--;
                                    if (lenExtraSize == 0) {
                                        inputCandidateLeft = len + maskSize;
                                        len = 0;
                                        maskSize = 0;
                                    }
                                }
                            } else {
                                inputCandidateLeft--;
                                if (inputCandidateLeft == 0) {
                                    ((Buffer) inputCandidate).flip();
                                    ByteBuffer tmp = ByteBuffer.allocate(inputCandidate.remaining());
                                    tmp.put(inputCandidate);
                                    ((Buffer) tmp).flip();

                                    try {
                                        ByteBuffer fb = tmp.duplicate();
                                        WebSocketFrame frame = new WebSocketFrame(fb);

                                        // enable processor to get prepared...
                                        getProcessor().onReceivedFrame(frame);
                                        try {
                                            // analyze and 
                                            onIncomingFrame(frame, fb);
                                        } finally {
                                            getProcessor().onHandledFrame(frame);
                                        }

                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                        int a = 0;
                                    }
                                    ((Buffer) inputCandidate).clear();
                                    inputCandidateLeft = -1;
                                }
                            }
                            if (!bb.hasRemaining()) {
                                break;
                            }
                        }
                    }
                }
            }

            return c - BufferTools.getRemaining(bbs);
        }
    }

    public List<ByteBuffer> get() throws IOException {
        if (!isInitialized()) {
            if (handshake != null) {
                return handshake.get();
            } else {
                return null;
            }
        }
        List<ByteBuffer> r = new ArrayList<ByteBuffer>();
        if (getProtocolHandler() != null) {
            getProtocolHandler().onProduce(getConnection(), this);
        }
        synchronized (output) {
            r.addAll(output);
            output.clear();
        }
        return r;
    }

    /**
     * Adapts frame to client or server before sending it.
     *
     * On client side the frame mask is initialize, on server side - it is
     * cleared.
     *
     * @param frame
     * @return
     */
    public WebSocketFrame prepareWebSocketFrame(WebSocketFrame frame) {
        if (handshake != null) {
            if (handshake.isClient()) {
                frame.setRandomMask();
            } else {
                frame.setMask(null);
            }
        }
        return frame;
    }

    ///////////////////////////////////////////////////////////////
    //// Client/server specifics deferred implementation methods
    ///////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////
    //// Data channel deferred implementation methods
    ///////////////////////////////////////////////////////////////
    public void fetch() throws IOException {
    }

    /**
     * data output [stream]
     *
     * @param bufs
     * @return
     * @throws IOException
     */
    public long write(Collection<ByteBuffer>... bufs) throws IOException {
        long l = BufferTools.getRemaining(bufs);
        if (l > 0) {
            for (Collection<ByteBuffer> bs : bufs) {
                if (bs == null) {
                    continue;
                }
                for (ByteBuffer bb : bs) {
                    if (bb != null && bb.hasRemaining()) {
                        output.add(bb);
                    }
                }
                bs.clear();
            }
        }
        return l;
    }

    /**
     * Check if has input data
     *
     * @return
     */
    public boolean hasInput() {
        return !input.isEmpty();
    }

    /**
     * Close input [stream]
     *
     * @throws IOException
     */
    public abstract void closeInput() throws IOException;

    /**
     * Close output [stream]
     *
     * @throws IOException
     */
    public abstract void closeOutput() throws IOException;

    /**
     * Returns true if connection is active.
     *
     * @return
     */
    public abstract boolean isConnected();

    /**
     * Closes connection if active and any related resources.
     *
     * Once closed the input/output streams are unavialable.
     */
    public abstract void closeConnection() throws IOException;

    public abstract <T> T getConnection();

    ///////////////////////////////////////////////////////////////
    //// Common operational and utility methods.
    ///////////////////////////////////////////////////////////////
    /**
     * Returns message handler.
     *
     * Message handler is responsible for restoring data from incoming frames
     * with concatenation of fragmented messages.
     *
     * Handler is used within <code>onIncomingFrame</code> method for
     * non-control message types.
     *
     * @return
     */
    public WebSocketMessageHandler getMessageHandler() {
        return handler;
    }

    public void setMessageHandler(WebSocketMessageHandler handler) {
        this.handler = (handler != null) ? handler : this.handler;
    }

    public WebSocketExtensions getWebSocketExtensions() {
        return extensions;
    }

    public void onIncomingFrame(WebSocketFrame frame, ByteBuffer payload) throws IOException {
        if (isRemoteClosed()) {
            // ignore any message after remote is closed...
            return;
        }
        //System.out.println(Thread.currentThread().getName() + ":onIncomingFrame:" + frame);
        switch (frame.getOpCode()) {
            case WebSocketFrame.OP_BINARY:
                getMessageHandler().onMessageStart(frame);
                if (frame.isFinalFragment()) {
                    if (frameMonitor != null && frameMonitor.check(FrameMonitor.IF_BINARY, this, frame, null)) {
                        frameMonitor.onCompletedFrame(this, frame, null, new ByteBuffer[]{payload});
                    }
                    Object msg = getMessageHandler().onMessageCompleted(this, frame, payload);
                    if (msg != null) {
                        messages.add(msg);
                    } else {
                        throw new IOException("Failed to finalize frame: " + frame);
                    }
                } else {
                    getMessageHandler().onMessageContinue(this, frame, payload);
                }
                break;
            case WebSocketFrame.OP_TEXT:
                getMessageHandler().onMessageStart(frame);
                if (frame.isFinalFragment()) {
                    if (frameMonitor != null && frameMonitor.check(FrameMonitor.IF_TEXT, this, frame, null)) {
                        frameMonitor.onCompletedFrame(this, frame, null, new ByteBuffer[]{payload});
                    }
                    Object msg = getMessageHandler().onMessageCompleted(this, frame, payload);
                    if (msg != null) {
                        messages.add(msg);
                    } else {
                        throw new IOException("Failed to finalize frame: " + frame);
                    }
                } else {
                    getMessageHandler().onMessageContinue(this, frame, payload);
                }
                break;
            case WebSocketFrame.OP_CONTINUATION:
                if (frame.isFinalFragment()) {
                    if (frameMonitor != null && frameMonitor.check(FrameMonitor.IF_CONTINUE, this, frame, null)) {
                        frameMonitor.onCompletedFrame(this, frame, null, new ByteBuffer[]{payload});
                    }
                    Object obj = getMessageHandler().onMessageCompleted(this, frame, payload);
                    messages.add(obj);
                } else {
                    getMessageHandler().onMessageContinue(this, frame, payload);
                }
                break;
            case WebSocketFrame.OP_PING: {
                // generate and send response PONG
                byte[] data = null;
                if (frame.getLength() > 0) {
                    data = new byte[(int) frame.getLength()];
                    int c = frame.readPayload(data, 0, null, payload);
                    if (c != data.length) {
                        throw new IOException("Failed to read all PING data for proper PONG response. Expected " + data.length + ", got " + c + ".");
                    }
                    data = getWebSocketExtensions().unapplyExtensions(frame, data);
                }
                pong(data);
            }
            break;
            case WebSocketFrame.OP_PONG: {
                // register PONG
                byte[] data = new byte[0];
                if (frame.getLength() > 0) {
                    data = new byte[(int) frame.getLength()];
                    int c = frame.readPayload(data, 0, payload);
                    if (c != data.length) {
                        throw new IOException("Failed to read all PONG data. Expected " + data.length + ", got " + c + ".");
                    }
                }
                byte[] pong = getWebSocketExtensions().unapplyExtensions(frame, data);
                if (getProcessor() != null) {
                    getProcessor().onPong(pong);
                }
            }
            break;
            case WebSocketFrame.OP_CONNECTION_CLOSE: {
                remoteClosed = true;
                byte[] data = new byte[(int) frame.getLength()];
                data = new byte[(int) frame.getLength()];
                int c = frame.readPayload(data, 0, payload);
                if (c != data.length) {
                    throw new IOException("Failed to read all Close data. Expected " + data.length + ", got " + c + ".");
                }
                data = getWebSocketExtensions().unapplyExtensions(frame, data);
                if (!isLocalClosed()) {
                    if (data != null && data.length > 1) {
                        closeCode = (short) ((data[0] & 0xFF) + (((data[1] & 0xFF) << 8)));
                        if (data.length > 2) {
                            closeData = Arrays.copyOfRange(data, 2, data.length - 1);
                        }
                        close(getCloseCode());
                    }
                }
                onClosed();
            }
            break;
            default:
                if (!getWebSocketExtensions().handleExtensionFrame(this, frame)) {
                    closeInput();
                    closeOutput();
                }
        }
    }

    public void onClosed() throws IOException {
        if (isLocalClosed() && isRemoteClosed()) {
            closeConnection();
        } else if (!isRemoteClosed()) {
            Thread close = new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("WS-closeRemote-" + WebSocket.this);
                    try {
                        long timeout = System.currentTimeMillis() + 10000;
                        while (!isRemoteClosed()) {
                            NetTools.delay(5);
                            if (System.currentTimeMillis() > timeout) {
                                break;
                            }
                        }
                    } finally {
                        try {
                            closeConnection();
                        } catch (IOException ioex) {
                        }
                    }
                }
            };
            close.setDaemon(true);
            close.start();
        }
    }

    public boolean hasNextMessage() {
        return !messages.isEmpty();
    }

    public int getNextMessagesCount() {
        return messages.size();
    }

    /**
     * Mixed sum of message size (byte[]) or length (String). Used to test
     * currently cached data amount.
     *
     * @return
     */
    public long getNextMessagesSizeEstimate() {
        long r = 0;
        if (hasNextMessage()) {
            try {
                for (Object msg : messages) {
                    if (msg instanceof String) {
                        r += ((String) msg).length();
                    } else if (msg instanceof byte[]) {
                        r += ((byte[]) msg).length;
                    }
                }
            } catch (Throwable th) {
                r = -1;
            }
        }
        return r;
    }

    public <T> T nextMessage() {
        synchronized (messages) {
            return (!messages.isEmpty())
                    ? (T) messages.remove(0)
                    : null;
        }
    }

    public synchronized void send(byte[] data) throws IOException {
        WebSocketFrame frame = prepareWebSocketFrame(new WebSocketFrame());
        frame.setOpCode(WebSocketFrame.OP_BINARY);

        int off = 0;
        int size = data.length;
        int len = Math.min(getMaxFrameSize(), size);

        if (frameMonitor != null && frameMonitor.check(FrameMonitor.OF_BINARY, this, frame, null)) {
            frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(data)}, null);
        }

        while (len > 0) {
            frame = prepareWebSocketFrame(frame);
            if (off > 0) {
                frame.setOpCode(WebSocketFrame.OP_CONTINUATION);
            }
            frame.setFinalFragment(len == size);
            //frame.setLength(len);

            byte[] buf = getWebSocketExtensions().applyExtensions(frame, data, off);

            if (buf == data) {
                frame.setLength(data.length - off);

                if (frameMonitor != null && frameMonitor.check(FrameMonitor.OF_BINARY, this, frame, null)) {
                    frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(data)}, off);
                }

                write(frame.write(data, off, true));
            } else {
                frame.setLength(buf.length);

                if (frameMonitor != null && frameMonitor.check(FrameMonitor.OF_BINARY, this, frame, null)) {
                    frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(buf)}, 0);
                }

                write(frame.write(buf, 0, false));
            }

            size -= len;
            off += len;
            len = Math.min(getMaxFrameSize(), size);
        }
    }

    public synchronized void send(String text) throws IOException {
        WebSocketFrame frame = prepareWebSocketFrame(new WebSocketFrame());
        frame.setOpCode(WebSocketFrame.OP_TEXT);

        char[] cdata = text.toCharArray();
        int csize = cdata.length;
        int coff = 0;
        int clen = Math.min(getMaxFrameSize() / 3, csize);

        if (frameMonitor != null && frameMonitor.check(FrameMonitor.OF_TEXT, this, frame, null)) {
            frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(text.getBytes("UTF-8"))}, null);
        }

        while (clen > 0) {
            byte[] data = new String(cdata, coff, clen).getBytes("UTF-8");
            frame = prepareWebSocketFrame(frame);
            if (coff > 0) {
                frame.setOpCode(WebSocketFrame.OP_CONTINUATION);
            }
            frame.setFinalFragment(clen == csize);
            //frame.setLength(data.length);

            byte[] buf = getWebSocketExtensions().applyExtensions(frame, data, 0);

            if (buf == data) {
                frame.setLength(data.length);
                frame.msgOff = coff;

                if (frameMonitor != null && frameMonitor.check(coff > 0 ? FrameMonitor.OF_CONTINUE : FrameMonitor.OF_TEXT, this, frame, null)) {
                    frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(data)}, coff);

                    List<ByteBuffer> r = frame.write(data, 0, false);
                    if (r.size() == 2 && isClient()) {
                        System.out.println("Limited output detected:\n  out =" + BufferTools.dump(r).replace("\n", "\n    ") + "\n  data=" + BufferTools.dump(data).replace("\n", "\n    "));
                        r = frame.write(data, 0, false);
                    }

                    r = BufferTools.aggregate(Integer.MAX_VALUE, false, r);
                    //frameMonitor.onOutgoingFrame(this, frame, null, r.toArray(new ByteBuffer[r.size()]), 0);
                    write(r);
                } else {
                    write(frame.write(data, 0, false));
                }
            } else {
                frame.setLength(buf.length);
                if (frameMonitor != null && frameMonitor.check(coff > 0 ? FrameMonitor.OF_CONTINUE : FrameMonitor.OF_TEXT, this, frame, null)) {
                    frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(buf)}, 0);
                    List<ByteBuffer> r = frame.write(buf, 0, false);

                    r = BufferTools.aggregate(Integer.MAX_VALUE, false, r);
                    //frameMonitor.onOutgoingFrame(this, frame, null, r.toArray(new ByteBuffer[r.size()]), 0);
                    write(r);
                } else {
                    //frameMonitor.onOutgoingFrame(this, frame, null, new ByteBuffer[]{ByteBuffer.wrap(buf)}, 0);
                    List<ByteBuffer> r = frame.write(buf, 0, false);
                    r = BufferTools.aggregate(Integer.MAX_VALUE, false, r);
                    write(r);
                }
            }

            csize -= clen;
            coff += clen;
            clen = Math.min(getMaxFrameSize() / 3, csize);
        }
    }

    public synchronized byte[] ping(byte[] data, boolean asynchronous) throws IOException {
        // skip while previous async call is not completed.
        if (asynchronous && getProcessor() == null) {
            return null;
        }
        if (!asynchronous || getProcessor().onPing()) {
            WebSocketFrame frame = prepareWebSocketFrame(new WebSocketFrame());
            frame.setOpCode(WebSocketFrame.OP_PING);
            frame.setFinalFragment(true);

            // prepare data if any
            int len = 0;
            if (data != null && data.length > 0) {
                len = Math.min(data.length, 125);
                data = Arrays.copyOf(data, len);
                frame.setLength(len);
            }
            //System.out.println(Thread.currentThread().getName() + ".ping[before]: " + new String(data));

            data = getWebSocketExtensions().applyExtensions(frame, data, 0);
            //System.out.println(Thread.currentThread().getName() + ".ping[after ]: " + new String(data));

            long pingNano = System.nanoTime();
            long pongDelay = -1;
            long timeout = System.currentTimeMillis() + maxTimeout;

            final PongMessageListener pml = (!asynchronous) ? new PongMessageListener() : null;
            if (pml != null) {
                getProcessor().addWebSocketMessageListener(pml);
            }

            // write the frame
            write(frame.write(data, 0, false));

            if (!asynchronous) {
                try {
                    CommonTools.wait(timeout, () -> {
                        return !pml.hasPong();
                    });
                    return pml.fetchPong();
                } finally {
                    getProcessor().removeWebSocketMessageListener(pml);
                }
            }

        }
        return null;
    }

    public synchronized void pong(byte... data) throws IOException {
        WebSocketFrame frame = prepareWebSocketFrame(new WebSocketFrame());
        frame.setOpCode(WebSocketFrame.OP_PONG);
        frame.setFinalFragment(true);

        // prepare data if any
        int len = 0;
        if (data != null && data.length > 0) {
            len = Math.min(data.length, 125);
            data = Arrays.copyOf(data, len);
            frame.setLength(len);
        }

        //System.out.println(Thread.currentThread().getName() + ".pong[before]: " + new String(data));
        data = getWebSocketExtensions().applyExtensions(frame, data, 0);
        //System.out.println(Thread.currentThread().getName() + ".pong[after ]: " + new String(data));

        // write the frame
        write(frame.write(data, 0, false));
    }

    public synchronized void close(short code, byte... extraData) throws IOException {
        if (isLocalClosed()) {
            return;
        }
        //System.out.println(Thread.currentThread().getName() + ".close: " + code + ((extraData != null) ? ", " + new String(extraData) : ""));
        localClosed = true;
        if (!isRemoteClosed()) {
            closeCode = code;
            closeData = extraData;
        }

        WebSocketFrame frame = prepareWebSocketFrame(new WebSocketFrame());
        frame.setOpCode(WebSocketFrame.OP_CONNECTION_CLOSE);
        frame.setFinalFragment(true);

        // prepare data: code + extra if any
        byte[] data = new byte[2];
        data[0] = (byte) (code & 0xFF);
        data[1] = (byte) ((code >> 8) & 0xFF);
        if (extraData != null && extraData.length > 0) {
            data = Arrays.copyOf(data, Math.min(125, extraData.length));
            System.arraycopy(extraData, 0, data, 2, Math.min(data.length - 2, extraData.length));
        }

        // set the data len
        frame.setLength(data.length);

        data = getWebSocketExtensions().applyExtensions(frame, data, 0);

        write(frame.write(data, 0, false));
    }

    public boolean acceptProtocol(String protocol) {
        if (protocol == null) {
            return false;
        }
        if (addOns != null && addOns.getProtocols() != null && addOns.getProtocols().contains(protocol)) {
            this.protocol = protocol;
            WebSocketProtocolHandler[] ps = addOns.getWebSocketProtocolHandler(protocol);
            if (ps != null) {
                for (WebSocketProtocolHandler p : ps) {
                    if (p.canInitialize(getConnection(), this)) {
                        this.protocolHandler = p;
                        break;
                    }
                }
            }
            return true;
        }
        if (allProtocols.contains(protocol)) {
            this.protocol = protocol;
            return true;
        }
        return false;
    }

    public String acceptExtension(String rp) {
        String r = null;
        if (rp == null) {
            return r;
        }
        rp = (rp != null) ? rp.trim() : rp;
        String[] rpp = rp.split(";");
        for (int i = 0; i < rpp.length; i++) {
            rpp[i] = rpp[i].trim();
        }
        if (addOns != null && addOns.getExtensions() != null && addOns.getExtensions().containsKey(rpp[0])) {
            r = (rp);
            WebSocketExtension wse = (WebSocketExtension) addOns.getExtensions().get(rpp[0]).clone();
            if (rpp.length > 1) {
                for (int i = 1; i < rpp.length; i++) {
                    String pn = rpp[i];
                    int idx = pn.indexOf("=");
                    if (idx != -1) {
                        String pv = pn.substring(idx + 1).trim();
                        pn = pn.substring(0, idx).trim();
                        wse.setParameter(pn, pv);
                    } else {
                        wse.setParameter(pn, true);
                    }
                }
            }
            getWebSocketExtensions().add(wse);
        } else if (allExtensions.containsKey(rpp[0])) {
            r = (rp);
            WebSocketExtension wse = (WebSocketExtension) allExtensions.get(rpp[0]).clone();
            if (rpp.length > 1) {
                for (int i = 1; i < rpp.length; i++) {
                    String pn = rpp[i];
                    int idx = pn.indexOf("=");
                    if (idx != -1) {
                        String pv = pn.substring(idx + 1).trim();
                        pn = pn.substring(0, idx).trim();
                        wse.setParameter(pn, pv);
                    } else {
                        wse.setParameter(pn, true);
                    }
                }
            }
            getWebSocketExtensions().add(wse);
        }
        return r;
    }

    public static Map<String, String[]> readHeaders(List<ByteBuffer> is) throws IOException {
        //System.out.println(Thread.currentThread().getName() + ":readHeaders:" + PDTools.toText(null, is));
        Map<String, String[]> r = new LinkedHashMap<String, String[]>();
        Head head = new Head();
        for (ByteBuffer bb : is) {
            head.add(bb);
            if (head.isHeadCompleted()) {
                break;
            }
        }
        {
            Map<String, String[]> hh = head.getHeaders();
            for (String key : hh.keySet()) {
                String[] vv = hh.get(key);
                r.put(key.toUpperCase(), vv);
            }
        }
        r.put("", head.getProtocolInfo());
        return r;
    }

    /**
     * @return the processor
     */
    public WebSocketProcessor getProcessor() {
        return processor;
    }

    /**
     * @param processor the processor to set
     */
    public void setProcessor(WebSocketProcessor processor) {
        this.processor = processor;
        if (processor != null && getDefaultMessageListener() != null) {
            processor.addWebSocketMessageListener(getDefaultMessageListener());
        }
    }

    /**
     * @return the remoteClosed
     */
    public boolean isRemoteClosed() {
        return remoteClosed;
    }

    /**
     * @return the localClosed
     */
    public boolean isLocalClosed() {
        return localClosed;
    }

    /**
     * @return the closeCode
     */
    public short getCloseCode() {
        return closeCode;
    }

    /**
     * @return the closeData
     */
    public byte[] getCloseData() {
        return closeData;
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * @param initialized the initialized to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        if (initialized && getProcessor() == null) {
            setProcessor(createProcessor());
        }
        for (WebSocketLifecycleListener l : getWSListeners()) {
            try {
                l.onInitialized(this);
            } catch (Throwable th) {
            }
        }
    }

    public WebSocketProcessor createProcessor() {
        WebSocketProcessor wsp = new WebSocketProcessor(this);
        return wsp;
    }

    /**
     * @return the defaultMessageListener
     */
    public WebSocketMessageListener getDefaultMessageListener() {
        return defaultMessageListener;
    }

    /**
     * @param defaultMessageListener the defaultMessageListener to set
     */
    public void setDefaultMessageListener(WebSocketMessageListener defaultMessageListener) {
        this.defaultMessageListener = defaultMessageListener;
    }

    /**
     * @return the protocolHandler
     */
    public WebSocketProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    /**
     * @param protocolHandler the protocolHandler to set
     */
    public void setProtocolHandler(WebSocketProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    /**
     * @return the frameMonitor
     */
    public FrameMonitor getFrameMonitor() {
        return frameMonitor;
    }

    /**
     * @param frameMonitor the frameMonitor to set
     */
    public void setFrameMonitor(FrameMonitor frameMonitor) {
        this.frameMonitor = frameMonitor;
        if (getWebSocketExtensions() != null) {
            getWebSocketExtensions().setFrameMonitor(frameMonitor);
        }
    }

    /**
     * @return the maxFrameSize
     */
    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * @param maxFrameSize the maxFrameSize to set
     */
    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public void addWebSocketLifecycleListener(WebSocketLifecycleListener l) {
        if (l != null && !wsListeners.contains(l)) {
            synchronized (wsListeners) {
                wsListeners.add(l);
            }
        }
    }

    public void removeWebSocketLifecycleListener(WebSocketLifecycleListener l) {
        if (l != null && wsListeners.contains(l)) {
            synchronized (wsListeners) {
                wsListeners.remove(l);
            }
        }
    }

    public WebSocketLifecycleListener[] getWSListeners() {
        synchronized (wsListeners) {
            return wsListeners.toArray(new WebSocketLifecycleListener[wsListeners.size()]);
        }
    }

    /**
     * Parameterized WebSocket initialization support: protocols, extensions,
     * ...
     */
    public static class WebSocketAddons {
        // instance-specific allowed protocols/extensions

        //private Collection<String> protocols;
        private Map<String, WebSocketProtocolHandler[]> protocols;
        private Map<String, WebSocketExtension> extensions;

        public WebSocketAddons() {
        }

        public WebSocketAddons addProtocol(String protocol, WebSocketProtocolHandler... phs) {
            if (protocol != null) {
                if (this.protocols == null) {
                    this.protocols = new LinkedHashMap<>();
                }
                WebSocketProtocolHandler[] ps = this.protocols.get(protocol);
                if (ps == null) {
                    ps = new WebSocketProtocolHandler[0];
                }
                if (phs != null) {
                    ps = merge(ps, phs);
                }
                this.protocols.put(protocol, ps);
            }
            return this;
        }

        public WebSocketAddons addExtensions(WebSocketExtension... extensions) {
            if (extensions != null) {
                for (WebSocketExtension wse : extensions) {
                    if (wse != null) {
                        if (this.extensions == null) {
                            this.extensions = new LinkedHashMap<String, WebSocketExtension>();
                        }
                        if (!this.extensions.containsKey(wse.getName())) {
                            this.extensions.put(wse.getName(), wse);
                        }
                    }
                }
            }
            return this;
        }

        public Collection<String> getProtocols() {
            return protocols.keySet();
        }

        public Map<String, WebSocketExtension> getExtensions() {
            return extensions;
        }

        public WebSocketProtocolHandler[] getWebSocketProtocolHandler(String protocol) {
            return (protocols != null) ? protocols.get(protocol) : null;
        }

        public WebSocketProtocolHandler[] merge(WebSocketProtocolHandler[] a, WebSocketProtocolHandler... b) {
            WebSocketProtocolHandler[] r = new WebSocketProtocolHandler[(a != null ? a.length : 0) + (b != null ? b.length : 0)];
            if (r.length > 0) {
                int off = 0;
                for (WebSocketProtocolHandler[] aa : new WebSocketProtocolHandler[][]{a, b}) {
                    if (aa == null) {
                        continue;
                    }
                    for (WebSocketProtocolHandler pa : aa) {
                        boolean isNew = true;
                        for (int i = 0; i < off; i++) {
                            if (r[i].equals(pa)) {
                                isNew = false;
                                break;
                            }
                        }
                        if (isNew) {
                            r[off++] = pa;
                        }
                    }
                }
                if (off < r.length) {
                    r = Arrays.copyOf(r, off);
                }
            }
            return r;
        }

        public void merge(WebSocketAddons addOns) {
            if (addOns == null) {
                return;
            }
            if (addOns.protocols != null && !addOns.protocols.isEmpty()) {
                for (Entry<String, WebSocketProtocolHandler[]> entry : addOns.protocols.entrySet()) {
                    if (protocols == null || !protocols.containsKey(entry.getKey())) {
                        addProtocol(entry.getKey(), merge(getWebSocketProtocolHandler(entry.getKey()), entry.getValue()));
                    }
                }
            }
            if (addOns.extensions != null && !addOns.extensions.isEmpty()) {
                for (Entry<String, WebSocketExtension> entry : addOns.extensions.entrySet()) {
                    if (extensions == null || !extensions.containsKey(entry.getKey())) {
                        addExtensions(entry.getValue());
                    }
                }
            }
        }

        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////// handshake helpers
        ////////////////////////////////////////////////////////////////////////
        /**
         * Returns available protocol names array or null if none.
         *
         * @return
         */
        public String[] getProposedProtocols() {
            String[] proposedProtocols = null;
            Collection<String> prots = getProtocols();
            if (prots != null && !prots.isEmpty()) {
                proposedProtocols = prots.toArray(new String[prots.size()]);
            }
            return proposedProtocols;
        }

        /**
         * Returns array of extension definitions (with parameters) or null if
         * none.
         *
         * @return
         */
        public String[] getProposedExtensions() {
            String[] proposedExtensions = null;
            if (getExtensions() != null && !getExtensions().isEmpty()) {
                Map<String, WebSocketExtension> exts = getExtensions();
                proposedExtensions = new String[exts.size()];
                int off = 0;
                for (Entry<String, WebSocketExtension> entry : exts.entrySet()) {
                    WebSocketExtension ext = entry.getValue();
                    String s = ext.getName();
                    Collection<String> epns = ext.getParameterNames();
                    if (epns != null && !epns.isEmpty()) {
                        for (String pn : epns) {
                            String v = ext.getParameter(pn);
                            s += "; " + pn + (v != null ? "=" + v : "");
                        }
                    }
                    proposedExtensions[off++] = s;
                }
                if (off == 0) {
                    proposedExtensions = null;
                } else if (off < proposedExtensions.length) {
                    proposedExtensions = Arrays.copyOf(proposedExtensions, off);
                }
            }
            return proposedExtensions;
        }
    }

    public static interface FrameMonitor {

        public static final long IF_BINARY = 0x0001;
        public static final long IF_TEXT = 0x0002;
        public static final long IF_CONTINUE = 0x0004;
        public static final long IF_UNMASKED = 0x0008;
        public static final long OF_BINARY = 0x0010;
        public static final long OF_TEXT = 0x0020;
        public static final long OF_CONTINUE = 0x0040;
        public static final long IF_MASKED = 0x0080;
        public static final long X_APPLIED = 0x0100;
        public static final long X_UNAPPLIED = 0x0200;

        boolean check(long options, WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy);

        void onCompletedFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload);

        void onOutgoingFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload, Integer off);
    }

    public static interface WebSocketLifecycleListener {

        void onOpened(WebSocket ws, Object... parameters);

        void onInitialized(WebSocket ws);

        void onClosed(WebSocket ws, Object... parameters);
    }
}
