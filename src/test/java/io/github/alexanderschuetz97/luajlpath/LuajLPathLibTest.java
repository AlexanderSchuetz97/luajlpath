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

import org.junit.Assert;
import org.junit.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuajLPathLibTest {


    public static LuaValue v(String a) {
        return LuaValue.valueOf(a);
    }

    public static Varargs v(LuaValue... v) {
        return LuaValue.varargsOf(v);
    }

    public static Varargs v(String... v) {
        LuaValue[] a = new LuaValue[v.length];
        for (int i = 0; i < v.length; i++) {
            a [i] = v(v[i]);
        }
         return v(a);
    }



    private static Globals globals() {
        Globals gl = JsePlatform.standardGlobals();
        LuaC.install(gl);
        gl.load(new LuajLPathLib());
        return gl;
    }

    @Test
    public void main() {
        System.out.println(System.getProperty("java.home"));
        Globals gl = globals();
        //System.out.println(gl.get("require").call("path").get("fnmatch").invoke(v("tm*************ionmlol", "tm*on*lol")).arg1());
        gl.load("\nx = require('path.fs')" +
                "\nx.chdir('/tmp/lua_288068100001/test_glob')" +
                "\nfor i,v in x.scandir(1) do" +
                "\nprint(i,v)" +
                "\nend" +
                "\nreturn t" +
                "\n" +
                "\n", "test.lua").call();

    }

    @Test
    public void testParent() {
        Globals gl = globals();
        String s = gl.get("require").call("path").get("parent").call("a").checkjstring();
        Assert.assertEquals(".", s);
    }

    @Test
    public void testSuffix() {
        Globals gl = globals();
        String s = gl.get("require").call("path").get("suffix").call("a/b.c").checkjstring();
        Assert.assertEquals(".c", s);
    }

    @Test
    public void testPath() {
        Globals gl = globals();
        String s = gl.get("require").call("path").call("/a/../../b").checkjstring();
        Assert.assertEquals("/b", s);
    }

    @Test
    public void testPath2() {
        Globals gl = globals();
        String s = gl.get("require").call("path").call("a/.").checkjstring();
        Assert.assertEquals("a/", s);
    }

    @Test
    public void testSuffixesWindows() {
        LuaFunction res = new JseWindowsLPathImpl().lib_suffixes(v("c:a/b.py")).checkfunction(1);
        Assert.assertEquals(".py", res.invoke().arg(2).checkjstring());
        Assert.assertTrue(res.call().isnil());

    }


    private void testMatch(String s1, String s2, boolean m) {
        Globals gl = globals();
        boolean s = gl.get("require").call("path").get("match").call(LuaValue.valueOf(s1), LuaValue.valueOf(s2)).toboolean();
        Assert.assertEquals(s1 + "      " + s2, m, s);
    }

    @Test
    public void testMatches() {
        testMatch("abc", "*", true);
        testMatch("a", "", true);
        testMatch("a", ".", false);
        testMatch(".", ".", true);
        testMatch("../../a", "../a", false);
        testMatch("../../a", "../../a", true);
        testMatch("../../a/", "../../a", false);
        testMatch("b.lua", "*.lua", true);
        testMatch("b.lua", "/*.lua", false);

    }

    @Test
    public void testDodgyMatch() {
        testMatch("a*c","a[*]c", true);
        testMatch("a***c","a*[*]*c", true);
        testMatch("a****c","a*[*]*c", false);
        testMatch("a*****c","a**[*]**c", true);
        testMatch("a******c","a**[*]**c", false);
    }

    @Test
    public void testFullyRetardedMatch() {
        testMatch("aAc","a[A-Z]c", true);
        testMatch("aBc","a[A-Z]c", true);
        testMatch("aCc","a[A-Z]c", true);
        testMatch("aDc","a[A-Z]c", false);
    }

    @Test
    public void testSadness() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("a");
        }


        testMatch(sb.toString(),"*a*a*a*a*a*a*a*a*a*a", true);
        sb.append("b");
        testMatch(sb.toString(),"*a*a*a*a*a*a*a*a*a*a", false);
    }

    @Test
    public void testWindowsDrive() {
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_drive(v("c:")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_drive(v("c:a/b")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_drive(v("c:/")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_drive(v("c:/a/b")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_drive(v("c:/a/b")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_drive(v("//.")).toString());
        Assert.assertEquals("\\\\.\\", new JseWindowsLPathImpl().lib_drive(v("//./")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_drive(v("//.//")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_drive(v("//?")).toString());
        Assert.assertEquals("\\\\?\\", new JseWindowsLPathImpl().lib_drive(v("//?/")).toString());
        Assert.assertEquals("\\\\?\\C:", new JseWindowsLPathImpl().lib_drive(v("//?/c:")).toString());
        Assert.assertEquals("\\\\?\\C:",  new JseWindowsLPathImpl().lib_drive(v("//?/c:/")).toString());
        Assert.assertEquals("",  new JseWindowsLPathImpl().lib_drive(v("a/b")).toString());
        Assert.assertEquals("C:",  new JseWindowsLPathImpl().lib_drive(v("c:a/b")).toString());
        Assert.assertEquals("C:",  new JseWindowsLPathImpl().lib_drive(v("c:/a/b")).toString());
        Assert.assertEquals("",  new JseWindowsLPathImpl().lib_drive(v("cd:/a/b")).toString());
        Assert.assertEquals("\\\\A\\",  new JseWindowsLPathImpl().lib_drive(v("//a/")).toString());
        Assert.assertEquals("\\\\A\\B",new JseWindowsLPathImpl().lib_drive(v( "//a/b")).toString());
        Assert.assertEquals("\\\\A\\B",new JseWindowsLPathImpl().lib_drive(v( "//a/b/a/b")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?/a")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?//a")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?///a/")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?///a/b")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?///a/b/")).toString());
        Assert.assertEquals("\\\\?\\C:",new JseWindowsLPathImpl().lib_drive(v( "//?/c:")).toString());
        Assert.assertEquals("\\\\?\\C:",new JseWindowsLPathImpl().lib_drive(v( "//?/c:/")).toString());
        Assert.assertEquals("\\\\?\\",new JseWindowsLPathImpl().lib_drive(v( "//?/cd:/")).toString());
    }

    @Test
    public void testWindowRoot() {
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("c:")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("c:a/b")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("c:/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("c:/a/b/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//a/b")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//a/b/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//a/b/c/d")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//.")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//./")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//.//")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//?")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("//?/")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("//?/c:")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//?/c:/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("/a")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//a")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("///a")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("c:/")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("//aaa/bb/")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("aa/bb/")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("c:")).toString());
        Assert.assertEquals("", new JseWindowsLPathImpl().lib_root(v("c:aa/bb")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_root(v("///aaa/bb")).toString());
    }

    @Test
    public void testWindowsAnchor() {
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_anchor(v("/a")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_anchor(v("//a")).toString());
        Assert.assertEquals("\\", new JseWindowsLPathImpl().lib_anchor(v("///a")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_anchor(v("c:")).toString());
        Assert.assertEquals("C:", new JseWindowsLPathImpl().lib_anchor(v("c:a/b")).toString());
        Assert.assertEquals("C:\\", new JseWindowsLPathImpl().lib_anchor(v("c:/")).toString());
        Assert.assertEquals("C:\\", new JseWindowsLPathImpl().lib_anchor(v("c:/a/b")).toString());
        Assert.assertEquals("\\\\A\\B\\", new JseWindowsLPathImpl().lib_anchor(v("//a/b")).toString());
        Assert.assertEquals("\\\\A\\B\\", new JseWindowsLPathImpl().lib_anchor(v("//a/b/")).toString());
        Assert.assertEquals("\\\\A\\B\\", new JseWindowsLPathImpl().lib_anchor(v("//a/b/c/d")).toString());
    }

    @Test
    public void testWindowsAbsDrive() {
        Assert.assertEquals("C:\\d\\c", new JseWindowsLPathImpl().lib_path(v("C:\\a\\b", "/d/c")).checkjstring(1));
        Assert.assertEquals("D:x\\y", new JseWindowsLPathImpl().lib_path(v("C:\\a\\b", "D:x/y")).checkjstring(1));
        Assert.assertEquals("\\\\SHARE\\MOUNT\\PATH", new JseWindowsLPathImpl().lib_path(v("C:\\a\\b", "//SHARE/MOUNT/PATH")).checkjstring(1));
        Assert.assertEquals("C:\\a\\b\\d\\c", new JseWindowsLPathImpl().lib_path(v("C:\\a\\b", "C:d\\c")).checkjstring(1));
        Assert.assertEquals("D:c", new JseWindowsLPathImpl().lib_path(v("c:a", "d:", "c")).checkjstring(1));
        Assert.assertEquals("\\\\?\\foo\\bar", new JseWindowsLPathImpl().lib_path(v("//?/foo", "//?/bar")).checkjstring(1));
    }

    @Test
    public void testLinuxAbsDrive() {
        Assert.assertEquals("/b", new JsePosixLPathImpl().lib_path(v("", "a/", "/b")).checkjstring(1));
    }


    @Test
    public void testGlobMatchIn() {
        FastLuaString path = new FastLuaString("test_glob/case_1/a");
        FastLuaString pattern = new FastLuaString("test_glob/case_1/**/a");
        AbstractLPathImpl.LPathPattern pat1 = LPathMatcher.compile(pattern.bytes, pattern.off, pattern.len, true, false);
        boolean b = pat1.match(path.bytes, path.off, path.len);
        Assert.assertTrue(b);
        AbstractLPathImpl.LPathPattern pat2 = LPathMatcher.compile(pattern.bytes, pattern.off, pattern.len, true, true);
        b = pat2.match(path.bytes, path.off, path.len);
        Assert.assertTrue(b);
    }


    @Test
    public void testGlobMatch2() {
        FastLuaString path = new FastLuaString("test/test2/file3");
        FastLuaString pattern = new FastLuaString("test/**/*f[i]le*");
        AbstractLPathImpl.LPathPattern compile = LPathMatcher.compile(pattern.bytes, pattern.off, pattern.len, true, true);
        Assert.assertTrue(compile.match(path.bytes, path.off, path.len));
        Assert.assertTrue(compile.match(path.bytes, path.off, path.len));

    }

    @Test
    public void testGlobMatch3() {
        FastLuaString path = new FastLuaString("test_glob/case_1/a/a/a/a/c/a/b");
        FastLuaString pattern = new FastLuaString("test_glob/**/a/b");
        AbstractLPathImpl.LPathPattern compile = LPathMatcher.compile(pattern.bytes, pattern.off, pattern.len, true, true);
        Assert.assertTrue(compile.match(path.bytes, path.off, path.len));

    }

    @Test
    public void testSlash() {
        testMatch("a/b/c","a/b/c", true);
    }

    @Test
    public void testSuffixIter() {
        Globals gl = globals();
        LuaTable lt = gl.load("\nx = require('path')" +
                "\nt = {}" +
                "\nfor i,v in x.suffixes('a.xxx.yyy.zzz') do" +
                "\nt[i] = v" +
                "\nend" +
                "\nreturn t" +
                "\n" +
                "\n", "test.lua").call().checktable();


        Assert.assertEquals(".xxx", lt.get(1).checkjstring());
        Assert.assertEquals(".yyy", lt.get(2).checkjstring());
        Assert.assertEquals(".zzz", lt.get(3).checkjstring());
        Assert.assertTrue(lt.get(4).isnil());
    }

    @Test
    public void testPartsIter() {
        Globals gl = globals();
        LuaTable lt = gl.load("\nx = require('path')" +
                "\nt = {}" +
                "\nfor i,v in x.parts('a/xxx/yyy/zzz') do" +
                "\nprint(i,v)" +
                "\nt[i] = v" +
                "\nend" +
                "\nreturn t" +
                "\n" +
                "\n", "test.lua").call().checktable();


        Assert.assertEquals("a", lt.get(1).checkjstring());
        Assert.assertEquals("xxx", lt.get(2).checkjstring());
        Assert.assertEquals("yyy", lt.get(3).checkjstring());
        Assert.assertEquals("zzz", lt.get(4).checkjstring());
        Assert.assertTrue(lt.get(5).isnil());
    }

    @Test
    public void testPartsDir() {
        Globals gl = globals();
        LuaTable lt = gl.load("x = require('path.fs')" +
                "\nt = {}" +
                "\nfor i,v in x.dir('/') do" +
                "\nprint(i,v)" +
                "\nt[i] = v" +
                "\nend" +
                "\nreturn t" +
                "\n" +
                "\n", "test.lua").call().checktable();


        Assert.assertEquals("dir", lt.get("/tmp").checkjstring());
    }

}
