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
package ssg.lib.http2.impl;

import ssg.lib.websocket.impl.WebSocketChannel;
import ssg.lib.websocket.impl.HttpWS;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.base.Head;
import ssg.lib.http.base.HttpData;
import static ssg.lib.http.base.HttpData.HH_UPGRADE;
import static ssg.lib.http.base.HttpData.HUPGR_HTTP2;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author 000ssg
 */
public class HttpConnectionUpgradeHTTP2<P extends Channel> implements HttpConnectionUpgrade<P> {

    @Override
    public boolean testUpgrade(String root, Head head) {
        if (head != null && head.isConnectionUpgrade()) {
            String upgradeTo = head.getHeader1(HH_UPGRADE);
            if (HUPGR_HTTP2.equalsIgnoreCase(upgradeTo)) {
                return true;
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
        HttpWS httpws = new HttpWS(new WebSocketChannel((SocketChannel) provider, data.getHead()), data);
        return httpws;
    }

}
