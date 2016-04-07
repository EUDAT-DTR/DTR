/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/**
 * Classloader which looks for classes and resources first in the server directory (and its classes and lib subdirectories).
 * Potentially allows to override server functionality.
 * Could be used to add custom storage or indexing capabilities.
 */
public class ServerClassLoader extends URLClassLoader {
    public ServerClassLoader(File serverDir) {
        super(new URL[0],determineParent());
        try {
            addURL(serverDir.toURI().toURL());
            File classes = new File(serverDir,"classes");
            if(classes.isDirectory()) {
                addURL(classes.toURI().toURL());
            }
            File[] files = new File(serverDir,"lib").listFiles();
            if(files!=null) {
                for(File file : files) {
                    if(file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                        addURL(file.toURI().toURL());
                    }
                }
            }
        }
        catch(MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
    
    private static ClassLoader determineParent() {
        ClassLoader res = Thread.currentThread().getContextClassLoader();
        if(res==null) res = ServerClassLoader.class.getClassLoader();
        if(res==null) res = ClassLoader.getSystemClassLoader();
        return res;
    }
    
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class c = findLoadedClass(name);
        if (c == null) {
            if(name.equals(LogbackContextInitializer.class.getName())) return loadLogbackContextInitializer();
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                c = getParent().loadClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
    
    private Class<?> loadLogbackContextInitializer() throws ClassNotFoundException {
        try {
            InputStream in = getResourceAsStream(LogbackContextInitializer.class.getName().replace('.','/')+".class");
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while((r = in.read(buf)) > 0) {
                bout.write(buf,0,r);
            }
            return defineClass(LogbackContextInitializer.class.getName(),bout.toByteArray(),0,bout.toByteArray().length);
        }
        catch(IOException e) {
            throw new ClassNotFoundException(LogbackContextInitializer.class.getName(),e);
        }
    }
    
    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        // only reveal logging configuration files to the server, not to users of the jar file, or to webapps running in the server
        if(url==null && ("logback.xml".equals(name) || "log4j.xml".equals(name)) && Thread.currentThread().getContextClassLoader()==this) {
            url = getResource(name.substring(0,name.indexOf('.')) + ".do-server-default.xml");
        }
        if (url == null) {
            url = getParent().getResource(name);
        }
        return url;    
    }
    
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> mine = findResources(name);
        // only reveal logging configuration files to the server, not to users of the jar file, or to webapps running in the server
        if(!mine.hasMoreElements() && ("logback.xml".equals(name) || "log4j.xml".equals(name)) && Thread.currentThread().getContextClassLoader()==this) {
            Enumeration<URL> res = getResources(name.substring(0,name.indexOf('.')) + ".do-server-default.xml");
            if(res!=null && res.hasMoreElements()) return res;
            else mine = res;
        }
        final Enumeration<URL> finalMine = mine;
        final Enumeration<URL> parents = getParent().getResources(name);
        return new Enumeration<URL>() {
            @Override
            public boolean hasMoreElements() {
                return finalMine.hasMoreElements() || parents.hasMoreElements();
            }
            @Override
            public URL nextElement() {
                if(finalMine.hasMoreElements()) return finalMine.nextElement();
                else return parents.nextElement();
            }
        };
    }
}
