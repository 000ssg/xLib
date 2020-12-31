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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static ssg.lib.wamp.messages.WAMPMessageType.T_AUTHENTICATE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CANCEL;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CHALLENGE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_INTERRUPT;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.messages.WAMPMessageType.WAMPValidationResult;

/**
 *
 * @author 000ssg
 */
public class WAMPMessage {

    WAMPMessageType type;
    Object[] data;

    /**
     * Build WAMPMessage object from WAMP message structure
     *
     * @param data
     * @throws WAMPException
     */
    public WAMPMessage(List data) throws WAMPException {
        if (data == null || data.size() < 1 || !(data.get(0) instanceof Number)) {
            throw new WAMPException("Invalid WAMP message structure: " + data);
        }
        int typeId = ((Number) data.get(0)).intValue();
        type = WAMPMessageType.getType(typeId, data.size() - 1);
        if (type != null) {
            this.data = new Object[data.size() - 1];
            for (int i = 1; i < data.size(); i++) {
                this.data[i - 1] = data.get(i);
            }
        } else {
            throw new WAMPException("Unrecognized message type (type variant): " + typeId + " for " + data);
        }
    }

    public List toList() {
        List r = new ArrayList(1 + data.length);
        r.add(type.id);
        for (Object o : data) {
            r.add(o);
        }
        return r;
    }

    public WAMPMessageType getType() {
        return type;
    }

    public Object[] getData() {
        return data;
    }
    
    public int getDataLength() {
        return data!=null ? data.length : 0;
    }

//    public <T> T getData(int order) {
//        return (T) data[order];
//    }
    public long getId(int order) {
        return ((Number) data[order]).longValue();
    }

    public long getInt(int order) {
        return ((Number) data[order]).longValue();
    }

    public Map<String, Object> getDict(int order) {
        return (Map<String, Object>) data[order];
    }

    public List getList(int order) {
        return (List) data[order];
    }

    public String getString(int order) {
        return (String) data[order];
    }

    public String getUri(int order) {
        return (String) data[order];
    }

    public WAMPMessage(WAMPMessageType type, Object... data) throws WAMPException {
        if (type == null) {
            try {
                WAMPMessageType.main(null);
            } catch (Throwable th) {
            }
            throw new WAMPException("No WAMP message type");
        }
        WAMPValidationResult vr = type.validate(data);
        if (WAMPValidationResult.ok != vr) {
            throw new WAMPException("Invalid WAMP message parameters: " + vr);
        }
        this.type = type;
        this.data = data;
    }

    @Override
    public String toString() {
        return "WAMPMessage{" + "type=" + type + ", data=" + ((data != null) ? data.length : "<none>") + '}';
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////// basic message builders
    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////
    /////////////////////////////////// session
    ///////////////////////////////////////////
    public static WAMPMessage hello(String realm, Map<String, Object> details) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.HELLO, realm, details);
    }

    public static WAMPMessage welcome(long session, Map<String, Object> details) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.WELCOME, session, details);
    }

    public static WAMPMessage abort(Map<String, Object> details, String reason) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.ABORT, details, reason);
    }

    public static WAMPMessage goodbye(Map<String, Object> details, String reason) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.GOODBYE, details, reason);
    }

    public static WAMPMessage error(long requestType, long requestRequestId, Map<String, Object> details, String error) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_ERROR, 4), requestType, requestRequestId, details, error);
    }

    public static WAMPMessage error(long requestType, long requestRequestId, Map<String, Object> details, String error, List arguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_ERROR, 5), requestType, requestRequestId, details, error, arguments);
    }

    public static WAMPMessage error(long requestType, long requestRequestId, Map<String, Object> details, String error, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_ERROR, 6), requestType, requestRequestId, details, error, arguments, argumentsKw);
    }

    ///////////////////////////////////////////
    ///////////////////////////////// publisher
    ///////////////////////////////////////////
    public static WAMPMessage publish(long request, Map<String, Object> options, String topic) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_PUBLISH, 3), request, options, topic);
    }

    public static WAMPMessage publish(long request, Map<String, Object> options, String topic, List arguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_PUBLISH, 4), request, options, topic, arguments);
    }

    public static WAMPMessage publish(long request, Map<String, Object> options, String topic, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_PUBLISH, 5), request, options, topic, arguments, argumentsKw);
    }

    public static WAMPMessage published(long publishRequestId, long publicationId) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.PUBLISHED, publishRequestId, publicationId);
    }

    ///////////////////////////////////////////
    //////////////////////////////// subscriber
    ///////////////////////////////////////////
    public static WAMPMessage subscribe(long request, Map<String, Object> options, String topic) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.SUBSCRIBE, request, options, topic);
    }

    public static WAMPMessage subscribed(long subscribeRequest, long subscription) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.SUBSCRIBED, subscribeRequest, subscription);
    }

    public static WAMPMessage unsubscribe(long request, long subscribedSubscription) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.UNSUBSCRIBE, request, subscribedSubscription);
    }

    public static WAMPMessage unsubscribed(long unsubscribeRequest) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.UNSUBSCRIBED, unsubscribeRequest);
    }

    public static WAMPMessage event(long subscribedSubscription, long publishedPublication, Map<String, Object> details) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_EVENT, 3), subscribedSubscription, publishedPublication, details);
    }

    public static WAMPMessage event(long subscribedSubscription, long publishedPublication, Map<String, Object> details, List arguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_EVENT, 4), subscribedSubscription, publishedPublication, details, arguments);
    }

    public static WAMPMessage event(long subscribedSubscription, long publishedPublication, Map<String, Object> details, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_EVENT, 5), subscribedSubscription, publishedPublication, details, arguments, argumentsKw);
    }

//    public static WAMPMessage event(long subscribedSubscription, Object... args) throws WAMPException {
//        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_EVENT, args), subscribedSubscription, args);
//    }
    ///////////////////////////////////////////
    //////////////////////////////////// caller
    ///////////////////////////////////////////
    public static WAMPMessage call(long request, Map<String, Object> options, String procedure) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_CALL, 3), request, options, procedure);
    }

    public static WAMPMessage call(long request, Map<String, Object> options, String procedure, List arguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_CALL, 4), request, options, procedure, arguments);
    }

    public static WAMPMessage call(long request, Map<String, Object> options, String procedure, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_CALL, 5), request, options, procedure, arguments, argumentsKw);
    }

    public static WAMPMessage result(long callRequest, Map<String, Object> details) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_RESULT, 2), callRequest, details);
    }

    public static WAMPMessage result(long callRequest, Map<String, Object> details, List yieldArguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_RESULT, 3), callRequest, details, yieldArguments);
    }

    public static WAMPMessage result(long callRequest, Map<String, Object> details, List yieldArguments, Map<String, Object> yieldArgumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_RESULT, 4), callRequest, details, yieldArguments, yieldArgumentsKw);
    }

    ///////////////////////////////////////////
    ////////////////////// callee: registration
    ///////////////////////////////////////////
    public static WAMPMessage register(long request, Map<String, Object> options, String procedure) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.REGISTER, request, options, procedure);
    }

    public static WAMPMessage registered(long registerRequest, long registration) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.REGISTERED, registerRequest, registration);
    }

    public static WAMPMessage unregister(long request, long registeredRegistration) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.UNREGISTER, request, registeredRegistration);
    }

    public static WAMPMessage unregistered(long unregisterRequest) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.UNREGISTERED, unregisterRequest);
    }

    ///////////////////////////////////////////
    //////////////////////// callee: invocation
    ///////////////////////////////////////////
    public static WAMPMessage invocation(long request, long registeredRegistration, Map<String, Object> details) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_INVOCATION, 3), request, registeredRegistration, details);
    }

    public static WAMPMessage invocation(long request, long registeredRegistration, Map<String, Object> details, List callArguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_INVOCATION, 4), request, registeredRegistration, details, callArguments);
    }

    public static WAMPMessage invocation(long request, long registeredRegistration, Map<String, Object> details, List callArguments, Map<String, Object> callArgumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_INVOCATION, 5), request, registeredRegistration, details, callArguments, callArgumentsKw);
    }

    public static WAMPMessage yield_(long invocationRequest, Map<String, Object> options) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_YIELD, 2), invocationRequest, options);
    }

    public static WAMPMessage yield_(long invocationRequest, Map<String, Object> options, List arguments) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_YIELD, 3), invocationRequest, options, arguments);
    }

    public static WAMPMessage yield_(long invocationRequest, Map<String, Object> options, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(WAMPMessageType.T_YIELD, 4), invocationRequest, options, arguments, argumentsKw);
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////// Advanced
    ////////////////////////////////////////////////////////////////////////////
    //////////////// Auth
    public static WAMPMessage challenge(String authMethod, Map<String, Object> extra) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(T_CHALLENGE, 2), authMethod, extra);
    }
    public static WAMPMessage authenticate(String signature, Map<String, Object> extra) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(T_AUTHENTICATE, 2), signature, extra);
    }

    //////////////// RPC
    public static WAMPMessage cancel(long invocationId, Map<String, Object> options) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(T_CANCEL, 2), invocationId, options);
    }

    public static WAMPMessage interrupt(long invocationId, Map<String, Object> options) throws WAMPException {
        return new WAMPMessage(WAMPMessageType.getType(T_INTERRUPT, 2), invocationId, options);
    }

}
