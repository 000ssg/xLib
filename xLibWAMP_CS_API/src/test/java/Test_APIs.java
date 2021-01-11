
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
/**
 *
 * @author 000ssg
 */
public class Test_APIs {

    /**
     * GUID: allows simple id distinction of items throughout APIs
     */
    private static AtomicInteger NEXT_ID = new AtomicInteger(1);

    public static interface Proto extends Serializable, Cloneable {
    }
    
    public static enum TestEnum {
        one,
        two,
        three
    }

    /**
     * API1 controls items A and B (as A children)
     */
    public static class API1 implements Proto {

        List<A> list = Collections.synchronizedList(new ArrayList<>());

        public List<A> find(String nameA, String nameB) {
            List<A> r = new ArrayList<>();

            for (A a : list) {
                if (nameA == null || a.getName().contains(nameA)) {
                    if (nameB == null) {
                        r.add(a);
                    } else {
                        if (a.getItems().isEmpty()) {
                            continue;
                        }
                        for (B b : a.getItems()) {
                            if (b.getName().contains(nameB)) {
                                r.add(a);
                                break;
                            }
                        }
                    }
                }
            }

            return r;
        }

        public A findA(int id) {
            for (A a : list) {
                if (a.id == id) {
                    return a;
                }
            }
            return null;
        }

        public B findB(int id) {
            for (A a : list) {
                if (a != null && a.items != null && a.items.isEmpty()) {
                    for (B b : a.items) {
                        if (b.id == id) {
                            return b;
                        }
                    }
                }
            }
            return null;
        }

        public int createA(String name, String... bNames) throws TestError1 {
            if (name != null) {
                A a = new A();
                a.setName(name);
                if (bNames != null) {
                    for (String n : bNames) {
                        if (n != null) {
                            B b = new B();
                            b.setName(n);
                            a.getItems().add(b);
                        }
                    }
                }
                list.add(a);
                return a.getId();
            }
            return 0;
        }

        public int createB(String nameA, String nameB) throws TestError1 {
            List<A> list = find(nameA, null);
            if (list != null && !list.isEmpty()) {
                B b = new B();
                b.setName(nameB);
                for (A a : list) {
                    if (a != null) {
                        a.getItems().add(b);
                    }
                }
                return b.getId();
            }
            return 0;
        }

        @Override
        public String toString() {
            return "API1{" + "list=" + list + '}';
        }

        public class A implements Proto {

            private int id = NEXT_ID.getAndIncrement();
            private String name;
            private String description;
            private List<B> items = Collections.synchronizedList(new ArrayList<>());
            private TestEnum rating=TestEnum.two;

            @Override
            public int hashCode() {
                return getId();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final A other = (A) obj;
                if (this.getId() != other.getId()) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "A{" + "id=" + getId() + ", name=" + getName() + ", description=" + getDescription() + ", items=" + getItems() + '}';
            }

            /**
             * @return the id
             */
            public int getId() {
                return id;
            }

            /**
             * @param id the id to set
             */
            public void setId(int id) {
                this.id = id;
            }

            /**
             * @return the name
             */
            public String getName() {
                return name;
            }

            /**
             * @param name the name to set
             */
            public void setName(String name) {
                this.name = name;
            }

            /**
             * @return the description
             */
            public String getDescription() {
                return description;
            }

            /**
             * @param description the description to set
             */
            public void setDescription(String description) {
                this.description = description;
            }

            /**
             * @return the items
             */
            public List<B> getItems() {
                return items;
            }

            /**
             * @param items the items to set
             */
            public void setItems(List<B> items) {
                this.items = items;
            }

            /**
             * @return the rating
             */
            public TestEnum getRating() {
                return rating;
            }

            /**
             * @param rating the rating to set
             */
            public void setRating(TestEnum rating) {
                this.rating = rating;
            }
        }

        public class B implements Proto {

            private int id = NEXT_ID.getAndIncrement();
            private String name;
            private float amount;

            @Override
            public int hashCode() {
                return getId();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final B other = (B) obj;
                if (this.getId() != other.getId()) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "B{" + "id=" + getId() + ", name=" + getName() + ", amount=" + getAmount() + '}';
            }

            /**
             * @return the id
             */
            public int getId() {
                return id;
            }

            /**
             * @param id the id to set
             */
            public void setId(int id) {
                this.id = id;
            }

            /**
             * @return the name
             */
            public String getName() {
                return name;
            }

            /**
             * @param name the name to set
             */
            public void setName(String name) {
                this.name = name;
            }

            /**
             * @return the amount
             */
            public float getAmount() {
                return amount;
            }

            /**
             * @param amount the amount to set
             */
            public void setAmount(float amount) {
                this.amount = amount;
            }

        }
    }

    /**
     * API1 controls items C and D (as D children)
     */
    public static class API2 implements Proto {

        List<C> list = Collections.synchronizedList(new ArrayList<>());

        public List<C> find(String nameA, String nameB) {
            List<C> r = new ArrayList<>();

            for (C a : list) {
                if (nameA == null || a.name.contains(nameA)) {
                    if (nameB == null) {
                        r.add(a);
                    } else {
                        if (a.items.isEmpty()) {
                            continue;
                        }
                        for (D b : a.items) {
                            if (b.name.contains(nameB)) {
                                r.add(a);
                                break;
                            }
                        }
                    }
                }
            }

            return r;
        }

        public int createC(String name, String... dNames) throws TestError2 {
            if (name != null) {
                C a = new C();
                a.name = name;
                if (dNames != null) {
                    for (String n : dNames) {
                        if (n != null) {
                            D b = new D();
                            b.name = n;
                            a.items.add(b);
                        }
                    }
                }
                list.add(a);
                return a.id;
            }
            return 0;
        }

        public int createD(String nameC, String nameD) throws TestError2 {
            List<C> list = find(nameC, null);
            if (list != null && !list.isEmpty()) {
                D b = new D();
                b.name = nameD;
                for (C a : list) {
                    if (a != null) {
                        a.items.add(b);
                    }
                }
                return b.id;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "API2{" + "list=" + list + '}';
        }

        public C findC(int id) {
            for (C a : list) {
                if (a.id == id) {
                    return a;
                }
            }
            return null;
        }

        public D findD(int id) {
            for (C a : list) {
                if (a != null && a.items != null && a.items.isEmpty()) {
                    for (D b : a.items) {
                        if (b.id == id) {
                            return b;
                        }
                    }
                }
            }
            return null;
        }

        public class C implements Proto {

            public final int id = NEXT_ID.getAndIncrement();
            public String name;
            public String description;
            List<D> items = Collections.synchronizedList(new ArrayList<>());

            @Override
            public int hashCode() {
                return id;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final C other = (C) obj;
                if (this.id != other.id) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "C{" + "id=" + id + ", name=" + name + ", description=" + description + ", items=" + items + '}';
            }
        }

        public class D implements Proto {

            public final int id = NEXT_ID.getAndIncrement();
            public String name;
            public float amount;

            @Override
            public int hashCode() {
                return id;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final D other = (D) obj;
                if (this.id != other.id) {
                    return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return "D{" + "id=" + id + ", name=" + name + ", amount=" + amount + '}';
            }

        }
    }

    public static class TestError1 extends Exception {

        public TestError1() {
        }

        public TestError1(String message) {
            super(message);
        }

        public TestError1(String message, Throwable cause) {
            super(message, cause);
        }

        public TestError1(Throwable cause) {
            super(cause);
        }
        
    }
    public static class TestError2 extends Exception {

        public TestError2() {
        }

        public TestError2(String message) {
            super(message);
        }

        public TestError2(String message, Throwable cause) {
            super(message, cause);
        }

        public TestError2(Throwable cause) {
            super(cause);
        }
        
    }
//    public static void main(String... args) throws Exception {
//        API1 api1 = new API1();
//        api1.createA("aaa", "b1", "b2", "b3");
//        List list = api1.find("aaa", null);
//        
//        list.add(WAMPTools.createDict("a", 1.0));
//
//        JSON.Encoder encoder = new JSON.Encoder(new ReflImpl()){
//            @Override
//            public void put(Object root) {
//                System.out.println("encoder.put: "+root);
//                super.put(root);
//            }
//        };
//        encoder.put(list);
//
//        CharBuffer cb = CharBuffer.allocate(1000);
//        int c = 0;
//        while ((c = encoder.read(cb)) > 0);
//
//        cb.flip();
//        System.out.println(BufferTools.toText(cb));
//    }
}
