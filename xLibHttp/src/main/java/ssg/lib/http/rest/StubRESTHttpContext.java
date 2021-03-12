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
package ssg.lib.http.rest;

import java.util.List;
import ssg.lib.common.Stub.StubContext;

/**
 *
 * @author sesidoro
 */
public class StubRESTHttpContext extends StubContext<List<RESTMethod>, RESTMethod, RESTParameter, Class> {

    public StubRESTHttpContext(String baseURL, String namespace, boolean generateExtendedComments) {
        super(baseURL, namespace, generateExtendedComments);
    }

    @Override
    public String nameOf(Object obj) {
        if (obj instanceof RESTMethod) {
            return ((RESTMethod) obj).getName();
        } else if (obj instanceof RESTParameter) {
            return ((RESTParameter) obj).getName();
        } else if (obj instanceof Class) {
            return ((Class) obj).getSimpleName();
        }
        return null;
    }

    @Override
    public List<RESTMethod> methods(List<RESTMethod> api) {
        return api;
    }

    @Override
    public List<RESTParameter> parameters(RESTMethod method) {
        return method.getParams();
    }

    @Override
    public PDIR direction(RESTParameter parameter) {
        if (parameter != null && parameter.getInOut() != null) {
            switch (parameter.getInOut()) {
                case IN:
                    return PDIR.in;
                case OUT:
                    return PDIR.out;
                case IN_OUT:
                    return PDIR.in_out;
            }
        }
        return null;
    }

    @Override
    public Class returnType(RESTMethod method) {
        return method != null ? method.getReturnType() : null;
    }

    @Override
    public Class type(RESTParameter p) {
        return p != null ? p.getType() : null;
    }
}
