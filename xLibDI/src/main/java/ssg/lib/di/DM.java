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
package ssg.lib.di;

import java.io.IOException;
import java.util.Collection;

/**
 * Data Maintenance interface: list and delete provider-related info.
 *
 * @author 000ssg
 * @param <P>
 */
public interface DM<P> {

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////// property names
    ////////////////////////////////////////////////////////////////////////////
    // provider (channel) is opened
    public static final String PN_OPENED = "p_opened";
    // provider input is closewd, writes are possibly available...
    public static final String PN_INPUT_CLOSED = "p_input_closed";
    // provider channel is secure -> certificates if possible
    public static final String PN_SECURE = "p_secure";

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// Maintenance
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Schedule health check procedure (if any). This should allow
     * provider-specific check to optimize/prevent clean-up procedures.
     *
     * @param provider
     * @throws IOException
     */
    void healthCheck(P provider) throws IOException;
    
    /**
     * Enable blocking of actual "write" and "read" operations.
     *
     * @param provider
     * @return
     */
    boolean isReady(P provider) throws IOException;
    
    /**
     * Release and delete any resources associated with given providers
     *
     * @param provider
     * @throws IOException
     */
    void delete(P provider) throws IOException;

    /**
     * List providers.
     *
     * @return
     */
    Collection<P> providers();

    /**
     * Provider events propagation channel.
     *
     * @param provider
     * @param event
     * @param parameters
     */
    void onProviderEvent(P provider, String event, Object... parameters);
}
