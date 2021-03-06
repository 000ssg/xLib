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
package ssg.lib.websocket.impl;

import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.base.Head;
import ssg.lib.http.base.HttpData;
import static ssg.lib.http.base.HttpData.HH_UPGRADE;
import static ssg.lib.http.base.HttpData.HUPGR_WEBSOCKET;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.service.Repository;
import ssg.lib.service.Repository.RepositoryListener;
import ssg.lib.websocket.WebSocketProcessor;

/**
 *
 * @author 000ssg
 */
public class HttpConnectionUpgradeWS<P extends Channel> implements HttpConnectionUpgrade<P>, RepositoryListener<HttpConnectionUpgrade> {

    Collection<Repository<HttpConnectionUpgrade>> owners = Collections.synchronizedCollection(new HashSet<>());

    WebSocketProcessor wsp;
    WebSocketProcessor.WebSocketMessageListener wsl;
    boolean requireAuth = false;

    public HttpConnectionUpgradeWS<P> configure(WebSocketProcessor wsp) {
        this.wsp = wsp;
        return this;
    }

    public HttpConnectionUpgradeWS<P> configure(WebSocketProcessor.WebSocketMessageListener wsl) {
        this.wsl = wsl;
        return this;
    }

    public HttpConnectionUpgradeWS<P> configureNeedAuth(boolean needAuth) {
        requireAuth = needAuth;
        return this;
    }

    @Override
    public boolean testUpgrade(String root, Head head) {
        if (head != null && head.isConnectionUpgrade()) {
            String upgradeTo = head.getHeader1(HH_UPGRADE);
            if (HUPGR_WEBSOCKET.equalsIgnoreCase(upgradeTo)) {
                if (testWSPath(root, head)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public HttpData doUpgrade(P provider, HttpData data) throws IOException {
        if (data instanceof HttpWS) {
            // already upgraded...
            return data;
        }
        if (requireAuth && data instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) data;
            if (req.getHttpSession() == null || req.getHttpSession().getUser() == null) {
                HttpResponse res = req.getResponse();
                res.setResponseCode(401, "Not autenticated");
                res.onHeaderLoaded();
                res.onLoaded();
                return data;
            }
        }
        HttpWS httpws = createWSHttp(provider, data);
        if (wsp != null) {
            httpws.ws.setProcessor(wsp);
        } else if (wsl != null) {
            if (httpws.ws.getProcessor() != null) {
                httpws.ws.getProcessor().addWebSocketMessageListener(wsl);
            } else {
                httpws.ws.setDefaultMessageListener(wsl);
                //throw new IOException("Failed to upgrade to WebSocket: no processor assigned.");
            }
        }
        return httpws;
    }

    /**
     * Binds WS connection upgrade to HTTP request path. By default any path
     * starting with "/" -> any path. Invoked as additional check from
     * testUpgrage...
     *
     * @param head
     * @return
     */
    public boolean testWSPath(String root, Head head) {
        return head != null && head.isHeadCompleted() && head.getProtocolInfo() != null && head.getProtocolInfo()[1].startsWith("/");
    }

    /**
     * Creates HttpWS instance to replace current HttpData instance. Invoked
     * from doUpgrade.
     *
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    public HttpWS createWSHttp(P provider, HttpData data) throws IOException {
        HttpWS httpws = new HttpWS(new WebSocketChannel((SocketChannel) provider, data.getHead()), data);
        return httpws;
    }

    @Override
    public void onAdded(Repository<HttpConnectionUpgrade> repository, HttpConnectionUpgrade item) {
        if (repository != null && item == this) {
            owners.add(repository);
        }
    }

    @Override
    public void onRemoved(Repository<HttpConnectionUpgrade> repository, HttpConnectionUpgrade item) {
        if (repository != null && item == this) {
            owners.remove(repository);
        }
    }

    public Collection<Repository<HttpConnectionUpgrade>> getOwners() {
        return Collections.unmodifiableCollection(owners);
    }
}
