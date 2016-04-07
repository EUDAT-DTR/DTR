package net.cnri.dobj;

import java.io.InputStream;

public interface RangeRequestStorageProxy extends StorageProxy {
    public InputStream getDataElement(String elementID, long start, long len) throws DOException;
}
