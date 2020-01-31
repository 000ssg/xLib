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
package ssg.lib.http.rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * @author 000ssg
 */
public class WSDLMethodsProvider extends AnnotationsBasedMethodsProvider {

    Method miName;
    Method miPath;
    Method moName;
    Method mpName;

    public WSDLMethodsProvider() {
        super("javax.jws.WebService", "javax.jws.WebMethod", "javax.jws.WebParam");
        miName = getAnnotationPropertyReader(wiClass, "name");
        miPath = getAnnotationPropertyReader(wiClass, "serviceName");
        moName = getAnnotationPropertyReader(wmClass, "operationName");
        mpName = getAnnotationPropertyReader(wpClass, "name");
    }

    @Override
    public boolean isOperable() {
        return super.isOperable() && moName != null && mpName != null;
    }

    @Override
    public String getOperationName(Annotation annotation) {
        if (moName != null) {
            try {
                return (String) moName.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String getParameterName(Annotation annotation) {
        if (mpName != null) {
            try {
                return (String) mpName.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String getProviderName(Annotation annotation) {
        if (miName != null) {
            try {
                return (String) miName.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String[] getProviderPaths(Annotation annotation) {
        if (miPath != null) {
            try {
                return new String[]{(String) miPath.invoke(annotation)};
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String getProviderDescription(Annotation annotation) {
        return null;
    }

    @Override
    public String getOperationDescription(Annotation annotation) {
        return null;
    }

    @Override
    public String getParameterDescription(Annotation annotation) {
        return null;
    }

    @Override
    public RESTAccess evaluateRESTAccess(RESTAccess access, Annotation annotation) {
        return null;
    }

}
