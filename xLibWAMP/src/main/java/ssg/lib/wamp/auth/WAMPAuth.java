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
package ssg.lib.wamp.auth;

import java.util.Map;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.util.WAMPTools;

/**
 * Non-WAMP utility class to store client authentication detail.
 *
 * @author sesidoro
 */
public class WAMPAuth {

    public static final WAMPAuth Anonymous = new WAMPAuth("none", "anonymous", "none", WAMPTools.EMPTY_DICT);

    private String method;
    private String authid;
    private String role;
    private Map<String, Object> details;

    public WAMPAuth(
            String method,
            String authid,
            String role,
            Map<String, Object> details
    ) {
        this.method = method;
        this.authid = authid;
        this.role = role;
        this.details = details;

    }

    public WAMPAuth(
            Map<String, Object> details
    ) {
        this.method = (String) details.get(K_AUTH_METHOD);
        this.authid = (String) details.get(K_AUTH_ID);
        this.role = (String) details.get(K_AUTH_ROLE);
        this.details = WAMPTools.createMap();
        this.details.putAll(details);
        this.details.remove(K_AUTH_METHOD);
        this.details.remove(K_AUTH_ID);
        this.details.remove(K_AUTH_ROLE);
    }

    /**
     * @return the method
     */
    public String getMethod() {
        return method;
    }

    /**
     * @return the authid
     */
    public String getAuthid() {
        return authid;
    }

    /**
     * @return the details
     */
    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * @return the role
     */
    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "WAMPAuth{" + "method=" + method + ", authid=" + authid + ", role=" + role + ", details=" + details + '}';
    }

}
