/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/**
 * Interface for DataElements which can be accessed as files.
 */
public interface FileDataElement extends DataElement {
    /**
     * Returns a file for the data of the data element.  Reading or writing the file corresponds to reading and writing the data element.
     * @return A file for the data of the data element.
     */
    java.io.File getFile() throws RepositoryException;
}
