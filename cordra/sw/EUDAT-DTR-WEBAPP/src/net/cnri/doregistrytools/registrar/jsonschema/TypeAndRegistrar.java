/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

public class TypeAndRegistrar {
    public String type;
    public String remoteRepository;
    
    public TypeAndRegistrar(String type, String remoteRepository) {
        this.type = type;
        this.remoteRepository = remoteRepository;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((remoteRepository == null) ? 0 : remoteRepository.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TypeAndRegistrar other = (TypeAndRegistrar) obj;
        if (remoteRepository == null) {
            if (other.remoteRepository != null) return false;
        } else if (!remoteRepository.equals(other.remoteRepository)) return false;
        if (type == null) {
            if (other.type != null) return false;
        } else if (!type.equals(other.type)) return false;
        return true;
    }
}
