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
package ssg.lib.wamp;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.events.WAMPPublisher;
import ssg.lib.wamp.events.WAMPSubscriber;
import ssg.lib.wamp.events.impl.WAMPSubscriptionBroker;
import ssg.lib.wamp.events.impl.WAMPSubscriptionPublisher;
import ssg.lib.wamp.events.impl.WAMPSubscriptionSubscriber;
import ssg.lib.wamp.rpc.WAMPCallee;
import ssg.lib.wamp.rpc.WAMPCaller;
import ssg.lib.wamp.rpc.WAMPDealer;
import ssg.lib.wamp.rpc.impl.callee.WAMPRPCCallee;
import ssg.lib.wamp.rpc.impl.caller.WAMPRPCCaller;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.WAMPException;

/**
 *
 * @author sesidoro
 */
public class WAMPActorFactory {

    static WAMPActorFactory defaultFactory;
    static final Map<Role, Class> actorInterfaces = new EnumMap<Role, Class>(Role.class) {
        {
            put(Role.broker, WAMPBroker.class);
            put(Role.publisher, WAMPPublisher.class);
            put(Role.subscriber, WAMPSubscriber.class);
            put(Role.dealer, WAMPDealer.class);
            put(Role.callee, WAMPCallee.class);
            put(Role.caller, WAMPCaller.class);
        }
    };

    Map<Role, Class> actors = new EnumMap<Role, Class>(Role.class);

    private WAMPActorFactory() {
//        actors.put(Role.broker, WAMPSubscriptionBroker.class);
//        actors.put(Role.publisher, WAMPSubscriptionPublisher.class);
//        actors.put(Role.subscriber, WAMPSubscriptionSubscriber.class);
//        actors.put(Role.dealer, WAMPRPCDealer.class);
//        actors.put(Role.callee, WAMPRPCCallee.class);
//        actors.put(Role.caller, WAMPRPCCaller.class);
        init(
                WAMPSubscriptionBroker.class,
                WAMPSubscriptionPublisher.class,
                WAMPSubscriptionSubscriber.class,
                WAMPRPCDealer.class,
                WAMPRPCCallee.class,
                WAMPRPCCaller.class
        );
    }

    /**
     * Limited/non-standard factory. May be used to create limited factory (e.g.
     * client or router only).
     *
     * @param implementations
     */
    public WAMPActorFactory(Class... implementations) {
        init(implementations);
    }

    /**
     * Associates each given class with Actor interface and sets if matching.
     *
     * Null and interface classes are ignored. Same class may implement multiple
     * interfaces (though ?)
     *
     * @param implementations
     */
    private void init(Class... implementations) {
        if (implementations != null) {
            for (Class clazz : implementations) {
                if (clazz == null || clazz.isInterface()) {
                    continue;
                }
                for (Entry<Role, Class> e : actorInterfaces.entrySet()) {
                    if (e.getValue().isAssignableFrom(clazz)) {
                        actors.put(e.getKey(), clazz);
                    }
                }
            }
        }
    }

    /**
     * Creates instance of WAMPActor implementation for given role.
     *
     * @param <T>
     * @param role
     * @param features
     * @return
     * @throws WAMPException
     */
    public <T extends WAMPActor> T newActor(Role role, WAMPFeature[] features) throws WAMPException {
        T r = null;
        Class clazz = actors.get(role);
        if (clazz != null) {
            try {
                Constructor cons = clazz.getConstructor(WAMPFeature[].class);
                if (cons == null && (features == null || features.length == 0 || features.length == 1 && features[0] == null)) {
                    cons = clazz.getConstructor();
                    r = (T) cons.newInstance();
                } else {
                    if (cons.isVarArgs()) {
                        r = (T) cons.newInstance(new Object[]{features});
                    } else {
                        r = (T) cons.newInstance(features);
                    }
                }
            } catch (NoSuchMethodException nsmex) {
                throw new WAMPException("No compatible WAMPActor constructor is found for '" + role + "': implementation '" + clazz.getName() + "'.", nsmex);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException iex) {
                throw new WAMPException("Failed to create WAMPActor instance for '" + role + "': implementation '" + clazz.getName() + "'.", iex);
            }
        } else {
            //throw new WAMPException("No WAMPActor implementation is configured or possible for '" + role + "'.");
        }

        return r;
    }

    /**
     * Creates WAMPActor instance using default factory.
     *
     * @param <T>
     * @param role
     * @param features
     * @return
     * @throws WAMPException
     */
    public static <T extends WAMPActor> T createActor(Role role, WAMPFeature... features) throws WAMPException {
        if (defaultFactory == null) {
            defaultFactory = new WAMPActorFactory();
        }
        return defaultFactory.newActor(role, features);
    }

    /**
     * Returns default factory
     *
     * @return
     */
    public static WAMPActorFactory getInstance() {
        return defaultFactory;
    }

    /**
     * Set default factory and returns effective default factory.
     *
     * If provided factory is invalid (null) it is ignored otherwise default
     * factory is changed to provided one.
     *
     * Resultant factory is guaranteed.
     *
     * @param factory
     * @return
     */
    public static WAMPActorFactory setInstance(WAMPActorFactory factory) {
        if (factory != null) {
            defaultFactory = factory;
        }
        return defaultFactory;
    }
}
