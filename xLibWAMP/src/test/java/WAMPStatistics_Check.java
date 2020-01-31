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
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;
import ssg.lib.wamp.stat.WAMPMessageStatistics;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.stat.Statistics;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author 000ssg
 */
public class WAMPStatistics_Check {

    public static void dump(Statistics... stats) {
        System.out.println("STAT[" + stats.length + "]");
        for (Statistics stat : stats) {
            System.out.println("  " + stat.dumpStatistics(false).replace("\n", "\n  "));
        }
    }

    public static void main(String... args) throws Exception {
        WAMPStatistics ws = new WAMPStatistics("root");
        ws.init(null);
        WAMPStatistics ws2 = ws.createChild(null, "ws");

        WAMPCallStatistics wcs = ws2.createChildCallStatistics("ch_call");
        WAMPMessageStatistics wms = ws2.createChildMessageStatistics("ch_msg");
        
        dump(ws,ws2,wcs,wms);
        
        wcs.onCall();
        dump(ws,ws2,wcs,wms);
        
        wcs.onCall();
        wcs.onDuration(1000000);
        dump(ws,ws2,wcs,wms);
        
        wms.onSent(WAMPMessage.hello("a", WAMPTools.EMPTY_DICT));
        dump(ws,ws2,wcs,wms);

        wms.onSent(WAMPMessage.hello("a", WAMPTools.EMPTY_DICT));
        wms.onSent(WAMPMessage.hello("a", WAMPTools.EMPTY_DICT));
        wms.onSent(WAMPMessage.hello("a", WAMPTools.EMPTY_DICT));
        wms.onReceived(WAMPMessage.hello("a", WAMPTools.EMPTY_DICT));
        dump(ws,ws2,wcs,wms);
    }
}
