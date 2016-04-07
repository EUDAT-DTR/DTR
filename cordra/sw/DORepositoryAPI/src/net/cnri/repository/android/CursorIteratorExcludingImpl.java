/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.android.CursorIteratorImpl.OfCursor;
import android.database.Cursor;

/** Like CursorIteratorImpl, but can exclude some elements
 */
class CursorIteratorExcludingImpl<T> implements CloseableIterator<T> {
    static interface CursorPredicate {
        boolean apply(Cursor c);
    }
    
    private Cursor cursor;
    private OfCursor<T> ofCursor;
    private CursorPredicate exclusion;
    private boolean more;
    
    /**
     * Constructs a new CursorIterator from the given cursor and method for translating current cursor state into an object.
     * @param cursor a cursor
     * @param ofCursor an encapsulated method to return an object from the current state of the cursor
     */
    public CursorIteratorExcludingImpl(Cursor cursor, OfCursor<T> ofCursor, CursorPredicate exclusion) {
        this.cursor = cursor;
        this.ofCursor = ofCursor;
        this.exclusion = exclusion;
        more = this.cursor.moveToFirst();
        advanceMore();
    }
    
    private void advanceMore() {
        while(more && exclusion.apply(cursor)) {
            more = cursor.moveToNext();
        }
        if(!more) close();
    }

    @Override
    public void close() {
        if(!cursor.isClosed()) cursor.close();
        more = false;
    }

    @Override
    public boolean hasNext() {
        advanceMore();
        return more;
    }

    @Override
    public T next() {
        T res = ofCursor.ofCursor(cursor);
        more = cursor.moveToNext();
        advanceMore();
        return res;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
   
}
