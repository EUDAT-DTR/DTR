/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.io.IOException;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

public interface SupportsFastCopyForLayeredRepo {
    /**
     * Copy this digital object (in the bottom repository of a {@link LayeredRepository} 
     * to another digital object (in the top repository).  For implementations that can be faster than naive copying.
     * In general this does not copy the data of data elements, but sets the attribute
     * {@link LayeredDataElement#ATTRIBUTE_DATA_ELEMENT_MISSING} to 1 on each data element not copied.
     * @param target the digital object to copy into
     */
    void copyTo(DigitalObject target) throws RepositoryException, IOException;
}
