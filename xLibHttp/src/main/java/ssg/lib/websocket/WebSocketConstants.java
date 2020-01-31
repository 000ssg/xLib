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

/**
 *
 * @author 000ssg
 */
public interface WebSocketConstants {

    // WebSocket closure reasons
    short CR_Abnormal_Closure = 1006;
    short CR_Going_Away = 1001;
    short CR_Internal_Server_Error = 1011;
    short CR_Invalid_frame_payload_data = 1007;
    short CR_Mandatory_Ext = 1010;
    short CR_Message_Too_Big = 1009;
    short CR_No_Status_Rcvd = 1005;
    short CR_Normal_Closure = 1000;
    short CR_Policy_Violation = 1008;
    short CR_Protocol_error = 1002;
    short CR_TLS_Handshake = 1015;
    short CR_Unsupported_Data = 1003;
    short CR__Reserved = 1004;

    // HTTP standard headers
    String HH_CONNECTION = "CONNECTION";
    String HH_ORIGIN = "ORIGIN";
    String HH_UPGRADE = "UPGRADE";

    // WebSocket-specific HTTP headers
    String S_WS_ = "Sec-WebSocket-";
    String S_WS_A = S_WS_ + "Accept";
    String S_WS_E = S_WS_ + "Extensions";
    String S_WS_K = S_WS_ + "Key";
    String S_WS_P = S_WS_ + "Protocol";
    String S_WS_S = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    String S_WS_V = S_WS_ + "Version";

    // WebSocket-specific values
    String ws_protocol = "websocket";
}
