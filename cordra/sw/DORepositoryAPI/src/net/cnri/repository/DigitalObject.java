/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.util.List;
import java.util.Map;

/**
 * An object for accessing a Digital Object within a Repository.  Generally obtained from a Repository
 * via the createDigitalObject() or getDigitalObject() methods.
 */
public interface DigitalObject {
    /**
     * Returns the repository through which this digital object is being accessed.
     * @return the repository through which this digital object is being accessed
     */
    Repository getRepository();
    /**
     * Returns the identifier for this digital object.
     * @return the identifier for this digital object
     */
    String getHandle();
    /**
     * Deletes this digital object from the repository through which it is being accessed.
     */
    void delete() throws RepositoryException;

    /**
     * Returns a Map view of the attributes of the digital object.
     * @return a Map view of the attributes of the digital object.
     */
    Map<String,String> getAttributes() throws RepositoryException;
    /**
     * Returns a CloseableIterator view of the attributes of the digital object.
     * @return a CloseableIterator view of the attributes of the digital object.
     */
    CloseableIterator<Map.Entry<String,String>> listAttributes() throws RepositoryException;
    /**
     * Returns the value for a particular attribute
     * @param name the attribute name
     * @return the attribute value
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
     * Verifies whether a data element with the given name exists within this digital object
     * @param name an identifier for a data element
     * @return whether that data element exists
     */
    boolean verifyDataElement(String name) throws RepositoryException;
    /**
     * Creates a new data element with the given name in this digital object.
     * @param name the identifier for the new data element
     * @return the newly-created data element
     * @throws CreationException if a data element with the given name already exists
     */
    DataElement createDataElement(String name) throws CreationException, RepositoryException;
    /**
     * Returns the data element with the given name.
     * @param name the identifier for the data element
     * @return the data element, or null if no such element exists
     */
    DataElement getDataElement(String name) throws RepositoryException;
    /**
     * Returns the data element with the given name, creating it if it does not exist.
     * @param name the identifier for the data element
     * @return the data element
     */
    DataElement getOrCreateDataElement(String name) throws RepositoryException;
    /**
     * Deletes any data element of the given name.
     * @param name the identifier for the data element
     */
    void deleteDataElement(String name) throws RepositoryException;
    /**
     * Return a list of the names of the data elements in this digital object.
     */
    List<String> getDataElementNames() throws RepositoryException;
    /**
     * Returns a list of the data elements in this digital object.
     */
    List<DataElement> getDataElements() throws RepositoryException;
    /**
     * Provides a CloseableIterator view of the names of the data elements in this digital object.
     */
    CloseableIterator<String> listDataElementNames() throws RepositoryException;
    /**
     * Provides a CloseableIterator view of the data elements in this digital object.
     */
    CloseableIterator<DataElement> listDataElements() throws RepositoryException;
}
