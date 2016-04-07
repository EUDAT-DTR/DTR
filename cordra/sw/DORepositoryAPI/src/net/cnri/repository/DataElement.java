/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * An object for accessing a Data Element within a Digital Object.  Generally obtained from a DigitalObject
 * via the createDataElement() or getDataElement() methods.
 */
public interface DataElement {
    /**
     * Returns the digital object of this data element.
     * @return the digital object of this data element
     */
    DigitalObject getDigitalObject();
    /**
     * Returns the name of this data element.
     * @return the name of this data element
     */
    String getName();
    /**
     * Deletes the data element from its digital object.
     */
    void delete() throws RepositoryException;
    
    /**
     * Returns a Map view of the attributes of the data element.
     * @return a Map view of the attributes of the data element.
     * @throws RepositoryException 
     */
    Map<String,String> getAttributes() throws RepositoryException;
    /**
     * Returns a CloseableIterator view of the attributes of the data element.
     * @return a CloseableIterator view of the attributes of the data element.
     * @throws RepositoryException 
     */
    CloseableIterator<Map.Entry<String,String>> listAttributes() throws RepositoryException;
    /**
     * Returns the value for a particular attribute
     * @param name the attribute name
     * @return the attribute value
     * @throws RepositoryException 
     */
    String getAttribute(String name) throws RepositoryException;
    /**
     * Sets the value of multiple attributes.  Attributes mapped to null will be deleted.
     * @param attributes the attributes to set, mapped to their new values
     */
    void setAttributes(Map<String,String> attributes) throws RepositoryException;
    /**
     * Sets a particular attribute.
     * @param name the attribute name
     * @param value the attribute value; if null, the attribute will be deleted. 
     */
    void setAttribute(String name,String value) throws RepositoryException;
    /**
     * Deletes multiple attributes.
     * @param names the names of the attributes to delete
     */
    void deleteAttributes(List<String> names) throws RepositoryException;
    /**
     * Deletes a particular attribute.
     * @param name the name of the attribute to delete
     */
    void deleteAttribute(String name) throws RepositoryException;
    
    /**
     * Returns the MIME type of the given data.  Needed to give access to this data to other
     * applications in the Android system.
     * @return the MIME type of this data
     * @throws RepositoryException 
     */
    String getType() throws RepositoryException;
    /**
     * Sets the MIME type of the given data.
     * @param type the new MIME type
     */
    void setType(String type) throws RepositoryException;
    /**
     * Provides access to the data as an InputStream.
     * @return an InputStream providing access to the data
     */
    InputStream read() throws RepositoryException;
    /**
     * Provides access to a specified portion of the data as an InputStream.
     * @param start the first byte of the data to send
     * @param len the number of bytes to send
     * @return an InputStream providing access to the data
     */
    InputStream read(long start, long len) throws RepositoryException;
    /**
     * Writes new data into the data element.
     * @param data an InputStream streaming the new data
     * @return the number of bytes of data written
     * @throws IOException
     */
    long write(InputStream data) throws IOException, RepositoryException;
    /**
     * Writes new data into the data element.
     * @param data an InputStream streaming the new data
     * @param append whether to append the new data to the existing data
     * @return the number of bytes of data written
     * @throws IOException
     */
    long write(InputStream data, boolean append) throws IOException, RepositoryException;
    /**
     * Returns the number of bytes of data in this data element.
     * @return the number of bytes of data in this data element
     */
    long getSize() throws RepositoryException;
}
