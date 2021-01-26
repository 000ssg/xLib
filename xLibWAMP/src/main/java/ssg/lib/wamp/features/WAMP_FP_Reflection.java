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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.events.WAMPEventListener;
import ssg.lib.wamp.events.WAMPSubscriber;
import ssg.lib.wamp.events.impl.WAMPSubscription;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.rpc.impl.dealer.DealerLocalProcedure;
import ssg.lib.wamp.rpc.impl.dealer.DealerProcedure;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.RB;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMP_FP_Reflection implements WAMPFeatureProvider, WAMPNodeListener {

    public static final String WR_RPC_TOPIC_LIST = "wamp.reflection.topic.list";
    public static final String WR_RPC_PROCEDURE_LIST = "wamp.reflection.procedure.list";
    public static final String WR_RPC_ERROR_LIST = "wamp.reflection.error.list";
    public static final String WR_RPC_TOPIC_DESCR = "wamp.reflection.topic.describe";
    public static final String WR_RPC_PROCEDURE_DESCR = "wamp.reflection.procedure.describe";
    public static final String WR_RPC_ERROR_DESCR = "wamp.reflection.error.describe";
    // exts
    public static final String WR_RPC_TYPE_LIST = "wamp.reflection.type.list";
    public static final String WR_RPC_TYPE_DESCR = "wamp.reflection.type.describe";
    // events
    public static final String WR_RPC_DEFINE = "wamp.reflect.define";
    public static final String WR_RPC_DESCRIBE = "wamp.reflect.describe";

    public static final String WR_EVENT_ON_DEFINE = "wamp.reflect.on_define";
    public static final String WR_EVENT_ON_UNDEFINE = "wamp.reflect.on_undefine";

    public static enum RT {
        type,
        proc,
        error,
        pub
    }

    Map<String, Map<String, Object>> types = WAMPTools.createSynchronizedMap();
    Map<String, List<Map<String, Object>>> procs = WAMPTools.createSynchronizedMap();
    Map<String, Map<String, Object>> errors = WAMPTools.createSynchronizedMap();
    Map<String, Map<String, Object>> pubs = WAMPTools.createSynchronizedMap();

    @Override
    public WAMPFeature[] getFeatures(WAMP.Role role) {
        switch (role) {
            case broker:
            case publisher:
            case subscriber:
                return new WAMPFeature[]{WAMPFeature.topic_reflection};
            case dealer:
            case caller:
            case callee:
                return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
            default:
                return new WAMPFeature[0];
        }
    }

    @Override
    public DealerProcedure[] getFeatureProcedures(WAMP.Role role) {
        return new DealerProcedure[]{
            rpcTopicList,
            rpcProcedureList,
            rpcErrorList,
            rpcTypeList,
            rpcTopicDescr,
            rpcProcedureDescr,
            rpcErrorDescr,
            rpcTypeDescr
        };
    }

    @Override
    public void prepareFeature(WAMPSession session) {
        if (session.hasLocalRole(WAMP.Role.subscriber) && session.hasLocalRole(WAMP.Role.caller)) try {
            final WAMPSubscriber ws = session.getRealm().getActor(WAMP.Role.subscriber);
            ws.subscribe(session, new WAMPEventListener.WAMPEventListenerBase(
                    WR_RPC_DEFINE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        define(argumentsKw, true);
                    }
            ));

            ws.subscribe(session, new WAMPEventListener.WAMPEventListenerBase(
                    WR_RPC_DESCRIBE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        define(argumentsKw, false);
                    }
            ));
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
    }

    /**
     * Returns description for specified name/type
     *
     * @param type
     * @param name
     * @return
     */
    public Map<String, Object>[] describe(RT type, String name) {
        Map<String, Object> r = null;
        switch (type) {
            case type:
                r = types.get(name);
                break;
            case proc:
                List<Map<String, Object>> lst = procs.get(name);
                return (lst != null) ? lst.toArray(new Map[lst.size()]) : null;
            case error:
                r = errors.get(name);
                break;
            case pub:
                r = pubs.get(name);
        }
        return r != null ? new Map[]{r} : null;
    }

    /**
     * Set description for specified name/type or clear it if definition is
     * null.
     *
     * @param type
     * @param name
     * @param definition
     */
    public void define(RT type, String name, Map<String, Object> definition) {
        //System.out.println("DEFINE: " + type + " " + name + " " + definition);
        if (type != null) {
            if (definition != null) {
                switch (type) {
                    case type:synchronized (types) {
                            types.put(name, definition);
                            if ("type".equals(definition.get("type"))) {
                                definition.remove("type");
                            }
                        }
                        break;
                    case proc:synchronized (procs) {
                            List<Map<String, Object>> procsData = procs.get(name);
                            if (procsData == null) {
                                procsData = WAMPTools.createSynchronizedList();
                                procs.put(name, procsData);
                            }
                            procsData.add(definition);
                        }
                        break;
                    case error:synchronized (errors) {
                            errors.put(name, definition);
                        }
                        break;
                    case pub:synchronized (pubs) {
                            pubs.put(name, definition);
                        }
                        break;
                }
            } else {
                switch (type) {
                    case type:
                        types.remove(name);
                        break;
                    case proc:
                        procs.remove(name);
                        break;
                    case error:
                        errors.remove(name);
                        break;
                    case pub:
                        pubs.remove(name);
                        break;
                }
            }
        }
    }

    /**
     * Set description for specified type/name/description hierarchy (uses
     * internally define(RT,name,desctiption).
     *
     * @param meta
     * @param defineOnly
     */
    public void define(Map<String, Object> meta, boolean defineOnly) {
        if (meta != null) {
            for (String key : meta.keySet()) {
                try {
                    RT type = RT.valueOf(key);
                    Map<String, Map<String, Object>> data = (Map) meta.get(key);
                    if (data != null) {
                        for (String name : data.keySet()) {
                            Object v = data.get(name);
                            if (v instanceof List) {
                                for (Object vi : (List) v) {
                                    Map<String, Object> descr = vi instanceof Map ? (Map) vi : null;
                                    define(type, name, descr != null ? descr : (defineOnly) ? WAMPTools.EMPTY_DICT : null);
                                }
                            } else {
                                Map<String, Object> descr = v instanceof Map ? (Map) v : null;
                                define(type, name, descr != null ? descr : (defineOnly) ? WAMPTools.EMPTY_DICT : null);
                            }
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
    }

// RPC implementations
    DealerLocalProcedure rpcTopicList = new DealerLocalProcedure(WR_RPC_TOPIC_LIST) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> lst = new ArrayList<>();
                lst.addAll(pubs.keySet());
                Collections.sort(lst);
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, lst));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_TOPIC_LIST).returns("string[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcProcedureList = new DealerLocalProcedure(WR_RPC_PROCEDURE_LIST) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> lst = new ArrayList<>();
                lst.addAll(procs.keySet());
                Collections.sort(lst);
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, lst));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_PROCEDURE_LIST).returns("string[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcErrorList = new DealerLocalProcedure(WR_RPC_ERROR_LIST) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> lst = new ArrayList<>();
                lst.addAll(errors.keySet());
                Collections.sort(lst);
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, lst));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_ERROR_LIST).returns("string[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcTypeList = new DealerLocalProcedure(WR_RPC_TYPE_LIST) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> lst = new ArrayList<>();
                lst.addAll(types.keySet());
                Collections.sort(lst);
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, lst));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_TYPE_LIST).returns("string[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };

    DealerLocalProcedure rpcTopicDescr = new DealerLocalProcedure(WR_RPC_TOPIC_DESCR) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> names = msg.getList(3);
                Map<String, Object> map = WAMPTools.createDict(null);
                for (String s : names) {
                    Map<String, Object> mapi = (s != null) ? pubs.get(s) : null;
                    if (mapi != null) {
                        map.put(s, mapi);
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, map));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_TOPIC_DESCR)
                            .parameter(0, null, "string[]", false)
                            .returns("dict[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcProcedureDescr = new DealerLocalProcedure(WR_RPC_PROCEDURE_DESCR) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> names = msg.getList(3);
                Map<String, Object> map = WAMPTools.createDict(null);
                for (String s : names) {
                    List<Map<String, Object>> mapi = (s != null) ? procs.get(s) : null;
                    if (mapi != null) {
                        map.put(s, mapi);
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, map));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_PROCEDURE_DESCR)
                            .parameter(0, null, "string[]", false)
                            .returns("dict[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcErrorDescr = new DealerLocalProcedure(WR_RPC_ERROR_DESCR) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> names = msg.getList(3);
                Map<String, Object> map = WAMPTools.createDict(null);
                for (String s : names) {
                    Map<String, Object> mapi = (s != null) ? errors.get(s) : null;
                    if (mapi != null) {
                        map.put(s, mapi);
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, map));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_ERROR_DESCR)
                            .parameter(0, null, "string[]", false)
                            .returns("dict[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };
    DealerLocalProcedure rpcTypeDescr = new DealerLocalProcedure(WR_RPC_TYPE_DESCR) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<String> names = msg.getList(3);
                Map<String, Object> map = WAMPTools.createDict(null);
                for (String s : names) {
                    Map<String, Object> mapi = (s != null) ? types.get(s) : null;
                    if (mapi != null) {
                        map.put(s, mapi);
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, map));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(WR_RPC_TYPE_DESCR)
                            .parameter(0, null, "string[]", false)
                            .returns("dict[]"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.procedure_reflection, WAMPFeature.topic_reflection};
        }
    };

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////// WAMPNodeListener
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreatedRealm(WAMPRealm realm) {
    }

    @Override
    public void onEstablishedSession(WAMPSession session) {
    }

    @Override
    public void onClosedSession(WAMPSession session) {
    }

    @Override
    public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
        if (WAMPMessageType.T_PUBLISH == msg.getType().getId()) {
            WAMPSubscription ws = (WAMPSubscription) (session.hasLocalRole(WAMP.Role.broker)
                    ? session.getRealm().getActor(WAMP.Role.broker)
                    : null);
            if (ws != null && msg.getDataLength() > 4) {
                String topic = ws.getTopic(session, msg);
                if (WR_RPC_DEFINE.equals(topic)) {
                    define(msg.getDict(4), true);
                } else if (WR_RPC_DESCRIBE.equals(topic)) {
                    define(msg.getDict(4), false);
                }
            }
        } else if (WAMPMessageType.T_REGISTER == msg.getType().getId()) {
            // TODO: check if meta has reflection data
            Map<String, Object> opts = msg.getDict(1);
            if (opts.containsKey("reflection")) {
                String name = msg.getString(2);
                Object obj = opts.get("reflection");
                if (obj instanceof Collection) {
                    for (Object obji : (Collection) obj) {
                        if (obji instanceof Map) {
                            define(RT.proc, name, (Map) obji);
                        }
                    }
                } else if (obj instanceof Map) {
                    define(RT.proc, name, (Map) obj);
                }
            }
        }
    }

    @Override
    public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
    }

    @Override
    public void onFatal(WAMPSession session, WAMPMessage msg) {
    }

    @Override
    public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
    }

}
