/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import net.cnri.repository.CloseableIterator;
import android.database.Cursor;

/** An iterator interface to an android.database.Cursor.  The underlying cursor can be accessed directly with getCursor(), 
 * and an instance of the parameter class T can be obtained from the current cursor state; alternately, the cursor
 * results can be iterated over using the standard java.util.Iterator methods.  The close() method will close the underlying 
 * cursor.
 */
public class CursorIteratorImpl<T> implements CloseableIterator<T> {
    /** An encapsulation of a method to return an object from the current state of an android.database.Cursor.
     */
    public static interface OfCursor<T> {
        /**
         * Returns an object from the current state of the passed cursor.
         * @param cursor a cursor
         * @return the object represented by the current state of the cursor
         */
        T ofCursor(Cursor cursor);
    }
    
    private Cursor cursor;
    private OfCursor<T> ofCursor;
    private boolean more;
    
    /**
     * Constructs a new CursorIterator from the given cursor and method for translating current cursor state into an object.
     * @param cursor a cursor
     * @param ofCursor an encapsulated method to return an object from the current state of the cursor
     */
    public CursorIteratorImpl(Cursor cursor, OfCursor<T> ofCursor) {
        this.cursor = cursor;
        this.ofCursor = ofCursor;
        more = this.cursor.moveToFirst();
    }
    
//    public Cursor getCursor() {
//        return cursor;
//    }
//
    @Override
    public void close() {
        if(!cursor.isClosed()) cursor.close();
        more = false;
    }

//    public T ofCursor(Cursor c) {
//        return ofCursor.ofCursor(c);
//    }
//    
    @Override
    public boolean hasNext() {
        if(!more) close();
        return more;
    }

    @Override
    public T next() {
        T res = ofCursor.ofCursor(cursor);
        more = cursor.moveToNext();
        if(!more) close();
        return res;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
