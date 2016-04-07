/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "dorepository-v2";
    static final int DATABASE_VERSION = 10;
    final Context context;
    
    public DatabaseHelper(Context context) {
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    	db.execSQL("CREATE TABLE OBJECTS " +
    			 "(" +
    			  "HANDLE TEXT, " +
                  "ELEMENT_NAME TEXT, " +
    			  "ATTRIBUTE_NAME TEXT, " +
    			  "VALUE TEXT, " +
    			  "PRIMARY KEY (HANDLE, ELEMENT_NAME, ATTRIBUTE_NAME)" +
    			  ");");
        db.execSQL("CREATE INDEX everything_index ON " 
                + "OBJECTS (HANDLE, ELEMENT_NAME, ATTRIBUTE_NAME, VALUE );");      
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        Cursor c = db.rawQuery("PRAGMA journal_mode=MEMORY", null); 
        c.close();
        db.execSQL("PRAGMA synchronous=OFF");
        db.execSQL("PRAGMA read_uncommitted=ON");
    }

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		
	}
}


