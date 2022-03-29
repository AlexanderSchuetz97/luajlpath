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

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.luaj.vm2.LuaValue.NONE;

/**
 * Fast Transfer object for LuaString that does not do hash computations or does string pooling.
 * byte[] and offsets must not be modified after creation.
 */
public class FastLuaString extends Varargs {

    public static final FastLuaString EMPTY = new FastLuaString(LuaValue.EMPTYSTRING);
    public static final FastLuaString DOT = new FastLuaString(LuaString.valueOf("."));
    public static final FastLuaString BACK_SLASH = new FastLuaString(LuaString.valueOf("\\"));
    public static final FastLuaString DOUBLE_DOT = new FastLuaString(LuaString.valueOf(".."));
    public static final FastLuaString WINDOWS_UNICODE_PREFIX = new FastLuaString(LuaString.valueOf("\\\\?\\"));



    public final byte[] bytes;
    public final int off;
    public final int len;

    //LAZY
    private LuaString str;

    public FastLuaString(byte[] bytes, int off, int len) {
        this.bytes = bytes;
        this.off = off;
        this.len = len;
    }

    public FastLuaString(LuaString str) {
        this(str.m_bytes, str.m_offset, str.m_length);
        this.str = str;
    }

    public FastLuaString(String string) {
        byte[] b = string.getBytes(StandardCharsets.UTF_8);
        this.bytes = b;
        this.off = 0;
        this.len = b.length;
    }


    public FastLuaString(FastLuaString fastString) {
        this.bytes = fastString.bytes;
        this.off = fastString.off;
        this.len = fastString.len;
        this.str = fastString.str;
    }

    public FastLuaString(Varargs varargs) {
        if (varargs instanceof FastLuaString) {
            FastLuaString fs = (FastLuaString) varargs;
            this.bytes = fs.bytes;
            this.off = fs.off;
            this.len = fs.len;
            this.str = fs.str;
            return;
        }
        if (varargs.narg() == 0) {
            this.bytes = EMPTY.bytes;
            this.off = 0;
            this.len = 0;
            this.str = LuaValue.EMPTYSTRING;
            return;
        }

        LuaString str = varargs.checkstring(1);
        this.bytes = str.m_bytes;
        this.off = str.m_offset;
        this.len = str.m_length;
        this.str = str;
    }

    public FastLuaString(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public LuaString toLuaString() {
        if (str != null) {
            return str;
        }
        str = LuaString.valueUsing(bytes, off, len);
        return str;
    }

    public byte first() {
        if (len == 0) {
            return 0;
        }
        return bytes[off];
    }

    public byte last() {
        if (len == 0) {
            return 0;
        }

        return bytes[off+len-1];
    }

    public byte last(int idx) {
        if (len <= idx) {
            return 0;
        }

        return bytes[off+len-(idx+1)];
    }

    public byte second() {
        if (len < 2) {
            return 0;
        }
        return bytes[off+1];
    }

    public boolean isDot() {
        return len == 1 && bytes[off] == '.';
    }

    public boolean isDoubleDot() {
        return len == 2 && bytes[off] == '.' && bytes[off+1] == '.';
    }

    public boolean isDoubleStar() {
        return len == 2 && bytes[off] == '*' && bytes[off+1] == '*';
    }

    public boolean startsWithDoubleSep() {
        return len > 1 && bytes[off] == File.separatorChar && bytes[off+1] == File.separatorChar;
    }

    public boolean startsWithWindowsUnicodePrefix() {
        if (len <= 3) {
            return false;
        }

        byte b1 = bytes[off];
        return (b1 == '\\' || b1 == '/') && bytes[off+1] == b1 && bytes[off+2] == '?' && bytes[off+3] == b1;
    }



    public boolean startsWithWindowsDrivePrefix() {
        if (len < 2) {
            return false;
        }

        byte b = bytes[off];
        return bytes[off+1] == ':' && ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z'));
    }

    public boolean endsWithDots() {
        return len > 1 && bytes[off+len-1] == '.' && bytes[off+len-2] == '.';
    }

    public boolean endsWithDot() {
        return len > 0 && bytes[off+len-1] == '.';
    }

    public FastLuaString addSep() {
        byte[] copy = new byte[len+1];
        System.arraycopy(bytes, off, copy, 0, len);
        copy[len] = (byte) File.separatorChar;
        return new FastLuaString(copy);
    }

    public FastLuaString sub(int start) {
        if (start <= 0) {
            return this;
        }

        if (start >= len) {
            return EMPTY;
        }

        return new FastLuaString(bytes, off+start, len-start);
    }

    //Pseudo Varargs impl that just delegates to toLuaString(). I could extend LuaValue but im already somewhat afraid of
    //handing down this object as this will not be instanceof LuaString in external lib code.

    @Override
    public LuaValue arg(int i) {
        return i == 1 ? toLuaString() : LuaValue.NIL;
    }

    @Override
    public int narg() {
        return 1;
    }

    @Override
    public LuaValue arg1() {
        return toLuaString();
    }


    //This is not cached, don't call it multiple times.
    @Override
    public String toString() {
        return toLuaString().checkjstring();
    }

    @Override
    public Varargs subargs(int start) {
        if (start == 1) {
            return this;
        }

        if (start > 1) {
            return NONE;
        }

        return LuaValue.argerror(1, "start must be > 0");
    }


    public FastLuaString cat(FastLuaString other) {
        if (len == 0) {
            return other;
        }

        if (other.len == 0) {
            return this;
        }

        byte[] cp = new byte[len+ other.len];
        System.arraycopy(bytes, off, cp, 0, len);
        System.arraycopy(other.bytes, other.off, cp, len, other.len);
        return new FastLuaString(cp);
    }

    public boolean startWith(FastLuaString other) {
        if (other.len > len) {
            return false;
        }

        for (int i = 0; i < other.len; i++) {
            if (bytes[off+i] != other.bytes[other.off+i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object obj) {
        FastLuaString other;
        if (obj instanceof FastLuaString) {
            other = (FastLuaString) obj;
        } else if (obj instanceof LuaString) {
            other = new FastLuaString((LuaString)obj);
        } else if (obj instanceof Varargs) {
            other = new FastLuaString((Varargs) obj);
        } else if (obj instanceof String) {
            other = new FastLuaString(obj.toString());
        } else {
            return false;
        }

        if (other.len != len) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (bytes[off+i] != other.bytes[other.off+i]) {
                return false;
            }
        }

        return true;
    }
}
