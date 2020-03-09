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

import ssg.lib.http.HttpMatcher;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.buffers.ByteBufferPipe;
import ssg.lib.common.buffers.ByteBufferPipeChunk;
import ssg.lib.common.buffers.ByteBufferPipeGUNZIP;
import ssg.lib.common.buffers.ByteBufferPipeGZIP;
import ssg.lib.common.buffers.ByteBufferPipeUnchunk;

/**
 * HttpData represents request or response headers and body.
 *
 * @author 000ssg
 */
public abstract class HttpData {

    public static final long HF_NONE = 0x0000;
    public static final long HF_SWITCHED = 0x0001; // non-http data flow (switched protocol)

    public static final String CRLF = "\r\n";
    public static final char CR = '\r';
    public static final char LF = '\n';

    public static final String HH_ACCEPT = "Accept";
    public static final String HH_ACCEPT_CHARSET = "Accept-Charset";
    public static final String HH_ACCEPT_ENCODING = "Accept-Encoding";
    public static final String HH_ACCEPT_LANGUAGE = "Accept-Language";
    public static final String HH_CONTENT_TYPE = "Content-Type";
    public static final String HH_CONTENT_LENGTH = "Content-Length";
    public static final String HH_TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String HH_CONTENT_ENCODING = "Content-Encoding";
    public static final String HH_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String HH_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HH_COOKIE = "Cookie";
    public static final String HH_SET_COOKIE = "Set-Cookie";
    public static final String HH_ETAG = "ETag";
    public static final String HH_CONNECTION = "Connection";
    public static final String HH_UPGRADE = "Upgrade";
    public static final String HH_HOST = "Host";
    public static final String HH_SERVER = "Server";
    public static final String HH_LOCATION = "Location";
    public static final String HH_REFERER = "Referer";
    public static final String HH_CACHE_CONTROL = "Cache-Control";
    public static final String HH_PRAGMA = "Pragma";
    public static final String HH_DATE = "Date";
    public static final String HH_EXPIRES = "Expires";
    public static final String HH_LAST_MODIFIED = "Last-Modified";
    public static final String HH_KEEP_ALIVE = "Keep-Alive";
    public static final String HH_IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String HH_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HH_ACCEPT_RANGES = "Accept-Ranges";
    public static final String HH_CONTENT_RANGE = "Content-Range";
    public static final String HH_RANGE = "Range";
    // transfer-encoding values
    public static final String HTE_CHUNKED = "chunked";
    public static final String HTE_COMPRESS = "compress";
    public static final String HTE_DEFLATE = "deflate";
    public static final String HTE_GZIP = "gzip";
    public static final String HTE_IDENTITY = "identity";
    // connection upgrade-related
    public static final String HCONN_CLOSE = "close";
    public static final String HCONN_KEEP_ALIVE = "keep-alive";
    public static final String HCONN_UPGRADE = "Upgrade";
    public static final String HUPGR_WEBSOCKET = "websocket";
    public static final String HUPGR_HTTP2 = "h2";
    // cache control(s)
    public static final String HCC_PUBLIC = "public";
    public static final String HCC_PRIVATE = "private";
    public static final String HCC_NO_CACHE = "no-cache";
    public static final String HCC_NO_STORE = "no-store";
    // data formats
    // public static final String GMT_TIME_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"; -> see toHeaderDateFormat...

    public String httpVersion = "HTTP/1.1";
    Head head = new Head(this);
    ByteBufferPipe input; // bytes preprocessor on adding data: unchunk, gunzip, etc.
    ByteBufferPipe output; // bytes post-processor on generating output data: gzip, chunk etc.
    Body body = new Body(this); //List<ByteBuffer> body = new ArrayList<ByteBuffer>();
    long size = 0;
    boolean completed = false;
    private boolean sent = false;
    long sentSize = 0;
    long outputSize = 0;
    boolean secure = false;
    boolean cancelled = false;
    // config
    private boolean bypassCompression = false;
    boolean allowMultipartBody = true;

    // timing
    long creationTime = System.currentTimeMillis();
    long lastUpdateTime = creationTime;
    long firstSendTime = 0;
    long lastSendTime = 0;
    long sendCompletionTime = 0;
    long completionTime = 0;

    // events listener
    List<HttpEventListener> listeners = new ArrayList<>();

    private List<Runnable> processors;
    private HttpContext context;

    private long flags = HF_NONE;

    public HttpData() {
    }

    public HttpData(HttpData http) throws IOException {
        this.setContext(http.getContext());
        this.head.initFrom(http.head);
        if (http.getBody() instanceof MultipartBody) {
            this.body = new MultipartBody(this);
        }
        this.body.initFrom(http.getBody());
    }

    public static ThreadLocal<DateFormat> hdtf = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf;
        }
    };

    public static String toHeaderDatetime(long time) {
        try {
            return hdtf.get().format(new Date(time));
        } catch (Throwable th) {
            return null;
        }
    }

    public static long fromHeaderDatetime(String time) {
        try {
            return hdtf.get().parse(time).getTime();
        } catch (Throwable th) {
            return 0;
        }
    }

    public boolean isHeaderLoaded() {
        return head.completed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    public void secure(boolean secure) {
        this.secure = secure;
    }

    public Head getHead() {
        return head;
    }

    public Body getBody() {
        return body;
    }

//    @Override
    public boolean ready() {
        return completed;
    }

//    @Override
    public long getCreationTime() {
        return creationTime;
    }

//    @Override
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

//    @Override
    public long getLastSendTime() {
        return lastSendTime;
    }

    public void addHttpEventListener(HttpEventListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeHttpEventListener(HttpEventListener l) {
        if (l != null && listeners.contains(l)) {
            listeners.remove(l);
        }
    }

    public HttpMatcher getMatcher() {
        return (head != null) ? head.matcher : null;
    }

    public long add(Collection<ByteBuffer>... bufs) throws IOException {
        long r = BufferTools.getRemaining(bufs);
        if (bufs != null) {
            for (Collection<ByteBuffer> bbs : bufs) {
                if (bbs != null) {
                    for (ByteBuffer bb : bbs) {
                        if (bb != null) {
                            add(bb);
                        }
                    }
                }
            }
        }
        return r - BufferTools.getRemaining(bufs);
    }

    void add(ByteBuffer bb) throws IOException {
        lastUpdateTime = System.currentTimeMillis();
        if (head.isSent() && !head.chunked) {
            if (head.size == null || head.size <= 0) {
                throw new IOException("Cannot add data in non-chunked mode since header is already sent.");
            }
        }
        boolean cc = completed;
        if (bb.hasRemaining()) {
            if (!head.completed) {
                head.add(bb);
                if (head.completed) {
                    if (head.chunked) {
                        //chunk = new Chunk();
                        input = new ByteBufferPipeUnchunk();
                    }
                    if (!isBypassCompression()) {
                        String ce = this.getContentEncoding();
                        if (ce != null && ce.contains(HTE_GZIP)) {
                            ByteBufferPipe gunzip = new ByteBufferPipeGUNZIP();
                            if (input != null) {
                                input.setNested(gunzip);
                            } else {
                                input = gunzip;
                            }
                        }
                    }
                    onHeaderLoaded();
                    if (listeners != null) {
                        for (HttpEventListener l : listeners) {
                            l.onHeaderLoaded(this);
                        }
                    }

                    // adjust body if needed
                    body = adjustBody(body);
                }
            }
            if (head.completed && bb.hasRemaining()) {
                if (input != null) {
                    // chunked or gzipped, read until completed
                    if (!head.chunked) {
                        size += bb.remaining();
                    }
                    input.write(bb);
                    if (!head.chunked && head.size != null && head.size == size) {
                        input.close();
                    }
                    ByteBuffer bb2 = input.read(4096);
                    while (bb2 != null) {
                        body.add(bb2);
                        bb2 = input.read(4096);
                        if (bb2 != null && bb2.remaining() == 0) {
                            break;
                        }
                    }
                    if (input.isClosed()) {
                        completed = true;
                    }
                } else {
                    // fixed-size body -> read content length bytes
                    size += bb.remaining();
                    body.add(bb);
                    if (head.size != null) {
                        if (head.size == size || head.isConnectionClose() && head.size - size <= 6) {
                            completed = true;
                        }
                    }
                }
            } else if (head.completed) {
                // no chunk of length -> no body -> completed
                if (!head.chunked && (head.size == null || head.size == 0)) {
                    completed = true;
                }
            }
        }
        if (!cc && completed) {
            onLoaded();
            if (listeners != null) {
                for (HttpEventListener l : listeners) {
                    l.onCompleted(this);
                }
            }
        }
    }

    /**
     * Short method to fetch full output in a single call. May take time/memory,
     * so consider using sequence of "get" calls.
     *
     * @return
     * @throws IOException
     */
    public List<ByteBuffer> getAll() throws IOException {
        List<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
        while (!isSent()) {
            List<ByteBuffer> bbs2 = get();
            if (bbs2 != null && !bbs2.isEmpty()) {
                bbs.addAll(bbs2);
            }
        }
        return bbs;
    }

    /**
     * Converts object to serialized HTTP data updating internal flags to
     * indicate done.
     *
     * @return
     * @throws IOException
     */
    public List<ByteBuffer> get() throws IOException {
        lastSendTime = System.currentTimeMillis();
        List<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
        if (!isSent()) {
            if (head.completed) {
                if (firstSendTime == 0) {
                    firstSendTime = lastSendTime;
                }
                // add headers if not yet
                long headerBytes = 0;
                if (!head.isSent()) {
                    if (listeners != null) {
                        for (HttpEventListener l : listeners) {
                            l.onPrepareSend(this);
                        }
                    }

                    fixHeadersBeforeSend("true".equals(head.getHeader1("no-chunks")));
                    head.setSent(true);
                    bbs.add(head.getByteBuffer());
                    headerBytes = BufferTools.getRemaining(bbs);
                    if (body != null) {
                        body.fixBodyBeforeSend();
                    }
                }
                // add body
                if (body != null && !body.isEmpty()) {
                    synchronized (body) {
                        if (output != null) {
                            Collection<ByteBuffer> bbbb = body.removeAsCollection();
                            //System.out.println("BODY CHUNK: "+PDTools.toText(null, bbbb));
                            output.write(bbbb.toArray(new ByteBuffer[bbbb.size()]));

                            // flush from output any remaining bytes...
                            if (completed && body.isEmpty() && output != null && !output.isClosed()) {
                                output.close();
                            }
                            ByteBuffer bb2 = output.read(4096);
                            while (bb2 != null) {
                                bbs.add(bb2);
                                bb2 = output.read(4096);
                                if (bb2 != null && bb2.remaining() == 0) {
                                    break;
                                }
                            }
                        } else {
                            bbs.addAll(body.removeAsCollection());
                        }
                    }
                }
                // check if last chunk is needed in chunked mode
                if (head.chunked && body.isEmpty()) {
                    if (completed) {
                        // CHUNK EOF
                        if (output != null && !output.isClosed()) {
                            output.close();

                            ByteBuffer bb2 = output.read(4096);
                            while (bb2 != null) {
                                bbs.add(bb2);
                                bb2 = output.read(4096);
                                if (bb2 != null && bb2.remaining() == 0) {
                                    break;
                                }
                            }
                        }

                        // completed data send for chuinked mode...
                        setSent(true);
                    }
                } else if (!head.chunked) {
                    if (size > 0) {
                        sentSize = BufferTools.getRemaining(bbs) - headerBytes;
                        if (size == sentSize) {
                            // completed data send for non-empty non-chunked body
                            setSent(true);
                        }
                    } else if (body.isEmpty() && completed) {
                        // completed data send for empty body.
                        setSent(true);
                    }
                }
            }
        }
        outputSize += BufferTools.getRemaining(bbs);
        return bbs;
    }

    /**
     * Invoked once header and body are completed...
     */
    public abstract void onHeaderLoaded();

    /**
     * Invoked once header and body are completed...
     */
    public void onLoaded() {
        if (completionTime == 0) {
            completionTime = System.currentTimeMillis();
        }
        if (!getHead().completed) {
            onHeaderLoaded();
        }
        if (!completed && getHead().completed) {
            completed = true;
        }
    }

    /**
     * Entry point to adjust body parameters, e.g. replace default
     * implementation with MultipartBody variant.
     *
     * @param body
     * @return
     */
    public Body adjustBody(Body body) {
        if (isAllowMultipartBody()) {
            String ct = getContentType();
            if (ct != null && ct.contains("multipart-")) {
                return new MultipartBody(this);
            }
        }
        return body;
    }

    /**
     * Ensure header has proper data length control headers
     * (Transfer-Encoding/Content-Length).
     *
     * @throws IOException
     */
    public void fixHeadersBeforeSend(boolean preventChunks) throws IOException {
        if (head.isSent()) {
            throw new IOException("Cannot fix already sent headres.");
        }
        // adjust headers
        String lengthHeader = null;
        boolean hasChunked = false;
        boolean hasGzip = false;
        boolean hasLength = false;
        for (String hn : head.headers.keySet()) {
            if ("Transfer-Encoding".equalsIgnoreCase(hn)) {
                String[] tes = head.headers.get(hn);
                if (tes != null) {
                    for (String te : tes) {
                        if (HTE_CHUNKED.equalsIgnoreCase(te)) {
                            hasChunked = true;
                            head.chunked = true;
                            break;
                        }
                    }
                }
            } else if ("Content-Length".equalsIgnoreCase(hn)) {
                hasLength = true;
                lengthHeader = hn;
            } else if ("Content-Encoding".equalsIgnoreCase(hn)) {
                String[] ces = head.headers.get(hn);
                if (ces != null) {
                    for (String ce : ces) {
                        if (HTE_GZIP.equalsIgnoreCase(ce)) {
                            hasGzip = true;
                            break;
                        }
                    }
                }
            }
        }
        // force chunked mode when sending ,ultipart body
        if (body instanceof MultipartBody && !preventChunks) {
            head.chunked = true;
        }
        if (hasGzip && !head.chunked) {
            head.chunked = true;
        }
        if (head.chunked) {
            if (hasLength) {
                // drop length
                head.headers.remove(lengthHeader);
            }
            if (!hasChunked) {
                head.addHeader("Transfer-Encoding", HTE_CHUNKED);
            }
        } else if (body.length() > 0 && !hasLength) {
            if (preventChunks) {
                long len = body.length();
                head.addHeader("Content-Length", "" + len);
            } else {
                // no length -> set chunked
                head.chunked = true;
                if (!hasChunked) {
                    head.addHeader("Transfer-Encoding", "chunked");
                }
            }
        } else if (hasChunked && !head.chunked) {
            head.chunked = true;
        }

        // prepare output post-processor...
        if (head.chunked) {
            output = new ByteBufferPipeChunk();
        }
        if (!isBypassCompression() && head.chunked || (head.size != null && head.size > 0)) {
            String ce = this.getContentEncoding();
            if (ce != null && ce.contains(HTE_GZIP)) {
                ByteBufferPipe gzip = new ByteBufferPipeGZIP();
                if (output != null) {
                    output.setNested(gzip);
                } else {
                    output = gzip;
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public String getContentType() {
        String ct = getHead().getHeader1(HH_CONTENT_TYPE);
        return ct;
    }

    public String getContentEncoding() {
        String ct = getHead().getHeader1(HH_CONTENT_ENCODING);
        return ct;
    }

    public String getContentCharset() {
        String ct = getHead().getHeader1(HH_CONTENT_TYPE);
        if (ct != null && ct.toLowerCase().contains("charset")) {
            int idx1 = ct.toLowerCase().indexOf("charset");
            idx1 = ct.indexOf("=", idx1 + 7);
            idx1++;
            int idx2 = ct.indexOf(";", idx1);
            if (idx2 == -1) {
                idx2 = ct.length();
            }
            return ct.substring(idx1, idx2).trim();
        }
        return null;
    }

    public String[] getTransferEncoding() {
        String ct = getHead().getHeader1(HH_TRANSFER_ENCODING);
        if (ct != null) {
            return ct.split(" ");
        } else {
            return null;
        }
    }

    public boolean isChunked() {
        String[] ss = getTransferEncoding();
        if (ss == null || ss.length == 0) {
            return false;
        }
        for (String s : ss) {
            if (HTE_CHUNKED.equals(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean isGZipped() {
        if (HTE_GZIP.equals(getContentEncoding())) {
            return true;
        }
        String[] ss = getTransferEncoding();
        if (ss == null || ss.length == 0) {
            return false;
        }
        for (String s : ss) {
            if (HTE_GZIP.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns value of "Content-Length" header as long or null if none or
     * invalid (unparseable).
     *
     * @return
     */
    public Long getContentLength() {
        String cl = getHead().getHeader1(HH_CONTENT_LENGTH);
        if (cl != null) {
            try {
                return Long.parseLong(cl);
            } catch (Throwable th) {
                return null;
            }
        } else {
            return null;
        }
    }

    public long getSentSize() {
        return sentSize;
    }

    public long getOutputSize() {
        return outputSize;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("\n  secure=" + secure);
        sb.append("\n  size=" + size);
        sb.append("\n  completed=" + completed + ((completed) ? " in " + (completionTime - creationTime) + "ms" : ""));
        sb.append("\n  sent=" + sent + "/" + sentSize + ((sent) ? " in " + (sendCompletionTime - firstSendTime) + "ms" : ""));
        if (context != null) {
            sb.append("\n  context=" + context.toString().replace("\n", "\n  "));
        }
        sb.append("\n  head=" + ("" + head).replace("\n", "\n  "));
        if (input != null) {
            sb.append("\n  input=" + ("" + input).replace("\n", "\n  "));
        }
        if (output != null) {
            sb.append("\n  output=" + ("" + output).replace("\n", "\n  "));
        }
        sb.append("\n  body=" + body.toString().replace("\n", "\n  "));
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    /**
     * @return the sent
     */
    public boolean isSent() {
        return sent;
    }

    /**
     * @param sent the sent to set
     */
    public void setSent(boolean sent) {
        if (!this.sent && sent) {
            sendCompletionTime = System.currentTimeMillis();
        }
        this.sent = sent;
        if (sent && body != null) {
            body.sent = true;
        }
    }

    /**
     * @return the bypassCompression
     */
    public boolean isBypassCompression() {
        return bypassCompression;
    }

    /**
     * @param bypassCompression the bypassCompression to set
     */
    public void setBypassCompression(boolean bypassCompression) {
        this.bypassCompression = bypassCompression;
    }

    public boolean isAllowMultipartBody() {
        return allowMultipartBody;
    }

    public void setAllowMultipartBody(boolean allow) {
        allowMultipartBody = allow;
    }

    /**
     * @return the processors to execute
     */
    public Runnable[] fetchProcessors() {
        Runnable[] r = null;
        if (processors != null && !processors.isEmpty()) {
            synchronized (processors) {
                r = processors.toArray(new Runnable[processors.size()]);
                processors.clear();
            }
        }
        return r;
    }

    public boolean hasProcessors() {
        return processors != null && !processors.isEmpty();
    }

    /**
     * @param processors the processor to add
     */
    public void addProcessors(Runnable... processors) {
        if (processors != null && processors.length > 0) {
            if (this.processors == null) {
                this.processors = Collections.synchronizedList(new ArrayList<Runnable>());
            }
            for (Runnable r : processors) {
                if (r != null) {
                    this.processors.add(r);
                }
            }
        }
    }

    /**
     * @return the context
     */
    public HttpContext getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(HttpContext context) {
        this.context = context;
    }

    /**
     * @return the flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @param flags the flags to set
     */
    public void setFlags(long flags) {
        this.flags = flags;
    }

    public boolean hasFlags(long flags) {
        return flags != HF_NONE && (this.flags & flags) == flags;
    }
}
