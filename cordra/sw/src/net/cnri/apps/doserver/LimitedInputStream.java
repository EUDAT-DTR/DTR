/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
    private final long start;
    private final long end;
    private long pos;
    private long mark = -1;
    private int lastReadLimit = 0;
    
    public LimitedInputStream(InputStream in, long start, long len) {
        super(in);
        this.start = start < 0 ? 0 : start;
        if(len < 0) {
            this.end = Long.MAX_VALUE;
        }
        else {
            this.end = Math.min(len,Long.MAX_VALUE - this.start) + this.start;
        }
    }

    private void skipStart() throws IOException {
        if(pos >= start) return;
        while(pos < start) {
            long skipped = in.skip(start - pos);
            if(skipped <= 0) {
                pos = start;
                return;
            }
            pos += skipped;
        }
        if(mark>=0) {
            in.mark(lastReadLimit);
            mark = pos;
        }
    }
    
    @Override
    public int available() throws IOException {
        skipStart();
        return (int)Math.min(in.available(),end - pos);
    }

    @Override
    public int read() throws IOException {
        skipStart();
        if(pos >= end) return -1;
        int result = in.read();
        if(result >= 0) pos++;
        return result;
    }

    @Override
    public int read(byte[] b, int off, int thisLen) throws IOException {
        skipStart();
        if(pos >= end) return -1;
        thisLen = (int)Math.min(thisLen,end - pos);
        int result = in.read(b,off,thisLen);
        if(result!=-1) pos += result;
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        skipStart();
        n = (int)Math.min(n,end - pos);
        long skipped = in.skip(n);
        pos += skipped;
        return skipped;
    }
    
    @Override
    public synchronized void mark(int readLimit) {
        if(pos >= start) in.mark(readLimit);
        else lastReadLimit = readLimit;
        mark = pos;
    }
    
    @Override
    public synchronized void reset() throws IOException {
        if(!in.markSupported()) {
            UnsupportedOperationException cause = new UnsupportedOperationException();
            IOException e = new IOException(cause.toString());
            e.initCause(cause);
            throw e;
        }
        if(mark < 0) throw new IOException("Mark not set");
        in.reset();
        pos = mark;
    }
}
