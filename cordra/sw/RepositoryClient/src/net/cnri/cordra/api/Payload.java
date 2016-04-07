package net.cnri.cordra.api;

import java.io.InputStream;

public class Payload {
    public String name;
    public String filename;
    public String mediaType;
    public long size;
    private InputStream in;
    
    public void setInputStream(InputStream in) {
        this.in = in;
    }
    
    public InputStream getInputStream() {
        return in;
    }
}