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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BufferingDI;

/**
 *
 * @author 000ssg
 */
public class EchoService<P> implements ServiceProcessor<P> {

    private long options = SPO_TRANSIENT;
    byte[] echoPrefix;
    byte[] echoSuffix;

    Repository<DataProcessor> dps = new Repository<>();

    public EchoService() {
    }

    public EchoService(byte[] echoPrefix) {
        this.echoPrefix = echoPrefix;
    }

    public EchoService(byte[] echoPrefix, byte[] echoSuffix) {
        this.echoPrefix = echoPrefix;
        this.echoSuffix = echoSuffix;
    }

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
        return SERVICE_MODE.request;
    }

    @Override
    public DI<ByteBuffer, P> initPD(ServiceProviderMeta<P> meta, SERVICE_MODE initialState, Collection<ByteBuffer>... data) throws IOException {
        return new Echo<P>(initialState, meta.isSecure());
    }

    @Override
    public SERVICE_FLOW_STATE test(P provider, DI<ByteBuffer, P> pd) throws IOException {
        if (pd instanceof Echo) {
            Echo echo = (Echo) pd;
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

    public class Echo<P> extends BufferingDI<ByteBuffer, P> {

        long inCount;
        long outCount;

        SERVICE_MODE state;
        boolean secure;

        public Echo() {
        }

        public Echo(SERVICE_MODE state, boolean secure) {
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

    public static class EchoDataProcessor implements DataProcessor {

        @Override
        public void onAssigned(Object p, DI di) {
            // no special interest in when assigned/deassigned
        }

        @Override
        public void onDeassigned(Object p, DI di) {
            // no special interest in when assigned/deassigned
        }

        @Override
        public HST probe(Object provider, DI data) {
            return HST.process;
        }

        @Override
        public SERVICE_PROCESSING_STATE check(Object provider, DI data) throws IOException {
            return SERVICE_PROCESSING_STATE.OK;
        }

        @Override
        public Runnable getRunnable(Object provider, DI data) {
            return null;
        }

        @Override
        public List<Task> getTasks(TaskPhase... phases) {
            return Collections.emptyList();
        }

    }
}
