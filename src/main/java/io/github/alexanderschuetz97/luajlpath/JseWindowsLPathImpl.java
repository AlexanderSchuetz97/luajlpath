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

import io.github.alexanderschuetz97.luajfshook.api.LuaPath;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import static org.luaj.vm2.LuaValue.*;

import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.util.LinkedList;

/**
 * Fallback for unsupported platforms. Implements all functions to the best of Javas abilities to do so.
 * Some functions are left unimplemented and fill return ERR_UNSUPPORTED.
 */
public class JseWindowsLPathImpl extends AbstractLPathImpl {

    @Override
    protected LuaValue info_getOS() {
        return WINDOWS;
    }

    @Override
    protected LuaValue info_getDevNull() {
        return DEV_NULL_WINDOWS;
    }

    @Override
    protected LuaValue info_getPathSeperator() {
        return SEMICOLON;
    }

    @Override
    protected boolean u_isPathCaseSensitive() {
        return false;
    }

    @Override
    protected byte u_getSeparator() {
        return '\\';
    }

    @Override
    protected Varargs lib_ansi(Varargs args) {

        //TODO encoding? Kinda pointless since all funcs want utf8 and linux is noop even in lpath.c
        if (args.narg() == 0) {
            return NONE;
        }
        int t = args.type(1);
        if (t == TINT || t == TNUMBER || t == TSTRING) {
            return args.arg(1);
        }

        return error("number/string expected, got " + args.arg(1).typename());
    }

    @Override
    protected Varargs lib_utf8(Varargs args) {
        //TODO encoding? Kinda pointless since all funcs want utf8 and linux is noop even in lpath.c
        if (args.narg() == 0) {
            return NONE;
        }
        int t = args.type(1);
        if (t == TINT || t == TNUMBER || t == TSTRING) {
            return args.arg(1);
        }

        return error("number/string expected, got " + args.arg(1).typename());
    }

    @Override
    protected Varargs lib_drive(Varargs args) {
        return u_drive(args);
    }


    @Override
    protected Varargs lib_fs_binpath(Varargs args) {
        String jhome = System.getProperty("java.home");
        if (jhome == null) {
            return JAVA;
        }

        //Probably a good guess
        return u_concat_path(u_varargsOf(jhome, "bin", "java.exe"));
    }

    @Override
    protected boolean u_isAbsolute(FastLuaString str) {
        if (str.len == 0) {
            return false;
        }

        byte b = str.bytes[str.off];
        if (u_isSeperator(b)) {
            return true;
        }

        if (str.len < 2) {
            return false;
        }

        return str.bytes[str.off+1] == ':' && ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z'));
    }

    protected boolean checkComponentLength(LuaPath path) {
        while(path != null) {
            if (path.name().length() > 255) {
                return true;
            }
            path = path.parent();
        }

        return false;
    }

    protected Varargs lib_fs_chdir(Varargs args) {
        LuaString path = u_concat_path(args).checkstring(1);
        String npath = path.checkjstring();
        LuaPath file = u_resolvePath(npath);


        if (checkComponentLength(file)) {
            return varargsOf(NIL, valueOf("chdir:" + npath + ":(errno=206): The filename or extension is too long."));
        }

        if (!file.exists()) {
            return varargsOf(NIL, valueOf("chdir:" + npath + ":(errno=2): No such file or directory"));
        }

        try {
            handler.setWorkDirectory(file.realPath());
        } catch (NotDirectoryException e) {
            return varargsOf(NIL, valueOf("chdir:" + npath + ":(errno=20): Not a directory"));
        } catch (IOException e) {
            return varargsOf(NIL, valueOf("chdir:" + npath + ":(errno=5): I/O error"));
        }

        return path;
    }

    protected Varargs lib_fs_makedirs(Varargs args) {
        FastLuaString string = u_concat_path(args);
        String jString = string.toString();
        LuaPath f = u_resolvePath(string.toString());
        if (f.exists()) {
            //This even returns if its a file...
            return string;
        }

        LuaPath parent = f.parent();

        while(parent != null) {
            if (parent.exists() && !parent.isDir()) {
                return u_err("makedirs:" + jString +":(errno=20): Not a directory");
            }

            parent = parent.parent();
        }



        try {
            f.mkdirs();
        } catch (IOException e) {
            //Makes no sense but theres a unit test for this case errno 206 would make more sense....
            if (checkComponentLength(f)) {
                return u_err("makedirs:" + jString +":(errno=2): No such file or directory");
            }
            return u_err("makedirs:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    protected Varargs lib_fs_mkdir(Varargs args) {
        FastLuaString string = u_concat_path(args);
        String jString = string.toString();
        LuaPath f = u_resolvePath(string.toString());
        if (f.exists()) {
            //This even returns if its a file...
            return string;
        }




        LuaPath parent = f.parent();

        if (parent == null || !parent.exists()) {
            return u_err("mkdir:" + jString +":(errno=2): No such file or directory");
        }

        if (!parent.isDir()) {
            return u_err("mkdir:" + jString +":(errno=20): Not a directory");
        }

        try {
            f.mkdir();
        } catch (IOException e) {
            //Makes no sense but theres a unit test for this case errno 206 would make more sense....
            if (checkComponentLength(f)) {
                return u_err("mkdir:" + jString +":(errno=2): No such file or directory");
            }
            return u_err("mkdir:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    @Override
    protected Varargs lib_parent(Varargs args) {
        LuaString path = u_concat_path(args).checkstring(1);
        boolean absolute = u_isAbsolute(path);
        FastLuaString anchor = new FastLuaString(absolute ? lib_anchor(path) : NONE);

        LinkedList<FastLuaString> strings = u_canonSplit(anchor, new FastLuaString(path), true, false);

        if (strings.isEmpty()) {
            if (absolute) {
                //Z: -> Z:..
                if (anchor.last() == ':') {
                   return anchor.cat(FastLuaString.DOUBLE_DOT);
                }
                return anchor;
            }

            return FastLuaString.DOUBLE_DOT;
        }

        //remove the last
        FastLuaString fs = strings.removeLast();
        if (fs.isDoubleDot()) {
            if (absolute) {
                return lib_anchor(path);
            }
            //We are already in the parent due to relative path beyond start of path
            strings.add(fs);
            strings.add(fs);
        }

        if (strings.isEmpty() && !absolute) {
            return FastLuaString.DOT;
        }

        FastLuaString res = u_join(anchor, strings);

        if (res.narg() == 0) {
            return res;
        }

        // z:a\b -> Z:a\b
        FastLuaString fsl = new FastLuaString(res);
        if (fsl.second() == ':' && u_canUpper(fsl.first())) {
            return u_toUpper(fsl, 0);
        }

        return fsl;
    }

    @Override
    protected boolean u_isSeperator(byte b) {
        return b == '/' || b == '\\';
    }

    @Override
    protected FastLuaString u_concat_path(Varargs args) {
        int n = args.narg();

        FastLuaString[] strings = new FastLuaString[n];
        //Size estimate
        int l = 0;
        int starti = 0;



        FastLuaString lastDrive = FastLuaString.EMPTY;
        for (int i = 0; i < n; i++) {
            FastLuaString s = new FastLuaString(args.checkstring(i+1));

            if (u_isAbsolute(s)) {
                FastLuaString wdrive = u_drive(s);
                FastLuaString wroot = u_root(s);
                if (wroot.len > 0 || !wdrive.equals(lastDrive)) {
                    starti = i;
                    l = 0;
                    lastDrive = wdrive;
                }
            }
            l+= s.len + 1; //Assume that adding one / is needed
            strings[i] = s;
        }

        LuaStringBuilder builder = new LuaStringBuilder(l);

        for (int x = starti; x < strings.length; x++) {
            FastLuaString str = strings[x];

            if (!builder.isEmpty()) {
                byte last = builder.getLast(0);
                if (!u_isSeperator(last) && last != ':') {
                    builder.append(separator);
                }
            }

            if (str.len == 0) {
                continue;
            }

            int i = 0;

            if (x > starti) {
                //Why?
                FastLuaString wdrive = u_drive(str);
                i+=wdrive.len;
            }

            boolean skipFS = false;
            for (; i < str.len; i++) {
                byte b = str.bytes[str.off+i];
                if (u_isSeperator(b)) {
                    b = separator;
                    if (skipFS) {
                        continue;
                    }

                    if (!builder.isEmpty() && u_isSeperator(builder.getLast(0))) {
                        if (builder.getPos() == 1) {
                            if (i + 1 >= str.len || !u_isSeperator(str.bytes[str.off + i + 1])) {
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



        FastLuaString result = builder.toFastString();
        FastLuaString drive = new FastLuaString(lib_drive(args));

        if (!result.startWith(drive)) {
            FastLuaString rdrive = new FastLuaString(lib_drive(result));
            return drive.cat(result.sub(rdrive.len));
        }

        return result;
    }

    protected FastLuaString u_drive(Varargs args) {
        LuaStringBuilder lsb = new LuaStringBuilder(0);
        for (int i = args.narg(); i >= 1; i--) {
            FastLuaString str = new FastLuaString(args.checkstring(i));
            if (str.len < 2) {
                continue;
            }

            boolean unicode = false;

            if (str.startsWithWindowsUnicodePrefix()) {
                str = str.sub(4);
                unicode = true;
            }

            // C:
            if (str.startsWithWindowsDrivePrefix()) {
                return unicode ? new FastLuaString(new byte[]{'\\','\\','?','\\', u_toUpper(str.first()), ':'}) :
                        new FastLuaString(new byte[]{u_toUpper(str.first()), ':'});
            }

            if (!unicode) {
                byte f = str.first();
                if ((f != '\\' && f != '/') || str.second() != f) {
                    continue;
                }

                str = str.sub(2);
            }


            FastLuaString address = null;

            if (unicode && str.len == 0) {
                return FastLuaString.WINDOWS_UNICODE_PREFIX;
            }

            lsb.checksize(str.len);
            int j = 0;
            for (; j < str.len; j++) {
                byte cur = str.bytes[str.off+j];
                if (cur == '\\' || cur == '/') {
                    FastLuaString ls = lsb.toFastString();

                    address = ls;
                    break;
                }

                lsb.append(cur);
            }

            if (address == null || address.len == 0) {
                return unicode ? FastLuaString.WINDOWS_UNICODE_PREFIX : FastLuaString.EMPTY;
            }

            j++;

            lsb.checksize(str.len);
            for (; j < str.len; j++) {
                byte cur = str.bytes[str.off+j];
                if (cur == '\\' || cur == '/') {
                    if (lsb.getPos() == 0) {
                        return unicode ? FastLuaString.WINDOWS_UNICODE_PREFIX : FastLuaString.EMPTY;
                    }
                    break;
                }

                lsb.append(cur);
            }

            FastLuaString mapping = lsb.toFastString();

            if (unicode) {
                if (mapping.len == 0) {
                    return FastLuaString.WINDOWS_UNICODE_PREFIX;
                }
                byte[] res = new byte[4+address.len+1+mapping.len];
                res[0] = '\\';
                res[1] = '\\';
                res[2] = '?';
                res[3] = '\\';
                res[4+address.len] = '\\';
                System.arraycopy(address.bytes, address.off, res, 4, address.len);
                System.arraycopy(mapping.bytes, mapping.off, res, 5+address.len, mapping.len);
                u_toUpper(res);
                return new FastLuaString(res);
            }

            byte[] res = new byte[2+address.len+1+mapping.len];
            res[0] = '\\';
            res[1] = '\\';
            res[2+address.len] = '\\';
            System.arraycopy(address.bytes, address.off, res, 2, address.len);
            System.arraycopy(mapping.bytes, mapping.off, res, 3+address.len, mapping.len);
            u_toUpper(res);
            return new FastLuaString(res);
        }

        return FastLuaString.EMPTY;
    }

    protected FastLuaString u_root(Varargs args) {
        for (int i = args.narg(); i >= 1; i--) {
            FastLuaString str = new FastLuaString(args.checkstring(i));
            if (str.len < 1) {
                continue;
            }

            if (str.startsWithWindowsUnicodePrefix()) {
                if (str.len == 4) {
                    return FastLuaString.EMPTY;
                }
                str = str.sub(4);
            }

            byte b1 = str.first();

            if (b1 == '\\') {
                return FastLuaString.BACK_SLASH;
            }

            if (b1 == '/') {
                return FastLuaString.BACK_SLASH;
            }

            if (str.len < 3) {
                continue;
            }


            // C:\ -> \
            // C:/ -> \
            byte b2 = str.bytes[str.off+1];
            byte b3 = str.bytes[str.off+2];
            if (u_isNormalChar(b1) && b2 == ':') {
                return (b3 == '\\' || b3 == '/') ? FastLuaString.BACK_SLASH : FastLuaString.EMPTY;
            }


        }

        return FastLuaString.EMPTY;
    }


    @Override
    protected Varargs lib_root(Varargs args) {
        return u_root(args);
    }

    @Override
    protected Varargs lib_anchor(Varargs args) {
        FastLuaString drive = u_drive(args);
        FastLuaString root = u_root(args);
        return drive.cat(root);
    }

    @Override
    protected Varargs lib_suffix(Varargs args) {
        FastLuaString name = u_getFileName(args);
        if (name.endsWithDot()) {
            return FastLuaString.EMPTY;
        }
        //>0 intenteded as . at start mean the file name is the extension!
        for (int i = name.len-1; i > 0; i--) {
            byte b = name.bytes[i+name.off];
            if (b == ' ') {
                //File extensions cant have whitespaces in windows...
                return FastLuaString.EMPTY;
            }
            if (b == '.') {
                return new FastLuaString(name.bytes, i+name.off, name.len-i);
            }
        }

        return FastLuaString.EMPTY;
    }

    @Override
    protected Varargs lib_suffixes(Varargs args) {
        FastLuaString name = u_getFileName(args);
        if (name.endsWithDot()) {
            return EMPTY_ITERATOR;
        }

        LinkedList<Varargs> suffixes = new LinkedList<>();
        int maxlen = name.len;
        //>0 intenteded as . at start mean the file name is the extension!
        for (int i = name.len-1; i > 0; i--) {
            if (name.bytes[i+name.off] == ' ') {
                break;
            }
            if (name.bytes[i+name.off] == '.') {
                suffixes.addFirst(new FastLuaString(name.bytes, i+name.off, maxlen-i));
                maxlen = i;
            }
        }

        return u_indexIterator(suffixes.iterator());
    }

    protected Varargs lib_stem(Varargs args) {
        FastLuaString name = u_getFileName(args);
        if (name.endsWithDot()) {
            return name;
        }
        for (int i = name.len-1; i > 0; i--) {
            if (name.bytes[i+name.off] == '.') {
                return new FastLuaString(name.bytes, name.off, i);
            }
        }

        return name;
    }
}
