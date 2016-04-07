/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

public class KeyUtil {

	public static final byte ATTRIBUTE_DELIMETER = (byte)0x00;
	public static final byte ELEMENT_DELIMETER = (byte)0x01;
	public static final byte ELEMENT_ATTRIBUTE_DELIMETER = (byte)0x02;
	
	public static final byte[] ATTRIBUTE_DELIMETER_ARRAY = new byte[] {ATTRIBUTE_DELIMETER};
	public static final byte[] ELEMENT_DELIMETER_ARRAY = new byte[] {ELEMENT_DELIMETER};
	public static final byte[] ELEMENT_ATTRIBUTE_DELIMETER_ARRAY = new byte[] {ELEMENT_ATTRIBUTE_DELIMETER};
	
	public static byte[] getHandleKey(String handle) {
		return BdbUtil.encodeAsPascalString(handle);
	}

	public static byte[] getAttributeSearchKey(String handle) {
        return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(handle), ATTRIBUTE_DELIMETER_ARRAY);
	}

	public static byte[] getAttributeKey(String handle, String attributeName) {
		return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(handle), ATTRIBUTE_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(attributeName));
	}

	public static byte[] getAttributeKey(byte[] handleKeyBytes, String attributeName) {
		return BdbUtil.concatBytes(handleKeyBytes, ATTRIBUTE_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(attributeName));
	}
	
	public static byte[] getElementKey(String handle, String elementName) {
		return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(handle), ELEMENT_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(elementName));
	}

	public static byte[] getElementKey(byte[] handleKeyBytes, String elementName) {
		return BdbUtil.concatBytes(handleKeyBytes, ELEMENT_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(elementName));
	}

	public static byte[] getElementAttributeSearchKey(String handle, String elementName) {
		return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(handle), ELEMENT_ATTRIBUTE_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(elementName));
	}

	public static byte[] getElementAttributeKey(String handle, String elementName, String attributeName) {
		return BdbUtil.concatBytes(BdbUtil.encodeAsPascalString(handle), ELEMENT_ATTRIBUTE_DELIMETER_ARRAY, BdbUtil.encodeAsPascalString(elementName), BdbUtil.encodeAsPascalString(attributeName));
	}

	public static byte[] getElementAttributeKey(byte[] elementAttributeStartKeyBytes, String attributeName) {
		return BdbUtil.concatBytes(elementAttributeStartKeyBytes, BdbUtil.encodeAsPascalString(attributeName));
	}

	public static boolean isHandleKey(byte[] key) {
		int handleLength = BdbUtil.fromByteArray(new byte[] {key[0], key[1], key[2], key[3]});
		return key.length == handleLength+4;
	}

	public static byte getByteAfterHandle(byte[] key, int handleLength) {
		return key[handleLength+4];
	}
	
	public static boolean isAttributeKey(byte[] key) {
		int handleLength = BdbUtil.fromByteArray(new byte[] {key[0], key[1], key[2], key[3]});
		if (key.length <= 4+handleLength) {
			return false;
		} 
		if (getByteAfterHandle(key, handleLength) == ATTRIBUTE_DELIMETER) {
			return true;
		}
		return false;
	}
	
	public static boolean isElementKey(byte[] key) {
		int handleLength = BdbUtil.fromByteArray(new byte[] {key[0], key[1], key[2], key[3]});
		if (key.length <= 4+handleLength) {
			return false;
		} 
		if (getByteAfterHandle(key, handleLength) == ELEMENT_DELIMETER) {
			return true;
		}
		return false;
	}
	
	public static boolean isElementAttributekey(byte[] key) {
		int handleLength = BdbUtil.fromByteArray(new byte[] {key[0], key[1], key[2], key[3]});
		if (key.length <= 4+handleLength) {
			return false;
		} 
		if (getByteAfterHandle(key, handleLength) == ELEMENT_ATTRIBUTE_DELIMETER) {
			return true;
		}
		return false;
	}

	public static int readHeadAt(byte[] bytes, int i) {
		byte[] headBytes = new byte[] { bytes[i], bytes[i+1], bytes[i+2], bytes[i+3]};
		return BdbUtil.fromByteArray(headBytes);
	}
	
	public static String getStringAtOffset(byte[] pascalBytes, int offset) {
		int length = readHeadAt(pascalBytes,offset);
		byte[] currentStringBytes = BdbUtil.getSubArray(pascalBytes, offset+4, offset+4+length);
		return BdbUtil.decode(currentStringBytes);
	}

	public static String extractHandle(byte[] key) {
		return getStringAtOffset(key, 0);
	}
	
	public static byte[] extractHandleBytes(byte[] key) {
        int handleLength = readHeadAt(key,0);
	    return(BdbUtil.getSubArray(key,0,4+handleLength));
	}
	
	public static String extractAttributeName(byte[] key) {
		int handleLength = readHeadAt(key,0);
		if(key.length <= 4+handleLength) return null;
		if (getByteAfterHandle(key, handleLength) == ELEMENT_DELIMETER) return null;
		if (getByteAfterHandle(key, handleLength) == ATTRIBUTE_DELIMETER) {
			return getStringAtOffset(key, 5+handleLength);
		}
		else { // ELEMENT_ATTRIBUTE
			int elementLength = readHeadAt(key, 5+handleLength);
			return getStringAtOffset(key, 5+handleLength+4+elementLength);
		}
	}

	public static byte[] extractAttributeNameBytes(byte[] key) {
		int handleLength = readHeadAt(key,0);
		if(key.length <= 4+handleLength) return null;
		if (getByteAfterHandle(key, handleLength) == ELEMENT_DELIMETER) return null;
		if (getByteAfterHandle(key, handleLength) == ATTRIBUTE_DELIMETER) {
			int endIndex = 5+handleLength+4+readHeadAt(key,5+handleLength);
			return BdbUtil.getSubArray(key, 5+handleLength, endIndex);
		}
		else { // ELEMENT_ATTRIBUTE
			return null; // only works for object-level attributes
		}
	}
	
	public static byte[] removeHandleBytesFromKey(byte[] key) {
		int handleLength = readHeadAt(key,0);
		return BdbUtil.getSubArray(key, 4+handleLength, key.length);
	}
	
	public static byte[] removeHandleBytesAndFirstDelimeterFromKey(byte[] key) {
		int handleLength = readHeadAt(key,0);
		return BdbUtil.getSubArray(key, 4+1+handleLength, key.length);
	}	

	public static String extractElementName(byte[] key) {
		int handleLength = readHeadAt(key,0);
		if(key.length <= 4+handleLength) return null;
		if (getByteAfterHandle(key, handleLength) == ATTRIBUTE_DELIMETER) {
			return null;
		}
		else { // ELEMENT or ELEMENT_ATTRIBUTE
			return getStringAtOffset(key, 5+handleLength);
		}
	}
	
	public static String keyToString(byte[] key) {
		String handle = extractHandle(key);
		String elementName = extractElementName(key);
		String attributeName = extractAttributeName(key);
		return handle + "," + elementName + "," + attributeName;
	}
	
	
	
}
