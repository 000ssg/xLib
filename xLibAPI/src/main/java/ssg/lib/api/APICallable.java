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

import java.util.List;
import ssg.lib.api.util.APIException;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public abstract interface APICallable {

    /**
     * Optional method to return API definition.
     *
     * Params may be used to choose proper proc (if multiple variants are
     * allowed) and treated as map, list or array.
     *
     * @param <T>
     * @param params
     * @return
     */
    default <T extends APIProcedure> T getAPIProcedure(Object params) {
        return null;
    }

    /**
     * Returns all API procedure variants
     *
     * @param <T>
     * @return
     */
    default <T extends APIProcedure> T[] getAPIProcedures() {
        return null;
    }

    default List toParametersList(Map<String, Object> params) {
        APIProcedure proc = getAPIProcedure(params);
        if (proc != null) {
            return proc.toParametersList(params);
        } else {
            return null;
        }
    }

    default Map<String, Object> toParametersMap(List params) {
        APIProcedure proc = getAPIProcedure(params);
        if (proc != null) {
            return proc.toParametersMap(params);
        } else {
            return null;
        }
    }

    default Map<String, Object> toParametersMap(Object[] params) {
        APIProcedure proc = getAPIProcedure(params);
        if (proc != null) {
            return proc.toParametersMap(params);
        } else {
            return null;
        }
    }

    <T> T call(Map<String, Object> params) throws APIException;
}
