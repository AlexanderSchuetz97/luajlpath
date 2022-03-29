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

import io.github.alexanderschuetz97.luajfshook.api.LuaFileSystemHandler;
import io.github.alexanderschuetz97.luajfshook.api.LuaPath;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.luaj.vm2.LuaValue.*;
import static org.luaj.vm2.LuaValue.varargsOf;

/**
 * Abstract base class that implements all features that can be implemented independent of platform.
 */
public abstract class AbstractLPathImpl {

    protected final byte separator = u_getSeparator();
    protected final char separatorChar = (char) u_getSeparator();
    protected final LuaString seperatorString = valueOf(String.valueOf(separatorChar));

    protected static final LuaString DEV_NULL = valueOf("/dev/null");
    protected static final LuaString DEV_NULL_WINDOWS = valueOf("null");
    protected static final LuaString SEMICOLON = valueOf(";");
    protected static final LuaString COLON = valueOf(":");

    protected static final LuaString LINUX = valueOf("linux");
    protected static final LuaString SLASH = valueOf("/");
    protected static final LuaString DOUBLE_SLASH = valueOf("//");
    protected static final LuaString DOUBLE_BACK_SLASH = valueOf("\\\\");
    protected static final LuaString BACK_SLASH = valueOf("\\");

    protected static final LuaString POSIX = valueOf("posix");
    protected static final LuaString WINDOWS = valueOf("windows");
    protected static final LuaString JAVA = valueOf("java");
    protected static final LuaString DOUBLE_STAR = valueOf("**");

    protected static final LuaFunction EMPTY_ITERATOR = new ZeroArgFunction() {
        @Override
        public LuaValue call() {
            return NIL;
        }
    };

    protected static final Varargs ERR_BINPATH_22 = u_err("binpath:(errno=22): Invalid argument");
    protected static final Varargs ERR_BINPATH_36 = u_err("binpath:(errno=36): File name too long");
    protected static final Varargs ERR_BINPATH_40 = u_err("binpath:(errno=40): Too many symbolic links encountered");
    protected static final Varargs ERR_BINPATH_13 = u_err("binpath:(errno=13): Permission denied");
    protected static final Varargs ERR_BINPATH_2 = u_err("binpath:(errno=2): No such file or directory");
    protected static final Varargs ERR_BINPATH_20 = u_err("binpath:(errno=20): Not a directory");
    protected static final Varargs ERR_BINPATH_5 = u_err("binpath:(errno=5): I/O error");
    protected static final Varargs ERR_GETENV_22 = u_err("getenv:(errno=22): Invalid argument");
    protected static final Varargs ERR_UNSETENV_22 = u_err("unsetenv:(errno=22): Invalid argument");
    protected static final Varargs ERR_SETENV_22 = u_err("setenv:(errno=22): Invalid argument");
    protected static final Varargs ERR_UNSUPPORTED = u_err("not supported");

    protected final Random rng = new Random();

    protected static final Set<FileVisitOption> OPTIONS = Collections.emptySet();

    protected LuaFileSystemHandler handler;

    protected void init(Globals globals, LuaFileSystemHandler handler) {
        this.handler = handler;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Lib methods exposed to lua via VarargFunction binding
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    protected Varargs lib_fs_chdir(Varargs args) {
        LuaString path = u_concat_path(args).checkstring(1);
        String npath = path.checkjstring();
        LuaPath file = u_resolvePath(npath);

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

    protected Varargs lib_ansi(Varargs args) {
        return args.arg1();
    }

    protected Varargs lib_utf8(Varargs args) {
        return args.arg1();
    }

    protected Varargs lib_abs(Varargs args) {
        FastLuaString fs = u_concat_path(args);

        String abs = u_resolvePath(fs.toString()).absolutePath().toString();
        if (u_isSeperator(fs.last()) && !abs.endsWith(File.separator)) {
            abs += File.separator;
        }

        if (fs.startsWithWindowsUnicodePrefix()) {
            abs = "\\\\?" + ((abs.startsWith("\\") ? "" : "\\") + abs);
        }

        LuaValue lstr = u_valueOfStr(abs);

        return lstr;
    }

    protected Varargs lib_rel(Varargs args) {
        FastLuaString fs = new FastLuaString(args.checkstring(1));
        LuaPath aP = u_resolvePath(fs.toString());
        LuaPath pwd;
        LuaString pwds = args.optstring(2, null);
        if (pwds == null) {
            pwd = handler.getWorkDirectory();
        } else {
            pwd = u_resolvePath(pwds);
        }

        FastLuaString aPString = new FastLuaString(aP.toString());
        FastLuaString aPDrive = new FastLuaString(lib_drive(aPString));
        FastLuaString pwdDrive = new FastLuaString(lib_drive(new FastLuaString(pwd.toString())));

        if (!aPDrive.equals(pwdDrive)) {
            return lib_path(fs);
        }

        String theRel = pwd.relative(aP).toString();
        if (u_isSeperator(fs.last()) && !theRel.endsWith(File.separator) && !theRel.endsWith("..")) {
            theRel+=separatorChar;
        }

        if (theRel.isEmpty()) {
            return FastLuaString.DOT;
        }

        LuaString str = valueOf(theRel);
        if (u_isAbsolute(str)) {
            return FastLuaString.DOT;
        }

        return str;
    }

    protected Varargs lib_fnmatch(Varargs args) {
        LuaString str1 = args.checkstring(1); //string
        LuaString str2 = args.checkstring(2); //token
        LPathPattern pat = u_compilePattern(str2.m_bytes, str2.m_offset, str2.m_length, false, u_isPathCaseSensitive());
        return pat.match(str1.m_bytes, str1.m_offset, str1.m_length) ? TRUE : FALSE;
    }

    protected Varargs lib_match(Varargs args) {
        LuaString str1 = args.checkstring(1);
        LuaString str2 = args.checkstring(2);

        if (str1.m_length == 0) {
            return NONE;
        }

        if (str2.m_length == 0) {
            return args.arg1();
        }

        LinkedList<FastLuaString> pattern = u_canonSplit(new FastLuaString(lib_anchor(str2)), new FastLuaString(str2), false, false);
        LinkedList<FastLuaString> name = u_canonSplit(new FastLuaString(lib_anchor(str1)), new FastLuaString(str1), false, false);

        if (u_isAbsolute(str2)) {
            if (!u_isAbsolute(str1)) {
                return NONE;
            }

            if (!lib_drive(str2).equals(lib_drive(str1))) {
                return NONE;
            }

            if (name.size() != pattern.size()) {
                return NONE;
            }
        } else {
            if (name.size() < pattern.size()) {
                return NONE;
            }

            if (pattern.size() == 0 && name.size() > 0) {
                return NONE;
            }

            if (pattern.size() > 0 && pattern.getFirst().isDoubleDot() && name.size() != pattern.size()) {
                return NONE;
            }
        }

        Iterator<FastLuaString> patternIter = pattern.descendingIterator();
        Iterator<FastLuaString> nameIter = name.descendingIterator();

        while (patternIter.hasNext()) {
            FastLuaString curPat = patternIter.next();
            //We check bounds above so this must have more or equal amount of elements otherwise we already return none.
            FastLuaString curName = nameIter.next();

            LPathPattern pat = u_compilePattern(curPat.bytes, curPat.off, curPat.len, false, u_isPathCaseSensitive());
            if (!pat.match(curName.bytes, curName.off, curName.len)) {
                return NONE;
            }
        }

        return args.arg1();
    }

    protected abstract Varargs lib_drive(Varargs args);

    protected Varargs lib_fs_getcwd(Varargs args) {
        return u_valueOfStr(handler.getWorkDirectory().toString());
    }

    protected Varargs lib_fs_isfile(Varargs args) {
        FastLuaString path = u_concat_path(args);
        return u_resolvePath(path.toString()).isFile() ? path : FALSE;
    }

    protected Varargs lib_fs_isdir(Varargs args) {
        FastLuaString path = u_concat_path(args);
        return u_resolvePath(path.toString()).isDir() ? path : FALSE;
    }

    protected Varargs lib_fs_ismount(Varargs args) {
        FastLuaString path = u_concat_path(args);
        LuaPath luaPath = u_resolvePath(path.toString());
        //This is best we can do without syscalls
        if (luaPath.parent() == null) {
            return path;
        }
        return FALSE;
    }

    protected Varargs lib_fs_islink(Varargs args) {
        FastLuaString path = u_concat_path(args);
        return u_resolvePath(path.toString()).isĹink() ? path : FALSE;

    }

    protected abstract Varargs lib_fs_binpath(Varargs args);

    protected Varargs lib_env_get(Varargs args) {
        String key = args.checkjstring(1);
        return u_valueOfStr(System.getenv(key));
    }

    protected Varargs lib_env_set(Varargs args) {
        return ERR_UNSUPPORTED;
    }

    protected Varargs lib_env_expand(Varargs args) {
        return ERR_UNSUPPORTED;
    }

    protected Varargs lib_env_uname(Varargs args) {
        //best we can do really
        return varargsOf(new LuaValue[] {
                u_valueOfStr("java"),
                u_valueOfStr("localhost"),
                u_valueOfStr(System.getProperty("java.version"), EMPTYSTRING),
                u_valueOfStr(System.getProperty("java.version"), EMPTYSTRING),
                u_valueOfStr(System.getProperty("os.arch"), EMPTYSTRING),
        });
    }



    protected Varargs lib_path(Varargs args) {
        FastLuaString cat = u_concat_path(args);
        if (cat.isDot()) {
            return cat;
        }

        FastLuaString anchor = new FastLuaString(lib_anchor(args));

        FastLuaString res = u_canon(anchor, cat);
        if ((u_isSeperator(cat.last()) || (u_isSeperator(cat.last(1)) && cat.endsWithDot())) && !u_isSeperator(res.last()) && !res.endsWithDots()) {
            return res.addSep();
        }

        return res;
    }



    protected abstract Varargs lib_root(Varargs args);

    protected abstract Varargs lib_anchor(Varargs args);

    protected Varargs lib_parent(Varargs args) {
        LuaString path = u_concat_path(args).checkstring(1);
        boolean absolute = u_isAbsolute(path);
        FastLuaString anchor = new FastLuaString(absolute ? lib_anchor(path) : NONE);

        LinkedList<FastLuaString> strings = u_canonSplit(anchor, new FastLuaString(path), true, false);

        if (strings.isEmpty()) {
            return absolute ? anchor : FastLuaString.DOUBLE_DOT;
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

        return u_join(anchor, strings);
    }



    protected Varargs lib_name(Varargs args) {
        return u_getFileName(args);
    }

    protected Varargs lib_stem(Varargs args) {
        FastLuaString name = u_getFileName(args);
        for (int i = name.len-1; i > 0; i--) {
            if (name.bytes[i+name.off] == '.') {
                return new FastLuaString(name.bytes, name.off, i);
            }
        }

        return name;
    }

    protected Varargs lib_suffix(Varargs args) {
        FastLuaString name = u_getFileName(args);
        //>0 intenteded as . at start mean the file name is the extension!
        for (int i = name.len-1; i > 0; i--) {
            if (name.bytes[i+name.off] == '.') {
                return new FastLuaString(name.bytes, i+name.off, name.len-i);
            }
        }

        return FastLuaString.EMPTY;
    }

    protected Varargs lib_suffixes(Varargs args) {
        FastLuaString name = u_getFileName(args);
        LinkedList<Varargs> suffixes = new LinkedList<>();
        int maxlen = name.len;
        //>0 intenteded as . at start mean the file name is the extension!
        for (int i = name.len-1; i > 0; i--) {
            if (name.bytes[i+name.off] == '.') {
                suffixes.addFirst(new FastLuaString(name.bytes, i+name.off, maxlen-i));
                maxlen = i;
            }
        }



        return u_indexIterator(suffixes.iterator());
    }


    protected Varargs lib_parts(Varargs args) {
        LuaValue last = u_last(args);
        if (last.isinttype()) {
            args = u_subargs(args, 1, args.narg()-1);
        }
        FastLuaString path = u_concat_path(args);
        boolean absolute = u_isAbsolute(path.toLuaString());
        FastLuaString anchor = new FastLuaString(absolute ? lib_anchor(path) : NONE);

        LinkedList<FastLuaString> strings = u_canonSplit(anchor, new FastLuaString(path), true, false);

        if (absolute) {
            strings.addFirst(anchor);
        }

        if (last.isinttype()) {
            int idx = last.toint();
            if (idx == 0) {
                return NONE;
            }

            if (idx < 0) {
                idx = strings.size()+idx;
            } else {
                idx--;
            }

            if (idx < 0 || idx >= strings.size()) {
                return NONE;
            }

            return strings.get(idx);
        }

        return u_indexIterator(strings.iterator());

    }

    protected Varargs lib_exists(Varargs args) {
        return valueOf(u_resolvePath(args).exists());
    }

    protected Varargs lib_fs_dir(Varargs args) {
        LuaString relative = args.narg() == 0 ? EMPTYSTRING : u_concat_path(args).checkstring(1);


        LuaPath file = u_resolvePath(relative.checkjstring());
        if (!file.exists()) {
            return u_err("dir:" + relative +":(errno=2): No such file or directory");
        }


        if (!file.isDir()) {
            return u_iterator(Collections.singleton(varargsOf(relative, valueOf("file"))).iterator());
        }

        List<LuaPath> ff;

        try {
                ff = file.list();
            } catch (IOException e) {
                return u_err("dir:" + relative +":(errno=5): I/O error");
            }

        List<Varargs> tempAr = new ArrayList<>(ff.size());

        for (LuaPath f : ff) {
            LuaValue lv = valueOf(f.name());
            tempAr.add(varargsOf(u_concat_path(varargsOf(relative, lv)).checkstring(1), f.isDir() ? valueOf("dir") : valueOf("file")));
        }

        return u_iterator(tempAr.iterator());
    }

    protected Varargs lib_fs_scandir(Varargs args) {
        LuaValue lv = args.arg(args.narg());

        int n = args.narg();
        int depth;
        Varargs toPath;
        final FastLuaString path;
        if (!lv.isint()) {
            depth = Integer.MAX_VALUE-1;
            toPath = args;
        } else {
            depth = lv.toint();
            if (depth <= 0) {
                depth = Integer.MAX_VALUE-1;
            }
            toPath  = u_subargs(args, 1, n-1);
        }

        path = args.narg() == 0 ? FastLuaString.EMPTY : u_concat_path(toPath);

        String rPath = path.toString();

        final LuaPath file = u_resolvePath(rPath);
        if (!file.exists()) {
            return u_err("scandir:" + rPath +":(errno=2): No such file or directory");
        }

        if (!file.isDir()) {
            return u_iterator(Collections.singleton(varargsOf(path.toLuaString(), valueOf("file"))).iterator());
        }

        final List<Varargs> result = new ArrayList<>();
        final Set<LuaPath> pathSet = new HashSet<>();

        try {
            file.walkFileTree(depth+1, true, new LuaPath.LuaFileVisitor() {
                private void visit(String visited, String type) {
                    if (visited.isEmpty()) {
                        result.add(u_varargsOf(path, valueOf(type)));
                        return;
                    }
                    result.add(u_varargsOf(u_concat_path(u_varargsOf(path, visited)), valueOf(type)));
                }

                @Override
                public FileVisitResult preVisitDirectory(LuaPath dir) throws IOException {


                    if (!pathSet.add(dir.realPath())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    visit(file.relative(dir).toString(), "in");

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(LuaPath child) throws IOException {
                    visit(file.relative(child).toString(), child.isDir() ? "dir" : "file");
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(LuaPath dir) throws IOException {
                    visit(file.relative(dir).toString(), "out");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return u_err("scandir:" + path +":(errno=5): I/O error");
        }

        return u_iterator(result.iterator());
    }

    protected Varargs fs_glob(Varargs args) {

        int n = args.narg();
        if (n == 0) {
            return u_iterator(Collections.<Varargs>emptyIterator());
        }

        LuaValue lv = args.arg(n);

        int depth;
        Varargs toPath;
        final FastLuaString path;
        final FastLuaString glob;
        if (!lv.isint()) {
            depth = Integer.MAX_VALUE-1;
            glob = new FastLuaString(args.checkstring(n));
            toPath = u_subargs(args, 1, n-1);
        } else {
            depth = lv.toint();
            if (depth <= 0) {
                depth = Integer.MAX_VALUE-1;
            }
            glob = new FastLuaString(args.checkstring(n-1));
            toPath  = u_subargs(args, 1, n-2);
        }

        if (glob.isDoubleStar() || u_endsWithSepDoubleStar(glob)) {
            return u_iterator(Collections.<Varargs>emptyIterator());
        }

        path = args.narg() == 0 ? FastLuaString.EMPTY : u_concat_path(toPath);

        String rPath = path.toString();

        final LuaPath file = u_resolvePath(rPath);
        if (!file.exists()) {
            return u_err("scandir:" + rPath +":(errno=2): No such file or directory");
        }

        if (!file.isDir()) {
            return u_iterator(Collections.singleton(varargsOf(path.toLuaString(), valueOf("file"))).iterator());
        }

        final List<Varargs> result = new ArrayList<>();
        final Set<LuaPath> pathSet = new HashSet<>();



        try {
            file.walkFileTree(depth+1, true, new LuaPath.LuaFileVisitor() {

                LPathPattern patA;
                LPathPattern patB;

                private void visit(String visited, String type, boolean inOut) {

                    if (visited.isEmpty()) {
                        return;
                    }

                    FastLuaString res = new FastLuaString(visited);

                    if (glob.len > 1 && u_isSeperator(glob.last()) && inOut) {
                        if (patB == null) {
                            patB = u_compilePattern(glob.bytes, glob.off, glob.len-1, true, u_isPathCaseSensitive());
                        }
                        if (patB.match(res.bytes, res.off, res.len)) {
                            result.add(u_varargsOf(res, valueOf(type)));
                            return;
                        }

                        return;
                    }


                    if (patA == null) {
                        patA = u_compilePattern(glob.bytes, glob.off, glob.len, true, u_isPathCaseSensitive());
                    }
                    if (patA.match(res.bytes, res.off, res.len)) {
                        result.add(u_varargsOf(res, valueOf(type)));
                    }
                }

                @Override
                public FileVisitResult preVisitDirectory(LuaPath dir) throws IOException {


                    if (!pathSet.add(dir.realPath())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    visit(file.relative(dir).toString(), "in", true);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(LuaPath child) throws IOException {
                    visit(file.relative(child).toString(), child.isDir() ? "dir" : "file", false);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(LuaPath dir) throws IOException {
                    visit(file.relative(dir).toString(), "out", true);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return u_err("scandir:" + path +":(errno=5): I/O error");
        }

        return u_iterator(result.iterator());
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
            return u_err("mkdir:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    protected Varargs lib_fs_rmdir(Varargs args) {
        FastLuaString string = u_concat_path(args);
        String jString = string.toString();
        LuaPath file = u_resolvePath(jString);
        if (!file.exists()) {
            return u_err("rmdir:" + jString +":(errno=2): No such file or directory");
        }

        if (!file.isDir()) {
            return u_err("rmdir:" + jString +":(errno=20): Not a directory");
        }

        try {
            file.delete();
        } catch (DirectoryNotEmptyException e) {
            return u_err("rmdir:" + jString +":(errno=39): Directory not empty");
        } catch (IOException e) {
            return u_err("rmdir:" + jString +":(errno=5): I/O error");
        }

        return string;
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
            return u_err("makedirs:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    protected Varargs lib_fs_removedirs(Varargs args) {
        FastLuaString string = u_concat_path(args);
        String jString = string.toString();
        LuaPath f = u_resolvePath(string.toString());

        try {
            f.walkFileTree(Integer.MAX_VALUE, false, new LuaPath.LuaFileVisitor() {

                @Override
                public FileVisitResult preVisitDirectory(LuaPath dir) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(LuaPath file) throws IOException {
                    file.delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(LuaPath dir) throws IOException {
                    dir.delete();
                    return FileVisitResult.CONTINUE;


                }
            });
        } catch (IOException e) {
            return u_err("removedirs:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    protected Varargs lib_fs_tmpdir(Varargs args) {

        //lpath uses rng xor clock, I can't be asked to do that.
        String name = args.optjstring(1, "lua_") + rng.nextInt(899998)+100001;

        LuaPath tPath = null;
        try {
            tPath = handler.tmpDir().child(name);
            tPath.mkdir();
            return valueOf(tPath.toString());
        } catch (FileAlreadyExistsException e) {
            if (tPath == null) {
                //Not possible
                return u_err("tmpdir:(errno=5): I/O error");
            }

            return valueOf(tPath.toString());
        } catch (Exception e) {
            return u_err("tmpdir:(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_ctime(Varargs args) {
        String path = args.checkjstring(1);
        LuaPath thePath = u_resolvePath(path);

        try {
            return valueOf(thePath.attributes().creationTime().to(TimeUnit.SECONDS));
        } catch (IOException e) {
            return u_err("touch:" + path +":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_mtime(Varargs args) {
        String path = args.checkjstring(1);
        LuaPath thePath = u_resolvePath(path);

        try {
            return valueOf(thePath.attributes().lastModifiedTime().to(TimeUnit.SECONDS));
        } catch (IOException e) {
            return u_err("touch:" + path +":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_atime(Varargs args) {
        String path = args.checkjstring(1);
        LuaPath thePath = u_resolvePath(path);

        try {
            return valueOf(thePath.attributes().lastAccessTime().to(TimeUnit.SECONDS));
        } catch (IOException e) {
            return u_err("touch:" + path +":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_touch(Varargs args) {
        String path = args.checkjstring(1);
        LuaPath thePath = u_resolvePath(path);
        if (!thePath.exists()) {
            try {
                thePath.createNewFile();
            } catch (IOException e) {
                return u_err("touch:" + path +":(errno=5): I/O error");
            }
        }

        long atime = args.isnoneornil(2) ? TimeUnit.MILLISECONDS.toSeconds(u_getTimestamp()) : args.checklong(2);
        long mtime = args.optlong(3, atime);

        try {
            thePath.setFileTimes(FileTime.from(mtime, TimeUnit.SECONDS), FileTime.from(atime, TimeUnit.SECONDS), null);
        } catch (IOException e) {
            return u_err("touch:" + path +":(errno=5): I/O error");
        }

        return TRUE;
    }



    protected Varargs lib_fs_remove(Varargs args) {
        FastLuaString string = u_concat_path(args);
        String jString = string.toString();
        LuaPath file = u_resolvePath(jString);
        if (!file.exists()) {
            return u_err("remove:" + jString +":(errno=2): No such file or directory");
        }

        try {
            file.delete();
        } catch (DirectoryNotEmptyException e) {
            return u_err("remove:" + jString +":(errno=39): Directory not empty");
        } catch (IOException e) {
            return u_err("remove:" + jString +":(errno=5): I/O error");
        }

        return string;
    }

    protected Varargs lib_fs_copy(Varargs args) {
        LuaString source = args.checkstring(1);
        LuaString target = args.checkstring(2);

        String sourceString = source.checkjstring();

        String targetString = target.checkjstring();

        LuaPath srcFile = u_resolvePath(source.checkjstring());
        LuaPath targetFile = u_resolvePath(target.checkjstring());


        if (!srcFile.exists()) {
            return u_err("open:" + sourceString +":(errno=2): No such file or directory");
        }

        if (srcFile.isDir()) {
            //Yes this is how it fails
            try {
                targetFile.delete();
                targetFile.createNewFile();
            } catch (IOException e) {
                //DC
            }

            return u_err("write:" + targetString +":(errno=14): Bad address");
        }



        try {
            srcFile.copyFile(targetFile);
        } catch (IOException e) {
            return u_err("read:" + sourceString +":(errno=5): I/O error");
        }

        return TRUE;
    }



    protected Varargs lib_fs_rename(Varargs args) {
        LuaString source = args.checkstring(1);
        LuaString target = args.checkstring(2);

        String sourceString = source.checkjstring();

        String targetString = target.checkjstring();

        LuaPath srcFile = u_resolvePath(source.checkjstring());
        LuaPath targetFile = u_resolvePath(target.checkjstring());


        if (!srcFile.exists()) {
            return u_err("rename:" + targetString +":(errno=2): No such file or directory");
        }

        if (srcFile.isDir()) {
            //Yes this is how it fails
            try {
                targetFile.delete();
                targetFile.createNewFile();
            } catch (IOException e) {
                //DC
            }

            return u_err("rename:" + targetString +":(errno=20): Not a directory");
        }



        try {
            srcFile.moveFile(targetFile);
        } catch (IOException e) {
            return u_err("rename:" + sourceString +":(errno=5): I/O error");
        }

        return TRUE;
    }

    protected Varargs lib_resolve(Varargs args) {
        String relative = u_concat_path(args).checkjstring(1);
        LuaPath path = handler.resolvePath(relative);
        try {
            return valueOf(path.absolutePath().realPath().toString());
        } catch (IOException e) {
            return u_err("realpath:" + relative + ":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_size(Varargs args) {
        String relative = u_concat_path(args).checkjstring(1);
        LuaPath absolute = u_resolvePath(relative);

        try {
            return valueOf(absolute.size());
        } catch (IOException e) {
            return u_err("size:" + relative +":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_unlockdirs(Varargs args) {
        //WELL Jse doesn't know what chmod is
        return TRUE;
    }

    protected Varargs lib_fs_symlink(Varargs args) {
        String placeLinkHereString = args.checkjstring(2);
        LuaPath target = u_resolvePath(args.checkjstring(1));
        LuaPath placeLinkHere = u_resolvePath(placeLinkHereString);

        try {
            placeLinkHere.symlink(target);
        } catch (IOException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=5): I/O error");
        }

        return TRUE;
    }

    protected abstract LuaValue info_getOS();

    protected abstract LuaValue info_getDevNull();

    protected LuaValue info_getSeperator() {
        return seperatorString;
    }

    protected abstract LuaValue info_getPathSeperator();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Internal UTILs
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected long u_getTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Varargs that joins 2 varargs.
     */
    protected final class JoinedVarargs extends Varargs {

        protected final Varargs v1;
        protected final Varargs v2;
        protected final int n1;
        protected final int n2;


        protected JoinedVarargs(Varargs v1, Varargs v2) {
            int nn1 = v1.narg();
            if (nn1 == 0) {
                this.v1 = v2;
                this.v2 = v2;
            } else {
                this.v1 = v1;
                this.v2 = v2;
            }

            this.n1 = nn1;
            this.n2 = v2.narg();
        }

        @Override
        public LuaValue arg(int i) {
            if (i < 1) {
                return LuaValue.NIL;
            }
            if (i <= n1) {
                return v1.arg(i);
            }

            return v2.arg(i-n1);
        }

        @Override
        public int narg() {
            return n1+n2;
        }

        @Override
        public LuaValue arg1() {
            return v1.arg1();
        }

        @Override
        public Varargs subargs(int start) {
            return u_subargs(this, start, narg());
        }
    }

    /**
     * SubVarargs that delegates to a varargs but has start and end (default Luaj only offers custom varargs with start offset but has no way to cut the tail of a varargs)
     */
    class SubVarargs extends Varargs {
        private final Varargs delegate;
        private final int start;
        private final int end;
        private final int n;

        SubVarargs(Varargs varargs, int start, int end) {
            this.delegate = varargs;
            this.start = start;
            this.end = end;
            this.n = end+1-start;
        }

        public LuaValue arg(int i) {
            if (i > n) {
                return LuaValue.NIL;
            }

            if (i < 1) {
                return LuaValue.NIL;
            }

            return delegate.arg(i+(start-1));
        }
        public LuaValue arg1() {
            return delegate.arg(start);
        }
        public int narg() {
            return n;
        }

        public Varargs subargs(final int start) {
            return u_subargs(this, start, n);
        }
    }


    protected static LuaValue u_valueOfStr(String str) {
        return str == null ? NIL : valueOf(str);
    }

    protected static LuaValue u_valueOfStr(String str, LuaValue defVal) {
        return str == null ? defVal : valueOf(str);
    }

    protected static Varargs u_err(String value) {
        return varargsOf(NIL, u_valueOfStr(value));
    }

    protected Varargs u_err(String func, int code, String loc) {
        return u_err(func + ":(errno=" + Integer.toString(code) + "): "
                +loc);
    }

    public static boolean u_isNormalChar(byte b) {
        return b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z';
    }

    protected void u_toUpper(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            b[i] = u_toUpper(b[i]);
        }
    }

    protected byte u_toUpper(byte c) {
        if (c >= 'a' && c <= 'z') {
            c -= 32;
        }

        return c;
    }

    public FastLuaString u_toUpper(FastLuaString str, int idx) {
        if (idx < 0 || idx > str.len) {
            return str;
        }

        byte[] buf = new byte[str.len];
        System.arraycopy(str.bytes, str.off, buf, 0, str.len);
        buf[idx] = u_toUpper(buf[idx]);
        return new FastLuaString(buf);
    }

    public FastLuaString u_toUpper(FastLuaString str) {
        byte[] buf = new byte[str.len];
        for (int i = 0; i < str.len; i++) {
            buf[i] = u_toUpper(str.bytes[i+str.off]);
        }

        return new FastLuaString(buf);
    }

    protected boolean u_canUpper(byte c) {
        return c >= 'a' && c <= 'z';
    }

    protected Varargs u_indexIterator(final Iterator<? extends Varargs> iter) {
        return new VarArgFunction() {

            private int counter = 0;

            @Override
            public Varargs invoke(Varargs args) {
                if (iter.hasNext()) {
                    counter++;
                    return varargsOf(valueOf(counter), iter.next());
                }

                return NONE;
            }
        };
    }

    protected Varargs u_iterator(final Iterator<? extends Varargs> iter) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (iter.hasNext()) {
                    return iter.next();
                }

                return NONE;
            }
        };
    }

    protected Varargs u_subargs(Varargs varargs, int start, int end) {
        int n = varargs.narg();
        if (end > n) {
            end = n;
        }

        if (start < 1) {
            start = 1;
        }

        if (end < start) {
            return NONE;
        }

        if (start == 1 && end == n) {
            return varargs;
        }

        if (start == end) {
            return varargs.arg(start);
        }

        if (end-start == 1) {
            return LuaValue.varargsOf(varargs.arg(start), varargs.arg(end));
        }

        return new SubVarargs(varargs, start, end);
    }

    protected Varargs u_varargsOf(String... strings) {
        if (strings.length < 3) {
            if (strings.length == 0) {
                return NONE;
            }

            if (strings.length == 1) {
                return u_valueOfStr(strings[0]);
            }

            return u_varargsOf(u_valueOfStr(strings[0]), u_valueOfStr(strings[1]));
        }

        LuaValue[] lva = new LuaValue[strings.length];
        for (int i = 0; i < lva.length; i++) {
            lva[i] = u_valueOfStr(strings[i]);
        }

        return LuaValue.varargsOf(lva);
    }


    protected Varargs u_varargsOf(Varargs args, String append) {
        if (append == null) {
            return args;
        }

        LuaValue val = LuaValue.valueOf(append);

        return u_varargsOf(args, val);
    }

    protected Varargs u_varargsOf(Varargs v1, Varargs v2) {
        if (v2.narg() == 0) {
            return v1;
        }

        int n1 = v1.narg();
        if (n1 == 0) {
            return v2;
        }

        if (n1 == 1) {
            return LuaValue.varargsOf(v1.arg1(), v2);
        }

        return new JoinedVarargs(v1, v2);
    }

    protected LuaValue u_last(Varargs args) {
        return args.arg(args.narg());
    }

    protected FastLuaString u_canon(FastLuaString anchor, FastLuaString path) {
        return u_join(anchor, u_canonSplit(anchor, path, true, anchor.len > 0));
    }


    protected FastLuaString u_join(FastLuaString prefix, List<FastLuaString> list) {
        if (list.isEmpty()) {
            return prefix;
        }

        int size = 0;
        for (FastLuaString f : list) {
            size+=f.len;
            //For Seperator
            size++;
        }

        size+=prefix.len;

        LuaStringBuilder builder = new LuaStringBuilder(size);
        builder.append(prefix.bytes, prefix.off, prefix.len);

        for (FastLuaString f : list) {
            builder.append(f.bytes, f.off, f.len);
            builder.append(separator);
        }

        //Remove last / or \
        builder.setPos(builder.getPos()-1);
        return builder.toFastString();
    }

    protected LinkedList<FastLuaString> u_canonSplit(FastLuaString anchor, FastLuaString path, boolean ignoreLastSlash, boolean ignorePreceding) {
        if (anchor != null) {
            if (path.len - anchor.len < 0) {
                return new LinkedList<>();
            }
            path = new FastLuaString(path.bytes, path.off + anchor.len, path.len - anchor.len);

        }

        if (ignoreLastSlash && u_isSeperator(path.last())) {
            path = new FastLuaString(path.bytes, path.off, path.len - 1);
        }

        LinkedList<FastLuaString> splitted = new LinkedList<>();

        if (path.len == 0) {
            return splitted;
        }

        u_split(path, splitted);

        int toRM = 0;
        Iterator<FastLuaString> iter = splitted.descendingIterator();
        while(iter.hasNext()) {
            FastLuaString next = iter.next();
            if (next.isDot()) {
                iter.remove();
                continue;
            }

            if (next.isDoubleDot()) {
                iter.remove();
                toRM++;
                continue;
            }

            if (toRM > 0) {
                toRM--;
                iter.remove();
                continue;
            }
        }

        if (ignorePreceding) {
            return splitted;
        }
        while (toRM > 0) {
            toRM--;
            splitted.addFirst(FastLuaString.DOUBLE_DOT);
        }



        //Convert to array to save on random access
        return splitted;
    }

    protected void u_split(FastLuaString str, List<FastLuaString> splitted) {
        int startIndex = str.off;
        for (int i = startIndex, end = str.off+str.len; i < end; i++) {
            if (!u_isSeperator(str.bytes[i])) {
                continue;
            }

            int len = i-startIndex;
            if (len == 0) {
                splitted.add(FastLuaString.EMPTY);
            } else {
                splitted.add(new FastLuaString(str.bytes, startIndex, len));
            }

            startIndex = i+1;
        }

        int len = str.len-(startIndex- str.off);
        if (len == 0) {
            splitted.add(FastLuaString.EMPTY);
        } else {
            splitted.add(new FastLuaString(str.bytes, startIndex, len));
        }
    }

    protected LuaPath u_resolvePath(Varargs args) {
        return u_resolvePath(u_concat_path(args).checkjstring(1));
    }

    protected LuaPath u_resolvePath(String path) {
        //Windows specific escaping of FileAPI W function escape sequence that java cannot chop properly...
        if (File.separatorChar == '\\' && path.startsWith("\\\\?\\")) {
            if (path.length() >= 6 && path.charAt(5) == ':') {
                path = path.substring(4);
            } else {
                path = path.substring(3);
            }
        }

        return handler.resolvePath(path);
    }

    public boolean u_endsWithSepDoubleStar(FastLuaString s) {
        return s.len >= 3 && s.bytes[s.off+s.len-3] == u_getSeparator() && s.bytes[s.off+s.len-2] == '*' && s.bytes[s.off+s.len-1] == '*';
    }

    protected byte u_getSeparator() {
        return (byte) File.separatorChar;
    }

    /**
     * Is the value considered a seperator char for the purpose of input path resolving?
     */
    protected abstract boolean u_isSeperator(byte b);

    protected abstract FastLuaString u_concat_path(Varargs args);

    protected boolean u_isAbsolute(LuaString str) {
        return u_isAbsolute(new FastLuaString(str));
    }

    protected abstract boolean u_isAbsolute(FastLuaString str);

    protected FastLuaString u_getFileName(Varargs args) {
        LuaString path = u_concat_path(args).checkstring(1);
        boolean absolute = u_isAbsolute(path);
        FastLuaString anchor = new FastLuaString(absolute ? lib_anchor(path) : NONE);

        LinkedList<FastLuaString> strings = u_canonSplit(anchor, new FastLuaString(path), true, false);

        if (strings.isEmpty()) {
            return FastLuaString.EMPTY;
        }


        return strings.getLast();
    }




    protected abstract boolean u_isPathCaseSensitive();

    protected interface LPathPattern {
        boolean match(byte[] string, int stringOffset, int stringLen);
    }

    protected LPathPattern u_compilePattern(byte[] token, int tokenOffset, int tokenLength, boolean glob, boolean caseSensitive) {
        return LPathMatcher.compile(token, tokenOffset, tokenLength, glob, caseSensitive);
    }


}
