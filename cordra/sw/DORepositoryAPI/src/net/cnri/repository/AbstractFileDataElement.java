/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractFileDataElement extends AbstractDataElement implements FileDataElement {
    @Override
    public InputStream read() throws RepositoryException {
        try {
            return new FileInputStream(getFile());
        }
        catch(FileNotFoundException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public long write(InputStream data, boolean append) throws IOException, RepositoryException {
        long totalBytesWritten = 0;
        OutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(), append),4096);
        try {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = data.read(buffer)) > 0) {
                out.write(buffer, 0, len);
                totalBytesWritten += len;
            }
        }
        finally {
            out.close();
        }
        return totalBytesWritten;
    }

    @Override
    public long getSize() throws RepositoryException {
        return getFile().length();
    }
}
