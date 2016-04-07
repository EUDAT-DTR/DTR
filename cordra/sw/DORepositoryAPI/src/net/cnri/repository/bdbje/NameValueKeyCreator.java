/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;

public class NameValueKeyCreator implements SecondaryKeyCreator {

	/**
	 * Creates a secondary key for each attribute that is set on the database. 
	 * This is used an a secondary Database that is an index of the first.
	 * 
	 * Secondary keys for element attributes are also created 
	 * 
	 * This allows fast searching of the database when we want all objects that have a particular attribute with a particular value.
	 */
	@Override
	public boolean createSecondaryKey(SecondaryDatabase secDb, DatabaseEntry keyEntry, DatabaseEntry dataEntry, DatabaseEntry resultEntry) {
		byte[] keyBytes = keyEntry.getData();
		byte[] attributeNameBytes = KeyUtil.extractAttributeNameBytes(keyBytes);
		if(attributeNameBytes==null) {
			if (KeyUtil.isElementAttributekey(keyBytes)) {//we might have an element attribute
				byte[] elementAttributeNameBytes = KeyUtil.removeHandleBytesAndFirstDelimeterFromKey(keyBytes);
				byte[] dataBytes = dataEntry.getData();
				byte[] secondaryKey = generateElementAttributKey(elementAttributeNameBytes, dataBytes);
				resultEntry.setData(secondaryKey);
				return true;
			}
			return false;
		}
		byte[] dataBytes = dataEntry.getData();
		byte[] secondaryKey = generateKey(attributeNameBytes, dataBytes);
		resultEntry.setData(secondaryKey);
		return true;
	}
	
	private static byte[] generateKey(byte[] nameBytes, byte[] valueBytes) {
		return BdbUtil.concatBytes(nameBytes, BdbUtil.toByteArray(valueBytes.length), valueBytes);
	}
	
	private static byte[] generateElementAttributKey(byte[] elementAttributeNameBytes, byte[] valueBytes) {
		return BdbUtil.concatBytes(elementAttributeNameBytes, BdbUtil.toByteArray(valueBytes.length), valueBytes);
	}

	public static byte[] generateKey(String name, String value) {
		return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(name), BdbUtil.encodeAsPascalString(value));
	}
	
	public static byte[] generateElementAttributKey(String elementName, String elementAttributeName, String value) {
		return BdbUtil.concatBytes( BdbUtil.encodeAsPascalString(elementName), 
									BdbUtil.encodeAsPascalString(elementAttributeName), 
									BdbUtil.encodeAsPascalString(value));
	}

}
