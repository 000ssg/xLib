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
import ssg.lib.common.TaskProvider;
import ssg.lib.di.DI;

/**
 * Data processor is assigned to data by service processor.
 *
 * @param <P>
 */
public interface DataProcessor<P> extends AssignableProcessor<P>, TaskProvider {

    /**
     * Handling status
     */
    public static enum HST {
        process, none
    }

    /**
     * Evaluate if and how given data processor can handle
     *
     * @param data
     * @return
     */
    HST probe(P provider, DI<ByteBuffer, P> data);

    /**
     * Check processing status, can order processing so to execute runnable.
     *
     * @param data
     * @return
     */
    SERVICE_PROCESSING_STATE check(P provider, DI<ByteBuffer, P> data) throws IOException;

    /**
     * Returns provider/data specific runnable
     *
     * @param provider
     * @param data
     * @return
     */
    Runnable getRunnable(P provider, DI<ByteBuffer, P> data);
}
