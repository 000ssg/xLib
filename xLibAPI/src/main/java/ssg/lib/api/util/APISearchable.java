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
package ssg.lib.api.util;

import java.util.Collection;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIProcedure;

/**
 *
 * @author 000ssg
 */
public interface APISearchable {

    /**
     * Returns item of specified type (compatible).
     *
     * @param <T>
     * @param matcher
     * @param type
     * @param result
     * @return
     */
    <T extends APIItem> Collection<T> find(APIMatcher matcher, Class type, Collection<T> result);

    public static interface APIMatcher {

        public static enum API_MATCH {
            exact, partial, over, none
        }

        /**
         * Typical test
         *
         * @param item
         * @param name
         * @return
         */
        static API_MATCH matchAPIProcedure(APIItem item, String name) {
            return (item instanceof APIProcedure)
                    ? item.fqn().equals(name)
                    ? API_MATCH.exact
                    : API_MATCH.none
                    : name.startsWith(item.fqn())
                    ? API_MATCH.partial
                    : API_MATCH.none;
        }

        /**
         * Typical FQN
         *
         * @param item
         * @param name
         * @return
         */
        static API_MATCH matchFQN(APIItem item, String name) {
            return item.fqn().equals(name)
                    ? API_MATCH.exact
                    : name.startsWith(item.fqn())
                    ? API_MATCH.partial
                    : item.fqn().startsWith(name)
                    ? API_MATCH.over
                    : API_MATCH.none;
        }

        API_MATCH matches(APIItem item);
    }
}
