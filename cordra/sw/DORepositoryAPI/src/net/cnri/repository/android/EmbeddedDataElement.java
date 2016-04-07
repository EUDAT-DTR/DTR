/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import net.cnri.repository.Constants;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.LimitedInputStream;

public class EmbeddedDataElement extends AttributeHolder implements DataElement {
    private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";
    
    private final EmbeddedDigitalObject digitalObject;
    private final Uri uri;
    private final String elementName;
    
    public EmbeddedDataElement(EmbeddedDigitalObject digitalObject, Uri uri, String elementName) {
        super(Uri.withAppendedPath(uri,Provider.AttributeColumns.ATTRIBUTE_PATH_SEGMENT), 
                Provider.AttributeColumns.ELEMENT_ATTRIBUTE_WHERE_NAME);
        this.digitalObject = digitalObject;
        this.uri = uri;
        this.elementName = elementName;
    }

    public void setContext(Context context) throws RepositoryException {
        digitalObject.setContext(context);
    }
    
    ProviderClientProxy getClient() {
        return digitalObject.getClient();
    }
    
    @Override
    public DigitalObject getDigitalObject() {
        return digitalObject;
    }

    @Override
    public String getName() {
        return elementName;
    }

    @Override
    public void delete() throws RepositoryException {
        getClient().delete(uri,null,null);
    }

    public Uri getUri() {
        return uri;
    }

    @Override
    public String getType() throws RepositoryException {
        String res = getAttribute(Constants.ELEMENT_ATTRIBUTE_TYPE);
        if(res==null) return DEFAULT_MIME_TYPE_ATTRIBUTE;
        return res;
    }

    @Override
    public void setType(String type) throws RepositoryException {
        setAttribute(Constants.ELEMENT_ATTRIBUTE_TYPE,type);
    }

    @Override
    public InputStream read() throws RepositoryException {
        try {
            return new ParcelFileDescriptor.AutoCloseInputStream(getClient().openFile(uri,"r"));
        }
        catch(FileNotFoundException e) {
            throw new InternalException("Could not open file for data element " + uri,e);
        }
    }
    
    @Override
    public InputStream read(long start, long len) throws RepositoryException {
        InputStream in = read();
        if(start<=0 & len<0) return in;
        else return new LimitedInputStream(in,start,len);
    }

    @Override
    public long write(InputStream data) throws IOException, RepositoryException {
        return write(data,false);
    }
    
    @Override
    public long write(InputStream data, boolean append) throws IOException, RepositoryException {
        long res = 0;
        OutputStream out = null;
        try {
            String mode = append ? "wa" : "w";
            out = new ParcelFileDescriptor.AutoCloseOutputStream(getClient().openFile(uri,mode));
            byte[] buf = new byte[4096];
            int r;
            while((r = data.read(buf))>0) {
                out.write(buf,0,r);
                res += r;
            }
        }
        catch(FileNotFoundException e) {
            throw new InternalException(e);
        }
        finally {
            if(out!=null) try { out.close(); } catch(Exception e) {}
        }
        return res;
    }

    @Override
    public long getSize() throws RepositoryException {
        ParcelFileDescriptor fd = null;
        try {
            fd = getClient().openFile(uri,"r");
            return fd.getStatSize();
        }
        catch(FileNotFoundException e) {
            throw new InternalException(e);
        }
        finally {
            if(fd!=null) try { fd.close(); } catch(Exception e) {}
        }

    }
}
