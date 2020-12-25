/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.wamp.features;

import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.rpc.impl.dealer.DealerProcedure;

/**
 *
 * @author sesidoro
 */
public class WAMP_FP_Reflection implements WAMPFeatureProvider {

    public static final String WR_RPC_TOPIC_LIST = "wamp.reflection.topic.list";
    public static final String WR_RPC_PROCEDURE_LIST = "wamp.reflection.procedure.list";
    public static final String WR_RPC_ERROR_LIST = "wamp.reflection.error.list";
    public static final String WR_RPC_TOPIC_DESCR = "wamp.reflection.topic.describe";
    public static final String WR_RPC_PROCEDURE_DESCR = "wamp.reflection.procedure.describe";
    public static final String WR_RPC_ERROR_DESCR = "wamp.reflection.error.describe";

    public static final String WR_RPC_DEFINE = "wamp.reflect.define";
    public static final String WR_RPC_DESCRIBE = "wamp.reflect.describe";

    public static final String WR_EVENT_ON_DEFINE = "wamp.reflect.on_define";
    public static final String WR_EVENT_ON_UNDEFINE = "wamp.reflect.on_undefine";

    @Override
    public WAMPFeature[] getFeatures(WAMP.Role role) {
        return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
    }

    @Override
    public DealerProcedure[] getFeatureProcedures(WAMP.Role role) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
