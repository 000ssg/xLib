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
package ssg.lib.wamp.messages;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.util.WAMPRuntimeException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPMessageType {

    public static enum WAMPValidationResult {
        ok,
        noData,
        sizeMismatch,
        typeMismatch,
        paramTypeMismatch
    }

    // session
    public static final int T_HELLO = 1;
    public static final int T_WELCOME = 2;
    public static final int T_ABORT = 3;
    public static final int T_GOODBYE = 6;
    public static final int T_ERROR = 8;
    // publishing
    public static final int T_PUBLISH = 16;
    public static final int T_PUBLISHED = 17;
    public static final int T_SUBSCRIBE = 32;
    public static final int T_SUBSCRIBED = 33;
    public static final int T_UNSUBSCRIBE = 34;
    public static final int T_UNSUBSCRIBED = 35;
    public static final int T_EVENT = 36;
    // Routed Remote Procedure Calls
    public static final int T_CALL = 48;
    public static final int T_RESULT = 50;
    public static final int T_REGISTER = 64;
    public static final int T_REGISTERED = 65;
    public static final int T_UNREGISTER = 66;
    public static final int T_UNREGISTERED = 67;
    public static final int T_INVOCATION = 68;
    public static final int T_YIELD = 70;

    
    // Advanced profile message codes
    public static final int T_CHALLENGE = 4;
    public static final int T_AUTHENTICATE = 5;
    public static final int T_CANCEL = 49;
    public static final int T_INTERRUPT = 69;
    
    
    
    private static int maxTypeId = 0;
    static Map<Integer, String> messageTypeNames = WAMPTools.createMap(true, (map) -> {
        scanTypeNames(map,WAMPMessageType.class);
    });

    // request message types indicate message needs response -> pending for response...
    static final Collection<Integer> requestMessageTypes = WAMPTools.createSet(false,
            // session methods are processed explicitly
            //add(T_HELLO);
            //add(T_GOODBYE);

            // evident request/response pairs: request side
            T_PUBLISH,
            T_SUBSCRIBE,
            T_UNSUBSCRIBE,
            T_CALL,
            T_REGISTER,
            T_UNREGISTER,
            T_INVOCATION
    );

    static final Map<Integer, WAMPMessageType[]> messageTypes = WAMPTools.createMap(true);

    static synchronized void scanTypeNames(Map<Integer, String> map, Class... classes) {
        for (Class clazz : classes) {
            Field[] fields = clazz.getFields();
            int[][] idx = new int[fields.length][];
            int off = 0;
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && Modifier.isPublic(f.getModifiers())
                        && f.getName().startsWith("T_")
                        && f.getType() == int.class) {
                    try {
                        idx[off++] = new int[]{i, f.getInt(null)};
                    } catch (Throwable th) {
                    }
                }
            }
            idx = Arrays.copyOf(idx, off);
            Arrays.sort(idx, new Comparator<int[]>() {
                @Override
                public int compare(int[] arg0, int[] arg1) {
                    Integer i0 = arg0[1];
                    return i0.compareTo(arg1[1]);
                }
            });
            //
            for (int[] ii : idx) {
                String n = fields[ii[0]].getName();
                n = n.substring(2);
                map.put(ii[1], n);
            }
        }
    }

    /**
     * map of message type -> sender[0]/receiver[1] roles
     */
    static final Map<Integer, WAMP.Role[][]> flow = WAMPTools.createMap(true, (map) -> {
        // session
        map.put(T_HELLO, new WAMP.Role[][]{
            {WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee},
            {WAMP.Role.broker, WAMP.Role.dealer}
        });
        map.put(T_WELCOME, new WAMP.Role[][]{
            {WAMP.Role.broker, WAMP.Role.dealer},
            {WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee}
        });
        map.put(T_ABORT, new WAMP.Role[][]{
            {WAMP.Role.broker, WAMP.Role.dealer},
            {WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee, WAMP.Role.broker, WAMP.Role.dealer}
        });
        map.put(T_GOODBYE, new WAMP.Role[][]{
            {WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee, WAMP.Role.broker, WAMP.Role.dealer},
            {WAMP.Role.broker, WAMP.Role.dealer}
        });
        map.put(T_ERROR, new WAMP.Role[][]{
            {WAMP.Role.broker, WAMP.Role.dealer, WAMP.Role.callee},
            {WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee, WAMP.Role.dealer}
        });

        // publishing - publish
        map.put(T_PUBLISH, new WAMP.Role[][]{
            {WAMP.Role.publisher},
            {WAMP.Role.broker}
        });
        map.put(T_PUBLISHED, new WAMP.Role[][]{
            {WAMP.Role.broker},
            {WAMP.Role.publisher}
        });
        // publishing - subscribe/receive
        map.put(T_SUBSCRIBE, new WAMP.Role[][]{
            {WAMP.Role.subscriber},
            {WAMP.Role.broker}
        });
        map.put(T_SUBSCRIBED, new WAMP.Role[][]{
            {WAMP.Role.broker},
            {WAMP.Role.subscriber}
        });
        map.put(T_UNSUBSCRIBE, new WAMP.Role[][]{
            {WAMP.Role.subscriber},
            {WAMP.Role.broker}
        });
        map.put(T_UNSUBSCRIBED, new WAMP.Role[][]{
            {WAMP.Role.broker},
            {WAMP.Role.subscriber}
        });
        map.put(T_EVENT, new WAMP.Role[][]{
            {WAMP.Role.broker},
            {WAMP.Role.subscriber}
        });
        // RPC - call
        map.put(T_CALL, new WAMP.Role[][]{
            {WAMP.Role.caller},
            {WAMP.Role.dealer}
        });
        map.put(T_RESULT, new WAMP.Role[][]{
            {WAMP.Role.dealer},
            {WAMP.Role.caller}
        });
        // RPC - registration
        map.put(T_REGISTER, new WAMP.Role[][]{
            {WAMP.Role.callee},
            {WAMP.Role.dealer}
        });
        map.put(T_REGISTERED, new WAMP.Role[][]{
            {WAMP.Role.dealer},
            {WAMP.Role.callee}
        });
        map.put(T_UNREGISTER, new WAMP.Role[][]{
            {WAMP.Role.callee},
            {WAMP.Role.dealer}
        });
        map.put(T_UNREGISTERED, new WAMP.Role[][]{
            {WAMP.Role.dealer},
            {WAMP.Role.callee}
        });
        // RPC - execution
        map.put(T_INVOCATION, new WAMP.Role[][]{
            {WAMP.Role.dealer},
            {WAMP.Role.callee}
        });
        map.put(T_YIELD, new WAMP.Role[][]{
            {WAMP.Role.callee},
            {WAMP.Role.dealer}
        });
    });
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// session lifecycle
    ////////////////////////////////////////////////////////////////////////////
    public static WAMPMessageType HELLO = new WAMPMessageType(
            T_HELLO,
            WAMP_DT.uri.toNamed("Realm"),
            WAMP_DT.dict.toNamed("Details")
    );
    public static WAMPMessageType WELCOME = new WAMPMessageType(
            T_WELCOME,
            WAMP_DT.id.toNamed("Session"),
            WAMP_DT.dict.toNamed("Details")
    );
    public static WAMPMessageType ABORT = new WAMPMessageType(
            T_ABORT,
            WAMP_DT.dict.toNamed("Details"),
            WAMP_DT.uri.toNamed("Reason")
    );
    public static WAMPMessageType GOODBYE = new WAMPMessageType(
            T_GOODBYE,
            WAMP_DT.dict.toNamed("Details"),
            WAMP_DT.uri.toNamed("Reason")
    );
    public static WAMPMessageType[] ERROR = new WAMPMessageType[]{
        new WAMPMessageType(
        T_ERROR,
        WAMP_DT.id.toNamed("REQUEST.Type"),
        WAMP_DT.id.toNamed("REQUEST.Request"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.uri.toNamed("Error")
        ),
        new WAMPMessageType(
        T_ERROR,
        WAMP_DT.id.toNamed("REQUEST.Type"),
        WAMP_DT.id.toNamed("REQUEST.Request"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.uri.toNamed("Error"),
        WAMP_DT.list.toNamed("Arguments")
        ),
        new WAMPMessageType(
        T_ERROR,
        WAMP_DT.id.toNamed("REQUEST.Type"),
        WAMP_DT.id.toNamed("REQUEST.Request"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.uri.toNamed("Error"),
        WAMP_DT.list.toNamed("Arguments"),
        WAMP_DT.dict.toNamed("ArgumentsKw")
        )
    };

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////// Publish & Subscribe
    ////////////////////////////////////////////////////////////////////////////
    public static WAMPMessageType[] PUBLISH = new WAMPMessageType[]{
        new WAMPMessageType(
        T_PUBLISH,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Topic")
        ),
        new WAMPMessageType(
        T_PUBLISH,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Topic"),
        WAMP_DT.list.toNamed("Arguments")
        ),
        new WAMPMessageType(
        T_PUBLISH,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Topic"),
        WAMP_DT.list.toNamed("Arguments"),
        WAMP_DT.dict.toNamed("ArgumentsKw")
        )
    };
    public static WAMPMessageType PUBLISHED = new WAMPMessageType(
            T_PUBLISHED,
            WAMP_DT.id.toNamed("PUBLISH.Request"),
            WAMP_DT.id.toNamed("Publication")
    );
    public static WAMPMessageType SUBSCRIBE = new WAMPMessageType(
            T_SUBSCRIBE,
            WAMP_DT.id.toNamed("Request"),
            WAMP_DT.dict.toNamed("Options"),
            WAMP_DT.uri.toNamed("Topic")
    );
    public static WAMPMessageType SUBSCRIBED = new WAMPMessageType(
            T_SUBSCRIBED,
            WAMP_DT.id.toNamed("SUBSCRIBE.Request"),
            WAMP_DT.id.toNamed("Subscription")
    );
    public static WAMPMessageType UNSUBSCRIBE = new WAMPMessageType(
            T_UNSUBSCRIBE,
            WAMP_DT.id.toNamed("Request"),
            WAMP_DT.id.toNamed("SUBSCRIBED.Subscription")
    );
    public static WAMPMessageType UNSUBSCRIBED = new WAMPMessageType(
            T_UNSUBSCRIBED,
            WAMP_DT.id.toNamed("UNSUBSCRIBE.Request")
    );
    public static WAMPMessageType[] EVENT = new WAMPMessageType[]{
        new WAMPMessageType(
        T_EVENT,
        WAMP_DT.id.toNamed("SUBSCRIBED.Subscription"),
        WAMP_DT.id.toNamed("PUBLISHED.Publication"),
        WAMP_DT.dict.toNamed("Details")
        ),
        new WAMPMessageType(
        T_EVENT,
        WAMP_DT.id.toNamed("SUBSCRIBED.Subscription"),
        WAMP_DT.id.toNamed("PUBLISHED.Publication"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("Arguments")
        ),
        new WAMPMessageType(
        T_EVENT,
        WAMP_DT.id.toNamed("SUBSCRIBED.Subscription"),
        WAMP_DT.id.toNamed("PUBLISHED.Publication"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("Arguments"),
        WAMP_DT.dict.toNamed("ArgumentsKw")
        )
    };

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////// Routed Remote Procedure Calls
    ////////////////////////////////////////////////////////////////////////////
    public static WAMPMessageType[] CALL = new WAMPMessageType[]{
        new WAMPMessageType(
        T_CALL,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Procedure")
        ),
        new WAMPMessageType(
        T_CALL,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Procedure"),
        WAMP_DT.list.toNamed("Arguments")
        ),
        new WAMPMessageType(
        T_CALL,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.uri.toNamed("Procedure"),
        WAMP_DT.list.toNamed("Arguments"),
        WAMP_DT.dict.toNamed("ArgumentsKw")
        )
    };
    public static WAMPMessageType[] RESULT = new WAMPMessageType[]{
        new WAMPMessageType(
        T_RESULT,
        WAMP_DT.id.toNamed("CALL.Request"),
        WAMP_DT.dict.toNamed("Details")
        ),
        new WAMPMessageType(
        T_RESULT,
        WAMP_DT.id.toNamed("CALL.Request"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("YIELD.Arguments")
        ),
        new WAMPMessageType(
        T_RESULT,
        WAMP_DT.id.toNamed("CALL.Request"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("YIELD.Arguments"),
        WAMP_DT.dict.toNamed("YIELD.ArgumentsKw")
        )
    };
    public static WAMPMessageType REGISTER = new WAMPMessageType(
            T_REGISTER,
            WAMP_DT.id.toNamed("Request"),
            WAMP_DT.dict.toNamed("Options"),
            WAMP_DT.uri.toNamed("Procedure")
    );
    public static WAMPMessageType REGISTERED = new WAMPMessageType(
            T_REGISTERED,
            WAMP_DT.id.toNamed("REGISTER.Request"),
            WAMP_DT.id.toNamed("Registration")
    );
    public static WAMPMessageType UNREGISTER = new WAMPMessageType(
            T_UNREGISTER,
            WAMP_DT.id.toNamed("Request"),
            WAMP_DT.id.toNamed("REGISTERED.Registration")
    );
    public static WAMPMessageType UNREGISTERED = new WAMPMessageType(
            T_UNREGISTERED,
            WAMP_DT.id.toNamed("UNREGISTER.Request")
    );
    public static WAMPMessageType[] INVOCATION = new WAMPMessageType[]{
        new WAMPMessageType(
        T_INVOCATION,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.id.toNamed("REGISTERED.Registration"),
        WAMP_DT.dict.toNamed("Details")
        ),
        new WAMPMessageType(
        T_INVOCATION,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.id.toNamed("REGISTERED.Registration"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("CALL.Arguments")
        ),
        new WAMPMessageType(
        T_INVOCATION,
        WAMP_DT.id.toNamed("Request"),
        WAMP_DT.id.toNamed("REGISTERED.Registration"),
        WAMP_DT.dict.toNamed("Details"),
        WAMP_DT.list.toNamed("CALL.Arguments"),
        WAMP_DT.dict.toNamed("CALL.ArgumentsKw")
        )
    };
    public static WAMPMessageType[] YIELD = new WAMPMessageType[]{
        new WAMPMessageType(
        T_YIELD,
        WAMP_DT.id.toNamed("INVOCATION.Registration"),
        WAMP_DT.dict.toNamed("Options")
        ),
        new WAMPMessageType(
        T_YIELD,
        WAMP_DT.id.toNamed("INVOCATION.Registration"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.list.toNamed("Arguments")
        ),
        new WAMPMessageType(
        T_YIELD,
        WAMP_DT.id.toNamed("INVOCATION.Registration"),
        WAMP_DT.dict.toNamed("Options"),
        WAMP_DT.list.toNamed("Arguments"),
        WAMP_DT.dict.toNamed("ArgumentsKw")
        )
    };

    
    // Advanced: Authentication
    public static WAMPMessageType CHALLENGE = new WAMPMessageType(
            T_CHALLENGE,
            new String[]{
                WAMP_DT.uri.toNamed("AuthMethod"),
                WAMP_DT.dict.toNamed("Extra")
            },
            new Role[]{Role.router},
            new Role[]{Role.client}
    );

    public static WAMPMessageType AUTHENTICATE = new WAMPMessageType(
            T_AUTHENTICATE,
            new String[]{
                WAMP_DT.uri.toNamed("Signature"),
                WAMP_DT.dict.toNamed("Extra")
            },
            new Role[]{Role.client},
            new Role[]{Role.router}
    );

    // Advanced: RPC
    public static WAMPMessageType CANCEL = new WAMPMessageType(
            T_CANCEL,
            new String[]{
                WAMP_DT.id.toNamed("CALL.Request"),
                WAMP_DT.dict.toNamed("Options")
            },
            new Role[]{Role.caller},
            new Role[]{Role.dealer}
    );
    public static WAMPMessageType INTERRUPT = new WAMPMessageType(
            T_INTERRUPT,
            new String[]{
                WAMP_DT.id.toNamed("INVOCATION.Request"),
                WAMP_DT.dict.toNamed("Options")
            },
            new Role[]{Role.dealer},
            new Role[]{Role.callee}
    );
    
    
    
    
    
    
    
    
    
    
    static void registerType(WAMPMessageType mt) {
        if (mt == null) {
            return;
        }
        maxTypeId = Math.max(maxTypeId, mt.getId());
        WAMPMessageType[] mts = messageTypes.get(mt.id);
        if (mts == null) {
            mts = new WAMPMessageType[]{mt};
            messageTypes.put(mt.id, mts);
        } else {
            boolean found = false;
            String mtS = mt.toString();
            for (WAMPMessageType t : mts) {
                if (mtS.equalsIgnoreCase(t.toString())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mts = Arrays.copyOf(mts, mts.length + 1);
                mts[mts.length - 1] = mt;
                messageTypes.put(mt.id, mts);
            } else {
                throw new WAMPRuntimeException("Duplicate (ambiguous) WAMP message type: " + mt);
            }
        }
    }

    /**
     * Returns name of type with given id or null if unknown.
     *
     * @param id
     * @return
     */
    public static String getTypeName(int id) {
        return messageTypeNames.get(id);
    }

    /**
     * Returns type definitions for given type id (T_*)
     *
     * @param typeId
     * @return
     */
    public static WAMPMessageType[] getType(int typeId) {
        return messageTypes.get(typeId);
    }

    /**
     * Returns type definition for given type id and arguments count. If
     * arguments count is 0 or less -> 1st type definition is returned.
     *
     * @param typeId
     * @param argsCount
     * @return
     */
    public static WAMPMessageType getType(int typeId, int argsCount) {
        WAMPMessageType[] mts = getType(typeId);
        if (mts != null) {
            if (argsCount <= 0) {
                return mts[0];
            }
            for (WAMPMessageType mt : mts) {
                if (mt.signature.length == argsCount) {
                    return mt;
                }
            }
        }
        return null;
    }

    /**
     * Returns type definition for given type id and argument value types.
     *
     * @param typeId
     * @param argsCount
     * @return
     */
    public static WAMPMessageType getType(int typeId, Object... args) {
        WAMPMessageType[] mts = getType(typeId);
        if (mts != null) {
            if (args == null || args.length == 0 || args.length == 1 && args[0] == null) {
                return mts[0];
            }
            for (WAMPMessageType mt : mts) {
                if (mt.signature.length == args.length) {
                    boolean matches = true;
                    for (int i = 0; i < mt.signature.length; i++) {
                        if (!WAMP_DT.fromNamed(mt.signature[i]).validate(args[i])) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return mt;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns max registered type id.
     *
     * @return
     */
    public static int getMaxTypeId() {
        return maxTypeId;
    }

    public static boolean isRequestMessageType(WAMPMessageType type) {
        return type != null && isRequestMessageType(type.id);
    }

    public static boolean isRequestMessageType(int typeId) {
        return requestMessageTypes.contains(typeId);
    }

    int id;
    String[] signature;

    public WAMPMessageType(int id, String... signature) {
        this.id = id;
        this.signature = signature;

        // self-registration
        registerType(this);
    }

    public WAMPMessageType(int id, String[] signature, Role[] fromFlow, Role[] toFlow) {
        this.id = id;
        this.signature = signature;

        // self-registration
        registerType(this);

        // publishing - publish
        flow.put(id, new Role[][]{
            fromFlow,
            toFlow
        });
    }
    
    public int getId() {
        return id;
    }

    public String getName() {
        return messageTypeNames.get(id);
    }

    public String[] signature() {
        return signature;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[" + ((messageTypeNames.containsKey(id)) ? messageTypeNames.get(id) : id));
        for (String s : signature) {
            sb.append(", ");
            sb.append(s);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Check if data match WAMPMEssageType...
     *
     * NOTE: Data represents list starting with message type followed by
     * parameters.
     *
     * @param data
     */
    public WAMPValidationResult validate(List data) {
        if (data == null || data.size() == 0) {
            return WAMPValidationResult.noData;
        }
        if (data.size() - 1 != signature.length) {
            return WAMPValidationResult.sizeMismatch;
        }
        // validate type
        Object type = data.get(0);
        if (!((Integer) this.id).equals(type)) {
            return WAMPValidationResult.typeMismatch;
        }
        // validate parameters
        for (int i = 1; i < data.size(); i++) {
            WAMP_DT dt = WAMP_DT.fromNamed(signature[i - 1]);
            if (dt == null || !dt.validate(data.get(i))) {
                return WAMPValidationResult.paramTypeMismatch;
            }
        }
        return WAMPValidationResult.ok;
    }

    /**
     * Check if data match WAMPMEssageType...
     *
     * NOTE: Data represents parameters only.
     *
     * @param data
     * @return
     */
    public WAMPValidationResult validate(Object... data) {
        if (data == null || data.length == 0) {
            return WAMPValidationResult.noData;
        }
        if (data.length != signature.length) {
            return WAMPValidationResult.sizeMismatch;
        }
        // validate parameters
        for (int i = 0; i < data.length; i++) {
            WAMP_DT dt = WAMP_DT.fromNamed(signature[i]);
            if (dt == null || !dt.validate(data[i])) {
                return WAMPValidationResult.paramTypeMismatch;
            }
        }
        return WAMPValidationResult.ok;
    }

    public static void main(String... args) throws Exception {
        System.out.println(WAMPMessageType.messageTypeNames.toString());
        System.out.println("MessageTypes: " + messageTypes.size());
        for (Entry<Integer, WAMPMessageType[]> e : messageTypes.entrySet()) {
            System.out.println("  " + messageTypeNames.get(e.getKey()) + "|" + e.getKey() + "\t[" + e.getValue().length + "]");
            for (WAMPMessageType t : e.getValue()) {
                System.out.println("    " + t);
            }
        }
    }

}
