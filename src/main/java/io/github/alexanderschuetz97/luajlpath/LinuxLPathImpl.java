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
import io.github.alexanderschuetz97.nativeutils.api.LinuxConst;
import io.github.alexanderschuetz97.nativeutils.api.LinuxNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.PermissionDeniedException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.UnknownNativeErrorException;
import io.github.alexanderschuetz97.nativeutils.api.structs.Stat;
import io.github.alexanderschuetz97.nativeutils.api.structs.Utsname;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import static org.luaj.vm2.LuaValue.FALSE;
import static org.luaj.vm2.LuaValue.*;
import static org.luaj.vm2.LuaValue.valueOf;
import static org.luaj.vm2.LuaValue.varargsOf;

/**
 * Implements platform specific lpath functions using linux syscalls.
 */
public class LinuxLPathImpl extends JsePosixLPathImpl {

    protected final LinuxNativeUtil nativeUtil = NativeUtils.getLinuxUtil();

    @Override
    protected LuaValue info_getOS() {
        return LINUX;
    }

    protected Varargs lib_fs_ismount(Varargs args) {
        try {
            LuaPath file = u_resolvePath(args).realPath().absolutePath();


            LuaPath parent = file.parent();
            if (parent == null) {
                return valueOf(file.toString());
            }

            Path syspath = file.toSystemPath();
            Path psyspath = parent.toSystemPath();
            if (syspath == null || psyspath == null) {
                return FALSE;
            }

            Stat mstat = nativeUtil.lstat(syspath.toString());
            Stat pstat = nativeUtil.lstat(psyspath.toString());

            if (mstat.getDev() != pstat.getDev()) {
                return valueOf(parent.toString());
            }

        } catch (UnknownNativeErrorException | IOException e) {
            //DONT CARE
        }

        return FALSE;
    }


    protected Varargs lib_fs_binpath(Varargs args) {
        try {
            return valueOf(nativeUtil.readlink("/proc/self/exe"));
        } catch (NotLinkException e) {
            return ERR_BINPATH_22;
        } catch (UnknownNativeErrorException e) {
            return u_err("binpath", e.intCode(), nativeUtil.strerror_r(e.intCode()));
        } catch (InvalidPathException e) {
            // quite unlikely
            return ERR_BINPATH_36;
        } catch (FileSystemLoopException e) {
            // quite unlikely
            return ERR_BINPATH_40;
        } catch (AccessDeniedException e) {
            return ERR_BINPATH_13;
        } catch (FileNotFoundException e) {
            return ERR_BINPATH_2;
        } catch (NotDirectoryException e) {
            return ERR_BINPATH_20;
        } catch (IOException e) {
            return ERR_BINPATH_5;
        }
    }

    protected Varargs lib_env_get(Varargs args) {
        String key = args.checkjstring(1);
        try {
            return u_valueOfStr(nativeUtil.getenv(key));
        } catch (IllegalArgumentException e) {
            return ERR_GETENV_22;
        } catch (UnknownNativeErrorException e) {
            return u_err("getenv", e.intCode(), nativeUtil.strerror_r(e.intCode()));
        }
    }

    protected Varargs lib_env_set(Varargs args) {
        String key = args.checkjstring(1);
        String value = args.optjstring(2, null);

        if (value == null) {
            try {
                nativeUtil.unsetenv(key);
                return NIL;
            } catch (IllegalArgumentException e) {
                return ERR_UNSETENV_22;
            } catch (UnknownNativeErrorException e) {
                return u_err("unsetenv", e.intCode(), nativeUtil.strerror_r(e.intCode()));
            }
        }

        try {
            nativeUtil.setenv(key, value, true);
            return args.arg(2);
        } catch (IllegalArgumentException e) {
            return ERR_SETENV_22;
        } catch (UnknownNativeErrorException e) {
            return u_err("setenv", e.intCode(), nativeUtil.strerror_r(e.intCode()));
        }
    }

    protected Varargs lib_env_expand(Varargs args) {
        String jstr = u_concat_path(args).checkjstring(1);
        String[] result;
        try {
            result = nativeUtil.wordexp(jstr, true, true, true);
        } catch (IllegalArgumentException exc) {
            return u_err("syntax error");
        }

        LuaValue[] lva = new LuaValue[result.length];
        for (int i = 0; i < lva.length; i++) {
            lva[i] = u_valueOfStr(result[i]);
        }
        return varargsOf(lva);
    }

    protected Varargs lib_env_uname(Varargs args) {
        Utsname uname = nativeUtil.uname();
        return varargsOf(new LuaValue[] {
                u_valueOfStr(uname.getSysname()),
                u_valueOfStr(uname.getNodename()),
                u_valueOfStr(uname.getRelease()),
                u_valueOfStr(uname.getVersion()),
                u_valueOfStr(uname.getMachine()),
        });
    }
    @Override
    protected Varargs lib_resolve(Varargs args) {
        String relative = u_concat_path(args).checkjstring(1);
        LuaPath path = u_resolvePath(relative);
        Path syspath = path.toSystemPath();
        if (syspath == null) {
            try {
                return valueOf(path.absolutePath().realPath().toString());
            } catch (IOException e) {
                return u_err("realpath:" + relative + ":(errno=5): I/O error");
            }
        }

        String absolute = syspath.toString();

        String realPath;
        try {
            realPath = nativeUtil.realpath(absolute);
        } catch (NotLinkException e) {
            return u_err("realpath:" + relative + ":(errno=22): Invalid argument");
        } catch (UnknownNativeErrorException e) {
            return u_err("realpath:" + relative, e.intCode(), nativeUtil.strerror_r(e.intCode()));
        } catch (InvalidPathException e) {
            return u_err("realpath:" + relative + ":(errno=36): File name too long");
        } catch (FileSystemLoopException e) {
            return u_err("realpath:" + relative + ":(errno=40): Too many symbolic links encountered");
        } catch (AccessDeniedException e) {
            return u_err("realpath:" + relative + ":(errno=13): Permission denied");
        } catch (FileNotFoundException e) {
            return u_err("realpath:" + relative + ":(errno=2): No such file or directory");
        } catch (NotDirectoryException e) {
            return u_err("realpath:" + relative + ":(errno=20): Not a directory");
        } catch (IOException e) {
            return u_err("realpath:" + relative + ":(errno=5): I/O error");
        }

        Path resultPath = Paths.get(realPath);

        LuaPath result = handler.resolveSysPath(resultPath);
        if (result == null) {
            try {
                return valueOf(path.absolutePath().realPath().toString());
            } catch (IOException e) {
                return u_err("realpath:" + relative + ":(errno=5): I/O error");
            }
        }

        return valueOf(result.toString());
    }

    @Override
    protected Varargs lib_fs_size(Varargs args) {
        String relative = u_concat_path(args).checkjstring(1);
        LuaPath absolute = u_resolvePath(relative);

        Path syspath = absolute.toSystemPath();
        if (syspath == null) {
            try {
                return valueOf(absolute.size());
            } catch (IOException e) {
                return u_err("size:" + relative +":(errno=5): I/O error");
            }
        }

        try {
            return valueOf(nativeUtil.stat(syspath.toAbsolutePath().toString()).getSize());
        } catch (UnknownNativeErrorException e) {
            return u_err("size:"  + relative, e.intCode(), nativeUtil.strerror_r(e.intCode()));
        } catch (InvalidPathException e) {
            return u_err("size:" + relative +":(errno=36): File name too long");
        } catch (FileSystemLoopException e) {
            return u_err("size:" + relative +":(errno=40): Too many symbolic links encountered");
        } catch (AccessDeniedException e) {
            return u_err("size:" + relative +":(errno=13): Permission denied");
        } catch (FileNotFoundException e) {
            return u_err("size:" + relative +":(errno=2): No such file or directory");
        } catch (IOException e) {
            return u_err("size:" + relative +":(errno=5): I/O error");
        }
    }

    @Override
    protected Varargs lib_fs_unlockdirs(Varargs args) {
        FastLuaString fs = u_concat_path(args);
        String js = fs.toString();

        LuaPath file = u_resolvePath(js);
        if (!file.exists()) {
            return u_err("unlockdirs:" + js +":(errno=2): No such file or directory");
        }
        if (!file.isDir()) {
            return u_err("unlockdirs:" + js +":(errno=20): Not a directory");
        }

        Path syspath = file.toSystemPath();
        if (syspath == null) {
            return TRUE;
        }

        final Set<Path> thePaths = new HashSet<>();

        try {
            Files.walkFileTree(syspath, OPTIONS, Integer.MAX_VALUE, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!thePaths.add(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    thePaths.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return u_err("unlockdirs:" + js +":(errno=5): I/O error");
        }

        for (Path path : thePaths) {
            String thePath = path.toAbsolutePath().toString();

            try {
                Stat stat = nativeUtil.stat(thePath);
                nativeUtil.chmod(thePath, (int) (stat.getMode() | LinuxConst.S_IWUSR));
            } catch (InvalidPathException e) {
                return u_err("unlockdirs:" + thePath +":(errno=36): File name too long");
            } catch (PermissionDeniedException e) {
                return u_err("unlockdirs:" + thePath +":(errno=1): Operation not permitted");
            } catch (FileSystemLoopException e) {
                return u_err("unlockdirs:" + thePath +":(errno=40): Too many symbolic links encountered");
            } catch (AccessDeniedException e) {
                return u_err("unlockdirs:" + thePath +":(errno=13): Permission denied");
            } catch (FileNotFoundException e) {
                return u_err("unlockdirs:" + thePath +":(errno=2): No such file or directory");
            } catch (UnknownNativeErrorException e) {
                return u_err("unlockdirs:"  + thePath, e.intCode(), nativeUtil.strerror_r(e.intCode()));
            } catch (IOException e) {
                return u_err("unlockdirs:" + thePath +":(errno=5): I/O error");
            }
        }

        return TRUE;
    }

    @Override
    protected Varargs lib_fs_symlink(Varargs args) {
        String placeLinkHereString = args.checkjstring(2);
        LuaPath target = u_resolvePath(args.checkjstring(1));
        LuaPath placeLinkHere = u_resolvePath(placeLinkHereString);

        Path sPlaceLinkHere = placeLinkHere.toSystemPath();
        Path sTarget = target.toSystemPath();
        if (sPlaceLinkHere == null || sTarget == null) {
            try {
                placeLinkHere.symlink(target);
            } catch (IOException e) {
                return u_err("symlink:" + placeLinkHereString +":(errno=5): I/O error");
            }

            return TRUE;
        }

        try {
            nativeUtil.symlink(sTarget.toString(), sPlaceLinkHere.toString());
        } catch (FileAlreadyExistsException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=17): File exists");
        } catch (InvalidPathException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=22): Invalid argument");
        } catch (AccessDeniedException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=13): Permission denied");
        } catch (ReadOnlyFileSystemException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=30): Read-only file system");
        } catch (IOException e) {
            return u_err("symlink:" + placeLinkHereString +":(errno=5): I/O error");
        } catch (UnknownNativeErrorException e) {
            return u_err("symlink:"  + placeLinkHereString, e.intCode(), nativeUtil.strerror_r(e.intCode()));
        }

        return TRUE;
    }


}
