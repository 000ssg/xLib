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
package ssg.lib.websocket;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 *
 * @author 000ssg
 */
public interface WebSocketExtension extends Serializable, Cloneable {

    String getName();

    <T> T getParameter(String name);

    void setParameter(String name, Object value);

    /**
     * Returns extension size for frame and optionally provided data/offset...
     *
     * @param frame
     * @param data
     * @param off
     * @return
     */
    int size(WebSocketFrame frame, byte[] data, int off);

    /**
     * Preprocesses data for the frame and fix frame properties. Returns
     * resultant ext+app data for sending.
     *
     * @param frame frame to send
     * @param data app data
     * @return ext+app data
     */
    byte[] prepareFrame(WebSocketFrame frame, byte[] data, int off) throws IOException;

    /**
     * Restores app data from received frame and ext+app data.
     *
     * @param frame
     * @param data
     * @return
     */
    byte[] restoreFrame(WebSocketFrame frame, byte[] data) throws IOException;

    /**
     * Check/do handle frame with non-standard opcode.
     *
     * @param ws
     * @param frame
     * @param data
     * @return
     * @throws IOException
     */
    boolean handleExtensionFrame(WebSocket ws, WebSocketFrame frame, ByteBuffer... payload) throws IOException;

    WebSocketExtension clone();
}
