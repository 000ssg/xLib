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

import ssg.lib.wamp.util.WAMPException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import static ssg.lib.wamp.WAMP.Role.broker;
import static ssg.lib.wamp.WAMP.Role.callee;
import static ssg.lib.wamp.WAMP.Role.caller;
import static ssg.lib.wamp.WAMP.Role.dealer;
import static ssg.lib.wamp.WAMP.Role.publisher;
import static ssg.lib.wamp.WAMP.Role.subscriber;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_call_canceling;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_call_timeout;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_call_trustlevels;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_caller_identification;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_event_history;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_pattern_based_registration;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_pattern_based_subscription;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_procedure_reflection;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_progressive_call_results;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_progressive_calls;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_publication_trustlevels;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_publisher_exclusion;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_publisher_identification;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_registration_meta_api;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_registration_revocation;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_sharded_registration;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_sharded_subscription;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_shared_registration;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_subscriber_blackwhite_listing;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_subscription_meta_api;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_topic_reflection;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_batched_ws_transport;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_challenge_response_authentication;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_cookie_authentication;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_longpoll_transport;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_rawsocket_transport;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_session_meta_api;
import static ssg.lib.wamp.WAMPConstantsAdvanced.FEATURE_x_ticket_authentication;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
 */
public class WAMPFeature implements Serializable {

    static final Map<String, WAMPFeature> features = WAMPTools.createMap();

    // RPC Features
    public static final WAMPFeature progressive_call_results = new WAMPFeature(
            FEATURE_progressive_call_results,
            caller,
            dealer,
            callee);
    public static final WAMPFeature progressive_calls = new WAMPFeature(
            FEATURE_progressive_calls,
            caller,
            dealer,
            callee);
    public static final WAMPFeature call_timeout = new WAMPFeature(
            FEATURE_call_timeout,
            caller,
            dealer,
            callee);
    public static final WAMPFeature call_canceling = new WAMPFeature(
            FEATURE_call_canceling,
            caller,
            dealer,
            callee);
    public static final WAMPFeature caller_identification = new WAMPFeature(
            FEATURE_caller_identification,
            caller,
            dealer,
            callee);
    public static final WAMPFeature call_trustlevels = new WAMPFeature(
            FEATURE_call_trustlevels,
            dealer,
            callee);
    public static final WAMPFeature registration_meta_api = new WAMPFeature(
            FEATURE_registration_meta_api,
            dealer);
    public static final WAMPFeature pattern_based_registration = new WAMPFeature(
            FEATURE_pattern_based_registration,
            dealer,
            callee);
    public static final WAMPFeature shared_registration = new WAMPFeature(
            FEATURE_shared_registration,
            dealer,
            callee);
    public static final WAMPFeature sharded_registration = new WAMPFeature(
            FEATURE_sharded_registration,
            dealer,
            callee);
    public static final WAMPFeature registration_revocation = new WAMPFeature(
            FEATURE_registration_revocation,
            dealer,
            callee);
    public static final WAMPFeature procedure_reflection = new WAMPFeature(
            FEATURE_procedure_reflection,
            dealer);

    // PubSub Features
    public static final WAMPFeature subscriber_blackwhite_listing = new WAMPFeature(
            FEATURE_subscriber_blackwhite_listing,
            publisher,
            broker);
    public static final WAMPFeature publisher_exclusion = new WAMPFeature(
            FEATURE_publisher_exclusion,
            publisher,
            broker);
    public static final WAMPFeature publisher_identification = new WAMPFeature(
            FEATURE_publisher_identification,
            publisher,
            broker,
            subscriber);
    public static final WAMPFeature publication_trustlevels = new WAMPFeature(
            FEATURE_publication_trustlevels,
            broker,
            subscriber);
    public static final WAMPFeature subscription_meta_api = new WAMPFeature(
            FEATURE_subscription_meta_api,
            broker);
    public static final WAMPFeature pattern_based_subscription = new WAMPFeature(
            FEATURE_pattern_based_subscription,
            broker,
            subscriber);
    public static final WAMPFeature sharded_subscription = new WAMPFeature(
            FEATURE_sharded_subscription,
            broker,
            subscriber);
    public static final WAMPFeature event_history = new WAMPFeature(
            FEATURE_event_history,
            broker,
            subscriber);
    public static final WAMPFeature topic_reflection = new WAMPFeature(
            FEATURE_topic_reflection,
            broker);

    // Other Advanced Features
    public static final WAMPFeature x_challenge_response_authentication = new WAMPFeature(
            FEATURE_x_challenge_response_authentication);
    public static final WAMPFeature x_cookie_authentication = new WAMPFeature(
            FEATURE_x_cookie_authentication);
    public static final WAMPFeature x_ticket_authentication = new WAMPFeature(
            FEATURE_x_ticket_authentication);
    public static final WAMPFeature x_rawsocket_transport = new WAMPFeature(
            FEATURE_x_rawsocket_transport);
    public static final WAMPFeature x_batched_ws_transport = new WAMPFeature(
            FEATURE_x_batched_ws_transport);
    public static final WAMPFeature x_longpoll_transport = new WAMPFeature(
            FEATURE_x_longpoll_transport);
    public static final WAMPFeature x_session_meta_api = new WAMPFeature(
            FEATURE_x_session_meta_api);

    public static WAMPFeature find(String name) {
        return features.get(name);
    }

    /**
     * Merges src and features sets. If no changes original src is returned.
     *
     * @param src
     * @param features
     * @return
     */
    public static WAMPFeature[] merge(WAMPFeature[] src, WAMPFeature... features) {
        WAMPFeature[] r = src;
        if (features != null) {
            for (WAMPFeature f : features) {
                if (f == null) {
                    continue;
                }
                if (r == null) {
                    r = new WAMPFeature[]{f};
                } else {
                    boolean found = false;
                    for (WAMPFeature ff : r) {
                        if (ff == f) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        r = Arrays.copyOf(r, r.length + 1);
                        r[r.length - 1] = f;
                    }
                }
            }
        }
        return r;
    }

    /**
     * Returns copy of merged src and features.
     *
     * @param src
     * @param features
     * @return
     */
    public static WAMPFeature[] mergeCopy(WAMPFeature[] src, WAMPFeature... features) {
        return merge(merge(null, src), features);
    }

    // instance
    String uri;
    WAMP.Role[] scope;

    public WAMPFeature(String name) {
        uri = name;
        features.put(uri, this);
    }

    public WAMPFeature(String uri, WAMP.Role... scope) {
        try {
            this.uri = uri;
            if (!WAMP_DT.uri.validate(uri)) {
                throw new WAMPException("Invalid feature URI: " + uri);
            }
            features.put(uri, this);
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
        this.scope = scope;
    }

    public String uri() {
        return uri;
    }

    public WAMP.Role[] scope() {
        return scope;
    }

    @Override
    public String toString() {
        return ((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName())
                + "{" + "uri=" + uri + ", scope=" + Arrays.asList(scope) + '}';
    }

}
