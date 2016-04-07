/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

import java.util.Arrays;

import net.cnri.repository.util.StringEncoding;

public class BdbUtil {

	public static byte[] concatBytes(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0,        a.length);
		System.arraycopy(b, 0, result, a.length, b.length);		
		return result;
	}
	
	public static byte[] concatBytes(byte[] a, byte[] b, byte[] c) {
		byte[] result = new byte[a.length + b.length + c.length];
		System.arraycopy(a, 0, result, 0,                 a.length);
		System.arraycopy(b, 0, result, a.length,          b.length);
		System.arraycopy(c, 0, result, a.length+b.length, c.length);
		return result;
	}
	
	public static byte[] concatBytes(byte[] a, byte[] b, byte[] c, byte[] d) {
		byte[] result = new byte[a.length + b.length + c.length + d.length];
		System.arraycopy(a, 0, result, 0,                 		   a.length);
		System.arraycopy(b, 0, result, a.length,          		   b.length);
		System.arraycopy(c, 0, result, a.length+b.length,		   c.length);
		System.arraycopy(d, 0, result, a.length+b.length+c.length, d.length);
		return result;
	}
	
	public static byte[] concatBytes(byte[] a, byte[] b, byte[] c, byte[] d, byte[] e) {
		byte[] result = new byte[a.length + b.length + c.length + d.length + e.length];
		System.arraycopy(a, 0, result, 0,                 		   			a.length);
		System.arraycopy(b, 0, result, a.length,          		   			b.length);
		System.arraycopy(c, 0, result, a.length+b.length,		  			c.length);
		System.arraycopy(d, 0, result, a.length+b.length+c.length,			d.length);
		System.arraycopy(e, 0, result, a.length+b.length+c.length+d.length, e.length);
		return result;
	}
	
	public static byte[] concatBytes(byte[] a, byte[] b, byte[] c, byte[] d, byte[] e, byte[] f) {
		byte[] result = new byte[a.length + b.length + c.length + d.length + e.length + f.length];
		System.arraycopy(a, 0, result, 0,                 		   					 a.length);
		System.arraycopy(b, 0, result, a.length,          		   					 b.length);
		System.arraycopy(c, 0, result, a.length+b.length,		  					 c.length);
		System.arraycopy(d, 0, result, a.length+b.length+c.length,					 d.length);
		System.arraycopy(e, 0, result, a.length+b.length+c.length+d.length, 		 e.length);
		System.arraycopy(f, 0, result, a.length+b.length+c.length+d.length+e.length, f.length);
		return result;
	}
	
	/**
	 * Returns a new byte[] that is a subarray of a. The subarray begins at the specified beginIndex and 
	 * extends to the byte at index endIndex - 1. Thus the length of the substring is endIndex-beginIndex.
	 * 
	 * beginIndex - the beginning index, inclusive.
	 * endIndex - the ending index, exclusive.
	 */
	
	public static byte[] getSubArray(byte[] a, int beginIndex, int endIndex) {
		byte[] result = new byte[(endIndex - beginIndex)];
		System.arraycopy(a, beginIndex, result, 0, result.length);
		return result;
	}
	
	public static int indexOf(byte[] keyBytes, byte target) {
		for (int i = 0; i < keyBytes.length; i++) {
			if (keyBytes[i] == target) {
				return i; 
			}
		}
		return -1;
	}
	
	public static boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		byte[] startOfBytes = new byte[prefix.length];
		System.arraycopy(bytes, 0, startOfBytes, 0, prefix.length);
		return Arrays.equals(prefix, startOfBytes);
	}
	
	public static byte[] encode(String hello) {
	    return StringEncoding.encodeUTF16BE(hello);
	}
	
	public static byte[] encodeAsPascalString(String hello) {
		byte[] tail = BdbUtil.encode(hello);
		byte[] head = BdbUtil.toByteArray(tail.length);
		return BdbUtil.concatBytes(head, tail);
	}

//	public static int storeAsPascalString(String hello, byte[] bytes, int offset) {
//		offset = storeInt(hello.length()*2,bytes,offset);
//		offset = storeString(hello,bytes,offset);
//		return offset;
//	}
//
//	public static int storeString(String hello, byte[] bytes, int offset) {
//        for(int i = 0; i < hello.length(); i++) {
//            char ch = hello.charAt(i);
//            bytes[offset++] = (byte)(ch >> 8);
//            bytes[offset++] = (byte)ch;
//        }
//		return offset;
//	}
//
//	public static int storeInt(int data, byte[] bytes, int offset) {
//		bytes[offset++] = (byte)((data >> 24) & 0xff);
//		bytes[offset++] = (byte)((data >> 16) & 0xff);
//		bytes[offset++] = (byte)((data >> 8) & 0xff);
//		bytes[offset++] = (byte)((data >> 0) & 0xff);
//		return offset;
//	}
//	
//	public static String retrievePascalString(byte[] bytes, int offset) {
//		int len = (bytes[offset++] & 0xFF)<<24 | (bytes[offset++] & 0xFF)<<16 | (bytes[offset++] & 0xFF)<<8 | (bytes[offset++] & 0xFF);
//        char[] chars = new char[len/2];
//        for(int i = 0; i < chars.length; i++) {
//            chars[i] = (char)((bytes[offset++] & 0xFF) << 8 | (bytes[offset++] & 0xFF));
//        }
//        return new String(chars);
//	}
//
//	public static int retrieveInt(byte[] bytes, int offset) {
//		int len = (bytes[offset++] & 0xFF)<<24 | (bytes[offset++] & 0xFF)<<16 | (bytes[offset++] & 0xFF)<<8 | (bytes[offset++] & 0xFF);
//        return len;
//	}
//
	public static String decode(byte[] bytes) {
	    return StringEncoding.decodeUTF16BE(bytes);
	}
	
	public static byte[] toByteArray(int data) {
		return new byte[] {
		(byte)((data >> 24) & 0xff),
		(byte)((data >> 16) & 0xff),
		(byte)((data >> 8) & 0xff),
		(byte)((data >> 0) & 0xff),
		};
	}
	
	public static int fromByteArray(byte[] bytes) {
		int result = 0;
		for (int i = 0; i < bytes.length; i++) {
			result = (result << 8) + (bytes[i] & 0xff);
		}
		return result;
	}
	
}
