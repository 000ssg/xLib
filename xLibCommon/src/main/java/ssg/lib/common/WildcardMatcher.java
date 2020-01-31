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

import java.util.Arrays;

/**
 * Supports '*' and '?' wildcard characters.
 */
public class WildcardMatcher implements Matcher<String> {

    boolean allowSingleMask = true;
    boolean withLeading = false;
    boolean withTrailing = false;
    char[][] wc;
    float weight = 1;
    boolean DEBUG = false;

    public WildcardMatcher(String wildcard) {
        init(wildcard);
    }

    public WildcardMatcher(String wildcard, boolean allowSingleMask) {
        this.allowSingleMask = allowSingleMask;
        init(wildcard);
    }

    public void init(String wildcard) {
        if (wildcard == null) {
            wc = new char[0][0];
        } else {
            String[] wcs = wildcard.split("\\*");
            if (wcs.length > 0 && wcs[0].isEmpty()) {
                wcs = Arrays.copyOfRange(wcs, 1, wcs.length);
            }
            wc = new char[wcs.length][];
            for (int i = 0; i < wc.length; i++) {
                wc[i] = wcs[i].toCharArray();
            }
            withLeading = wildcard.startsWith("*");
            withTrailing = wc.length > 0 && wildcard.endsWith("*");
        }
    }

    @Override
    public float match(String t) {
        if (wc.length == 0 && withLeading) {
            return 1;
        }
        int pos = 0;
        int ppos = 0;
        for (char ch : t.toCharArray()) {
            // pos is -1 if end is reached
            if (pos == -1) {
                if (withTrailing) {
                    return 1;
                } else {
                    return 0;
                }
            }
            boolean m = wc[pos][ppos] == ch || allowSingleMask && wc[pos][ppos] == '?';
            if (DEBUG) {
                System.out.println("     '" + ch + "' " + ((m) ? "==" : "!=") + " '" + wc[pos][ppos] + "' [" + pos + "][" + ppos + "]");
            }
            if (!m) {
                if (pos == 0 && withLeading || pos > 0 && ppos == 0) {
                    continue;
                } else {
                    return 0;
                }
            }
            ppos++;
            if (ppos == wc[pos].length) {
                ppos = 0;
                pos++;
                if (pos == wc.length) {
                    pos = -1;
                }
            }
        }
        return (pos == -1 || pos > 0 || ppos > 0) ? 1 : 0;
    }

    @Override
    public float weight() {
        return weight;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("weight=" + weight + ", wc=" + (((withLeading) ? 1 : 0) + ((withTrailing) ? 1 : 0) + wc.length) + ": ");
        if (withLeading) {
            sb.append(" *");
        }
        for (char[] ch : wc) {
            sb.append("|" + new String(ch));
        }
        if (withTrailing) {
            sb.append("|*");
        }
        sb.append('}');
        return sb.toString();
    }

}
