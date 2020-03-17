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
package ssg.lib.http.base;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import ssg.lib.common.Replacement;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.buffers.ByteBufferPipeReplacement;

/**
 * Http forwarder passes fromLocal data to toRemote, and return fromRemote data
 * to toLocal adjusting local/remote host names whenever found in texts.
 *
 * @author 000ssg
 */
public class HttpForwarder {

    Channel local;
    Channel remote;
    // data wrappers
    HttpRequest localReq;
    HttpRequest remoteReq;
    HttpResponse localResp;
    HttpResponse remoteResp;
    List<ByteBuffer> responseCache = new ArrayList<ByteBuffer>();
    // recoding data
    String remoteHost;
    String localHost;
    String remoteHostWithPort;
    String localHostWithPort;
    boolean localIsSecure = false;
    boolean remoteIsSecure = false;
    Replacement replacement;
    ByteBuffer replacerBuf = ByteBuffer.allocate(4096);

    private HttpForwarderListener listener;

    public HttpForwarder(Channel local, Channel remote) {
        this.local = local;
        this.remote = remote;
        init();
    }

    public HttpForwarder(Channel local, boolean localIsSecure, Channel remote, boolean remoteIsSecure) {
        this.local = local;
        this.remote = remote;
        this.localIsSecure = localIsSecure;
        this.remoteIsSecure = remoteIsSecure;
        init();
    }

    void init() {
        evalHosts((NetworkChannel) local, (SocketChannel) remote);
    }

    public Channel getRemote() {
        return remote;
    }

    public Channel getLocal() {
        return local;
    }

    public void setLocalHost(String localHost, String localHostWithPort) {
        this.localHost = localHost;
        this.localHostWithPort = localHostWithPort;
    }

    public void setRemoteHost(String remoteHost, String remoteHostWithPort) {
        this.remoteHost = remoteHost;
        this.remoteHostWithPort = remoteHostWithPort;
    }

    public void evalHosts(NetworkChannel local, SocketChannel remote) {
        String remoteHost = null;
        String localHost = null;
        if (remoteHost == null && remote != null) {
            try {
                remoteHost = remote.getRemoteAddress().toString();
                int idx = remoteHost.indexOf("/");
                remoteHostWithPort = (idx > 1) ? remoteHost.substring(0, idx) + remoteHost.substring(remoteHost.indexOf(":") - 1) : remoteHost.substring(1);
                remoteHost = (idx > 1) ? remoteHost.substring(0, idx) : remoteHost.substring(1, remoteHost.indexOf(":"));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (localHost == null && local != null) {
            try {
                localHost = local.getLocalAddress().toString();
                int idx = localHost.indexOf("/");
                localHostWithPort = (idx > 1) ? localHost.substring(0, idx) + localHost.substring(localHost.indexOf(":") - 1) : localHost.substring(1);
                localHost = (idx > 1) ? localHost.substring(0, idx) : localHost.substring(1, localHost.indexOf(":"));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (localHost != null) {
            this.localHost = localHost;
        }
        if (remoteHost != null) {
            this.remoteHost = remoteHost;
        }
        if (this.localHost != null && this.remoteHost != null) {
            replacement = new Replacement(this.localHost, this.remoteHost);
        }
    }

    void close(boolean remote) {
        //
        if (getListener() != null) {
            getListener().onHttpForwarderEvent(this, HttpForwarderListener.PHASE.closeRR, localResp);
        }
        reset();
    }

    public void reset() {
        //System.out.println(getClass().getName() + ".reset: " + ((localResp != null) ? localResp.isSent() : "no incomplete response"));
        localReq = null;
        remoteReq = null;
        localResp = null;
        remoteResp = null;
        ((Buffer) replacerBuf).clear();
        responseCache.clear();
    }

    public int getPreferredBufferSize(boolean internal) {
        return 4096;
    }

    /**
     * Accepts request for forwarding
     *
     * @param bbs
     * @throws IOException
     */
    public void fromLocal(Collection<ByteBuffer>... bbs) throws IOException {
        if (localReq != null && localResp != null) {
            // reset forwarding if previous req/resp is done...
            if (localResp.isSent()) {// .isCompleted()) {
                close(true);
            }
        }
        // request data
        if (localReq == null) {
            //reset();
            localReq = new HttpRequest() {
                @Override
                public void onLoaded() {
                    super.onLoaded();
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.localRequest, localReq);
                    }
                }

                @Override
                public void onHeaderLoaded() {
                    super.onHeaderLoaded();
                    // adjust localhost to request-defined value
                    String host = getHead().getHeader1(HttpData.HH_HOST);
                    if (host != null && !localHostWithPort.startsWith(host)) {
                        if (!host.contains(":") && localHostWithPort.contains(":")) {
                            localHost = host;
                            localHostWithPort = host + localHostWithPort.substring(localHostWithPort.indexOf(":"));
                        } else {
                            localHostWithPort = host;
                            if (host.contains(":")) {
                                host = host.substring(0, host.lastIndexOf(":"));
                            }
                            localHost = host;
                            if (localHost != null && remoteHost != null) {
                                replacement = new Replacement("//" + localHost, "//" + remoteHostWithPort);
                                if (getListener() != null) {
                                    replacement = getListener().buildReplacement(HttpForwarder.this, this, replacement);
                                }
                            }
                        }
                    }
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.localRequestHeader, localReq);
                    }
                }

                @Override
                public void onResponseHeaderSent(HttpResponse resp) {
                    super.onResponseHeaderSent(resp);
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.localResponseHeader, resp);
                    }
                }

                @Override
                public void onResponseSent(HttpResponse resp) {
                    super.onResponseSent(resp);
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.localResponse, resp);
                    }
                }

            };
            localReq.setAllowMultipartBody(false);
            if (bbs != null) {
                for (int i = 0; i < bbs.length; i++) {
                    if (bbs[i] != null) {
                        localReq.add(bbs[i]);
                    }
                }
            }
        } else {
            if (bbs != null && BufferTools.hasRemaining(bbs)) {
                if (localReq.isCompleted() && localResp != null && !localResp.isSent()) {
                    System.out.println("Request data before response is sent: " + BufferTools.dump(bbs).replace("\n", "\n  "));
                }
                synchronized (localReq) {
                    localReq.add(bbs);
                }
            }
        }
        // prepare forwardable version of rewquest once local request head is recognized
        if (remoteReq == null && localReq.isHeaderLoaded()) {
            remoteReq = new HttpRequest(true) {
                @Override
                public void onSent() {
                    super.onSent();
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.remoteRequest, remoteReq);
                    }
                }

                @Override
                public void onHeaderSent() {
                    super.onHeaderSent();
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.remoteRequestHeader, remoteReq);
                    }
                }

                @Override
                public void onResponseHeaderLoaded(HttpResponse resp) {
                    super.onResponseHeaderLoaded(resp);
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.remoteResponseHeader, resp);
                    }
                }

                @Override
                public void onResponseLoaded(HttpResponse resp) {
                    super.onResponseLoaded(resp);
                    if (getListener() != null) {
                        getListener().onHttpForwarderEvent(HttpForwarder.this, HttpForwarderListener.PHASE.remoteResponse, resp);
                    }
                }

            };
            remoteReq.setAllowMultipartBody(false);
            remoteReq.getHead().initFrom(localReq.getHead());
            fixRemoteRequestHeader();
        }
        if (remoteReq != null && localReq != null) {
            if (localReq.isCompleted()) {
                synchronized (remoteReq) {
                    // ensure any request data from local are copied to remote
                    Collection<ByteBuffer> data = null;
                    while ((data = localReq.getBody().removeAsCollection()) != null) {
                        if (data.isEmpty()) {
                            break;
                        }
                        for (ByteBuffer bb : data) {
                            remoteReq.getBody().add(bb);
                        }
                    }
                    remoteReq.onLoaded();
                }
            }
        }
    }

    /**
     * Remote request data to write
     *
     * @return
     * @throws IOException
     */
    public ByteBuffer[] toRemote() throws IOException {
        ByteBuffer[] r = null;
        if (remoteReq != null && !remoteReq.isSent()) {
            synchronized (remoteReq) {
                if (!remoteReq.getHead().isSent()) {
                    remoteReq.getHead().setHeader(HttpData.HH_CACHE_CONTROL, "private, no-store, max-age=0");
                    remoteReq.fixHeadersBeforeSend(false);
                    r = new ByteBuffer[]{remoteReq.getHead().getByteBuffer()};
                    remoteReq.getHead().setSent(true);
                }
                if (!remoteReq.isSent()) {
                    List<ByteBuffer> rr = remoteReq.get();
                    if (rr != null && !rr.isEmpty()) {
                        if (r != null) {
                            int off = r.length;
                            r = Arrays.copyOf(r, off + rr.size());
                            for (int i = 0; i < rr.size(); i++) {
                                r[off + i] = rr.get(i);
                            }
                        } else {
                            r = rr.toArray(new ByteBuffer[rr.size()]);
                        }
                    }
                }
            }
        }
        return r;
    }

    /**
     * Remote response data
     *
     * @param bbs
     * @throws IOException
     */
    public void fromRemote(Collection<ByteBuffer>... bbs) throws IOException {
        if (remoteResp == null && remoteReq != null) {
            remoteResp = new HttpResponse(remoteReq);
            remoteReq.response = remoteResp;
            remoteResp.setAllowMultipartBody(false);
        }
        if (remoteResp != null) {
            if (bbs != null) {
                remoteResp.add(bbs);
            }
            if (localResp == null && remoteResp.isHeaderLoaded()) {
                localResp = new HttpResponse(localReq);
                localReq.response = localResp;
                localResp.setAllowMultipartBody(false);
                localResp.getHead().initFrom(remoteResp.getHead());
                if (needReplacement(localResp)) {
                    if (localResp.getContentLength() != null && localResp.getContentLength() > 0) {
                        // remove content length
                        localResp.setHeader("Content-Length", null);
                        localResp.getHead().size = null;
                        localResp.size = 0;
                        if (!localResp.isChunked()) {
                            localResp.setHeader("Transfer-Encoding", "chunked");
                            //localResp.getHead().chunked = true;
                        }
                    }
                    if (!localResp.isChunked() && (localResp.getContentLength() != null && localResp.getContentLength() > 0)) {
                        localResp.setHeader("Transfer-Encoding", "chunked");
                        //localResp.getHead().chunked = true;
                    }
                    if (needReplacement(localResp)) {
                        Replacement reverse = (replacement != null) ? replacement.reverseCopy() : null;
                        if(getListener()!=null) {
                            reverse=getListener().buildReplacement(this, localResp, reverse);
                        }
                        if (reverse != null) {
                            localResp.input = new ByteBufferPipeReplacement(reverse);
                        }
                    }
                }
                localResp.onHeaderLoaded();
            }
            synchronized (this) {
                if (localResp != null && !localResp.isCompleted()) {
                    boolean rrc = remoteResp.isCompleted();
                    remoteResp.getHead().setSent(true);
                    ByteBuffer[] r = null;
                    synchronized (remoteResp.getBody().data()) {
                        if (!remoteResp.getBody().data().isEmpty()) {
                            r = remoteResp.getBody().data().toArray(new ByteBuffer[remoteResp.getBody().data().size()]);
                            remoteResp.getBody().data().clear();
                        }
                    }

                    if (r != null) {
                        synchronized (localResp) {
                            for (ByteBuffer bb : r) {
                                if (bb != null && bb.hasRemaining()) {
                                    int bc = bb.remaining();
                                    String sss = BufferTools.toText(null, bb);
                                    long bbc = localResp.getBody().length;
                                    localResp.add(bb);
                                    //System.out.println("ADD " + bc + "->LRESP: " + bbc + " -> " + localResp.getBody().length+"\n  "+sss.replace("\n", "\n  "));
                                }
                            }
                            if (rrc) {
                                if (localResp.input != null && !localResp.input.isClosed()) {
                                    localResp.input.close();
                                    localResp.add(ByteBuffer.allocate(0));
                                }
                            }
                        }
                    }
                    if (rrc) {
                        localResp.onLoaded();
                    }
                }
            }
        }
    }

    /**
     * Local response data
     *
     * @return
     * @throws IOException
     */
    public ByteBuffer[] toLocal() throws IOException {
        ByteBuffer[] r = null;
        if (localResp != null) {
            if (localResp.isSent()) {
                return r;
            } else {
                if (!localResp.getHead().isSent()) {
                    fixLocalResponseHeader();
                    localResp.fixHeadersBeforeSend(false);
                    responseCache.add(localResp.getHead().getByteBuffer());
                    localResp.getHead().setSent(true);
                }
                if (!localResp.isSent()) {
                    List<ByteBuffer> bbs = localResp.get();
                    if (bbs != null && !bbs.isEmpty()) {
                        for (ByteBuffer rbb : bbs) {
                            responseCache.add(rbb);
                        }
                    }
                }
            }
        }
        if (!responseCache.isEmpty()) {
            synchronized (responseCache) {
                r = responseCache.toArray(new ByteBuffer[responseCache.size()]);
                responseCache.clear();
            }
        }
        return r;
    }

    public boolean hasExternalData() {
        if (!responseCache.isEmpty()) {
            return true;
        } else if (localResp == null) {
            return false;
        } else if (localResp.isCompleted() && !localResp.getHead().isSent() || localResp.getBody().size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean needReplacement(HttpData data) {
        if (getListener() != null) {
            Boolean need = getListener().needReplacement(data);
            if (need != null) {
                return need;
            }
        }
        String contentType = data.getContentType();

        if (contentType == null) {
            return false;
        } else if (contentType.contains("image")) {
            return false;
        } else if (contentType.contains("multipart-")) {
            return false;
        } else {
            return contentType.contains("text") || contentType.contains("json") || contentType.contains("xml") || contentType.contains("script");
        }
    }

    /**
     * Performs corrections to remote request header: adjusts "host" value and
     * converts to chunked stream if has data.
     */
    public synchronized void fixRemoteRequestHeader() throws IOException {
        // adjust host...
        remoteReq.setHeader("host", remoteHost);
        Long cl = remoteReq.getContentLength();
        if (!remoteReq.isChunked() && cl != null && remoteReq.getContentLength() > 0) {
            remoteReq.setHeader("Transfer-Encoding", "chunked");
        }
        remoteReq.onHeaderLoaded();
    }

    /**
     *
     */
    public synchronized void fixLocalResponseHeader() throws IOException {
        String loc = localResp.getHead().getHeader1("Location");
        if (loc != null && loc.contains(remoteHost)) {
            if (loc.contains(remoteHostWithPort)) {
                loc = loc.replace(remoteHostWithPort, localHostWithPort);
            } else {
                loc = loc.replace(remoteHost, localHostWithPort);
            }
            if (localIsSecure != remoteIsSecure && loc.contains("http")) {
                if (!localIsSecure && loc.startsWith("https://")) {
                    loc = "http://" + loc.substring(8);
                } else if (localIsSecure && loc.startsWith("http://")) {
                    loc = "https://" + loc.substring(7);
                }
            }
            localResp.setHeader("Location", loc);
        }
    }

    /**
     * Interface to track forwarding process as well as affect replacement
     * decision(s).
     */
    public static interface HttpForwarderListener {

        /**
         * Forwarding life cycle phases in the transition order.
         */
        public static enum PHASE {
            localRequestHeader,
            localRequest,
            remoteRequestHeader,
            remoteRequest,
            remoteResponseHeader,
            remoteResponse,
            localResponseHeader,
            localResponse,
            closeRR
        }

        /**
         * Returns true if replacement is needed, false - if not, null - use
         * default logic of HttpForwarder.
         *
         * @param data
         * @return
         */
        Boolean needReplacement(HttpData data);

        /**
         * Builds replacement for given data. Returns provided replacement if no
         * special logic is needed.
         *
         * @param forwarder
         * @param data request or response (with full header) for replacement
         * @param replacement default replacement to return or re-use
         * @return
         */
        Replacement buildReplacement(HttpForwarder forwarder, HttpData data, Replacement replacement);

        /**
         * Notifications on forwarding life cycle events.
         *
         * @param pahse
         * @param data
         */
        void onHttpForwarderEvent(HttpForwarder forwarder, PHASE pahse, HttpData data);
    }

    public static class HttpForwarderListenerDebug implements HttpForwarderListener {

        public static final int PO_TIMESTAMP = 0x0001;
        public static final int PO_THREAD = 0x0002;
        public static final int PO_NAME = 0x0004;
        public static final int PO_DATA = 0x0008;

        PrintStream out = System.out;
        int options = PO_TIMESTAMP | PO_THREAD | PO_NAME | PO_DATA;

        public HttpForwarderListenerDebug() {
        }

        public HttpForwarderListenerDebug configure(PrintStream out) {
            this.out = out;
            return this;
        }

        @Override
        public Boolean needReplacement(HttpData data) {
            return null;
        }

        @Override
        public Replacement buildReplacement(HttpForwarder forwarder, HttpData data, Replacement replacement) {
            return replacement;
        }

        @Override
        public void onHttpForwarderEvent(HttpForwarder forwarder, PHASE phase, HttpData data) {
            if (out != null) {
                StringBuilder sb = new StringBuilder();
                if ((options & PO_TIMESTAMP) != 0) {
                    sb.append("[" + System.currentTimeMillis() + "]");
                }
                if ((options & PO_THREAD) != 0) {
                    sb.append("[" + Thread.currentThread().getName() + "]");
                }
                if ((options & PO_NAME) != 0) {
                    sb.append("[" + (getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()) + "]");
                }
                sb.append("[" + phase + "]");
                if (data != null) {
                    if ((options & PO_DATA) != 0) {
                        sb.append("\n  ");
                        switch (phase) {
                            case remoteResponseHeader:
                            case remoteResponse:
                            case localResponseHeader:
                            case localResponse:
                                sb.append(("" + data).replace("\n", "\n  |  "));
                                break;
                            default:
                                sb.append(("" + data).replace("\n", "\n  |  "));
                        }
                    } else {
                        if (data instanceof HttpRequest) {
                            sb.append(((HttpRequest) data).getQuery() + "  " + data.getContentLength());
                        } else if (data instanceof HttpResponse) {
                            HttpResponse resp = (HttpResponse) data;
                            sb.append(resp.getResponseCode() + "  " + resp.getResponseMessage() + "; " + resp.getContentLength());
                        }
                    }
                }

                out.println(sb.toString());
            }
        }

    }

    /**
     * @return the listener
     */
    public HttpForwarderListener getListener() {
        return listener;
    }

    /**
     * @param listener the listener to set
     */
    public void setListener(HttpForwarderListener listener) {
        this.listener = listener;
    }
}
