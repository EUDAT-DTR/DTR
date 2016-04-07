/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.highlevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.wrapper.DigitalObjectWrapper;
import net.cnri.repository.wrapper.RepositoryWrapper;

public class HighLevelDigitalObject extends DigitalObjectWrapper {
	
    public HighLevelDigitalObject(RepositoryWrapper repositoryWrapper, DigitalObject originalDigitalObject) {
		super(repositoryWrapper, originalDigitalObject);
		
	}

	/**
	 * Treats the value of the attribute as a new line separated list of strings.
	 * Returns the tokens as List of Strings 
     */
	public List<String> getAsListAttribute(String name) throws RepositoryException {
		String attribute = originalDigitalObject.getAttribute(name);
		if ("".equals(attribute)) {
			return new ArrayList<String>();
		} else {
			return new ArrayList<String>(Arrays.asList(attribute.split("\n")));
		}
	}

    /**
     * Treats the value of the attribute as a new line separated list of strings.
     * Appends value to the list.
     */
	public void addToListAttribute(String name, String value) throws RepositoryException {
		String attribute = originalDigitalObject.getAttribute(name);
		setAttribute(name, attribute + "\n" + value);
	}

    /**
     * Treats the value of the attribute as a new line separated list of strings.
     * Finds the first occurrence of this string in the list and removes it.
     */
	public void removeFromListAttribute(String name, String value) throws RepositoryException {
		List<String> strings = getAsListAttribute(name);
		strings.remove(value);
		setListAttribute(name, strings);
	}
	
    /**
     * Treats the value of the attribute as a new line separated list of strings.
     * Removes the string at the specified position in this list
     */
	public void removeFromListAttribute(String name, int index) throws RepositoryException {
		List<String> strings = getAsListAttribute(name);
		strings.remove(index);
		setListAttribute(name, strings);
	}

    /**
     * Treats the value of the attribute as a new line separated list of strings.
     * Returns the index of the first occurrence of the specified string in this list, or -1 if this list does not contain the element.
     */
	public int indexOfInListAttribute(String name, String value) throws RepositoryException {
		List<String> strings = getAsListAttribute(name);
		return strings.indexOf(value);
	}
	
	/**
	 * Concatenates the list of strings into a newline separated string and sets it on the attribute called "name"
	 */
	public void setListAttribute(String name, List<String> strings) throws RepositoryException {
		String value = toNewLineSeparatedString(strings);
		setAttribute(name, value);
	}
	
	private static String toNewLineSeparatedString(List<String> values) {
		String result = "";
		for (int i = 0; i < values.size(); i++) {
			result += values.get(i);
			if (i < values.size()-1) {
				result += "\n";
			}
		}
		return result;
	}
}
