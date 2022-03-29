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


import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LuajLPathStandaloneTest {

    public static void main(String[] args) throws Exception {
        Globals globals = JsePlatform.debugGlobals();
        globals.load(new LuajLPathLib());

        //LuaJ is a bit retarded here, normally xpcall calls a hook function with the error object and the return value of
        //the hook function is returned. Luaj always calls tojstring() on the return value of the hook function.
        //Luaunit uses this to pass around a table. needless to say, this won't work so we fix it.
        globals.set("xpcall", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs varargs) {
                try {
                    return varargsOf(TRUE, varargs.arg1().invoke(varargs.subargs(3)));
                } catch (LuaError le) {
                    try {
                        Field f = le.getClass().getDeclaredField("traceback");
                        f.setAccessible(true);
                        //C lua does not concat the traceback to the message passed to the hook function...
                        f.set(le, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return varargsOf(FALSE, varargs.arg(2).invoke(le.getMessageObject()));
                } catch (Exception e) {
                    return varargsOf(FALSE, varargs.arg(2).invoke(new LuaError(e).getMessageObject()));
                }
            }
        });

        globals.set("pcall", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs varargs) {
                try {
                    return varargsOf(TRUE, varargs.arg1().invoke(varargs.subargs(2)));
                } catch (LuaError le) {
                    try {
                        Field f = le.getClass().getDeclaredField("traceback");
                        f.setAccessible(true);
                        //C lua does not concat the traceback to the returned message
                        f.set(le, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return varargsOf(FALSE, le.getMessageObject());
                } catch (Exception e) {
                    return varargsOf(FALSE, new LuaError(e).getMessageObject());
                }
            }
        });

        LuaTable mod = globals.load(loadURL("https://raw.githubusercontent.com/starwing/lpath/master/luaunit.lua"), "luaunit.lua").call().checktable();

        globals.package_.setIsLoaded("luaunit", mod);
        globals.load(loadURL("https://raw.githubusercontent.com/starwing/lpath/master/test.lua"), "test.lua").call();

        new File("test").delete();
    }

    public static String loadURL(String url) throws Exception {
        byte[] buf = new byte[512];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try(InputStream inputStream = new URL(url).openStream()) {
            int i = 0;
            while(i != -1) {
                i = inputStream.read(buf);
                if (i > 0) {
                    baos.write(buf, 0, i);
                }
            }
        }

        //I dont care that the encoding may be different!
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}
