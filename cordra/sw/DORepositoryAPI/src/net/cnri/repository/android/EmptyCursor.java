/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.CrossProcessCursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

/**
 * An implementation of Cursor.  Intended for subclasses which will implement respond().
 */
public class EmptyCursor implements CrossProcessCursor {

    @Override
    public void close() {
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        throw new IllegalArgumentException();
    }

    @Override
    public void deactivate() {
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public int getColumnCount() {
        return 0;
    }

    @Override
    public int getColumnIndex(String columnName) {
        return -1;
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
        throw new IllegalArgumentException();
    }

    @Override
    public String getColumnName(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public String[] getColumnNames() {
        return new String[0];
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public double getDouble(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public Bundle getExtras() {
        return Bundle.EMPTY;
    }

    @Override
    public float getFloat(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public int getInt(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public long getLong(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public int getPosition() {
        return -1;
    }

    @Override
    public short getShort(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public String getString(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        return false;
    }

    @Override
    public boolean isAfterLast() {
        return true;
    }

    @Override
    public boolean isBeforeFirst() {
        return true;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isFirst() {
        return false;
    }

    @Override
    public boolean isLast() {
        return false;
    }

    @Override
    public boolean isNull(int columnIndex) {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean move(int offset) {
        return false;
    }

    @Override
    public boolean moveToFirst() {
        return false;
    }

    @Override
    public boolean moveToLast() {
        return false;
    }

    @Override
    public boolean moveToNext() {
        return false;
    }

    @Override
    public boolean moveToPosition(int position) {
        return (position==-1);
    }

    @Override
    public boolean moveToPrevious() {
        return false;
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        // no-op
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        // no-op
    }

    @Override
    public boolean requery() {
        return true;
    }

    @Override
    public Bundle respond(Bundle extras) {
        return Bundle.EMPTY;
    }

    @Override
    public void setNotificationUri(ContentResolver cr, Uri notifyUri) {
        // no-op
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        // no-op
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // no-op
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        // no-op
    }

    @Override
    public CursorWindow getWindow() {
        return null;
    }
    
    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return true;
    }
}
