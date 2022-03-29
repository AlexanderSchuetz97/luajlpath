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

import static org.luaj.vm2.LuaValue.EMPTYSTRING;

public class JsePosixLPathImpl extends AbstractLPathImpl {

    @Override
    protected LuaValue info_getOS() {
        return POSIX;
    }

    @Override
    protected LuaValue info_getDevNull() {
        return DEV_NULL;
    }

    @Override
    protected LuaValue info_getPathSeperator() {
        return COLON;
    }

    @Override
    protected boolean u_isPathCaseSensitive() {
        return true;
    }

    @Override
    protected Varargs lib_drive(Varargs args) {
        return EMPTYSTRING;
    }

    @Override
    protected Varargs lib_fs_binpath(Varargs args) {
        String jhome = System.getProperty("java.home");
        if (jhome == null) {
            return JAVA;
        }

        //Probably a good guess
        return u_concat_path(u_varargsOf(jhome, "bin", "java"));
    }

    protected boolean u_isAbsolute(FastLuaString str) {
        if (str.len == 0) {
            return false;
        }

        if (str.bytes[str.off] == separator) {
            return true;
        }

        return false;
    }

    @Override
    protected byte u_getSeparator() {
        return '/';
    }

    @Override
    protected boolean u_isSeperator(byte b) {
        return b == '/';
    }

    @Override
    protected FastLuaString u_concat_path(Varargs args) {
        int n = args.narg();

        LuaString[] strings = new LuaString[n];
        //Size estimate
        int l = 0;
        int starti = 0;



        for (int i = 0; i < n; i++) {
            LuaString s = args.checkstring(i+1);

            if (u_isAbsolute(s)) {
                starti = i;
                l = 0;
            }
            l+= s.m_length + 1; //Assume that adding one / is needed
            strings[i] = s;
        }

        LuaStringBuilder builder = new LuaStringBuilder(l);

        for (int x = starti; x < strings.length; x++) {
            LuaString str = strings[x];
            if (!builder.isEmpty() && !u_isSeperator(builder.getLast(0))) {
                builder.append(separator);
            }

            if (str.m_length == 0) {
                continue;
            }

            boolean skipFS = false;
            for (int i = 0; i < str.m_length; i++) {
                byte b = str.m_bytes[str.m_offset+i];
                if (u_isSeperator(b)) {
                    b = separator;
                    if (skipFS) {
                        continue;
                    }

                    if (!builder.isEmpty() && u_isSeperator(builder.getLast(0))) {
                        if (builder.getPos() == 1) {
                            if (i + 1 >= str.m_length || !u_isSeperator(str.m_bytes[str.m_offset + i + 1])) {
                                builder.append(b);
                                continue;
                            }
                            skipFS = true;
                        }
                        continue;
                    }
                }

                skipFS = false;
                builder.append(b);
            }
        }

        if (builder.getPos() == 0) {
            return FastLuaString.DOT;
        }



        return builder.toFastString();
    }

    protected Varargs root_internal(Varargs args) {
        for (int i = args.narg(); i >= 1; i--) {
            LuaString str = args.checkstring(i);
            if (str.m_length == 0) {
                continue;
            }

            if (str.m_bytes[str.m_offset] == '/') {
                if (str.m_length == 1) {
                    return SLASH;
                }

                if (str.m_bytes[str.m_offset+1] == '/') {
                    if (str.m_length == 2) {
                        return DOUBLE_SLASH;
                    }

                    if (str.m_bytes[str.m_offset+2] == '/') {
                        return SLASH;
                    }

                    return DOUBLE_SLASH;
                }

                return SLASH;
            }
        }

        return EMPTYSTRING;
    }

    @Override
    protected Varargs lib_root(Varargs args) {
        return root_internal(args);
    }

    @Override
    protected Varargs lib_anchor(Varargs args) {
        return lib_root(args);
    }
}
