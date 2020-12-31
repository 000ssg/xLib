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
package ssg.lib.wamp.rpc.impl.callee;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.rpc.WAMPCallee;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.rpc.impl.Procedure;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class CalleeProcedure extends Procedure {

    Callee callee;
    List<Long> wip = WAMPTools.createSynchronizedList();

    public CalleeProcedure(String name, Map<String, Object> options, Callee callee) {
        super(name, options);
        this.callee = callee;
    }

    public static interface Callee<T> {

        default void partial(CalleeCall call, List args, Map<String, Object> argsKw) throws WAMPException {
            WAMPCallee wc = call.session.getRealm().getActor(WAMP.Role.callee);
            wc.yield_(call.session, call.getId(), false, args, argsKw);
        }

        Future<T> invoke(CalleeCall call, ExecutorService executor, String name, List args, Map<String, Object> argsKw) throws WAMPException;
    }
}
