/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.Stub.StubContext;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author 000ssg
 */
public class StubRESTHttpContext extends StubContext<RESTHttpDataProcessor, Entry<HttpMatcher, RESTMethod>, RESTParameter, Class> {

    public StubRESTHttpContext(String baseURL, String namespace, boolean generateExtendedComments) {
        super(baseURL, namespace, generateExtendedComments);
    }

    @Override
    public String nameOf(Object obj) {
        if (obj instanceof Entry) {
            return ((Entry<HttpMatcher, RESTMethod>) obj).getValue().getName();
        } else if (obj instanceof RESTParameter) {
            return ((RESTParameter) obj).getName();
        } else if (obj instanceof Class) {
            return ((Class) obj).getSimpleName();
        }
        return null;
    }

    @Override
    public String pathOf(Entry<HttpMatcher, RESTMethod> method) {
        return method.getKey().getPath();
    }

    @Override
    public List<Entry<HttpMatcher, RESTMethod>> methods(RESTHttpDataProcessor api) {
        List<Entry<HttpMatcher, RESTMethod>> ms = new ArrayList<>();
        for (Entry<HttpMatcher, RESTMethod[]> me : ((Map<HttpMatcher, RESTMethod[]>) api.methods).entrySet()) {
            final HttpMatcher p = me.getKey();
            for (final RESTMethod m : me.getValue()) {
                ms.add(new Entry() {
                    @Override
                    public Object getKey() {
                        return p;
                    }

                    @Override
                    public Object getValue() {
                        return m;
                    }

                    @Override
                    public Object setValue(Object value) {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                });
            }
        }
        return ms;
    }

    @Override
    public boolean isAllowedParameter(RESTParameter p) {
        if (HttpRequest.class.isAssignableFrom(p.getType()) || HttpUser.class.isAssignableFrom(p.getType())) {
            return false;
        } else {
            return super.isAllowedParameter(p);
        }
    }

    @Override
    public List<RESTParameter> parameters(Entry<HttpMatcher, RESTMethod> method) {
        return method.getValue().getParams();
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
    public Class returnType(Entry<HttpMatcher, RESTMethod> method) {
        return method != null ? method.getValue().getReturnType() : null;
    }

    @Override
    public Class type(RESTParameter p) {
        return p != null ? p.getType() : null;
    }
}
