/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.InputStream;

public class Payload {
    String name;
    InputStream in;
    String mimetype;
    String filename;
    
    public Payload(String name, InputStream in, String mimetype, String filename) {
        this.name = name;
        this.in = in;
        this.mimetype = mimetype;
        this.filename = filename;
    }
}
