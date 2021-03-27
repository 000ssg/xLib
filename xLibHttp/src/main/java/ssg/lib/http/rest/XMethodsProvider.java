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

import ssg.lib.http.RAT;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class XMethodsProvider extends AnnotationsBasedMethodsProvider {

    static String packageName = XMethodsProvider.class.getPackage().getName() + ".annotations.";

    Method miName;
    Method miDescription;
    Method miPaths;
    Method moName;
    Method moPaths;
    Method moDescription;
    Method mpName;
    Method mpDescription;
    Method mpOptional;

    // access...
    Method miActions;
    Method miRoles;
    Method miTags;
    Method moRoles;
    Method moTags;
    Method mmRoles;
    Method mmTags;

    public XMethodsProvider() {
        // com.ssg.xcs.xpd.http.rest.annotations
        super(
                packageName + "XType",
                packageName + "XMethod",
                packageName + "XParameter",
                packageName + "XAccess",
                packageName + "XOption"
        );
        miName = getAnnotationPropertyReader(wiClass, "name");
        miDescription = getAnnotationPropertyReader(wiClass, "description");
        miPaths = getAnnotationPropertyReader(wiClass, "paths");
        moName = getAnnotationPropertyReader(wmClass, "name");
        moDescription = getAnnotationPropertyReader(wmClass, "description");
        moPaths = getAnnotationPropertyReader(wmClass, "paths");
        mpName = getAnnotationPropertyReader(wpClass, "name");
        mpDescription = getAnnotationPropertyReader(wpClass, "description");
        mpOptional = getAnnotationPropertyReader(wpClass, "optional");

        miActions = getAnnotationPropertyReader(waClass, "actions");
        miRoles = getAnnotationPropertyReader(waClass, "roles");
        miTags = getAnnotationPropertyReader(waClass, "tags");
        moRoles = getAnnotationPropertyReader(woClass, "roles");
        moTags = getAnnotationPropertyReader(woClass, "tags");
        mmRoles = getAnnotationPropertyReader(wmClass, "roles");
        mmTags = getAnnotationPropertyReader(wmClass, "tags");
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
    public String[] getOperationPaths(Annotation annotation) {
        if (moPaths != null) {
            try {
                String[] ss = (String[]) moPaths.invoke(annotation);
                if (ss == null || ss.length == 0) {
                    ss = new String[]{getOperationName(annotation)};
                }
                if (ss != null && ss.length == 1 && (ss[0] == null || ss[0].isEmpty())) {
                    ss = null;
                }
                return ss;
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return super.getOperationPaths(annotation);
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
    public boolean isOptionalParameter(Annotation annotation) {
        if (mpOptional != null) {
            try {
                return (Boolean) mpOptional.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return false;
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
    public String getProviderDescription(Annotation annotation) {
        if (miDescription != null) {
            try {
                return (String) miDescription.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String[] getProviderPaths(Annotation annotation) {
        if (miPaths != null) {
            try {
                Object o = (Object) miPaths.invoke(annotation);
                if (o instanceof String[]) {
                    return (String[]) o;
                } else if (o instanceof String) {
                    return new String[]{(String) o};
                }
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String getOperationDescription(Annotation annotation) {
        if (moDescription != null) {
            try {
                return (String) moDescription.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public String getParameterDescription(Annotation annotation) {
        if (mpDescription != null) {
            try {
                return (String) mpDescription.invoke(annotation);
            } catch (IllegalAccessException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        return null;
    }

    @Override
    public RESTAccess evaluateRESTAccess(RESTAccess access, Annotation annotation) {
        String[] actions = null;
        String[] itags = null;
        String[] iroles = null;
        String[] otags = null;
        String[] oroles = null;
        String[] mtags = null;
        String[] mroles = null;
        boolean hasI = false;
        boolean hasM = false;
        boolean hasO = false;

        if (miActions != null) {
            try {
                actions = (String[]) miActions.invoke(annotation);
                if (actions != null && actions.length == 0) {
                    actions = null;
                }
                if (actions != null) {
                    hasI = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (miRoles != null) {
            try {
                iroles = (String[]) miRoles.invoke(annotation);
                if (iroles != null && iroles.length == 0) {
                    iroles = null;
                }
                if (iroles != null) {
                    hasI = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (miTags != null) {
            try {
                itags = (String[]) miTags.invoke(annotation);
                if (itags != null && itags.length == 0) {
                    itags = null;
                }
                if (itags != null) {
                    hasI = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (moRoles != null) {
            try {
                oroles = (String[]) moRoles.invoke(annotation);
                if (oroles != null && oroles.length == 0) {
                    oroles = null;
                }
                if (oroles != null) {
                    hasO = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (moTags != null) {
            try {
                otags = (String[]) moTags.invoke(annotation);
                if (otags != null && otags.length == 0) {
                    otags = null;
                }
                if (otags != null) {
                    hasO = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (mmRoles != null) {
            try {
                mroles = (String[]) mmRoles.invoke(annotation);
                if (mroles != null && mroles.length == 0) {
                    mroles = null;
                }
                if (mroles != null) {
                    hasM = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }
        if (mmTags != null) {
            try {
                mtags = (String[]) mmTags.invoke(annotation);
                if (mtags != null && mtags.length == 0) {
                    mtags = null;
                }
                if (mtags != null) {
                    hasM = true;
                }
            } catch (IllegalAccessException iaex) {
            } catch (IllegalArgumentException iaex) {
            } catch (InvocationTargetException itex) {
            }
        }

        if (hasI || hasO || hasM) {
            if (access == null) {
                access = new RESTAccess();
            }
            if (hasI) {
                RAT rad = access.getInstance();
                if (rad == null) {
                    rad = new RAT();
                    access.setInstance(rad);
                }
                if (actions != null) {
                    List<String> l = rad.getActions();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setActions(l);
                    }
                    for (String s : actions) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
                if (iroles != null) {
                    List<String> l = rad.getRoles();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setRoles(l);
                    }
                    for (String s : iroles) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
                if (itags != null) {
                    List<String> l = rad.getTags();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setTags(l);
                    }
                    for (String s : itags) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
            }
            if (hasO) {
                RAT rad = access.getMethod();
                if (rad == null) {
                    rad = new RAT();
                    access.setMethod(rad);
                }
                if (oroles != null) {
                    List<String> l = rad.getRoles();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setRoles(l);
                    }
                    for (String s : oroles) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
                if (otags != null) {
                    List<String> l = rad.getTags();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setTags(l);
                    }
                    for (String s : otags) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
            }
            if (hasM) {
                RAT rad = access.getMethod();
                if (rad == null) {
                    rad = new RAT();
                    access.setMethod(rad);
                }
                if (mroles != null) {
                    List<String> l = rad.getRoles();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setRoles(l);
                    }
                    for (String s : mroles) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
                if (mtags != null) {
                    List<String> l = rad.getTags();
                    if (l == null) {
                        l = new ArrayList<>();
                        rad.setTags(l);
                    }
                    for (String s : mtags) {
                        if (!l.contains(s)) {
                            l.add(s);
                        }
                    }
                }
            }
        }

        return access;
    }

}
