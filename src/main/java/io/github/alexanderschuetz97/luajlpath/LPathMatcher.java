//
// Copyright Alexander Schütz, 2022
//
// This file is part of luajlpath.
//
// luajlpath is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// luajlpath is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// A copy of the GNU Lesser General Public License should be provided
// in the COPYING & COPYING.LESSER files in top level directory of luajlpath.
// If not, see <https://www.gnu.org/licenses/>.
//
package io.github.alexanderschuetz97.luajlpath;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * This is used by fnmatch and glob functions.
 *
 * Even tho it is called glob the pattern/rules are not quite the same as what one would normally expect a "glob" pattern to be.
 * This implementation is inline with the unit tests of lpath.c and manual tests.
 *
 * One could implement the actual "glob" pattern/rules but that would break compatibility with lpath.c
 */
public class LPathMatcher {

    private static final RootToken NO = new LPathMatcher.RootToken();
    static {
        NO.token = new NoToken();
    }

    public static AbstractLPathImpl.LPathPattern compile(byte[] token, int tokenOffset, int tokenLength, boolean glob, boolean caseSensitive) {
        if (tokenLength == 0) {
            return NO;
        }

        GlobToken current = new DummyToken();
        GlobToken root = current;
        int tkStart = 0;
        for (int i = 0; i < tokenLength; i++) {
            byte tk = token[i+tokenOffset];
            if (tk == '[') {
                int nidx = nextIndexOf(token, tokenOffset, tokenLength, i, (byte) ']');
                if (nidx != -1 && nidx-i > 1) {
                    if (i-tkStart > 0) {
                        current.next = new LiteralGlobToken(token, tkStart + tokenOffset, i - tkStart);
                        current = current.next;
                    }
                    current.next = new PatternGlobToken(token, tokenOffset+i+1, (nidx-i)-1);
                    current = current.next;
                    tkStart = nidx+1;
                    i = nidx;
                }
                continue;
            }




            if (tk == '?') {
                if (i-tkStart > 0) {
                    current.next = new LiteralGlobToken(token, tkStart + tokenOffset, i - tkStart);
                    current = current.next;
                }
                current.next = new AnyCharGlobToken();
                current = current.next;
                tkStart = i+1;
                continue;
            }

            if (tk == '*') {
                if (i-tkStart > 0) {
                    current.next = new LiteralGlobToken(token, tkStart + tokenOffset, i - tkStart);
                    current = current.next;
                }

                boolean isStrong = false;
                if (glob && (i == 0 || isSeperator(caseSensitive, token[i+tokenOffset-1])) && (i+1 >= tokenLength || (i+2 < tokenLength && token[i+tokenOffset+1] == '*' && isSeperator(caseSensitive, token[i+tokenOffset+2])))) {
                    isStrong = true;
                }

                current.next = new WildcardGlobToken(glob, isStrong);
                current = current.next;
                tkStart = i+1;
                if (isStrong) {
                    tkStart+=2;
                    i+=2;
                }
                continue;
            }
        }

        if (tkStart < tokenLength) {
            current.next = new LiteralGlobToken(token, tokenOffset+tkStart, tokenLength-tkStart);
        }

        RootToken lp = new RootToken();
        lp.token = root;
        lp.caseSensitive = caseSensitive;
        return lp;
    }

    protected static boolean proceed(MatcherState state, GlobToken token) {
        while(token != null && !state.done) {
            if (!token.match(state)) {
                return false;
            }

            if (state.fail) {
                return false;
            }

            token = token.next;
        }

        return state.idx == state.len && !state.fail;
    }

    protected static boolean isSeperator(boolean caseSensitive, byte b1) {
        if (caseSensitive) {
            return b1 == File.separatorChar;
        }

        return b1 == '/' || b1 == '\\';
    }

    protected static boolean isCharEqual(MatcherState state, byte b1, byte b2) {
        if (state.caseSensitive) {
            return b1 == b2;
        }

        if (toUpper(b1) == toUpper(b2)) {
            return true;
        }

        if (b1 == '/') {
            if (b2 == '\\') {
                return true;
            }
            return true;
        }

        if (b1 == '\\') {
            return b2 == '/';
        }

        return false;
    }

    protected static class MatcherState {
        //basically the is posix flag
        protected final boolean caseSensitive;
        protected final byte[] string;
        protected final int off;
        protected final int len;
        protected int idx;
        protected WildcardGlobToken cwk;
        private boolean done = false;
        private boolean fail = false;

        protected MatcherState(byte[] string, int off, int len, boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            this.string = string;
            this.off = off;
            this.len = len;
        }
    }

    public static class RootToken implements AbstractLPathImpl.LPathPattern {
        public int wildcardCounter;
        boolean caseSensitive;
        GlobToken token;

        @Override
        public boolean match(byte[] string, int stringOffset, int stringLen) {
            if (stringLen == 0 || token == null) {
                return true;
            }
            MatcherState state = new MatcherState(string, stringOffset, stringLen, caseSensitive);
            return proceed(state, token);
        }
    }

    protected static abstract class GlobToken {
        GlobToken next;
        abstract boolean match(MatcherState state);

        @Override
        public String toString() {
            if (next == null) {
                return  getClass().getSimpleName();
            }

            return getClass().getSimpleName() + " -> " + next;
        }
    }

    protected static class NoToken extends GlobToken {

        @Override
        boolean match(MatcherState state) {
            return false;
        }
    }

    protected static class DummyToken extends GlobToken {

        @Override
        boolean match(MatcherState state) {
            return true;
        }
    }

    protected static class LiteralGlobToken extends GlobToken {

        private final byte[] token;
        private final int off;
        private final int len;

        LiteralGlobToken(byte[] token, int off, int len) {
            this.token = token;
            this.off = off;
            this.len = len;
        }

        @Override
        public boolean match(MatcherState state) {
            if (state.len -state.idx < len) {
                return false;
            }

            for (int i = 0; i < len; i++) {
                byte b1 = token[off+i];
                byte b2 = state.string[i+state.idx+state.off];
                if (!isCharEqual(state, b1, b2)) {
                    return false;
                }

            }

           state.idx+=len;

            return true;
        }

        @Override
        public String toString() {
            String self = "LiteralGlobToken(" + new String(token, off, len, StandardCharsets.UTF_8) + ")";
            if (next == null) {
                return self;
            }

            return self + " -> " + next;
        }
    }

    protected static class WildcardGlobToken extends GlobToken {

        private final boolean glob;
        private final boolean isDouble;

        WildcardGlobToken(boolean glob, boolean isdouble) {
            this.glob = glob;
            this.isDouble = isdouble;
        }

        @Override
        boolean match(MatcherState state) {
            if (state.len - state.idx < 1) {
                return true;
            }

            if (state.string[state.idx] == '*') {
                state.idx++;
                return true;
            }

            WildcardGlobToken prev = state.cwk;

            int bidx = state.idx;
            while(true) {
                int len = state.len - state.idx;
                if (len > 0 ) {
                    byte tk = state.string[state.idx];
                    if (tk == '*') {
                        if (prev != null) {
                            state.fail = true;
                        }
                        return false;
                    }

                    if (glob && !isDouble && isSeperator(state.caseSensitive, tk)) {
                        state.idx++;
                        state.cwk = prev;
                        //Match next after / * looses the power only in glob mode tho... fnmatch doesnt care about this
                        return true;
                    }

                }

                state.cwk = this;
                if (proceed(state, next)) {
                    state.done = true;
                    return true;
                }

                if (len < 1) {
                    if (prev != null) {
                        state.fail = true;
                    }

                    return false;
                }

                if (state.fail) {
                    if (prev != null) {
                        state.fail = true;
                    }
                    return false;
                }

                bidx++;
                state.idx = bidx;
            }
        }
    }

    protected static class AnyCharGlobToken extends GlobToken {

        @Override
        public boolean match(MatcherState state) {
            if (state.len -state.idx < 1) {
                return false;
            }

           state.idx++;

            return true;
        }
    }

    protected static class PatternGlobToken extends GlobToken {

        final boolean[] accepted = new boolean[0xff];

        /**
         * This is bonkers and makes absolutely no sense
         */
        PatternGlobToken(byte[] token, int off, int len) {
            boolean negate = false;
            int pv = -1;
            for (int i = 0; i < len; i++) {
                int tk = token[off+i] & 0xff;
                if (tk == '!' && i == 0) {
                    negate = true;
                    continue;
                }

                //Basically whenever a sequence of X-Y is encoutered any 2 chars in the ascii table after X will be accepted, Y is ignored but must be any char.
                //This wraps around so [\255-Y] will pass \000 & \001 & \255. This makes absolutely no sense and has nothing to do with how glob is supposed ot work.
                if (tk == '-' && pv != -1 && i+1 < len) {
                    //We skip Y
                    //X is stored in pv variable
                    i++;
                    accepted[(pv+1) & 0xff] = true;
                    accepted[(pv+2) & 0xff] = true;
                    pv = -1;
                    continue;
                }

                accepted[tk] = true;
                pv = tk;
            }

            if (negate) {
                for (int i = 0; i < accepted.length; i++) {
                    accepted[i] = !accepted[i];
                }
            }
        }

        @Override
        public boolean match(MatcherState state) {
            if (state.len -state.idx < 1) {
                return false;
            }

            if (!accepted[state.string[state.idx] & 0xff]) {
                return false;
            }


           state.idx++;
            return true;
        }
    }

    public static int nextIndexOf(byte[] b, int off, int len, int idx, byte search) {
        for (int i = idx; i < len; i++) {
            if (b[off+i] == search) {
                return i;
            }
        }

        return -1;
    }

    public static byte toUpper(byte c) {
        if (c >= 'a' && c <= 'z') {
            c -= 32;
        }

        return c;
    }

}
