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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Matcher;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.service.ABC_ServiceProcessor.ABC_DataProcessor;

/**
 * Demo service that provides different response for A, B or C requests.
 *
 * @author 000ssg
 */
public class ABC_ServiceProcessor<P> implements ServiceProcessor<P> {

    Repository<DataProcessor> dps = new Repository<>();
    Map<Character, ABC_DataProcessor> dpm = new LinkedHashMap<>();
    long options = SPO_TRANSIENT;

    public ABC_ServiceProcessor(char... chars) {
        if (chars != null) {
            for (char ch : chars) {
                this.addDataHandler(ch, "Simple '" + ch + "': ");
            }
        }
    }

    public void addDataHandler(final char ch, final String resp) {
        ABC_DataProcessor adp = dpm.get(ch);
        if (adp != null && !adp.resp.equals(resp)) {
            dps.find(new Matcher<DataProcessor>() {
                @Override
                public float match(DataProcessor t) {
                    try {
                        ABC_DataProcessor abc = (ABC_DataProcessor) t;
                        return (abc.scope == ch && abc.resp.equals(resp)) ? 1 : 0;
                    } catch (Throwable th) {
                    }
                    return 0;
                }

                @Override
                public float weight() {
                    return 1;
                }
            }, null);
        }
        if (adp == null) {
            adp = new ABC_DataProcessor(ch, resp);
            dps.addItem(adp);
            dpm.put(ch, adp);
        }
    }

    @Override
    public void onAssigned(P p, DI<?, P> di) {
        System.out.println("Assigned " + this + " to " + p);
    }

    @Override
    public void onDeassigned(P p, DI<?, P> di) {
        System.out.println("Deassigned " + this + " from " + p);
    }

    @Override
    public String getName() {
        return "ABC service";
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
    public SERVICE_MODE probe(ServiceProviderMeta<P> meta, Collection<ByteBuffer> data) {
        char ch = getABC(data);
        if (dpm.containsKey(ch)) {
            return SERVICE_MODE.request;
        }
        return SERVICE_MODE.unknown;
    }

    @Override
    public DI<ByteBuffer, P> initPD(ServiceProviderMeta<P> meta, SERVICE_MODE initialState, Collection<ByteBuffer>... data) throws IOException {
        char ch = getABC(data);
        if (dpm.containsKey(ch)) {
            ABC_DI di = new ABC_DI();
            di.write(meta.getProvider(), data);
            return di;
        }
        return null;
    }

    @Override
    public SERVICE_FLOW_STATE test(P provider, DI<ByteBuffer, P> pd) throws IOException {
        ABC_DI di = (pd instanceof ABC_DI) ? (ABC_DI) pd : null;
        return (di instanceof ABC_DI && di.data(provider) != null && di.data(provider)[1] != null && di.data(provider)[0] != null)
                ? SERVICE_FLOW_STATE.in
                : (di.data(provider) != null && di.data(provider)[0] != null && di.data(provider)[1] != null)
                ? SERVICE_FLOW_STATE.out
                : (di.data(provider) == null || di.data(provider)[0] == null && di.data(provider)[1] == null)
                ? SERVICE_FLOW_STATE.completed
                : SERVICE_FLOW_STATE.failed;
    }

    @Override
    public SERVICE_PROCESSING_STATE testProcessing(P provider, DI<ByteBuffer, P> pd) throws IOException {
        return SERVICE_PROCESSING_STATE.failed;
    }

    @Override
    public void onServiceError(P provider, DI<ByteBuffer, P> pd, Throwable error) throws IOException {
        // ABC does not react to service errors
    }

    @Override
    public Repository<DataProcessor> getDataProcessors(P provider, DI<ByteBuffer, P> pd) {
        return dps;
    }

    public char getABC(Collection<ByteBuffer>... data) {
        ByteBuffer bb = BufferTools.firstNonEmpty(data);
        if (bb != null) {
            bb.mark();
            byte b = bb.get();
            bb.rewind();
            switch (b) {
                case 'A':
                    return 'A';
                case 'B':
                    return 'B';
                case 'C':
                    return 'C';
                default:
            }
        }
        return 0;
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        return Collections.emptyList();
    }

    public class ABC_DataProcessor implements DataProcessor {

        char scope;
        String resp;

        public ABC_DataProcessor(char scope, String resp) {
            this.scope = scope;
            this.resp = resp;
        }

        @Override
        public void onAssigned(Object p, DI di) {
            System.out.println("Assigned " + this + " to " + p);
        }

        @Override
        public void onDeassigned(Object p, DI di) {
            System.out.println("Deassigned " + this + " from " + p);
        }

        @Override
        public HST probe(Object provider, DI data) {
            HST hst = HST.none;
            if (data instanceof ABC_DI && ((ABC_DI) data).firstChar(provider) == scope) {
                ABC_DI di = (ABC_DI) data;
                ByteBuffer[] bb = di.data(provider);
                if (bb[1] != null && bb[0] == null) {
                    bb[0] = ByteBuffer.wrap(resp.getBytes());
                    return HST.process;
                }
            }
            return hst;
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

    public class ABC_DI<P> extends BaseDI<ByteBuffer, P> {

        /**
         * ByteBuffer[]{0 - prefix, 1-input]. ABC data processor is responsible
         * for adding prefix -> then output is produced.
         */
        Map<P, ByteBuffer[]> input = new LinkedHashMap<>();

        public char firstChar(P provider) {
            ByteBuffer[] bufs = input.get(provider);
            if (bufs[1] != null) {
                bufs[1].mark();
                char ch = (char) bufs[1].get();
                bufs[1].rewind();
                return ch;
            }
            return 0;
        }

        public ByteBuffer[] data(P provider) {
            ByteBuffer[] bufs = input.get(provider);
            return bufs;
        }

        @Override
        public long size(Collection<ByteBuffer>... data) {
            return BufferTools.getRemaining(data);
        }

        @Override
        public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
            List<ByteBuffer> bufs = new ArrayList<>();
            BufferTools.moveBuffersTo(bufs, data);
            if (bufs != null && bufs.size() > 1) {
                bufs = (List<ByteBuffer>) BufferTools.aggregate(
                        (int) BufferTools.getRemaining(bufs),
                        true,
                        bufs);
            }
            if (bufs != null && bufs.size() == 1) {
                ByteBuffer[] bb = input.get(provider);
                if (bb == null || bb[1] == null) {
                    input.put(provider, new ByteBuffer[]{null, bufs.get(0)});
                }
            }
        }

        @Override
        public List<ByteBuffer> produce(P provider) throws IOException {
            ByteBuffer[] bb = (input.containsKey(provider)) ? input.get(provider) : null;
            if (bb != null && bb[0] != null) {
                List<ByteBuffer> r = new ArrayList<>();
                r.add(bb[0]);
                r.add(bb[1]);
                bb[0] = null;
                bb[1] = null;
                return r;
            }
            return null;
        }

    }
}
