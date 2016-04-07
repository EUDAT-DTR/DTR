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
 * An object for accessing a Digital Object Repository.  This interface allows direct access to digital objects and data elements without having to go through the DigitalObject and DataElement classes.
 */
public interface DirectRepository extends Repository {
    /**
     * Returns a Map view of the attributes of the digital object or data element.
     * @return a Map view of the attributes of the digital object or data element.
     */
    Map<String,String> getAttributes(String handle, String elementName) throws RepositoryException;
    /**
     * Returns a CloseableIterator view of the attributes of the digital object or data element.
     * @return a CloseableIterator view of the attributes of the digital object or data element.
     */
    CloseableIterator<Map.Entry<String,String>> listAttributes(String handle, String elementName) throws RepositoryException;
    /**
     * Returns the value for a particular attribute
     * @param name the attribute name
     * @return the attribute value
     */
    String getAttribute(String handle, String elementName, String name) throws RepositoryException;
    /**
     * Sets the value of multiple attributes.  Attributes mapped to null will be deleted.
     * @param attributes the attributes to set, mapped to their new values
     */
    void setAttributes(String handle, String elementName, Map<String,String> attributes) throws RepositoryException;
    /**
     * Sets a particular attribute.
     * @param name the attribute name
     * @param value the attribute value; if null, the attribute will be deleted. 
     */
    void setAttribute(String handle, String elementName, String name,String value) throws RepositoryException;
    /**
     * Deletes multiple attributes.
     * @param names the names of the attributes to delete
     */
    void deleteAttributes(String handle, String elementName, List<String> names) throws RepositoryException;
    /**
     * Deletes a particular attribute.
     * @param name the name of the attribute to delete
     */
    void deleteAttribute(String handle, String elementName, String name) throws RepositoryException;
    
    /**
     * Verifies whether a data element with the given name exists within this digital object
     * @param name an identifier for a data element
     * @return whether that data element exists
     */
    boolean verifyDataElement(String handle, String name) throws RepositoryException;
    /**
     * Creates a new data element with the given name in this digital object.
     * @param name the identifier for the new data element
     * @throws CreationException if a data element with the given name already exists
     */
    void createDataElement(String handle, String name) throws CreationException, RepositoryException;
    /**
     * Deletes any data element of the given name.
     * @param name the identifier for the data element
     */
    void deleteDataElement(String handle, String name) throws RepositoryException;
    /**
     * Provides a CloseableIterator view of the names of the data elements in this digital object.
     */
    CloseableIterator<String> listDataElementNames(String handle) throws RepositoryException;
    /**
     * Provides access to the data as an InputStream.
     * @return an InputStream providing access to the data
     */
    InputStream read(String handle, String elementName) throws RepositoryException;
    /**
     * Writes new data into the data element.
     * @param data an InputStream streaming the new data
     * @param append whether to append the new data to the existing data
     * @return the number of bytes of data written
     * @throws IOException
     */
    long write(String handle, String elementName, InputStream data, boolean append) throws IOException, RepositoryException;
    /**
     * Returns the number of bytes of data in this data element.
     * @return the number of bytes of data in this data element
     */
    long getSize(String handle, String elementName) throws RepositoryException;
    /**
     * Returns a file for the data of the data element.  Reading or writing the file corresponds to reading and writing the data element.
     * If the repository does not support files for data elements, returns null.
     * @return A file for the data of the data element, or null if the repository does not support files for data elements.
     */
    java.io.File getFile(String handle, String elementName) throws RepositoryException;
}
