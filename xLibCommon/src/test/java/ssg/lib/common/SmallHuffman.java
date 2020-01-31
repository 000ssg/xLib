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
package ssg.lib.common;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author sesidoro
 */
public class SmallHuffman extends Huffman {

    boolean DEBUG = false;
    Map<Integer, int[]> dict = new LinkedHashMap<Integer, int[]>() {
        {
            put((int) '0', new int[]{(int) '0', 3, 0});
            put((int) '1', new int[]{(int) '1', 3, 1});
            put((int) '2', new int[]{(int) '2', 3, 2});
            put((int) '3', new int[]{(int) '3', 3, 3});
            put((int) '4', new int[]{(int) '4', 3, 4});
            put((int) '5', new int[]{(int) '5', 3, 5});
            put((int) '6', new int[]{(int) '6', 3, 6});
            put((int) '7', new int[]{(int) '7', 3, 7});
            put((int) '8', new int[]{(int) '8', 3, 8});
            put((int) '9', new int[]{(int) '9', 3, 9});
            put((int) 256, new int[]{(int) 256, 6, 0xFFFFFFFF});
        }
    };
    HTree root = buildTree(null, null, dict, new HashSet<Integer>());

    @Override
    public Map<Integer, int[]> getDictionary() {
        return dict;
    }

    @Override
    public HTree getRoot() {
        return root;
    }

}
