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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import ssg.lib.api.API;
import ssg.lib.api.APICallable;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIGroup;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.T1Engine;
import ssg.lib.api.util.Reflective_API_Builder.API_Reflective;
import ssg.lib.api.T1Engine.T1;
import ssg.lib.api.util.APISearchable.APIMatcher;
import ssg.lib.api.util.APISearchable.APIMatcher.API_MATCH;
import ssg.lib.api.util.Reflective_API_Builder.Reflective_API_Context;

/**
 *
 * @author 000ssg
 */
public class Reflective_API_BuilderTest {

    public Reflective_API_BuilderTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public static T1Engine getTestItem() {
        return T1Engine.create(new String[][]{
            {"A", "B", "C"},
            {"A", "B", "CC"},
            {"A", "BB", "CC"}
        });
    }

    public static <T> T call(API api, String name, Object instance, Map<String, Object> params, boolean dump) {
        Object r = null;

        Collection<APIProcedure> procss = api.find((item) -> {
            return APIMatcher.matchAPIProcedure(item, name);
        }, APIProcedure.class, null);
        List<APIProcedure> procs = new ArrayList<>();
        procs.addAll(procss);
        float[] match = new float[procs.size()];
        int best = 0;
        for (int i = 0; i < match.length; i++) {
            match[i] = procs.get(i).testParameters(params);
        }
        // best from +
        for (int i = 0; i < match.length; i++) {
            if (match[i] > match[best]) {
                best = i;
            }
        }
        for (int i = 0; i < match.length; i++) {
            if (Math.abs(match[i]) > match[best]) {
                best = i;
            }
        }

        APICallable caller = api.createCallable(procs.get(best), instance);

        try {
            r = caller.call(params);
            if (dump) {
                System.out.println("R(" + params + "): " + r);
            }
        } catch (APIException aex) {
            aex.printStackTrace();
        }

        return (T) r;
    }
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Test of buildAPI method, of class Reflective_API_Builder.
     */
    @Test
    public void testBuildAPI() {
        System.out.println("buildAPI");
        String name = "demo";
        Reflective_API_Builder.ReflectiveFilter filter = null;
        T1Engine demo = getTestItem();
        API result = Reflective_API_Builder.buildAPI(name, new Reflective_API_Context(filter), demo.getClass());
        System.out.println("API: " + result);
        System.out.println("API (fqn): " + result.toFQNString());
        assertEquals(name, result.name);
        //
        Collection<APIProcedure> procs = result.find((item) -> {
            return APIMatcher.matchAPIProcedure(item, name+".T1Engine.first");
        }, APIProcedure.class, null);
        APICallable caller = result.createCallable(procs.iterator().next(), demo);

        try {
            Object r = caller.call(new HashMap() {
                {
                    put("a", "A");
                }
            });
            System.out.println("R: " + r);
        } catch (APIException aex) {
            aex.printStackTrace();
        }

        System.out.println("Call first('A'): " + call(result, name+".T1Engine.first", demo, new HashMap() {
            {
                put("a", "A");
            }
        }, false));

        System.out.println("Call find(,'B'): " + call(result, name+".T1Engine.find", demo, new HashMap() {
            {
                put("b", "B");
            }
        }, false));
    }

    /**
     * Test of buildGroup method, of class Reflective_API_Builder.
     */
    @Test
    public void testBuildGroup() {
        System.out.println("buildGroup");
        API api = new API_Reflective("demo");
        APIGroup group = new APIGroup("test",api.getScopeForChild());
        T1Engine demo = getTestItem();
        Class type = demo.getClass();
        Reflective_API_Builder.ReflectiveFilter filter = null;
        Reflective_API_Builder.buildGroup(api, group, type, new Reflective_API_Context(filter));
        System.out.println("API: " + api);
        System.out.println("Group: " + group);
    }

    /**
     * Test of buildType method, of class Reflective_API_Builder.
     */
    @Test
    public void testBuildType() {
        System.out.println("buildType");
        API api = new API_Reflective("demo");
        T1Engine demo = getTestItem();
        T1Engine[] demos = new T1Engine[]{demo};
        Class type = demo.getClass();
        Reflective_API_Builder.ReflectiveFilter filter = null;
        APIDataType result = Reflective_API_Builder.buildType(api, type, new Reflective_API_Context(filter));
        System.out.println("API 1: " + api);
        result = Reflective_API_Builder.buildType(api, demos.getClass(), new Reflective_API_Context(filter));
        System.out.println("API 2: " + api);
    }

    /**
     * Test of buildMethod method, of class Reflective_API_Builder.
     */
    @Test
    public void testBuildMethod() throws Exception {
        System.out.println("buildMethod");
        API api = new API_Reflective("demo");
        Class type = T1.class;
        Method m = type.getMethod("random");
        Method m2 = type.getMethod("random", int.class);
        Reflective_API_Builder.ReflectiveFilter filter = null;
        APIProcedure result = Reflective_API_Builder.buildMethod(api, null, type, m, new Reflective_API_Context(filter));
        APIProcedure result2 = Reflective_API_Builder.buildMethod(api, null, type, m2, new Reflective_API_Context(filter));
        System.out.println("API: " + api);
        System.out.println("APIProcedure 1: " + result);
        System.out.println("APIProcedure 2: " + result2);
        // notice: globally registered functions do not have type prefix!
        System.out.println("Call random(): " + call(api, "random", result, null, false));
        System.out.println("Call random(3): " + call(api, "random", result, new HashMap() {
            {
                put("count", 3);
            }
        }, false));
    }

    /**
     * Test of buildMethod method, of class Reflective_API_Builder.
     */
    @Test
    public void test_API_Publishers() throws Exception {
        System.out.println("test API_Publishers");
        API api = Reflective_API_Builder.buildAPI("demo2", new Reflective_API_Context(null), T1.class, T1Engine.class);
        System.out.println("API: " + api);
        System.out.println("Call random(): " + call(api, "demo2.T1.random", null, null, false));
        System.out.println("Call random(3): " + call(api, "demo2.T1.random", null, new HashMap() {
            {
                put("count", 3);
            }
        }, false));

        API_Publishers pubs = new API_Publishers().add(null, api);
        System.out.println("Published apis: " + pubs.getAPINames());
        for (String pubn : pubs.getAPINames()) {
            API_Publisher pub = pubs.getAPIPublisher(pubn);
            Collection<APIProcedure> procs = pub.getAPI().find((item) -> {
                return item instanceof APIProcedure ? API_MATCH.exact : API_MATCH.partial;
            }, APIProcedure.class, null);
            System.out.println("  APIProcedures[" + procs.size() + "]");
            for (APIProcedure proc : procs) {
                System.out.println("    " + proc.fqn());
            }
            Collection<APIDataType> types = pub.getAPI().find((item) -> {
                return item instanceof APIDataType ? API_MATCH.exact : API_MATCH.partial;
            }, APIDataType.class, null);
            System.out.println("  Types[" + types.size() + "]");
            for (APIDataType type : types) {
                System.out.println("    " + type.fqn());
            }

            APICallable callable = pub.getCallable("random", null);
            System.out.println("Callable[null]: " + callable);
            callable = pub.getCallable("random", null);
            System.out.println("Callable[[3]]: " + callable);
            callable = pub.getCallable("random", null);
            System.out.println("Callable[[3]]: " + callable);
            callable = pub.getCallable("random", null);
            System.out.println("Callable[count=3]: " + callable);

            System.out.println("Try calls:");
            for (Object params : new Object[]{
                3,
                null,
                new Object[]{3},
                3,
                Collections.singletonList(3),
                Collections.singletonMap("count", 3),
                Collections.singletonMap("count", "sdf"),
                Collections.singletonMap("count", Math.PI)
            }) {
                callable = null;
                Map map = null;
                List list = null;
                Object[] arr = null;
                if (params == null) {
                    callable = pub.getCallable("T1.random", null);
                } else if (params instanceof Map) {
                    callable = pub.getCallable("T1.random", null);
                    map = (Map) params;
                } else if (params instanceof List) {
                    callable = pub.getCallable("T1.random", null);
                    list = (List) params;
                } else if (params.getClass().isArray() && params.getClass().getComponentType().isPrimitive()) {
                    callable = pub.getCallable("T1.random", null);
                    arr = new Object[]{params};
                } else if (params.getClass().isArray() && !params.getClass().getComponentType().isPrimitive()) {
                    callable = pub.getCallable("T1.random", null);
                    arr = (Object[]) params;
                } else if (!params.getClass().isArray()) {
                    callable = pub.getCallable("T1.random", null);
                    arr = new Object[]{params};
                }

                System.out.println("Params: " + params);

                Object result = null;
                if (callable != null) {
                    try {
                        if (map != null) {
                            result = callable.call(map);
                        } else if (list != null) {
                            result = callable.call(callable.toParametersMap(list));
                        } else if (arr != null) {
                            result = callable.call(callable.toParametersMap(arr));
                        } else {
                            result = callable.call(null);
                        }
                        System.out.println("Callable: " + callable + " -> " + result);
                    } catch (Throwable th) {
                        System.out.println("Callable: " + callable + " -> " + th);
                    }
                }
            }

        }

    }

}
