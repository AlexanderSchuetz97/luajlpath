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
import io.github.alexanderschuetz97.luajfshook.api.LuajFSHook;
import io.github.alexanderschuetz97.nativeutils.api.NativeUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.File;

public class LuajLPathLib extends TwoArgFunction {

    private LuaFileSystemHandler handler;

    private AbstractLPathImpl impl;

    protected LuaFileSystemHandler createFileSystemHandler(Globals globals) {
        return LuajFSHook.getOrInstall(globals);
    }

    public LuaFileSystemHandler getFileSystemHandler() {
        return handler;
    }

    protected AbstractLPathImpl createImpl(Globals globals) {
        if (NativeUtils.isLinux()) {
            return new LinuxLPathImpl();
        }

        if (NativeUtils.isWindows()) {
            return new WindowsLPathImpl();
        }

        //Syscalls not available fallback to JSE impl...

        if (detectPosix()) {
            return new JsePosixLPathImpl();
        }

        return new JseWindowsLPathImpl();
    }

    protected boolean detectPosix() {
        //probably good enough
        return File.separatorChar == '/';
    }


    @Override
    public LuaValue call(LuaValue arg1, LuaValue arg2) {
        Globals globals = arg2.checkglobals();
        handler = createFileSystemHandler(globals);
        impl = createImpl(globals);


        if (impl == null) {
            return error("impl is null");
        }

        if (handler == null) {
            return error("LuaFileSystemHandler is null");
        }

        impl.init(globals, handler);

        LuaValue path = getPathTable();

        globals.package_.setIsLoaded("path", path.checktable());
        globals.package_.setIsLoaded("path.fs", getFsTable().checktable());
        globals.package_.setIsLoaded("path.env", getEnvTable().checktable());
        globals.package_.setIsLoaded("path.info", getInfoTable().checktable());

        return path;
    }

    protected LuaValue getPathTable() {
        LuaTable path = new LuaTable();
        LuaValue meta = getPathMetaTable();
        if (meta != null) {
            path.setmetatable(meta);
        }

        path.set("resolve", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_resolve(args);
            }
        });

        path.set("ansi", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_ansi(args);
            }
        });

        path.set("utf8", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_utf8(args);
            }
        });

        path.set("abs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_abs(args);
            }
        });

        path.set("rel", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_rel(args);
            }
        });

        path.set("fnmatch", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fnmatch(args);
            }
        });

        path.set("match", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_match(args);
            }
        });

        path.set("drive", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_drive(args);
            }
        });

        path.set("root", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_root(args);
            }
        });

        path.set("anchor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_anchor(args);
            }
        });

        path.set("parent", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_parent(args);
            }
        });

        path.set("name", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_name(args);
            }
        });

        path.set("stem", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_stem(args);
            }
        });

        path.set("suffix", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_suffix(args);
            }
        });

        path.set("suffixes", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_suffixes(args);
            }
        });

        path.set("parts", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_parts(args);
            }
        });

        path.set("exists", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_exists(args);
            }
        });

        path.set("cwd", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_getcwd(args);
            }
        });

        path.set("bin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_binpath(args);
            }
        });

        path.set("isdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_isdir(args);
            }
        });

        path.set("islink", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_islink(args);
            }
        });

        path.set("isfile", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_isfile(args);
            }
        });

        path.set("ismount", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_ismount(args);
            }
        });


        return path;
    }

    protected LuaValue getPathMetaTable() {
        LuaValue meta = new LuaTable();
        meta.set(CALL, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_path(args.subargs(2));
            }
        });
        return meta;
    }

    protected LuaValue getFsTable() {
        LuaTable fs = new LuaTable();

        fs.set("dir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_dir(args);
            }
        });

        fs.set("scandir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_scandir(args);
            }
        });

        fs.set("glob", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.fs_glob(args);
            }
        });

        fs.set("chdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_chdir(args);
            }
        });

        fs.set("mkdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_mkdir(args);
            }
        });

        fs.set("rmdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_rmdir(args);
            }
        });

        fs.set("makedirs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_makedirs(args);
            }
        });

        fs.set("removedirs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_removedirs(args);
            }
        });

        fs.set("unlockdirs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_unlockdirs(args);
            }
        });

        fs.set("tmpdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_tmpdir(args);
            }
        });

        fs.set("ctime", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_ctime(args);
            }
        });

        fs.set("mtime", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_mtime(args);
            }
        });

        fs.set("atime", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_atime(args);
            }
        });

        fs.set("size", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_size(args);
            }
        });

        fs.set("touch", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_touch(args);
            }
        });

        fs.set("remove", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_remove(args);
            }
        });

        fs.set("copy", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_copy(args);
            }
        });

        fs.set("rename", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_rename(args);
            }
        });

        fs.set("symlink", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_symlink(args);
            }
        });

        fs.set("exists", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_exists(args);
            }
        });

        fs.set("getcwd", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_getcwd(args);
            }
        });

        fs.set("binpath", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_binpath(args);
            }
        });

        fs.set("isdir", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_isdir(args);
            }
        });

        fs.set("islink", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_islink(args);
            }
        });

        fs.set("isfile", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_isfile(args);
            }
        });

        fs.set("ismount", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_fs_ismount(args);
            }
        });


        return fs;

    }

    protected LuaValue getInfoTable() {
        LuaTable info = new LuaTable();
        info.set("platform", impl.info_getOS());
        info.set("sep", impl.info_getSeperator());
        info.set("devnull", impl.info_getDevNull());
        info.set("pathsep", impl.info_getPathSeperator());
        //These are constant on all platforms
        info.set("altsep", "/");
        info.set("curdir", ".");
        info.set("pardir", "..");
        info.set("extsep", ".");
        return info;
    }

    protected LuaValue getEnvTable() {
        LuaTable env = new LuaTable();
        env.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_env_get(args);
            }
        });
        env.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_env_set(args);
            }
        });
        env.set("expand", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_env_expand(args);
            }
        });
        env.set("uname", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return impl.lib_env_uname(args);
            }
        });
        return env;
    }

}
