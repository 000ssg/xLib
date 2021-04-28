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

/**
 *
 * @author 000ssg
 */
public interface WAMPConstantsAdvanced {

    // Advanced profile
    public static final String ERROR_Canceled = "wamp.error.canceled";
    public static final String ERROR_OptionNotAllowed = "wamp.error.option_not_allowed";
    public static final String ERROR_NoEligibleCallee = "wamp.error.no_eligible_callee";
    public static final String ERROR_RejectedIdentityDisclose = "wamp.error.option_disallowed.disclose_me";
    public static final String ERROR_NetworkFailure = "wamp.error.network_failure";
    public static final String ERROR_Unavailable = "wamp.error.unavailable";
    public static final String ERROR_NoAvailableCallee = "wamp.error.no_available_callee";

    // Advanced feature option constants
    // RPC Features
    public static final String FEATURE_progressive_call_results = "progressive_call_results";
    public static final String FEATURE_progressive_calls = "progressive_calls";
    public static final String FEATURE_call_timeout = "call_timeout";
    public static final String FEATURE_call_canceling = "call_canceling";
    public static final String FEATURE_caller_identification = "caller_identification";
    public static final String FEATURE_call_trustlevels = "call_trustlevels";
    public static final String FEATURE_registration_meta_api = "registration_meta_api";
    public static final String FEATURE_pattern_based_registration = "pattern_based_registration";
    public static final String FEATURE_shared_registration = "shared_registration";
    public static final String FEATURE_sharded_registration = "sharded_registration";
    public static final String FEATURE_registration_revocation = "registration_revocation";
    public static final String FEATURE_procedure_reflection = "procedure_reflection";
    public static final String FEATURE_call_reroute = "call_reroute";
    // PubSub Features
    public static final String FEATURE_subscriber_blackwhite_listing = "subscriber_blackwhite_listing";
    public static final String FEATURE_publisher_exclusion = "publisher_exclusion";
    public static final String FEATURE_publisher_identification = "publisher_identification";
    public static final String FEATURE_publication_trustlevels = "publication_trustlevels";
    public static final String FEATURE_subscription_meta_api = "subscription_meta_api";
    public static final String FEATURE_pattern_based_subscription = "pattern_based_subscription";
    public static final String FEATURE_sharded_subscription = "sharded_subscription";
    public static final String FEATURE_event_history = "event_history";
    public static final String FEATURE_topic_reflection = "topic_reflection";

    // Other Advanced Features
    public static final String FEATURE_x_challenge_response_authentication = "challenge-response authentication";
    public static final String FEATURE_x_cookie_authentication = "cookie authentication";
    public static final String FEATURE_x_ticket_authentication = "ticket authentication";
    public static final String FEATURE_x_rawsocket_transport = "rawsocket transport";
    public static final String FEATURE_x_batched_ws_transport = "batched WS transport";
    public static final String FEATURE_x_longpoll_transport = "longpoll transport";
    public static final String FEATURE_x_session_meta_api = "session_meta_api";
    public static final String FEATURE_x_testament_meta_api = "testament_meta_api";
    
}
