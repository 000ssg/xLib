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
package ssg.lib.wamp.events;

import java.util.List;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public interface WAMPEventListener {

    String getTopic();

    Map<String, Object> getOptions();
    
    long getSubscriptionId();

    void onSubscribed(long subscriptionId);
    
    WAMPEventHandler handler();

    
    public static class WAMPEventListenerBase implements WAMPEventListener {
        long subscriptionId;
        String topic;
        Map<String,Object> options;
        WAMPEventHandler handler;

        public WAMPEventListenerBase(String topic, Map<String, Object> options, WAMPEventHandler handler) {
            this.topic = topic;
            this.options = options;
            this.handler=handler;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public Map<String, Object> getOptions() {
            return options;
        }

        @Override
        public long getSubscriptionId() {
            return subscriptionId;
        }

        @Override
        public void onSubscribed(long subscriptionId) {
            this.subscriptionId=subscriptionId;
        }

        @Override
        public WAMPEventHandler handler() {
            return handler;
        }
        
    }
    
    /**
     * Lambda-friendly handler...
     */
    public static interface WAMPEventHandler {
        void onEvent(long subscriptionId, long publicationId, Map<String, Object> options, List arguments, Map<String, Object> argumentsKw);
    }
}
