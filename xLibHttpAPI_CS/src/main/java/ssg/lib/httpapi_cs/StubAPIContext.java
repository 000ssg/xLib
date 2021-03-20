/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.httpapi_cs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import ssg.lib.api.API;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.util.APISearchable.APIMatcher.API_MATCH;
import ssg.lib.common.Stub.StubContext;

/**
 *
 * @author 000ssg
 */
public class StubAPIContext extends StubContext<API, APIProcedure, APIParameter, APIDataType> {

    public StubAPIContext(String baseURL, String namespace, boolean generateExtendedComments) {
        super(baseURL, namespace, generateExtendedComments);
    }

    @Override
    public String nameOf(Object obj) {
        if (obj instanceof APIItem) {
            return ((APIItem) obj).fqn();
        }
        return null;
    }

    @Override
    public List<APIProcedure> methods(API api) {
        Collection<APIProcedure> c = (Collection<APIProcedure>) (Object) api.find((item) -> {
            return item instanceof APIProcedure ? API_MATCH.exact : API_MATCH.partial;
        }, APIProcedure.class, null);
        if (c instanceof List) {
            return (List) c;
        } else {
            List<APIProcedure> r = new ArrayList<>();
            if (c != null) {
                r.addAll(c);
            }
            return r;
        }
    }

    @Override
    public List<APIParameter> parameters(APIProcedure method) {
        if (method != null) {
            return method.toParametersList((Map) method.params);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public PDIR direction(APIParameter parameter) {
        if (parameter != null) {
            switch (parameter.direction) {
                case in:
                    return PDIR.in;
                case out:
                    return PDIR.out;
                case in_out:
                    return PDIR.in_out;
                case ret:
                    return PDIR.ret;
            }
        }
        return null;
    }

    @Override
    public APIDataType returnType(APIProcedure method) {
        return method != null && method instanceof APIFunction ? ((APIFunction) method).response.type : null;
    }

    @Override
    public APIDataType type(APIParameter parameter) {
        return parameter != null ? parameter.type : null;
    }
}
