/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

public class FileMetadataResponse {
    String filename;
    String mimetype;
    
    public FileMetadataResponse(String filename, String mimetype) {
        this.filename = filename;
        this.mimetype = mimetype;
    }
}
