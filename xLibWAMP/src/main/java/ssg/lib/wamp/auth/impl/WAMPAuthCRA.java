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
package ssg.lib.wamp.auth.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHODS;
import ssg.lib.wamp.messages.WAMPMessage;
import static ssg.lib.wamp.messages.WAMPMessageType.T_AUTHENTICATE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CHALLENGE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_HELLO;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPAuthCRA implements WAMPAuthProvider {

    static final DateFormat timestampDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    Map<Long, Map<String, Object>> wip = WAMPTools.createSynchronizedMap();
    String defaultAuthProvider = "wcra";
    String defaultAuthRole = "guest";
    private String secret = "WAMPCRA secret";

    public WAMPAuthCRA() {
    }

    public WAMPAuthCRA(String secret) {
        this.secret = secret;
    }

    @Override
    public String name() {
        return "wampcra";
    }

    @Override
    public boolean needChallenge(WAMPSession session, WAMPMessage msg) throws WAMPException {
        return true;
    }

    @Override
    public boolean isChallenged(WAMPSession session) {
        return wip.containsKey(session.getId());
    }

    @Override
    public WAMPMessage challenge(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
            // router
            switch (msg.getType().getId()) {
                case T_HELLO: { // build CHALLENGE msg
                    Map<String, Object> details = msg.getDict(1);
                    List<String> authMethods = (List<String>) details.get(K_AUTH_METHODS);
                    String authid = (String) details.get(K_AUTH_ID);
                    if (authMethods != null && authMethods.contains(name())) {
                        Map<String, Object> dict = WAMPTools.createDict(K_AUTH_METHOD, name(), K_AUTH_ID, authid);
                        dict.put("nonce", nonce(session, authid, details));
                        dict.put(K_AUTH_PROVIDER, authprovider(session, authid, details));
                        dict.put("timestamp", timestampDF.format(new Date()));
                        dict.put(K_AUTH_ROLE, authrole(session, authid, details));
                        dict.put("session", session.getId());
                        wip.put(session.getId(), dict);
                        String dictS = dict2string(dict);
                        try {
                            return WAMPMessage.challenge(name(), WAMPTools.createDict("challenge", dictS));
                        } finally {
                            dict.put("signature", signature(getSecret(session, msg), dictS));
                            dict.put("timeout", System.currentTimeMillis());
                        }
                    }
                }
                break;
            }
        } else {
            // client
        }
        return null;
    }

    @Override
    public WAMPMessage authenticate(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
        } else {
            // client
            switch (msg.getType().getId()) {
                case T_CHALLENGE: { // build AUTHENTICATE msg as response to CHALLENGE
                    String authMethod = msg.getString(0);
                    if (name().equals(authMethod)) {
                        String extra = (String) msg.getDict(1).get("challenge");
                        String signature = signature(getSecret(session, msg), extra);
                        return WAMPMessage.authenticate(signature, WAMPTools.EMPTY_DICT);
                    }
                    break;
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> authenticated(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
            // router
            switch (msg.getType().getId()) {
                case T_AUTHENTICATE: { // verify AUTHENTICATE msg
                    Map<String, Object> auth = wip.remove(session.getId());
                    String signatureR = auth != null && auth.get("signature") instanceof String ? (String) auth.get("signature") : null;
                    String signatureC = msg.getString(0);
                    if (signatureR != null && signatureR.equals(signatureC)) {
                        Map<String, Object> authInfo = WAMPTools.createDict(K_AUTH_METHOD, name());
                        authInfo.put(K_AUTH_ID, auth.get(K_AUTH_ID));
                        authInfo.put(K_AUTH_ROLE, auth.get(K_AUTH_ROLE));
                        authInfo.put(K_AUTH_PROVIDER, auth.get(K_AUTH_PROVIDER));
                        return authInfo;
                    }
                }
                break;
            }
        } else {
        }
        return null;
    }

    public String nonce(WAMPSession session, String authid, Map<String, Object> details) {
        return UUID.randomUUID().toString();
    }

    public String authprovider(WAMPSession session, String authid, Map<String, Object> details) {
        return defaultAuthProvider;
    }

    public String authrole(WAMPSession session, String authid, Map<String, Object> details) {
        return defaultAuthRole;
    }

    String getSecret(WAMPSession session, WAMPMessage msg) throws WAMPException {
        return secret;
    }

    String signature(String key, String data) throws WAMPException {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
        } catch (Throwable th) {
            throw new WAMPException("Failed to evaluate HMAC-SHA256 signature", th);
        }
    }

    String dict2string(Map<String, Object> dict) {
        if (dict == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (String key : dict.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(',');
            }
            sb.append('"');
            sb.append(key);
            Object v = dict.get(key);
            sb.append("\":");
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v.toString());
            } else if (v instanceof String) {
                sb.append('"');
                sb.append(v.toString());
                sb.append('"');
            } else {
                sb.append('"');
                sb.append(v.toString());
                sb.append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

//    public Map<String, Object> string2dict(String dictS) {
//        if (dictS == null || "null".equals(dictS) || !(dictS.startsWith("{") && dictS.endsWith("}"))) {
//            return null;
//        }
//        Map<String, Object> dict = new LinkedHashMap<>();
//        String[] ss = dictS.substring(1, dictS.length() - 1).split(",");
//        for (String s : ss) {
//            String[] ssi = s.split(":");
//            for (int i = 0; i < ssi.length; i++) {
//                ssi[i] = ssi[i].trim();
//            }
//            String key = ssi[0].substring(1, ssi[0].length() - 1);
//            Object value = null;
//            if ("null".equals(ssi[1])) {
//                // null === null
//            } else if ("true".equals(ssi[1])) {
//                value = true;
//            } else if ("false".equals(ssi[1])) {
//                value = false;
//            } else if (ssi[1].startsWith("'") || ssi[1].startsWith("\"")) {
//                value = ssi[1].substring(1, ssi[1].length() - 1);
//            } else if (ssi[1].contains(".") || ssi[1].toLowerCase().contains("e")) {
//                value = Double.parseDouble(ssi[1]);
//            } else {
//                value = Long.parseLong(ssi[1]);
//            }
//            dict.put(key, value);
//        }
//        return dict;
//    }
}
