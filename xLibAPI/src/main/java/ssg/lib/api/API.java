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
package ssg.lib.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.api.dbms.DB_API;

/**
 * API is consistent hierarchical (optionally) set of functions and data types
 * providing method execution.
 *
 * @author 000ssg
 */
public abstract class API extends APIGroup {

    private static final long serialVersionUID = 1L;

    public API(String apiName) {
        super(APIItemCategory.model, apiName);
    }

    public Object matchContext(Collection<APIProcedure> procs, Map<Object, APICallable> callables, Object... candidates) {
        return candidates != null ? candidates[0] : null;
    }

    public abstract <T extends APICallable> T createCallable(APIProcedure proc, Object context);

    public static class APIResult extends LinkedHashMap<String, Object> {

        public APIResult add(String name, Object value, DB_API.APIResult dbResult) {
            put(name, value);
            return dbResult;
        }
    }
}
