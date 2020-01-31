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
import java.security.cert.Certificate;
import java.util.Collection;
import ssg.lib.common.TaskProvider;
import ssg.lib.di.DI;

/**
 * Represents a service: identifiable request/response oriented data processing
 * with optional data processors repository.
 */
public interface ServiceProcessor<P> extends AssignableProcessor<P>, TaskProvider {

    public static final long SPO_NO_OPTIONS = 0x0000; // just explicitly declared "no options".
    public static final long SPO_TRANSIENT = 0x0001; // transient service: the one that is cleared once service-related request is completed

    String getName();

    /**
     * Allows service behavior control. Returns true if all requested options
     * are supported.
     *
     * @param options
     * @return
     */
    boolean hasOptions(long options);

    /**
     * Allows service behavior control. Returns true if any of requested options
     * are supported.
     *
     * @param options
     * @return
     */
    boolean hasOption(long options);

    /**
     * Check if these data can initialize service request... or may represent
     * service response.
     *
     * @param data
     * @return request|unknown only
     */
    SERVICE_MODE probe(ServiceProviderMeta<P> meta, Collection<ByteBuffer> data);

    DI<ByteBuffer, P> initPD(ServiceProviderMeta<P> meta, SERVICE_MODE initialState, Collection<ByteBuffer>... data) throws IOException;

    SERVICE_FLOW_STATE test(P provider, DI<ByteBuffer, P> pd) throws IOException;

    void onServiceError(P provider, DI<ByteBuffer, P> pd, Throwable error) throws IOException;

    Repository<DataProcessor> getDataProcessors(P provider, DI<ByteBuffer, P> pd);

    public static interface ServiceProviderMeta<P> {

        P getProvider();
        
        ProviderStatistics getStatistics();

        boolean isSecure();

        Certificate[] getCertificates();
    }

}
