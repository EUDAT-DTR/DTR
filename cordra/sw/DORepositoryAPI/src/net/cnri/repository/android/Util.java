/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import net.cnri.repository.util.StringEncoding;
import android.os.Bundle;
import android.os.Parcel;

public class Util {
    public static String serializeBundleAsUriComponent(Bundle bundle) {
        if(bundle==null) bundle = Bundle.EMPTY;
        Parcel parcel = Parcel.obtain();
        parcel.writeBundle(bundle);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return StringEncoding.encodeUriSafeBytes(bytes);
    }

    public static Bundle deserializeBundleFromUriComponent(String s) {
        if(s==null) return Bundle.EMPTY;
        Parcel parcel = Parcel.obtain();
        byte[] bytes = StringEncoding.decodeUriSafeBytes(s);
        parcel.unmarshall(bytes,0,bytes.length);
        parcel.setDataPosition(0);
        Bundle res = parcel.readBundle();
        parcel.recycle();
        return res;
    }

    public static String serializeBundleAsString(Bundle bundle) {
        if(bundle==null) bundle = Bundle.EMPTY;
        Parcel parcel = Parcel.obtain();
        parcel.writeBundle(bundle);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return StringEncoding.decodeUTF16BE(bytes);
    }

    public static Bundle deserializeBundleFromString(String s) {
        if(s==null) return Bundle.EMPTY;
        Parcel parcel = Parcel.obtain();
        byte[] bytes = StringEncoding.encodeUTF16BE(s);
        parcel.unmarshall(bytes,0,bytes.length);
        parcel.setDataPosition(0);
        Bundle res = parcel.readBundle();
        parcel.recycle();
        return res;
    }
}
