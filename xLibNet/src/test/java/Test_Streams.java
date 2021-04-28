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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Test streams usage (simple) and check performance overhead for trivial
 * case(s)
 *
 * @author 000ssg
 */
public class Test_Streams {

    static AtomicInteger nextGroupId = new AtomicInteger(1);
    static AtomicInteger nextLocId = new AtomicInteger(1);
    static AtomicInteger nextItemId = new AtomicInteger(1);

    public static void main(String... args) throws Exception {
        // prepare data set
        List<Loc> locs = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            locs.add(new Loc());
        }
        for (int i = 0; i < 5; i++) {
            groups.add(new Group());
        }

        for (Loc l : locs) {
            for (Group g : groups) {
                items.add(new Item(l, g));
            }
        }

        // test stream operations
        Map<Integer, Integer> i2g = items.stream().collect(Collectors.toMap(Item::getId, Item::getGroupId));
        System.out.println(i2g);
        Map<ItemInGroup, Item> it2g = items.stream().collect(Collectors.toMap(Test_Streams::toItemInGroup, Test_Streams::toItem));
        System.out.println(it2g);

        // test stream operation performance
        // stream map
        long s01 = System.nanoTime();
        Map<ItemInGroup, Item> it2g2 = items.stream().collect(Collectors.toMap(item -> {
            return new ItemInGroup(item);
        }, item -> {
            return item;
        }));
        long s02 = System.nanoTime();

        // direct map
        Map<ItemInGroup, Item> it2g2_ = new HashMap<>();
        for (Item i : items) {
            it2g2_.put(new ItemInGroup(i), i);
        }
        long s03 = System.nanoTime();

        // stream map
        Map<ItemInGroup, Item> it2g2__ = items.stream().collect(Collectors.toMap(item -> {
            return new ItemInGroup(item);
        }, item -> {
            return item;
        }));
        long s04 = System.nanoTime();

        // visualize and verify results identity
        System.out.println(it2g2);
        System.out.println(it2g2_);

        for (ItemInGroup ig : it2g.keySet()) {
            if (!it2g2.containsKey(ig)) {
                System.out.println("missing " + ig);
            }
        }

        // finally look on performance test results
        System.out.println("Streamed map: " + (s02 - s01) / 1000000f + ", direct map: " + (s03 - s02) / 1000000f + ", and streamed again: " + (s04 - s03) / 1000000f);

    }

    public static ItemInGroup toItemInGroup(Item item) {
        return new ItemInGroup(item);
    }

    public static Item toItem(Item item) {
        return item;
    }

    public static class Group {

        public int id = nextGroupId.getAndIncrement();
    }

    public static class Loc {

        public int id = nextLocId.getAndIncrement();
    }

    public static class Item {

        private int id = nextItemId.getAndIncrement();
        private int locId;
        private int groupId;

        public Item(Loc loc, Group group) {
            if (loc != null) {
                locId = loc.id;
            }
            if (group != null) {
                groupId = group.id;
            }
        }

        /**
         * @return the id
         */
        public int getId() {
            return id;
        }

        /**
         * @return the locId
         */
        public int getLocId() {
            return locId;
        }

        /**
         * @return the groupId
         */
        public int getGroupId() {
            return groupId;
        }
    }

    public static class ItemInLoc {

        public int loc;
        public int item;

        @Override
        public String toString() {
            return "ItemInLoc{" + "loc=" + loc + ", item=" + item + '}';
        }
    }

    public static class ItemInGroup {

        public int group;
        public int item;

        public ItemInGroup(Item item) {
            this.group = item.getGroupId();
            this.item = item.getId();
        }

        @Override
        public String toString() {
            return "ItemInGroup{" + "group=" + group + ", item=" + item + '}';
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 47 * hash + this.group;
            hash = 47 * hash + this.item;
            return hash;
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
            final ItemInGroup other = (ItemInGroup) obj;
            if (this.group != other.group) {
                return false;
            }
            if (this.item != other.item) {
                return false;
            }
            return true;
        }
    }
}
