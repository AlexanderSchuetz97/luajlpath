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

import java.nio.charset.StandardCharsets;

/**
 * Utility to build a lua string (aka utf-8 byte array)
 */
public class LuaStringBuilder {

    private static final byte[] EMPTY = new byte[0];

    private byte[] buffer;
    private int pos;

    public LuaStringBuilder(int size) {
        buffer = new byte[size];
    }

    public void checksize(int bsize) {
        pos = 0;
        if (buffer.length < bsize) {
            buffer = new byte[bsize];
        }
    }

    private void ensureSize(int size) {
        if (buffer.length >= size) {
            return;
        }

        //TODO smarter/faster incrementer?
        byte[] b = new byte[size];
        System.arraycopy(buffer, 0, b, 0, pos);
        buffer = b;
    }

    public void append(byte[] b, int off, int len) {
        ensureSize(pos+len);
        System.arraycopy(b, off, buffer, pos, len);
        pos+=len;
    }

    public void append(String string) {
        byte[] b = string.getBytes(StandardCharsets.UTF_8);
        append(b);
    }

    public void append(byte[] b) {
        append(b, 0, b.length);
    }

    public void append(byte b) {
        ensureSize(pos+1);
        buffer[pos] = b;
        pos++;
    }

    public byte get(int pos) {
        if (this.pos <= pos) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return buffer[pos];
    }

    public byte getLast(int pos) {

        if (pos < 0) {
            throw new IllegalArgumentException();
        }

        return buffer[this.pos-pos-1];
    }

    public boolean isEmpty() {
        return pos == 0;
    }

    public void setPos(int x) {
        if (x > pos) {
            return;
        }
        pos = x;

    }

    public LuaString toLuaString() {
        if (pos == 0) {
            return LuaValue.EMPTYSTRING;
        }

        LuaString luaString = LuaString.valueUsing(buffer, 0, pos);
        pos = 0;
        buffer = EMPTY;
        return luaString;
    }

    public FastLuaString toFastString() {
        if (pos == 0) {
            return FastLuaString.EMPTY;
        }

        FastLuaString fastString = new FastLuaString(buffer, 0, pos);
        pos = 0;
        buffer = EMPTY;
        return fastString;
    }

    public FastLuaString copyFastString() {
        if (pos == 0) {
            return FastLuaString.EMPTY;
        }

        byte[] b = new byte[pos];
        System.arraycopy(buffer, 0, b, 0, pos);
        pos = 0;
        return new FastLuaString(b);
    }

    @Override
    public String toString() {
        return new String(buffer, 0, pos, StandardCharsets.UTF_8);
    }

    public void reset() {
        pos = 0;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getPos() {
        return pos;
    }
}
