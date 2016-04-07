/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

public class StringEncoding {
    private static final char[] SIXTY_FOUR = "@ABCDEFGHIJKLMNOPQRSTUVWXYZ+,-._0abcdefghijklmnopqrstuvwxyz345~7".toCharArray();
    
    /** Ridiculously optimized encoding and decoding of arbitrary strings into URL-safe path segments.
     * ASCII chars encode in two bytes; other chars in 3 bytes.
     */
    public static String encodeUriSafe(String s) {
        StringBuilder sb = new StringBuilder(s.length()*3);
        for(int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if((ch & 0xFF00)==0) {
                sb.append((char)((ch>>>4) | 0x40));
                sb.append((char)((ch & 0x0F) | 0x40));
            }
            else {
                int chShifted = ch>>>12;
                char chLetter = chShifted==0 ? 'p' : (char)(chShifted | 0x60);
                sb.append(chLetter);
                sb.append(SIXTY_FOUR[(ch>>>6) & 0x3F]);
                sb.append(SIXTY_FOUR[ch & 0x3F]);
            }
        }
        return sb.toString();
    }
    
    private static int fromSixtyFour(char ch) {
        if((ch&0x40)==0x40) return ch & 0x3F; // everything except +,-.0-9
        if((ch&0x10)==0x10 && (ch&0x0F)!=0) return (ch & 0x07) | 0x38; // 1-9
        if(ch==0x20) return 0x1B; // special handling for space in case + became space
        return (ch & 0x1F) + 0x10; // +,-.0
    }

    public static String decodeUriSafe(String s)  {
        StringBuilder sb = new StringBuilder(s.length()/2);
        for(int i = 0; i < s.length(); i++) {
            char ch1 = s.charAt(i);
            if((ch1&0x60)==0x60) {
                char ch2 = s.charAt(++i);
                char ch3 = s.charAt(++i);
                sb.append((char)(ch1<<12 | fromSixtyFour(ch2)<<6 | fromSixtyFour(ch3)));
            }
            else {
                char ch2 = s.charAt(++i);
                sb.append((char)((ch1&0x0F)<<4 | (ch2&0x0F)));
            }
        }
        return sb.toString();
    }
    
    public static String encodeUriSafeBytes(byte[] bytes) {
        char[] chars = new char[bytes.length*2];
        int index = 0;
        for(int i = 0; i < bytes.length; i++) {
            int ch = bytes[i] & 0xFF;
            chars[index++] = (char)((ch>>>4) | 0x40);
            chars[index++] = (char)((ch & 0x0F) | 0x40);
        }
        return new String(chars);
    }
    
    public static byte[] decodeUriSafeBytes(String s) {
        byte[] bytes = new byte[s.length()/2];
        int index = 0;
        for(int i = 0; i < s.length(); i++) {
            char ch1 = s.charAt(i);
            char ch2 = s.charAt(++i);
            bytes[index++] = (byte)((ch1&0x0F)<<4 | (ch2&0x0F));
        }
        return bytes;
    }
    
    public static byte[] encodeUTF16BE(String s) {
        byte[] bytes = new byte[s.length()*2];
        int index = 0;
        for(int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            bytes[index++] = (byte)(ch >> 8);
            bytes[index++] = (byte)ch;
        }
        return bytes;
    }
    
    public static String decodeUTF16BE(byte[] bytes) {
        char[] chars = new char[bytes.length/2];
        for(int i = 0; i < chars.length; i++) {
            chars[i] = (char)((bytes[i*2] & 0xFF) << 8 | (bytes[i*2+1] & 0xFF));
        }
        return new String(chars);
    }
    
    /**
     * Convert a string to bytes using the ISO-8859-1 encoding.  This implementation does not behave well on input which uses characters greater than 256 (that is, outside of ISO-8859-1).
     * @param s string to be encoded as bytes
     * @return a byte array corresponding to the characters in s; only faithful if all characters in s are less than 256.
     */
    public static byte[] encodeISO88591(String s) {
        byte[] bytes = new byte[s.length()];
        for(int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            bytes[i] = (byte)ch;
        }
        return bytes;
    }
    
    public static String decodeISO88591(byte[] bytes) {
        char[] chars = new char[bytes.length];
        for(int i = 0; i < chars.length; i++) {
            chars[i] = (char)(bytes[i] & 0xFF);
        }
        return new String(chars);
    }
}
