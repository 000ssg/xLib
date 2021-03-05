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
package ssg.lib.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Http user has unique id, optional name (display name), optional domain, and
 * optional RAT (Roles/Actions/Tags).
 *
 * Other user data (e.g. authentification type, domain-specific properties etc.)
 * are handled using named objects as properties.
 *
 * @author 000ssg
 */
public class HttpUser {

    public static final String P_AUTH_TYPE = "authType";

    public static enum AUTH_TYPE {
        none, password, basic, certificate, oauth, token, other
    }

    String domain;
    String id;
    String user;
    RAT rat;
    Map<String, Object> properties = new LinkedHashMap<>();

    public HttpUser() {
    }

    public HttpUser(String domain, String id, String user, RAT rat) {
        this.domain = domain;
        this.id = id;
        this.user = user;
        this.rat = rat;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return user;
    }

    public String getDomainName() {
        return domain;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()));
        sb.append('{');
        if (rat != null) {
            if (domain != null) {
                sb.append("\n  domain: " + domain);
            }
            sb.append("\n  id: " + id);
            if (user != null) {
                sb.append("\n  name: " + user);
            }
            if (rat != null) {
                sb.append("\n  rat : " + rat.toString().replace("\n", "\n    "));
            }
            if (properties != null && !properties.isEmpty()) {
                sb.append("\n  properties[" + properties.size() + "]:");
                for (String pn : properties.keySet()) {
                    sb.append("\n    " + pn + ": " + ("" + properties.get(pn)).replace("\n", "\n      "));
                }
            }
            sb.append('\n');
        } else {
            if (domain != null) {
                sb.append(domain);
            }
            sb.append(", ");
            sb.append(id);
            if (user != null) {
                sb.append(", ");
                sb.append(user);
            }
            if (properties != null && !properties.isEmpty()) {
                sb.append(", ");
                sb.append(properties.keySet());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public List<String> getRoles() {
        if (rat != null && rat.getRoles() != null) {
            return Collections.unmodifiableList(rat.getRoles());
        } else {
            return Collections.emptyList();
        }
    }
}
