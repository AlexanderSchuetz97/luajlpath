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
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import io.github.alexanderschuetz97.nativeutils.api.WinConst;
import io.github.alexanderschuetz97.nativeutils.api.WindowsNativeUtil;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.InvalidFileDescriptorException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.SharingViolationException;
import io.github.alexanderschuetz97.nativeutils.api.exceptions.UnknownNativeErrorException;
import io.github.alexanderschuetz97.nativeutils.api.structs.RegData;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.luaj.vm2.LuaValue.FALSE;
import static org.luaj.vm2.LuaValue.TRUE;
import static org.luaj.vm2.LuaValue.valueOf;
import static org.luaj.vm2.LuaValue.varargsOf;

/**
 * Implements platform specific lpath functions using windows api calls.
 */
public class WindowsLPathImpl extends JseWindowsLPathImpl {

    protected final WindowsNativeUtil nativeUtil = NativeUtils.getWindowsUtil();

    @Override
    protected LuaValue info_getOS() {
        return WINDOWS;
    }

    @Override
    protected LuaValue info_getDevNull() {
        return DEV_NULL_WINDOWS;
    }

    @Override
    protected boolean u_isPathCaseSensitive() {
        return false;
    }

    @Override
    protected Varargs lib_drive(Varargs args) {
        return u_drive(args);
    }


    @Override
    protected Varargs lib_fs_ismount(Varargs args) {
        FastLuaString path = u_concat_path(args);

        if (path.startsWithWindowsUnicodePrefix()) {
            path = path.sub(4);
        }

        if (path.startsWithDoubleSep()) {
            boolean fs = false;
            for (int i = 3; i < path.len; i++) {
                if (path.bytes[path.off+i] == '\\') {
                    if (fs) {
                        return i+1 == path.len ? TRUE : FALSE;
                    }

                    fs = true;
                }
            }

            if (fs) {
                return path;
            }
        }

        String str = path.toString();

        if (str.startsWith(".")) {
            return FALSE;
        }

        String res;
        try {
            res = nativeUtil.GetVolumePathNameW("\\\\?\\" + str);
            if (res.startsWith("\\\\?\\")) {
                res = res.substring(4);
            }
        } catch (UnknownNativeErrorException e) {
            return u_err("GetVolumePathNameW", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }
        if (str.equals(res)) {
            return path;
        }

        return FALSE;
    }

    @Override
    protected Varargs lib_fs_binpath(Varargs args) {

        String path;
        try {
            path = nativeUtil.GetModuleFileNameA(0);
        } catch (UnknownNativeErrorException e) {
            return u_err("GetModuleFileNameA", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }

        return u_valueOfStr(path);
    }

    @Override
    protected Varargs lib_env_get(Varargs args) {
        String key = args.checkjstring(1);
        try {
            return u_valueOfStr(nativeUtil.GetEnvironmentVariableA(key));
        } catch (UnknownNativeErrorException e) {
            return u_err("GetEnvironmentVariableA", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }
    }

    @Override
    protected Varargs lib_env_set(Varargs args) {
        String key = args.checkjstring(1);
        String value = args.optjstring(2, null);

        try {
            nativeUtil.SetEnvironmentVariableA(key, value);
            return args.arg(2);
        } catch (UnknownNativeErrorException e) {
            return u_err("SetEnvironmentVariableA", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }
    }

    @Override
    protected Varargs lib_env_expand(Varargs args) {
        String jstr = u_concat_path(args).checkjstring(1);
        try {
            return u_valueOfStr(nativeUtil.ExpandEnvironmentStringsA(jstr));
        } catch (UnknownNativeErrorException e) {
            return u_err("ExpandEnvironmentStringsA", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }
    }

    @Override
    protected Varargs lib_env_uname(Varargs args) {

        int major;
        int minor;
        int build;

        try {
            long hkey = nativeUtil.RegOpenKeyExA(WinConst.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", 0, WinConst.KEY_QUERY_VALUE);
            try {
                RegData data = nativeUtil.RegQueryValueExA(hkey, "CurrentBuildNumber");
                if (!(data.value() instanceof Number)) {
                    return u_err("uname", 13, nativeUtil.FormatMessageA(13));
                }

                build = data.asInt();

                data = nativeUtil.RegQueryValueExA(hkey, "CurrentMajorVersionNumber");
                if (!(data.value() instanceof Number)) {
                    return u_err("uname", 13, nativeUtil.FormatMessageA(13));
                }

                major = data.asInt();

                data = nativeUtil.RegQueryValueExA(hkey, "CurrentMinorVersionNumber");
                if (!(data.value() instanceof Number)) {
                    return u_err("uname", 13, nativeUtil.FormatMessageA(13));
                }

                minor = data.asInt();

            } finally {
                nativeUtil.RegCloseKey(hkey);
            }
        } catch (UnknownNativeErrorException e) {
            return u_err("uname", e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }


        return varargsOf(new LuaValue[] {
                u_valueOfStr("Windows " + major + "." + minor + " Build " + build),
                valueOf(major),
                valueOf(minor),
                valueOf(build),
        });
    }

    @Override
    protected Varargs lib_resolve(Varargs args) {
        FastLuaString fsl = u_concat_path(args);
        String relative = fsl.toString();
        LuaPath path = u_resolvePath(relative);
        Path syspath = path.toSystemPath();
        if (syspath == null) {
            try {
                return u_valueOfStr(path.realPath().toString());
            } catch (IOException e) {
                return u_err("resolve:" + relative + ":(errno=5): I/O error");
            }
        }

        try {
            //no idea why lpath.c uses FILE_FLAG_BACKUP_SEMANTICS
            long hdl = -1;
            try {
                hdl = nativeUtil.CreateFileW("\\\\?\\" + syspath, 0, true, true, true, WindowsNativeUtil.CreateFileA_createMode.OPEN_EXISTING, WinConst.FILE_FLAG_BACKUP_SEMANTICS);
            } catch (UnknownNativeErrorException err) {
                return u_err("resolve:"  + relative, err.intCode(), nativeUtil.FormatMessageA(err.intCode()));
            }

            try {
                String str = nativeUtil.GetFinalPathNameByHandleW(hdl, true, WindowsNativeUtil.Path_VolumeName.VOLUME_NAME_DOS);
                // C:\Windows -> \\?\C:\Windows gotta crop the shit
                if (!fsl.startsWithWindowsUnicodePrefix() && str.startsWith("\\\\?\\")) {
                    str = str.substring(4);
                }
                return u_valueOfStr(str);
            } finally {
                try {
                    nativeUtil.CloseHandle(hdl);
                } catch (Exception e) {
                    //DON'T CARE, because there is nothing we can do!
                }
            }
        } catch (InvalidFileDescriptorException e) {
            return u_err("resolve:" + relative + ":(errno=6): The handle is invalid.");
        } catch (FileAlreadyExistsException e) {
            //Nonsense because we pass OPEN_EXISTING
            return u_err("resolve:" + relative + ":(errno=80): The file exists.");
        } catch (SharingViolationException e) {
            return u_err("resolve:" + relative + ":(errno=32): The process cannot access the file because it is being used by another process.");
        } catch (UnknownNativeErrorException e) {
            return u_err("resolve:"  + relative, e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        }
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
            return valueOf(nativeUtil._stat64(syspath.toAbsolutePath().toString()).getSize());
        } catch (UnknownNativeErrorException e) {
            return u_err("size:"  + relative, e.intCode(), nativeUtil.strerror_s(e.intCode()));
        } catch (InvalidPathException e) {
            return u_err("size:" + relative +":(errno=36): File name too long");
        } catch (FileNotFoundException e) {
            return u_err("size:" + relative +":(errno=2): No such file or directory");
        } catch (Exception e) {
            return u_err("size:" + relative +":(errno=5): I/O error");
        }
    }

    protected Varargs lib_fs_copy(Varargs args) {
        LuaString source = args.checkstring(1);
        LuaString target = args.checkstring(2);

        String sourceString = source.checkjstring();

        String targetString = target.checkjstring();

        LuaPath srcFile = u_resolvePath(source.checkjstring());
        LuaPath targetFile = u_resolvePath(target.checkjstring());


        if (!srcFile.exists()) {
            //Missing path is intentional. Even has a unit test for some reason.
            return u_err("copy::(errno=2): No such file or directory");
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

    @Override
    protected Varargs lib_fs_unlockdirs(Varargs args) {
        final String relative = u_concat_path(args).checkjstring(1);
        LuaPath absolute = u_resolvePath(relative);

        final AtomicReference<Varargs> err = new AtomicReference<>();
        try {
            absolute.walkFileTree(Integer.MAX_VALUE, false, new LuaPath.LuaFileVisitor() {

                //true continue walking, false stop
                private boolean unlock(LuaPath f) {
                    Path sys = f.toSystemPath();
                    if (sys == null) {
                        return true;
                    }

                    String syss = sys.toString();

                    int attr;
                    try {
                        attr = nativeUtil.GetFileAttributesA(syss);
                    } catch (UnknownNativeErrorException e) {
                        err.set(u_err("GetFileAttributesA:"  + relative, e.intCode(), nativeUtil.FormatMessageA(e.intCode())));
                        return false;
                    }

                    try {
                        nativeUtil.SetFileAttributesA(syss, attr &~ WinConst.FILE_ATTRIBUTE_READONLY);
                    } catch (UnknownNativeErrorException e) {
                        err.set(u_err("SetFileAttributesA:"  + relative, e.intCode(), nativeUtil.FormatMessageA(e.intCode())));
                        return false;
                    }

                    return true;
                }

                @Override
                public FileVisitResult preVisitDirectory(LuaPath dir) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(LuaPath dir) throws IOException {
                    return unlock(dir) ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(LuaPath dir) throws IOException {
                    return unlock(dir) ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                }
            });
        } catch (IOException e) {
            if (err.get() != null) {
                return err.get();
            }
            return u_err("unlockdirs:" + relative +":(errno=5): I/O error");
        }

        if (err.get() != null) {
            return err.get();
        }

        return TRUE;
    }

    @Override
    protected Varargs lib_fs_symlink(Varargs args) {
        LuaPath source = u_resolvePath(args.checkjstring(1));
        LuaPath target = u_resolvePath(args.checkjstring(1));

        Path sSource = source.toSystemPath();
        Path sTarget = target.toSystemPath();
        if (sSource == null || sTarget == null) {
            try {
                source.symlink(target);
            } catch (IOException e) {
                return u_err("symlink:" + sTarget +":(errno=5): I/O error");
            }

            return TRUE;
        }

        try {
            nativeUtil.CreateSymbolicLinkA(sTarget.toString(), sSource.toString(), source.isDir(), false);
        } catch (UnknownNativeErrorException e) {
            return u_err("symlink:"  + sSource, e.intCode(), nativeUtil.FormatMessageA(e.intCode()));
        } catch (Exception e) {
            return u_err("symlink:" + sTarget + ":(errno=5): I/O error");
        }

        return TRUE;
    }
}
