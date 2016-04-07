/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerOutputStream extends OutputStream {
    static final Logger logger = LoggerFactory.getLogger("System.err");
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    
    @Override
    public synchronized void flush() throws IOException {
        byte[] bytes = bout.toByteArray();
        bout.reset();
        String string = new String(bytes,"UTF-8");
        int index = rightmostNonSpace(string);

        if(index<0) return;
        else if(index + 1 < string.length()) logger.error(string.substring(0,index+1));
        else logger.error(string);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        bout.write(b,off,len);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        // TODO Auto-generated method stub
        bout.write(b);
    }

    @Override
    public synchronized void write(int b) throws IOException {
        bout.write(b);
    }
    
    private static int rightmostNonSpace(String string) {
        for(int index = string.length() - 1; index >= 0; index--) {
            char ch = string.charAt(index);
            if(ch>' ') return index;
        }
        return -1;
    }
}
