//package com.neolink.providers.contacts;
//import com.neolink.providers.contacts.PrivateContactsDataHelper;
//import com.neolink.providers.contacts.PrivateContactsDataHelper.Tables;
//import com.neolink.providers.util.*;
//
//import android.content.AsyncQueryHandler;
//import android.content.ContentProvider;
//import android.content.ContentUris;
//import android.content.ContentValues;
//import android.content.Context;
//import android.content.UriMatcher;
//import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteQueryBuilder;
//import android.net.Uri;
//import com.neolink.provider.PrivateCallLog;
//import com.neolink.provider.PrivateCallLog.PrivateCalls;
//import com.neolink.provider.PrivateContactContract;
//import android.provider.Settings;
//import android.text.TextUtils;
//import android.util.Log;
//import neolink.telephony.PrivateManager;
//import neolink.telephony.PrivateMode;
//import android.app.Application;
//import neolink.telephony.PrivateIntents;
//
//public class PrivateCallLogProvider extends ContentProvider{
//	private static final String LOG_TAG = "PrivateCallLogPrivate";
//	private static final boolean DEBUG = true;
//	
//	private static final int CALLS = 1;
//	private static final int CALLS_ID = 2;
//	
//	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
//	
//	static{
//		sUriMatcher.addURI(PrivateCallLog.AUTHORITY, "calls", CALLS);
//		sUriMatcher.addURI(PrivateCallLog.AUTHORITY, "calls/#", CALLS_ID);
//	}
//	
//	private PrivateContactsDataHelper mDbHelper;
//	private Context mContext;
//	
//	@Override
//	public boolean onCreate() {
//		mContext = getContext();
//		mDbHelper = getDatabaseHelper(mContext);
//		return true;
//	}
//	
//	private PrivateContactsDataHelper getDatabaseHelper(final Context context){
//		return PrivateContactsDataHelper.getInstance(context);
//	}
//
//	@Override
//	public Cursor query(Uri uri, String[] projection, String selection,
//			String[] selectionArgs, String sortOrder) {
//		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
//		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//		qb.setTables(Tables.PRIVATE_CALL);
//		int match = sUriMatcher.match(uri);
//		com.neolink.providers.util.Utils.logView("PrivateCallLogProvider query uri :"+uri+",selection :"+selection+"selectionArgs=="+selectionArgs);
//		switch(match){
//		case CALLS:
//			break;
//		case CALLS_ID:
//			break;
//		}
//		Cursor c = db.query(Tables.PRIVATE_CALL, projection, selection, selectionArgs, null, null, sortOrder);/*qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);*/
//		if(c != null){
//			Utils.logView("PrivateCallLogProvider count :"+c.getCount());
//			c.setNotificationUri(getContext().getContentResolver(), PrivateCallLog.CONTENT_URI);
//		}
//		Utils.logView("PrivateCallLogProvider db path :"+db.getPath());
//		return c;
//	}
//
//	@Override
//	public String getType(Uri uri) {
//		int match = sUriMatcher.match(uri);
//		switch(match){
//		case CALLS:
//			return PrivateCalls.CONTENT_TYPE;
//		case CALLS_ID:
//			return PrivateCalls.CONTENT_ITEM_TYPE;
//			default:
//				throw new IllegalArgumentException("Unknown URI: "+ uri);
//		}
//	}
//
//	@Override
//	public Uri insert(Uri uri, final ContentValues values) {
//
//		Long modeType = (long) -1;
//		int mMode = Settings.Secure.getInt(getContext().getContentResolver(),
//				Settings.Secure.PRIVATE_PHONE_MODE, PrivateMode.MODE_UNKNOWN);
//
//		if (mMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
//			modeType = (long) PrivateContactContract.Contacts.CONTACTS_IMPORT_BY_PCTOOL_PDT;
//		} else if (mMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
//			modeType = (long) PrivateContactContract.Contacts.CONTACTS_IMPORT_BY_PCTOOL_MPT;
//		} 
//		values.put(PrivateCallLog.PrivateCalls.CALL_LOG_MODE, modeType);
//		Utils.logView("PrivateCallLogProvider num11="
//				+ values.get(PrivateCallLog.PrivateCalls.NUMBER));
//		if( ! TextUtils.isEmpty(((String)values.get(PrivateCallLog.PrivateCalls.NUMBER)))){
//			String[] number = new String[]{ values.get(PrivateCallLog.PrivateCalls.NUMBER).toString()}; 
//			Cursor contactcursor = mContext.getContentResolver().query(
//					PrivateContactContract.Data.CONTENT_URI,
//					null,
//					PrivateContactContract.Data.DATA1 + "= ?",
//					number, null);
//			if (contactcursor != null) {
//				while (contactcursor.moveToNext()) {
//					String data_id = contactcursor.getString(1);
//
//					Utils.logView("PrivateCallLogProvider data_id"
//							+ data_id);
//
//					Cursor contactname = mContext.getContentResolver().query(
//							PrivateContactContract.Contacts.CONTENT_URI, null,
//							PrivateContactContract.Contacts._ID + "= ?",
//							new String[] { data_id }, null);
//					if (contactname != null) {
//						while (contactname.moveToNext()) {
//
//							Utils.logView("PrivateCallLogProvider num2233777=="
//									+ contactname.getString(1));
//
//							values.put(PrivateCallLog.PrivateCalls.DISPLAY_NAME,
//									contactname.getString(1));
//							values.put(PrivateCallLog.PrivateCalls.CALLLOG_CONTACTS_ID, contactname.getString(0));
//						}
//						contactname.close();
//					}
//
//				}
//			}else{
//			}
//			contactcursor.close();
//		}else{
//			
//			return null;
//		}
//
//		SQLiteDatabase db = mDbHelper.getReadableDatabase();
//		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//		
//		long rowId = 0;
//		int match = sUriMatcher.match(uri);
//		switch(match){
//		case CALLS:
//			qb.setTables(Tables.PRIVATE_CALL);
//			rowId = db.insert(Tables.PRIVATE_CALL, null, values);
//			break;
//		case CALLS_ID:
//			break;
//		}
//		
//		if(rowId > 0){
//			getContext().getContentResolver().notifyChange(PrivateCallLog.CONTENT_URI, null,false);
//			return ContentUris.withAppendedId(uri, rowId);
//		}
//		return null;
//	}
//
//	@Override
//	public int delete(Uri uri, String selection, String[] selectionArgs) {
//		return 0;
//	}
//
//	@Override
//	public int update(Uri uri, ContentValues values, String selection,
//			String[] selectionArgs) {
//
//		SQLiteDatabase db = mDbHelper.getReadableDatabase();
//		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
//
//		long rowId = 0;
//		
//		Utils.logView("PrivateCallLogProvider update"
//				+"uri="+uri+"values="+values+"selection="+selection);
//		
//		
//		int match = sUriMatcher.match(uri);
//		switch (match) {
//		case CALLS:
//			qb.setTables(Tables.PRIVATE_CALL);
//			rowId = db.update(Tables.PRIVATE_CALL,values, null,  null);
//			break;
//		case CALLS_ID:
//			break;
//		}
//
//		if (rowId > 0) {
//			getContext().getContentResolver().notifyChange(
//					PrivateCallLog.CONTENT_URI, null, false);
//		}
//		return 0;
//	}
//
//}
