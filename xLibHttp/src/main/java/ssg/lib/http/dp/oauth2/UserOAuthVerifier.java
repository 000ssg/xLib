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
package ssg.lib.http.dp.oauth2;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.JSON;
import ssg.lib.http.HttpAuthenticator.UserVerifier;
import ssg.lib.http.HttpAuthenticator.VerificationResult;
import ssg.lib.http.HttpUser;
import ssg.lib.oauth2.client.OAuthClient;
import ssg.lib.oauth2.client.OAuthContext;
import ssg.lib.oauth2.client.OAuthContextBase;
import ssg.lib.oauth2.client.OAuthUserInfo;

/**
 *
 * @author 000ssg
 */
public class UserOAuthVerifier implements UserVerifier {

    Map<String, OAuthClient> oauths = new LinkedHashMap<>();
    Map<OAuthClient, String> roauths = new LinkedHashMap<>();
    Map<String, Map<String, String>> users = new LinkedHashMap<>();

    /**
     * Configure JSON decoder for OAuthClient implementations if none!
     */
    static {
        if (!OAuthContextBase.isConvertedConfigured()) {
            JSON.Decoder decoder = new JSON.Decoder();
            OAuthContextBase.configure((text) -> {
                return decoder.readObject(text, Map.class);
            });
        }
    }

    public UserOAuthVerifier() {
    }

    public UserOAuthVerifier(Map<String, OAuthClient> oauths) {
        if (oauths != null) {
            for (Entry<String, OAuthClient> entry : oauths.entrySet()) {
                addOAuth(entry.getKey(), entry.getValue());
            }
        }
    }

    public UserOAuthVerifier(OAuthHttpDataProcessor oauths) {
        if (oauths != null && oauths.oauths != null) {
            for (Entry<String, OAuthClient> entry : oauths.oauths.entrySet()) {
                addOAuth(entry.getKey(), entry.getValue());
            }
        }
    }

    public UserOAuthVerifier addOAuth(String name, OAuthClient client) {
        if (name != null && client != null) {
            oauths.put(name, client);
            roauths.put(client, name);
            if (users.get(name) == null) {
                users.put(name, new LinkedHashMap<String, String>());
            }
        }
        return this;
    }

    public UserOAuthVerifier removeOAuth(String name) {
        if (name != null && oauths.containsKey(name)) {
            OAuthClient oa = oauths.remove(name);
            if (roauths.containsKey(oa)) {
                roauths.remove(oa);
            }
            if (users.containsKey(name)) {
                users.remove(name);
            }
        }
        return this;
    }

    public Collection<String> getOAuthKeys() {
        return oauths.keySet();
    }

    @Override
    public boolean canVerify(Object... parameters) {
        return parameters != null && parameters.length > 0 && parameters[0] instanceof OAuthContext && oauths.containsValue(((OAuthContext) parameters[0]).getOAuth());
    }

    @Override
    public VerificationResult verify(Object... parameters) throws IOException {
        VerificationResult r = null;
        OAuthUserInfo oau = null;
        String id = null;
        if (canVerify(parameters)) {
            OAuthContext oac = (OAuthContext) parameters[0];
            if (oac.getOAuthUserInfo() != null) {
                oau = oac.getOAuthUserInfo();
                Map<String, String> map = users.get(roauths.get(oac.getOAuth()));
                if (map != null) {
                    id = map.get(oau.id());
                    if (id == null) {
                        id = map.get(oau.email());
                    }
                    if (id == null && registerUser(oau.id(), parameters)) {
                        id = oau.id();
                    }
                } else {
                    if (registerUser(oau.id(), parameters)) {
                        id = oau.id();
                    }
                }
            }
            if (id != null) {
                r = new VerificationResult(this, id, oau.name(), oac.domain());
            }
        }
        return r;
    }

    @Override
    public Map<String, Object> verifiedProperties(Object... parameters) {
        if (canVerify(parameters)) {
            OAuthContext oac = (OAuthContext) parameters[0];
            try {
                OAuthUserInfo oau = oac.getOAuthUserInfo();
                if (oau != null) {
                    Map<String, Object> props = new LinkedHashMap<>();
                    if (oau.getProperties() != null) {
                        props.putAll(oau.getProperties());
                    }
                    if (oau.email() != null) {
                        props.put("email", oau.email().toLowerCase());
                    }
                    props.put("oauth", oac);
                    return props;
                }
            } catch (Throwable th) {
            }
        }
        return null;
    }

    @Override
    public boolean registerUser(String name, Object... parameters) {
        if (name == null || parameters == null || parameters.length < 1 || !(parameters[0] instanceof OAuthContext)) {
            return false;
        }

        try {
            OAuthContext oac = (OAuthContext) parameters[0];
            OAuthUserInfo oau = oac.getOAuthUserInfo();
            Map<String, String> map = users.get(roauths.get(oac.getOAuth()));
            if (canRegisterNew(oau, parameters)) {
                return doRegisterUser(map, name, oau);
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
        return false;
    }

    public boolean canRegisterNew(OAuthUserInfo oau, Object... parameters) {
        return oau != null && oau.id() != null;
    }

    /**
     * Perform actual OAuth user mappings for the name.
     *
     * @param map
     * @param name
     * @param oau
     * @return
     */
    public boolean doRegisterUser(Map<String, String> map, String name, OAuthUserInfo oau) {
        boolean r = false;
        if (oau.email() != null) {
            map.put(oau.email(), name);
            r = true;
        }
        if (oau.id() != null) {
            map.put(oau.id(), name);
            r = true;
        }
        return r;
    }

    @Override
    public boolean unregisterUser(String name) {
        boolean r = false;
        if (name != null) {
            for (Map<String, String> map : users.values()) {
                if (map.containsValue(name)) {
                    Collection<String> keys = new HashSet<>();
                    for (Entry<String, String> entry : map.entrySet()) {
                        if (name.equals(entry.getValue())) {
                            keys.add(entry.getKey());
                        }
                    }
                    if (!keys.isEmpty()) {
                        r = true;
                        for (String key : keys) {
                            map.remove(key);
                        }
                    }
                }
            }
        }
        return r;
    }

    @Override
    public HttpUser.AUTH_TYPE getType() {
        return HttpUser.AUTH_TYPE.oauth;
    }

}
