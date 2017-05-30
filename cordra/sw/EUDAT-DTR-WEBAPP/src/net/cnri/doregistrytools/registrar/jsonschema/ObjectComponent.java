/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

public abstract class ObjectComponent {
    public final String type;
    public final String remoteRepository;
    public final String mediaType;

    public ObjectComponent(String type, String remoteRepository, String mediaType) {
        this.type = type;
        this.remoteRepository = remoteRepository;
        this.mediaType = mediaType;
    }
}
