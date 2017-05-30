/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.InputStream;

public class ObjectComponentStream extends ObjectComponent {
    
    public final InputStream in;
    public final String filename;
    public final Long start;
    public final Long end;
    public final long size;
    
    public ObjectComponentStream(InputStream in, String filename, String type, String remoteRepository, String mediaType, Long start, Long end, long size) {
        super(type, remoteRepository, mediaType);
        this.in = in;
        this.filename = filename;
        this.start = start;
        this.end = end;
        this.size = size;
    }
}
