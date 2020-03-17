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

/**
 *
 * @author 000ssg
 */
public class Replacement implements Cloneable {

    public static enum MATCH {
        none, partial, exact;

        public MATCH toHighest(MATCH m) {
            if (order(m) > order(this)) {
                return m;
            } else {
                return this;
            }
        }

        public int order() {
            return order(this);
        }

        public static int order(MATCH m) {
            if (m != null) {
                switch (m) {
                    case none:
                        return 0;
                    case partial:
                        return 1;
                    case exact:
                        return 2;
                }
            }
            return -1;
        }
    }

    private byte[] src;
    private byte[] trg;
    int pos;
    MATCH state = MATCH.none;
    private boolean autoReset = true;

    public Replacement(Object src, Object trg) {
        if (src instanceof byte[]) {
            this.src = (byte[]) src;
        } else if (src instanceof String) {
            this.src = ((String) src).getBytes();
        }
        if (trg instanceof byte[]) {
            this.trg = (byte[]) trg;
        } else if (trg instanceof String) {
            this.trg = ((String) trg).getBytes();
        }
        pos = 0;
    }

    public MATCH next(byte b) {
        MATCH r = MATCH.none;

        // reset if last was exact match nad no reset yet...
        if (autoReset && state == MATCH.exact) {
            state = MATCH.none;
            pos = 0;
        }

        if (getSrc()[pos] == b) {
            pos++;
            if (getSrc().length == pos) {
                r = MATCH.exact;
            } else {
                r = MATCH.partial;
            }
        } else {
            if (pos > 0) {
                // check if shorter match is possible...
                for (int j = pos; j > 0; j--) {
                    if (getSrc()[j - 1] == b) {
                        // check if matching to start...
                        boolean found = true;
                        for (int k = j - 1; k > 0; k--) {
                            if (getSrc()[k] != getSrc()[k - 1]) {
                                found = false;
                                break;
                            }
                        }
                        if (found) {
                            pos = j;
                            r = MATCH.partial;
                            break;
                        }
                    }
                }
            }
        }
        state = r;
        return r;
    }

    public int getMatchLength() {
        return pos;
    }

    public void reset() {
        pos = 0;
        state = MATCH.none;
    }

    public MATCH getMatchState() {
        return state;
    }

    @Override
    public String toString() {
        return "Replacement{"
                + "\n  pos=" + pos
                + ", state=" + state
                + "\n  src[" + getSrc().length + "]=" + new String(getSrc())
                + ((pos > 0) ? "\n  mtc[" + pos + "]=" + new String(getSrc(), 0, pos) : "")
                + "\n  trg[" + getTrg().length + "]=" + new String(getTrg())
                + "\n}";
    }

    /**
     * @return the autoReset
     */
    public boolean isAutoReset() {
        return autoReset;
    }

    /**
     * @param autoReset the autoReset to set
     */
    public void setAutoReset(boolean autoReset) {
        this.autoReset = autoReset;
    }

    public Replacement copy() {
        try {
            Replacement r = (Replacement) clone();
            return r;
        } catch (Throwable th) {
            return null;
        }
    }

    public Replacement reverseCopy() {
        try {
            Replacement r = (Replacement) clone();
            byte[] tmp = r.src;
            r.src = r.trg;
            r.trg = tmp;
            r.reset();
            return r;
        } catch (Throwable th) {
            return null;
        }
    }

    /**
     * @return the src
     */
    public byte[] getSrc() {
        return src;
    }

    /**
     * @return the trg
     */
    public byte[] getTrg() {
        return trg;
    }

    public int[] getSizes() {
        return new int[]{
            getSrc() != null ? getSrc().length : -1,
            getTrg() != null ? getTrg().length : -1
        };
    }

    public static class Replacements extends Replacement {

        // potentials
        Replacement[] replacements;
        MATCH[] states;
        // last exact match
        Replacement last;
        int lastML = 0;

        public Replacements(Replacement... replacements) {
            super(null, null);
            this.replacements = replacements;
            if (replacements != null) {
                states = new MATCH[replacements.length];
            } else {
                states = new MATCH[0];
            }
        }

        @Override
        public Replacement copy() {
            Replacements copy = (Replacements) super.copy();
            if (replacements != null) {
                for (int i = 0; i < replacements.length; i++) {
                    copy.replacements[i] = replacements[i].copy();
                }
            }
            return copy;
        }

        @Override
        public Replacement reverseCopy() {
            Replacements copy = (Replacements) super.reverseCopy();
            if (replacements != null) {
                for (int i = 0; i < replacements.length; i++) {
                    copy.replacements[i] = replacements[i].reverseCopy();
                }
            }
            return copy;
        }

        @Override
        public MATCH getMatchState() {
            MATCH r = MATCH.none;
            if (replacements == null || replacements.length == 0) {
                return r;
            }

            for (Replacement repl : replacements) {
                r = r.toHighest(repl.getMatchState());
            }

            return r;
        }

        @Override
        public void reset() {
            super.reset();
            if (replacements != null) {
                for (Replacement repl : replacements) {
                    repl.reset();
                }
                for (int i = 0; i < states.length; i++) {
                    states[i] = MATCH.none;
                }
                last = null;
                lastML = 0;
            }
        }

        @Override
        public int getMatchLength() {
            return lastML;
        }

        @Override
        public MATCH next(byte b) {
            MATCH r = MATCH.none;
            if (replacements == null || replacements.length == 0) {
                return r;
            }

            Replacement oldLast = last;
            int ml = 0;

            for (int i = 0; i < replacements.length; i++) {
                Replacement repl = replacements[i];
                MATCH ri = repl.next(b);
                ml = Math.max(ml, repl.getMatchLength());
                if (ri != states[i]) {
                    // changed -> update last if needed
                    if (MATCH.exact == ri) {
                        if (oldLast != null) {
                            // detect if duplicate last -> error or ignore?
                            int a = 0;
                        }
                        last = repl;
                    } else if (oldLast == repl && last == repl) {
                        repl.reset();
                        last = null;
                    } else if (MATCH.none == ri && ri != states[i]) {
                        repl.reset();
                    }
                }
                states[i] = ri;
                r = r.toHighest(ri);
            }

            lastML = ml;
            return r;
        }

        @Override
        public byte[] getTrg() {
            return (last != null) ? last.getTrg() : null;
        }

        @Override
        public byte[] getSrc() {
            return (last != null) ? last.getSrc() : null;
        }

        @Override
        public int[] getSizes() {
            int[] r = new int[]{-1, -1};
            if (last != null) {
                return last.getSizes();
            }
            if (replacements == null || replacements.length == 0) {
                for (int i = 0; i < replacements.length; i++) {
                    int[] ri = (replacements[i] != null) ? replacements[i].getSizes() : null;
                    if (ri != null) {
                        for (int j = 0; j < r.length; j++) {
                            r[j] = Math.max(r[j], ri[j]);
                        }
                    }
                }
            }
            return r;
        }

        @Override
        public String toString() {
            return toString(false);
        }

        public String toString(boolean compact) {
            StringBuilder sb = new StringBuilder();
            sb.append("Replacements{");
            if (replacements != null) {
                for (int i = 0; i < replacements.length; i++) {
                    sb.append("\n  ");
                    sb.append(replacements[i] == last ? '*' : '.');
                    sb.append(("" + replacements[i]).replace("\n", (compact) ? "\\n" : "\n  "));
                }
            }
            sb.append("\n  states=");
            for (int i = 0; i < states.length; i++) {
                sb.append(' ');
                sb.append(states[i]);
            }
            sb.append("\n  match length=" + lastML);
            sb.append("\n  last=" + ("" + last).replace("\n", "\n  "));
            sb.append('}');
            return sb.toString();
        }

    }
}
