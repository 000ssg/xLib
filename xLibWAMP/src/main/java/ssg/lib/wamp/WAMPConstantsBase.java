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
 * @author sesidoro
 */
public interface WAMPConstantsBase {

    // URIs
    public static final String InvalidURI = "wamp.error.invalid_uri";
    public static final String NoSuchProcedure = "wamp.error.no_such_procedure";
    public static final String ProcedureAlreadyExists = "wamp.error.procedure_already_exists";
    public static final String NoSuchRegistration = "wamp.error.no_such_registration";
    public static final String NoSuchSubscription = "wamp.error.no_such_subscription";

    // Session
    public static final String SystemShutdown = "wamp.close.system_shutdown";
    public static final String CloseRealm = "wamp.close.close_realm";
    public static final String AckClose = "wamp.close.goodbye_and_out";
    public static final String ProtocolViolation = "wamp.error.protocol_violation";

    // Authorization
    public static final String NotAuthorized = "wamp.error.not_authorized";
    public static final String AuthorizationFailed = "wamp.error.authorization_failed";
    public static final String NoSuchRealm = "wamp.error.no_such_realm";
    public static final String NoSuchRole = "wamp.error.no_such_role";
}
