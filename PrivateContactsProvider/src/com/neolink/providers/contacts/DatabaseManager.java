package com.neolink.providers.contacts;

import java.util.concurrent.atomic.AtomicInteger;

import android.database.sqlite.SQLiteDatabase;

public class DatabaseManager {
 
    private AtomicInteger mOpenCounter = new AtomicInteger();
 
    private static DatabaseManager instance;
    private static PrivateContactsDataHelper mDatabaseHelper;
    private SQLiteDatabase mDatabase;
 
    public static synchronized void initializeInstance(PrivateContactsDataHelper helper) {
        if (instance == null) {
            instance = new DatabaseManager();
            mDatabaseHelper = helper;
        }
    }
 
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(DatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }
 
        return instance;
    }
 
    public synchronized SQLiteDatabase openDatabase() {
        if(mOpenCounter.incrementAndGet() == 1) {
            // Opening new database
            mDatabase = mDatabaseHelper.getWritableDatabase();
        }
        return mDatabase;
    }
 
    public synchronized void closeDatabase() {
        if(mOpenCounter.decrementAndGet() == 0) {
            // Closing database
            mDatabase.close();
 
        }
    }}