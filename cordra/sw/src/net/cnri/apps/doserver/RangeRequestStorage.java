package net.cnri.apps.doserver;

import java.io.InputStream;

import net.cnri.dobj.DOException;

public interface RangeRequestStorage {
    public InputStream getDataElement(String objectID, String elementID, long start, long len) throws DOException;
}
