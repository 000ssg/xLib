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
public class SpringBootWebMethodsProvider extends AnnotationsBasedMethodsProvider {

    Class wppClass;
    Class wbpClass;

    Method miName;
    Method moName;
    Method moPath;
    Method moValue;
    Method mpName;
    Method mpValue;
    Method mpRequired;
    Method mppName;
    Method mppValue;
    Method mppRequired;
    Method mbpRequired;

    public SpringBootWebMethodsProvider() {
        super(
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.RequestParam"
        );

        wppClass = getAnnotationForClassName("org.springframework.web.bind.annotation.PathVariable");
        wbpClass = getAnnotationForClassName("org.springframework.web.bind.annotation.RequestBody");

        miName = getAnnotationPropertyReader(wiClass, "value");
        moName = getAnnotationPropertyReader(wmClass, "name");
        moPath = getAnnotationPropertyReader(wmClass, "path");
        moValue = getAnnotationPropertyReader(wmClass, "value");
        mpName = getAnnotationPropertyReader(wpClass, "name");
        mpValue = getAnnotationPropertyReader(wpClass, "value");
        mpRequired = getAnnotationPropertyReader(wpClass, "required");
        mppName = getAnnotationPropertyReader(wppClass, "name");
        mppValue = getAnnotationPropertyReader(wppClass, "value");
        mppRequired = getAnnotationPropertyReader(wppClass, "required");
        mbpRequired = getAnnotationPropertyReader(wbpClass, "required");
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
            } catch (Throwable th) {
            }
        }
        return null;
    }

    @Override
    public String[] getOperationPaths(Annotation annotation) {
        String[] r = null;
        if (moPath != null) {
            try {
                r = (String[]) moPath.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (r == null && r.length > 0 && moValue != null) {
            try {
                r = (String[]) moValue.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        return r;
    }

    @Override
    public String getParameterName(Annotation annotation) {
        String s = null;
        if (mpName != null) {
            try {
                s = (String) mpName.invoke(annotation);
                if (s != null && s.isEmpty()) {
                    s = null;
                }
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (s == null && mpValue != null) {
            try {
                s = (String) mpValue.invoke(annotation);
                if (s != null && s.isEmpty()) {
                    s = null;
                }
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (s == null && mppName != null) {
            try {
                s = (String) mppName.invoke(annotation);
                if (s != null && s.isEmpty()) {
                    s = null;
                }

            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (s == null && mppValue != null) {
            try {
                s = (String) mppValue.invoke(annotation);
                if (s != null && s.isEmpty()) {
                    s = null;
                }
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (s == null && wbpClass != null && wbpClass.isAssignableFrom(annotation.getClass())) {
            s = BODY_AS_PARAMETER;
        }
        return s;
    }

    @Override
    public String getProviderName(Annotation annotation) {
        if (miName != null) {
            try {
                return (String) miName.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        return null;
    }

    @Override
    public boolean isOptionalParameter(Annotation annotation) {
        Boolean b = null;
        if (mpRequired != null) {
            try {
                b = !((Boolean) mpRequired.invoke(annotation));
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (b == null && mppRequired != null) {
            try {
                b = !((Boolean) mppRequired.invoke(annotation));
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        if (b == null && mbpRequired != null) {
            try {
                b = !((Boolean) mbpRequired.invoke(annotation));
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            } catch (Throwable th) {
            }
        }
        return (b != null) ? b : false;
    }

    @Override
    public String[] getProviderPaths(Annotation annotation) {
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
