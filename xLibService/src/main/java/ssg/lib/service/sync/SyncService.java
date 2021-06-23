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
package ssg.lib.service.sync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BufferingDI;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.EchoService;
import ssg.lib.service.Repository;
import ssg.lib.service.SERVICE_FLOW_STATE;
import ssg.lib.service.SERVICE_MODE;
import ssg.lib.service.SERVICE_PROCESSING_STATE;
import ssg.lib.service.ServiceProcessor;

/**
 * Prototyping generic instances syncrhonization mechanism.
 *
 * Sync service uses connectors that exchange sync messages to duplicate missing
 * data defined in domains as groups with tree-like items characterized by
 * name,type,id,timestamp.
 *
 *
 *
 * @author 000ssg
 */
public class SyncService<P> implements ServiceProcessor<P> {

    private long options = SPO_TRANSIENT;
    Repository<DataProcessor> dps = new Repository<>();

    List<SyncConnector> connectors = new ArrayList<SyncConnector>();
    Map<String, SyncDataProvider> providers = new LinkedHashMap<>();

    @Override
    public void onAssigned(P p, DI<?, P> di) {
        // no special interest in when assigned/deassigned
    }

    @Override
    public void onDeassigned(P p, DI<?, P> di) {
        // no special interest in when assigned/deassigned
    }

    @Override
    public SERVICE_MODE probe(ServiceProviderMeta<P> meta, Collection<ByteBuffer> data) {
        byte[] buf = new byte[7];
        ByteBuffer probe = BufferTools.probe(ByteBuffer.wrap(buf), data);
        if (probe.remaining() == buf.length) {
            String method = new String(buf);
            if ("SYNCREQ".equals(method)) {
                return SERVICE_MODE.request;
            } else if ("SYNCRSP".equals(method)) {
                return SERVICE_MODE.response;
            }
        }
        return SERVICE_MODE.failed;
    }

    @Override
    public DI<ByteBuffer, P> initPD(ServiceProviderMeta<P> meta, SERVICE_MODE initialState, Collection<ByteBuffer>... data) throws IOException {
        return new Sync<P>(initialState, meta.isSecure());
    }

    @Override
    public SERVICE_FLOW_STATE test(P provider, DI<ByteBuffer, P> pd) throws IOException {
        if (pd instanceof Sync) {
            EchoService.Echo echo = (EchoService.Echo) pd;
            if (echo.hasInput(provider) || echo.hasOutput(provider)) {
                return SERVICE_FLOW_STATE.in_out;
            } else {
                return SERVICE_FLOW_STATE.completed;
            }
        } else {
            return SERVICE_FLOW_STATE.failed;
        }
    }

    @Override
    public SERVICE_PROCESSING_STATE testProcessing(P provider, DI<ByteBuffer, P> pd) throws IOException {
        return SERVICE_PROCESSING_STATE.failed;
    }

    @Override
    public void onServiceError(P provider, DI<ByteBuffer, P> pd, Throwable error) throws IOException {
        // no special treatment for echo...
    }

    @Override
    public Repository<DataProcessor> getDataProcessors(P provider, DI<ByteBuffer, P> pd) {
        return dps;
    }

    @Override
    public String getName() {
        return "Echo service";
    }

    @Override
    public boolean hasOptions(long options) {
        return (this.options & options) == options;
    }

    @Override
    public boolean hasOption(long options) {
        return (this.options & options) != 0;
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        return Collections.emptyList();
    }

    public class Sync<P> extends BufferingDI<ByteBuffer, P> {

        long inCount;
        long outCount;

        SERVICE_MODE state;
        boolean secure;

        public Sync() {
        }

        public Sync(SERVICE_MODE state, boolean secure) {
            this.state = state;
            this.secure = secure;
        }

        @Override
        public long write(P provider, Collection<ByteBuffer>... bufs) throws IOException {
            List<ByteBuffer> bbl = new ArrayList<>();
            long c = BufferTools.moveBuffersTo(bbl, bufs);
            if (c > 0) {
                inCount = c;

                String text = "echo for " + ((secure) ? "secure " : "") + provider + ": input=" + BufferTools.toText("ISO-8859-1", bbl);
                ByteBuffer bb = ByteBuffer.wrap(text.getBytes("ISO-8859-1"));
                outCount = bb.remaining();

                //String text = "echo for " + provider + ": input=" + PDTools.toText("ISO-8859-1", bbl);
                push(provider, Collections.singletonList(bb));
            }
            return c;
        }
    }

    public static interface SyncDataHandler {

        String id();

        List<ByteBuffer> update(Collection<ByteBuffer>... data);
    }

    /**
     * SyncData is packet describing type, id, format, timestamp, and content of
     * sync data exchange item.
     */
    public static class SyncData {

        public static final byte O_GZIPPED = 0x01;
        public static final byte O_FULL = 0x02;

        /**
         * [0-7] - type (SYNCREQ/SYNCRES), [8] - options, [9-17] - id, [18-26] -
         * timestamp
         */
        byte[] header = new byte[7 + 1 + 8 + 8];
    }
}
