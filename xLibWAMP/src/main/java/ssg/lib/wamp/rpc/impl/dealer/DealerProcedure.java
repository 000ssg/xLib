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
package ssg.lib.wamp.rpc.impl.dealer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.rpc.impl.Procedure;

/**
 *
 * @author 000ssg
 */
public class DealerProcedure extends Procedure implements Cloneable {

    Object owner;
    WAMPSession session;
    AtomicInteger rerouted = new AtomicInteger();

    public DealerProcedure(Object owner, String name, Map<String, Object> options, WAMPSession session) {
        super(name, options);
        this.owner = owner;
        this.session = session;
    }

    public Map<String, Object> getReflectionMeta() {
        return null;
    }

    @Override
    public String toStringExt() {
        return super.toStringExt()
                + (owner != null ? ", owner=" + owner : "")
                + (session != null ? ", session=" + session.getId() : "")
                + (rerouted.get() > 0 ? ", rerouted=" + rerouted.get() : "");
    }

    @Override
    public DealerProcedure clone() {
        try {
            return (DealerProcedure) super.clone();
        } catch (CloneNotSupportedException cnsex) {
            return null;
        }
    }

}
