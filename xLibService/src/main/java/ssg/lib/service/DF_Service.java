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
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channel;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Matcher;
import ssg.lib.common.TaskExecutor;
import ssg.lib.common.TaskExecutor.TaskExecutorListener;
import ssg.lib.common.TaskProvider;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DF;
import ssg.lib.di.DI;
import ssg.lib.di.DM;
import ssg.lib.di.base.BaseDF;
import ssg.lib.di.base.BaseDI;
import ssg.lib.service.DF_Service.ServiceHandler;
import ssg.lib.service.DataProcessor.HST;
import static ssg.lib.service.DataProcessor.HST.none;
import static ssg.lib.service.DataProcessor.HST.process;
import ssg.lib.service.Repository.ListeningMatcher;
import ssg.lib.service.Repository.Matched;
import ssg.lib.service.Repository.RepositoryListener;
import static ssg.lib.service.SERVICE_MODE.failed;
import static ssg.lib.service.SERVICE_PROCESSING_STATE.OK;
import static ssg.lib.service.SERVICE_PROCESSING_STATE.needProcess;
import static ssg.lib.service.SERVICE_PROCESSING_STATE.preparing;
import static ssg.lib.service.ServiceProcessor.SPO_TRANSIENT;
import ssg.lib.service.ServiceProcessor.ServiceProviderMeta;

/**
 * Service organizes service handler, processors, and data processors for
 * provider-specific data handling.
 *
 * @author 000ssg
 * @param <P>
 */
public class DF_Service<P extends Channel> extends BaseDF<ByteBuffer, P> implements TaskProvider, RepositoryListener<TaskProvider>, TaskExecutorListener {

    TaskExecutor taskExecutor;

    Repository<ServiceProcessor> services = new Repository<>((RepositoryListener) this);
    Repository<DataProcessor> dataProcessors = new Repository<>((RepositoryListener) this);
    List<DF_ServiceListener> listeners = new ArrayList<>();

    // runtime: per provider service handler and security object...
    Map<P, ServiceHandler> serviceHandlers = new HashMap<>();
    Map<P, Object> serviceSecurity = new HashMap<>();
    Map<P, ProviderStatistics> serviceStatisticsRT = new HashMap<>();
    List<ProviderStatistics> serviceStatistics = new ArrayList<>();
    Map<P, Long> providerOpened = new HashMap<>(); // for statistics...

    public DF_Service() {
    }

    public DF_Service(DF<ByteBuffer, P> filter) {
        filter(filter);
    }

    public DF_Service(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
        if (taskExecutor != null) {
            taskExecutor.addTaskExecutorListener(this);
        }
    }

    public DF_Service(DF<ByteBuffer, P> filter, TaskExecutor taskExecutor) {
        filter(filter);
        this.taskExecutor = taskExecutor;
        if (taskExecutor != null) {
            taskExecutor.addTaskExecutorListener(this);
        }
    }

    public DF_Service<P> configureFilter(DF<ByteBuffer, P> filter) {
        filter(filter);
        return this;
    }

    public DF_Service<P> configureExecutor(TaskExecutor taskExecutor) {
        if (this.taskExecutor != null) {
            this.taskExecutor.removeTaskExecutorListener(this);
        }
        this.taskExecutor = taskExecutor;
        if (taskExecutor != null) {
            taskExecutor.addTaskExecutorListener(this);
        }
        return this;
    }

    public DF_Service<P> configureService(int order, ServiceProcessor... services) {
        this.services.configure(order, services);
        return this;
    }

    public DF_Service<P> configureDataProcessor(int order, DataProcessor... dataProcessors) {
        this.dataProcessors.configure(order, dataProcessors);
        return this;
    }

    public DF_Service<P> configureListener(DF_ServiceListener... ls) {
        addServiceListener(ls);
        return this;
    }

    /**
     * Create service-centric DI, i.e. DI with DF_Service as filter.
     *
     * @return
     */
    public DI<ByteBuffer, P> buildDI() {
        DI<ByteBuffer, P> di = new BaseDI<ByteBuffer, P>() {
            @Override
            public long size(Collection<ByteBuffer>... data) {
                return BufferTools.getRemaining(data);
            }

            @Override
            public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
                if (BufferTools.hasRemaining(data)) {
                    throw new UnsupportedOperationException("Not supported: service MUST handle (consume) all data without leaving unhandled bytes.");
                }
            }

            @Override
            public List<ByteBuffer> produce(P provider) throws IOException {
                return null;
            }
        };
        di.filter(this);
        return di;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// TaskProvider impl
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        if (phases == null || phases.length == 0) {
            return Collections.emptyList();
        }
        List<Task> r = new ArrayList<>();

        for (ServiceProcessor sp : services.items()) {
            List<Task> spr = sp.getTasks(phases);
            if (spr != null) {
                r.addAll(spr);
            }
        }
        for (DataProcessor dp : dataProcessors.items()) {
            List<Task> dpr = dp.getTasks(phases);
            if (dpr != null) {
                r.addAll(dpr);
            }
        }

        Collections.sort(r, TaskProvider.getTaskComparator(true));
        return r;
    }

    @Override
    public void onAdded(Repository<TaskProvider> repository, TaskProvider item) {
        if (item != null && taskExecutor != null) {
            taskExecutor.execute(this, (List<Runnable>) (Object) item.getTasks(TaskPhase.initial));
        }
    }

    @Override
    public void onRemoved(Repository<TaskProvider> repository, TaskProvider item) {
        if (item != null && taskExecutor != null) {
            taskExecutor.execute(this, (List<Runnable>) (Object) item.getTasks(TaskPhase.terminal));
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////// TaskExecutorListener impl
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onSubmitted(Object identifier, Runnable run, long submittedAt) {
        ProviderStatistics ps = serviceStatisticsRT.get(identifier);
        if (ps != null) {
            ps.addTask(submittedAt);
        }
    }

    @Override
    public void onCompleted(Object identifier, Runnable run, long submittedAt, long startedAt, long durationNano, Throwable error) {
        ProviderStatistics ps = serviceStatisticsRT.get(identifier);
        if (ps != null) {
            ps.updateTask(startedAt, durationNano, error);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public Map<String, Object> getStatistics() {
        Map<String, Object> r = new LinkedHashMap<>();
        List<ProviderStatistics> r1 = new ArrayList<>(); // wip
        List<ProviderStatistics> r2 = new ArrayList<>(); // done
        Map<P, Long> r3 = new LinkedHashMap<>(); // opened
        Map<String, Long> r31 = new LinkedHashMap<>(); // opened
        r1.addAll(serviceStatisticsRT.values());
        r2.addAll(serviceStatistics);
        r3.putAll(providerOpened);
        long time = System.currentTimeMillis();
        r.put("TIMESTAMP", time);
        for (P p : r3.keySet()) {
            r31.put("" + p, time - r3.get(p));
        }
        r.put("WIP", r1);
        r.put("DONE", r2);
        r.put("OPENED", r31);
        return r;
    }

    public void clearStatistics(long from) {
        synchronized (serviceStatistics) {
            Iterator<ProviderStatistics> it = serviceStatistics.iterator();
            while (it.hasNext()) {
                ProviderStatistics ps = it.next();
                if (ps.counters != null && ps.counters[ps.counters.length - 1][0] < from) {
                    it.remove();
                }
            }
        }
    }

    public void addServiceListener(DF_ServiceListener... ls) {
        if (ls != null) {
            for (DF_ServiceListener l : ls) {
                if (l != null && !listeners.contains(l)) {
                    listeners.add(l);
                }
            }
        }
    }

    public void removeServiceListener(DF_ServiceListener... ls) {
        if (ls != null) {
            for (DF_ServiceListener l : ls) {
                if (l != null && listeners.contains(l)) {
                    listeners.remove(l);
                }
            }
        }
    }

    public boolean hasServiceListeners() {
        return !listeners.isEmpty();
    }

    public void notifyServiceEvent(P provider, ServiceHandler handler, DF_ServiceListener.SERVICE_EVENT event, Object... params) {
        if (!listeners.isEmpty()) {
            for (DF_ServiceListener l : listeners) {
                l.onServiceEvent(
                        this,
                        (handler != null) ? handler : serviceHandlers.get(provider),
                        event,
                        provider,
                        params
                );
            }
        }
    }

    public Repository<ServiceProcessor> getServices() {
        return services;
    }

    public Repository<DataProcessor> getDataProcessors() {
        return dataProcessors;
    }

    public P ensureProvider(P provider) {
        return provider;
    }

    public boolean isSecure(P provider) {
        Object v = serviceSecurity.get(provider);
        return (v != null)
                ? (v instanceof Boolean)
                        ? (Boolean) v
                        : (v instanceof Certificate[])
                                ? true
                                : false
                : false;
    }

    public ProviderStatistics createProviderStatistics(P provider) {
        ProviderStatistics ps = new ProviderStatistics("" + provider);
        if (providerOpened.containsKey(provider)) {
            Long po = providerOpened.remove(provider);
            if (po != null) {
                ps.setOpened(po);
            }
        }
        return ps;
    }

    /**
     * Process data passed thru chains.
     *
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    @Override
    public List<ByteBuffer> readFilter(DM<P> owner, P provider, Collection<ByteBuffer>... data) throws IOException {
        List<ByteBuffer> r = null;
        provider = ensureProvider(provider);

        ProviderStatistics ps = serviceStatisticsRT.get(provider);
//        if (ps == null) {
//            ps = createProviderStatistics(provider);
//            //ps.addCounter(null, null);
//            serviceStatisticsRT.put(provider, ps);
//        }

        // check if has pre-tasks
        if (taskExecutor != null) {
            List<Task> tasks = this.getTasks(TaskPhase.regular);
            if (!tasks.isEmpty()) {
                taskExecutor.execute(getClass().getSimpleName() + "_rf_pre_" + System.currentTimeMillis(), (List<Runnable>) (Object) tasks);
            }
        }

        // enable internal data flow
        ServiceHandler sh = serviceHandlers.get(provider);
        if (sh != null && sh.di!=null) {
            sh.verifyProcessing();
            sh.verifyFlow();

            r = sh.read();
            sh.verifyFlow();
        } else {
            r = BufferTools.toList(true, data);
        }

        if (hasServiceListeners()) {
            notifyServiceEvent(provider, sh, DF_ServiceListener.SERVICE_EVENT.read_ext, data, r);
        }

        // check if has post-tasks
        if (taskExecutor != null) {
            List<Task> tasks = this.getTasks(TaskPhase.regular);
            if (!tasks.isEmpty()) {
                taskExecutor.execute(getClass().getSimpleName() + "_rf_post_" + System.currentTimeMillis(), (List<Runnable>) (Object) tasks);
            }
        }

        long c = BufferTools.getRemaining(r);
        if (c > 0) {
            if (ps != null) {
                ps.updateCounter(null, null, 0, 0, c);
            } else {
                int a = 0;
            }
        }
        return r;
    }

    @Override
    public List<ByteBuffer> writeFilter(DM<P> owner, P provider, Collection<ByteBuffer>... data) throws IOException {
        ProviderStatistics ps = serviceStatisticsRT.get(provider);

        // check if has pre-tasks
        if (taskExecutor != null) {
            List<Task> tasks = this.getTasks(TaskPhase.regular);
            if (!tasks.isEmpty()) {
                //taskExecutor.execute(getClass().getSimpleName() + "_wf_pre_" + System.currentTimeMillis(), (List<Runnable>) (Object) tasks);
                taskExecutor.execute(provider, (List<Runnable>) (Object) tasks);
            }
        }

        List<ByteBuffer> r = BufferTools.toList(true, data);
        long c = BufferTools.getRemaining(r);
        long c0 = c;
        if (c > 0) {
            if (ps == null) {
                ps = createProviderStatistics(provider);
                //ps.addCounter(null, null);
                serviceStatisticsRT.put(provider, ps);
            }

            List<ByteBuffer> lst = new ArrayList<>();
            c -= toInternal(provider, lst, data);
        }

        if (hasServiceListeners()) {
            notifyServiceEvent(provider, null, DF_ServiceListener.SERVICE_EVENT.write_ext, data, r);
        }

        // check if has pre-tasks
        if (taskExecutor != null) {
            List<Task> tasks = this.getTasks(TaskPhase.regular);
            if (!tasks.isEmpty()) {
                //taskExecutor.execute(getClass().getSimpleName() + "_wf_post_" + System.currentTimeMillis(), (List<Runnable>) (Object) tasks);
                taskExecutor.execute(provider, (List<Runnable>) (Object) tasks);
            }
        }

        if (c0 > 0) {
            ps.updateCounter(null, null, 0, c0 - c, 0);
        }

        return (c > 0) ? r : null;
    }

    @Override
    public void delete(P provider) throws IOException {
        super.delete(provider);
        if (provider != null) {
            ServiceHandler sh = serviceHandlers.get(provider);
            if (sh != null) {
                if (sh.processor != null) {
                    try {
                        sh.processor.onDeassigned(provider, sh.di);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                if (sh.service != null) {
                    try {
                        sh.service.onDeassigned(provider, sh.di);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                if (sh.di != null) {
                    try {
                        sh.di.delete(provider);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
            ProviderStatistics ps = serviceStatisticsRT.remove(provider);
            if (providerOpened.containsKey(provider)) {
                Long po = null;
                synchronized (providerOpened) {
                    po = providerOpened.remove(provider);
                }
                if (po != null) {
                    if (ps != null) {
                        ps.setOpened(po);
                    } else {
                        onDeleteNoStatistics(provider, po);
                    }
                }
            }
            if (ps != null) {
                ps.close();
                serviceStatistics.add(ps);
            }
        }
    }

    public void onDeleteNoStatistics(P provider, Long opened) {
        int a = 0;
    }

    public void onDeleteUnassociated(P provider, ServiceHandler sh) throws IOException {
        //System.out.println("DF_Service -> DELETE/CLOSE " + ((sh != null) ? "processed request/response" : "unassociated") + " provider " + provider);
        provider.close();
    }

    @Override
    public void onProviderEvent(P provider, String event, Object... params) {
        super.onProviderEvent(provider, event, params);
        if (DM.PN_SECURE.equals(event)) {
            Object v = null;
            if (params != null) {
                for (Object p : params) {
                    if (p instanceof Boolean && v == null) {
                        v = p;
                    } else if (p instanceof Certificate[]) {
                        v = p;
                    }
                }
            }
            serviceSecurity.put(provider, v);
        } else if (DM.PN_OPENED.equals(event)) {
            long t = (params != null && params.length > 0 && params[0] instanceof Number) ? ((Number) params[0]).longValue() : System.currentTimeMillis();
            providerOpened.put(provider, t);
        } else if (DM.PN_INPUT_CLOSED.equals(event)) {
            ServiceHandler sh = serviceHandlers.get(provider);
            if (!serviceStatisticsRT.containsKey(provider) || sh != null && sh.processed) {
                if (provider.isOpen()) {
                    try {
                        onDeleteUnassociated(provider, sh);
//                        System.out.println("DF_Service -> DELETE/CLOSE " + ((sh != null) ? "processed request/response" : "unassociated") + " provider " + provider);
//                        provider.close();
                    } catch (Throwable th) {
                    }
                }
            }
            int a = 0;
        }

        if (hasServiceListeners()) {
            notifyServiceEvent(provider, null, DF_ServiceListener.SERVICE_EVENT.provider_event, event, params);
        }
    }

    public long toInternal(P provider, List<ByteBuffer> to, Collection<ByteBuffer>... from) throws IOException {
        long c = BufferTools.getRemaining(from);
        provider = ensureProvider(provider);
        ServiceHandler sh = serviceHandlers.get(provider);

        if (hasServiceListeners()) {
            notifyServiceEvent(provider, sh, DF_ServiceListener.SERVICE_EVENT.consume_ext, from);
        }

        if (sh == null) {
            sh = new ServiceHandler(provider);
            if (sh.ps == null) {
                sh.ps = serviceStatisticsRT.get(sh.provider);
            }

            sh.secure = isSecure(sh.provider);
            if (sh.secure) {
                Object v = serviceSecurity.get(provider);
                if (v instanceof Certificate[]) {
                    sh.remoteCertificates = (Certificate[]) v;
                }
            }
            serviceHandlers.put(provider, sh);

            if (hasServiceListeners()) {
                notifyServiceEvent(provider, sh, DF_ServiceListener.SERVICE_EVENT.init_handler);
            }
        }

        while (true) {
            if (sh.service == null || sh.di == null) {
                ServiceProcessor sp = bindService(sh, from);
            }

            if (sh.processor == null) {
                DataProcessor dp = bindDataProcessor(sh, (sh.service != null) ? sh.service.getDataProcessors(provider, sh.di) : null, getDataProcessors());
            }

            sh.write(from);

            SERVICE_PROCESSING_STATE sps = sh.verifyProcessing();
            switch (sps) {
                case preparing:
                    break;
                case needProcess:
                    break;
                case failed:
                    break;
                case OK:
                    break;
                default:
                    break;
            }

            if (BufferTools.getRemaining(from) > 0) {
                // if we need to consume data check if current request or response is completed or failed -> allow to handle next request or response.
                SERVICE_FLOW_STATE fst = sh.verifyFlow();
                if (SERVICE_FLOW_STATE.completed != fst && SERVICE_FLOW_STATE.failed != fst) {
                    break;
                }
            } else {
                break;
            }
        }
        return c - BufferTools.getRemaining(from);

    }

    public ServiceProcessor bindService(final ServiceHandler sh, final Collection<ByteBuffer>[] from) throws IOException {
        if (sh.ps != null) {
            sh.ps.updateRAMCounter();
            sh.ps.addCounter(null, null);
        }
        if (sh.service != null) {
            sh.reset();
            if (sh.service.hasOption(SPO_TRANSIENT)) {
                if (hasServiceListeners()) {
                    notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.done_service_processor, sh.service);
                }
                sh.service.onDeassigned(sh.provider, sh.di);
                sh.service = null;
            } else {
                // check if service still valid, otherwise clear it...
                SERVICE_MODE sst = sh.service.probe(sh, (from != null && from.length > 0) ? from[0] : null);
                switch (sst) {
                    case request:
                    case response:
                        if (hasServiceListeners()) {
                            notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.keep_service_processor, from);
                        }
                        sh.di = sh.service.initPD(sh, sst, from);
                        if (hasServiceListeners()) {
                            notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.init_di, sh.di);
                        }
                        sh.service.onAssigned(sh.provider, sh.di);
                        break;
                    case failed:
                    case unknown:
                    default:
                        if (hasServiceListeners()) {
                            notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.done_service_processor, sh.service);
                        }
                        sh.service = null;
                }
            }
        }
        if (sh.service == null) {
            //final boolean secure = isSecure(sh.provider);
            Matched<ServiceProcessor>[] ms = services.findMatched(new Matcher<ServiceProcessor>() {
                @Override
                public float match(ServiceProcessor t) {
                    SERVICE_MODE sst = t.probe(sh, (from != null && from.length > 0) ? from[0] : null);
                    switch (sst) {
                        case request:
                            return 1.0f;
                        case response:
                            return 0.5f;
                        case failed:
                        case unknown:
                        default:
                    }
                    return 0;
                }

                @Override
                public float weight() {
                    return 1;
                }
            }, null, true);
            if (ms != null && ms.length > 0) {
                sh.service = ms[0].getItem();
                if (hasServiceListeners()) {
                    notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.init_service_processor, from, ms);
                }
                sh.di = sh.service.initPD(sh, (ms[0].getLevel() == 1) ? SERVICE_MODE.request : SERVICE_MODE.response, from);
                if (hasServiceListeners()) {
                    notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.init_di, sh.di);
                }
                sh.service.onAssigned(sh.provider, sh.di);
            } else {
                int a = 0;
            }
        }
        return sh.service;
    }

    public DataProcessor bindDataProcessor(final ServiceHandler sh, Repository<DataProcessor>... dataProcessors) {
        if (sh == null || sh.di == null || dataProcessors == null || dataProcessors.length == 0) {
            return null;
        }
        for (Repository<DataProcessor> dp : dataProcessors) {
            if (dp == null) {
                continue;
            }
            Matched<DataProcessor>[] mps = dp.findMatched(new ListeningMatcher<DataProcessor>() {
                DataProcessor lastProcessor;
                HST lastHST;

                @Override
                public float match(DataProcessor t) {
                    HST sst = t.probe(sh.provider, sh.di);
                    switch (sst) {
                        case process:
                            lastProcessor = t;
                            lastHST = sst;
                            return 1;
                        case none:
                        default:
                            lastProcessor = null;
                            lastHST = null;
                    }
                    return 0;
                }

                @Override
                public float weight() {
                    return 1;
                }

                @Override
                public void onFound(Matched<DataProcessor> matched, Matcher<DataProcessor> matcher, boolean top) {
                    if (false) {
                        matched.setParameters(new Object[]{lastHST});
                    }
                }

                @Override
                public void onFound(Matched<DataProcessor>[] matched, Matcher<DataProcessor> matcher) {
                }

            },
                    null, // no explicit listener, just use combined matcher/listener above
                    true // sort results...
            );

            // detect pre/post processors, if any
            if (mps != null) {
//                System.out.println("MR: " + sh.di.toString().replace("\n", "\n    "));
//                for (int i = 0; i < mps.length; i++) {
//                    System.out.println("\n  [" + i + "] " + mps[i].getLevel() + " -> " + mps[i].getItem().toString().replace("\n", "\n     |"));
//                }
            }

            if (mps != null && mps.length > 0 && sh.processor == null) {
                sh.processor = mps[0].getItem();
                if (hasServiceListeners()) {
                    notifyServiceEvent(sh.provider, sh, DF_ServiceListener.SERVICE_EVENT.init_data_processor, sh.provider, mps);
                }
                sh.processor.onAssigned(sh.provider, sh.di);
                break;
            }

            if (sh.processor != null) {
                break;
            }
        }
        return sh.processor;
    }

    /**
     * Encapsulates service/processor binding to provider data
     */
    public class ServiceHandler implements ServiceProviderMeta {

        P provider;
        boolean secure;
        Certificate[] remoteCertificates;
        ServiceProcessor service;
        SERVICE_PROCESSING_STATE lastSPS;
        DataProcessor processor;
        DI<ByteBuffer, P> di;
        Throwable error;
        Runnable processing;
        boolean processed = false;
        Throwable reportedError;
        ProviderStatistics ps;

        public ServiceHandler() {
        }

        public ServiceHandler(P provider) {
            this.provider = provider;
        }

        public void reset() {
            if (hasServiceListeners()) {
                notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.before_reset);
            }
            lastSPS = null;
            if (processor != null) {
                processor.onDeassigned(provider, di);
            }
            processor = null;
            di = null;
            error = null;
            reportedError = null;
            processing = null;
            processed = false;
        }

        @Override
        public ProviderStatistics getStatistics() {
            return ps;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ServiceHandler{"
                    + "\n  provider=" + provider
                    + "\n  secure=" + secure
                    + ((remoteCertificates != null && remoteCertificates.length > 0) ? "\n  remoteCertificates[" + remoteCertificates.length + "]=" + ("" + remoteCertificates[0]).replace("\n", "\n  ") : "")
                    + "\n  service=" + service
                    + "\n  lastSPS=" + lastSPS
                    + "\n  processor=" + processor
                    + ((di != null) ? "\n  di=" + di.toString().replace("\n", "\n  ") : "")
                    + "\n  processing=" + processing
                    + "\n  processed=" + processed
                    + ((error != null) ? "\n  error=" + error.toString().replace("\n", "\n  ") : "")
                    + '}');
            return sb.toString();
        }

        @Override
        public P getProvider() {
            return provider;
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        @Override
        public Certificate[] getCertificates() {
            return remoteCertificates;
        }

        public ServiceProcessor getService() {
            return service;
        }

        public DataProcessor getDataProcessor() {
            return processor;
        }

        public <Z extends DI<ByteBuffer, P>> Z getData() {
            return (Z) di;
        }

        public SERVICE_FLOW_STATE verifyFlow() {
            try {
                SERVICE_FLOW_STATE fst = service.test(provider, di);
                switch (fst) {
                    case completed:
                    case failed:
                        if (processor != null) {
                            processor.onDeassigned(provider, di);
                        }
                        processor = null;
                        di = null;
                        break;
                    case in:
                    case out:
                    case in_out:
                    default:
                }
                if (hasServiceListeners()) {
                    notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, fst);
                }
                return fst;
            } catch (Throwable ioex) {
                error = ioex;
                if (hasServiceListeners()) {
                    notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_flow, ioex);
                }
                return SERVICE_FLOW_STATE.failed;
            }
        }

        public SERVICE_PROCESSING_STATE verifyProcessing() throws IOException {
            if (service != null || processor != null) {
                try {
                    lastSPS = (processor != null) ? processor.check(provider, di) : service.testProcessing(provider, di);
                    switch (lastSPS) {
                        case needProcess:
                            if (!processed && processing == null) {
                                startProcess();
                            }
                            if (!processed) {
                                if (hasServiceListeners()) {
                                    notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, SERVICE_PROCESSING_STATE.processing);
                                }
                                return SERVICE_PROCESSING_STATE.processing;
                            }
                        case preparing:
                        case failed:
                            if (processor == null) {
                                if (error == null) {
                                    error = new IOException("No data processor.");
                                }
                                if (error != reportedError) {
                                    service.onServiceError(provider, di, error);
                                }
                                reportedError = error;
                                if (hasServiceListeners()) {
                                    notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, SERVICE_PROCESSING_STATE.failed, error);
                                }
                                return lastSPS;
                            }
                        case OK:
                        default:
                            if (hasServiceListeners()) {
                                notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, lastSPS);
                            }
                            return lastSPS;
                    }
                } catch (Throwable ioex) {
                    error = ioex;
                    service.onServiceError(provider, di, error);
                    reportedError = error;
                    if (hasServiceListeners()) {
                        notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, SERVICE_PROCESSING_STATE.failed, ioex);
                    }
                    return SERVICE_PROCESSING_STATE.failed;
                }
            } else {
                if (error == null) {
                    error = new IOException("No data processor.");
                }
                if (service != null) {
                    try {
                        if (error != reportedError) {
                            service.onServiceError(provider, di, error);
                        }
                        reportedError = error;
                        if (hasServiceListeners()) {
                            notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.verify_processing, SERVICE_PROCESSING_STATE.failed, error);
                        }
                    } catch (Throwable th) {
                    }
                }
                return SERVICE_PROCESSING_STATE.failed;
            }
        }

        public long write(Collection<ByteBuffer>... from) throws IOException {
            if (di == null) {
                throw new IOException("No DI to write for " + provider);
            }
            long r = di.write(provider, from);
            if (hasServiceListeners()) {
                notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.write_int, from, r);
            }
            return r;
        }

        public List<ByteBuffer> read() throws IOException {
            if (di == null) {
                throw new IOException("No DI to read for " + provider);
            }
            List<ByteBuffer> r = (di != null) ? di.read(provider) : null;
            if (hasServiceListeners() && r != null && BufferTools.hasRemaining(r)) {
                notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.read_int, r);
            }
            return r;
        }

        public void startProcess() throws IOException {
            if (processor != null) {
                switch (processor.check(provider, di)) {
                    case preparing:
                        break;
                    case needProcess:
                        // do this only once
                        if (!processed && processing == null) {
                            final Runnable prr = processor.getRunnable(provider, di);
                            if (prr != null) {
                                if (hasServiceListeners()) {
                                    notifyServiceEvent(provider, this, DF_ServiceListener.SERVICE_EVENT.init_executable, prr);
                                }
                                Runnable r = new Runnable() {
                                    @Override
                                    public void run() {
                                        processing = prr;
                                        try {
                                            processing.run();
                                        } catch (Throwable th) {
                                            error = th;
                                        } finally {
                                            processed = true;
                                            processing = null;
                                        }
                                    }
                                };

                                if (taskExecutor != null) {
                                    taskExecutor.execute(this, r);
                                } else {
                                    Thread th = new Thread(r);
                                    th.setName("THREAD:" + service.getName() + ":exec:" + System.currentTimeMillis() + "/0:" + processor);
                                    th.setDaemon(true);
                                    th.start();
                                }
                            }
                        }
                        break;
                    case failed:
                    case OK:
                    default:
                }
            }
        }
    }

    /**
     * Service listener enables monitoring of data processing.
     *
     * @param <P>
     */
    public static interface DF_ServiceListener<P extends Channel> {

        public static enum SERVICE_EVENT {
            no_event, // placeholder, used to indicate filtering but nothing is expected...
            provider_event, // catch provider-specific events
            read_ext, // produce data to send
            write_ext, // accept received data
            consume_ext, // consume received data
            init_handler, // initialize provider-specific handler
            init_service_processor, // initialize service processor
            keep_service_processor, // keep initially selected service processor for further requests...
            done_service_processor, // service processor unbind from handler
            init_di, // initialize data interface
            init_data_processor, // initialize data processor
            init_executable,
            read_int,
            write_int,
            verify_flow,
            verify_processing,
            before_reset
        }

        void onServiceEvent(DF_Service<P> service, DF_Service<P>.ServiceHandler handler, SERVICE_EVENT type, P provider, Object... params);
    }

    public static class DebuggingDF_ServiceListener<P extends Channel> implements DF_ServiceListener<P> {

        PrintStream out = System.out;
        Collection<SERVICE_EVENT> events;
        Collection<P> providers;

        public DebuggingDF_ServiceListener() {
        }

        public DebuggingDF_ServiceListener(SERVICE_EVENT... events) {
            includeEvents(events);
        }

        public DebuggingDF_ServiceListener(PrintStream out, SERVICE_EVENT... events) {
            this.out = out;
            includeEvents(events);
        }

        public Collection<SERVICE_EVENT> debuggingEvents() {
            return (events != null) ? Collections.unmodifiableCollection(events) : Collections.emptyList();
        }

        public Collection<P> debuggingProviders() {
            return (providers != null) ? Collections.unmodifiableCollection(providers) : Collections.emptyList();
        }

        public DebuggingDF_ServiceListener includeEvents(SERVICE_EVENT... events) {
            if (this.events == null) {
                this.events = new HashSet<>();
            }
            if (events != null) {
                for (SERVICE_EVENT event : events) {
                    if (event != null) {
                        this.events.add(event);
                    }
                }
            }
            return this;
        }

        public DebuggingDF_ServiceListener excludeEvents(SERVICE_EVENT... events) {
            if (this.events != null && !this.events.isEmpty() && events != null) {
                for (SERVICE_EVENT event : events) {
                    if (event != null && this.events.contains(event)) {
                        this.events.remove(event);
                    }
                }
            }
            return this;
        }

        public DebuggingDF_ServiceListener allEvents() {
            if (events != null) {
                events.clear();
            }
            return this;
        }

        public DebuggingDF_ServiceListener includeProviders(P... providers) {
            if (this.providers == null) {
                this.providers = new HashSet<>();
            }
            if (providers != null) {
                for (P provider : providers) {
                    if (provider != null) {
                        this.providers.add(provider);
                    }
                }
            }
            return this;
        }

        public DebuggingDF_ServiceListener excludeProviders(P... providers) {
            if (this.providers != null && !this.providers.isEmpty() && providers != null) {
                for (P provider : providers) {
                    if (provider != null && this.providers.contains(provider)) {
                        this.providers.remove(provider);
                    }
                }
            }
            return this;
        }

        public DebuggingDF_ServiceListener allProviders() {
            if (providers != null) {
                providers.clear();
            }
            return this;
        }

        @Override
        public void onServiceEvent(
                DF_Service<P> service,
                DF_Service<P>.ServiceHandler handler,
                DF_ServiceListener.SERVICE_EVENT type,
                P provider,
                Object... params) {
            if (out == null) {
                return;
            }
            if (events != null && !events.isEmpty() && !events.contains(type)) {
                return;
            }
            if (providers != null && !providers.isEmpty() && !providers.contains(provider)) {
                return;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("[" + Thread.currentThread().getName() + "][" + System.currentTimeMillis() + "] " + type + "  " + provider);
            if (service != null) {
                sb.append("\n  SERVICE: " + service.getClass().getName());
            }
            if (handler != null) {
                if (DF_ServiceListener.SERVICE_EVENT.init_service_processor == type || DF_ServiceListener.SERVICE_EVENT.init_data_processor == type) {
                    sb.append("\n  HANDLER: " + handler.getClass().getName() + "\n    " + handler.toString().replace("\n", "\n    "));
                } else {
                    sb.append("\n  HANDLER: " + handler.getClass().getName());
                }
            }
            if (params != null && params.length > 0 && params.length > 0 && params[0] != null) {
                sb.append("\n  PARAMS : ");
                for (int i = 0; i < params.length; i++) {
                    sb.append("\n    [" + i + "] " + paramToString(params[i]).replace("\n", "\n      "));
                }
            }

            out.println(sb.toString());
        }

        public String paramToString(Object p) {
            if (p == null) {
                return "<null>";
            }
            try {
                if (p.getClass().isArray()) {
                    if (Buffer.class.isAssignableFrom(p.getClass().getComponentType())) {
                        if (CharBuffer.class.isAssignableFrom(p.getClass().getComponentType())) {
                            return BufferTools.toText((CharBuffer[]) p);
                        } else if (ByteBuffer.class.isAssignableFrom(p.getClass().getComponentType())) {
                            return BufferTools.toText("ISO-8859-1", (ByteBuffer[]) p);
                        }
                    } else if (Collection.class.isAssignableFrom(p.getClass().getComponentType())) {
                        Collection[] cs = (Collection[]) p;
                        if (cs != null && cs.length > 0 && !cs[0].isEmpty() && cs[0].iterator().next() instanceof ByteBuffer) {
                            return BufferTools.toText("ISO-8859-1", (Collection<ByteBuffer>[]) p);
                        }
                    } else {
                        // object
                        StringBuilder sb = new StringBuilder();
                        int len = Array.getLength(p);
                        sb.append("[" + len + "]");
                        for (int i = 0; i < len; i++) {
                            sb.append("\n  " + i + ":");
                            sb.append(paramToString(Array.get(p, i)).replace("\n", "\n    "));
                        }
                        return sb.toString();
                    }
                } else if (p instanceof Collection) {
                    Collection cs = (Collection) p;
                    if (!cs.isEmpty() && cs.iterator().next() instanceof ByteBuffer) {
                        return BufferTools.toText("ISO-8859-1", (Collection<ByteBuffer>) p);
                    }
                }
            } catch (Throwable th) {
            }
            return "" + p;
        }
    }
}
