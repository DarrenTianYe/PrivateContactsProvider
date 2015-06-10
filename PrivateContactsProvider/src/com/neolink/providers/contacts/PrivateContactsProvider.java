package com.neolink.providers.contacts;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import com.android.common.content.ProjectionMap;
import com.google.android.collect.Maps;
import com.neolink.providers.contacts.PrivateContactsDataHelper.Tables;
import com.neolink.providers.contacts.PrivateContactsDataHelper.Views;
import com.neolink.providers.util.HanziToPinyin;
import com.neolink.providers.util.HanziToPinyin.Token;
import com.neolink.providers.util.Utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteTransactionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.StrictMode;
import android.provider.BaseColumns;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.ContactCounts;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MHTUtil;
import neolink.telephony.PrivateContactConst;
import neolink.telephony.PrivateContactContracts;
import neolink.telephony.PrivateManager;
import neolink.telephony.PrivateMode;
import neolink.telephony.PrivateContactConst.DataKindMimeType;
import neolink.telephony.PrivateContactContracts.CallLogColumns;
import neolink.telephony.PrivateContactContracts.Contacts;
import neolink.telephony.PrivateContactContracts.ContactsColumns;
import neolink.telephony.PrivateContactContracts.Data;
import neolink.telephony.PrivateContactContracts.DataColumns;
import neolink.telephony.PrivateContactContracts.GroupContact;
import neolink.telephony.PrivateContactContracts.GroupContactColumns;
import neolink.telephony.PrivateContactContracts.MimetypesColumns;
import neolink.telephony.PrivateContactContracts.ZoneColumns;
import android.app.Application;
import android.app.SearchManager;

public class PrivateContactsProvider extends ContentProvider implements
		SQLiteTransactionListener {
	public static final String LOG_TAG = "PrivateContactsProvider";
	private HandlerThread mBackgroundThread;
	private Handler mBackgroundHandler;
	private static final UriMatcher URI_MATCHER = new UriMatcher(
			UriMatcher.NO_MATCH);
	
	private PrivateContactsDataHelper mPrivateContactsDatabaseHelper;
	private SQLiteStatement mContactDisplayNameUpdate;
	private SQLiteStatement mContactToDataUpdate;
	private SQLiteStatement mContactToCallLogUpdate;
	private SQLiteStatement mGroupToDataUpdate;

	private PrivateContactTransactionContext mContactTransactionContext = new PrivateContactTransactionContext();
	private final ThreadLocal<PrivateContactTransactionContext> mTransactionContext = new ThreadLocal<PrivateContactTransactionContext>();

	private static final int PRIVATE_CONTACTS = 100;
	private static final int PRIVATE_CONTACTS_ID = 101;
	private static final int PRIVATE_CONTACTS_ID_DATAS = 102;
	private static final int DATABASE_VERSION = 2;
	private static HashMap<String, String> sNotesProjectionMap;
	private static final int PRIVATE_DATA = 200;
	private static final int PRIVATE_DATA_GROUP = 201;
	private static final int PRIVATE_DATA_CONTACTS = 202;
	private static final int PRIVATE_DATA_ID = 203;

	private static final int PRIVATE_GROUP_CONTACT = 300;
	private static final int PRIVATE_GROUP_CONTACT_ID = 301;

	private static final int PRIVATE_GROUP_TYPE = 111;

	private static final int PRIVATE_CALLLOG = 115;
	private static final int PRIVATE_CALLLOG_ID = 116;

	private static final int PRIVATE_INDEXSEARCH_ID = 800;
	private static final int PRIVATE_GROUP_ZONE = 801;
	private static final int PRIVATE_MIMETYPE = 802;

	private final ThreadLocal<SQLiteDatabase> mDb = new ThreadLocal<SQLiteDatabase>();
	private final ThreadLocal<PrivateContactsDataHelper> mDbHelper = new ThreadLocal<PrivateContactsDataHelper>();

	private  HashMap<String, Long> mMimeTypeCache;
	private final HashMap<String, Long> mZoneIdCache = new HashMap<String, Long>();

	public static final String UPDATE_CONTACTS = "SELECT "
			+ DataColumns.MIMETYPE_ID + "," + Data.DATA1 + "," + Data.DATA2
			+ "," + Data.DATA3 + " FROM " + Tables.PRIVATE_DATA + " WHERE "
			+ Data.CONTACT_ID + " = ?";
	
	public static final String UPDATE_CALL_LOG_FROM_CONTACTS = "SELECT "
			+ ContactsColumns._ID + ","
			+ ContactsColumns.DISPLAY_NAME + " FROM " + Tables.PRIVATE_CONTACT
			+ " WHERE " +
			ContactsColumns.NUMBER + " =? ";

	public static final String UPDATE_CALL_LOG_FROM_GROUP = "SELECT "
			+ GroupContactColumns._ID + ","
			+ GroupContactColumns.GROUP_MODE_TYPE + ","
			+ GroupContactColumns.GROUP_CONTACT_NUMBER + " FROM "
			+ Tables.PRIVATE_CONTACT + " WHERE ("
			+ GroupContactColumns.GROUP_MODE_TYPE + " = ?" + " AND "
			+ GroupContactColumns.GROUP_CONTACT_NUMBER + " = ?" + " )";

	static {

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "contacts",
				PRIVATE_CONTACTS);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "contacts/#",
				PRIVATE_CONTACTS_ID);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY,
				"contacts_filter/#", PRIVATE_INDEXSEARCH_ID);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY,
				"contacts/#/datas", PRIVATE_CONTACTS_ID_DATAS);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_data",
				PRIVATE_DATA);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_data/#",
				PRIVATE_DATA_ID);
		
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_data_group",
				PRIVATE_DATA_GROUP);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_data_contact",
				PRIVATE_DATA_CONTACTS);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_groups",
				PRIVATE_GROUP_CONTACT);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY,
				"PrivateGroupContact/#", PRIVATE_GROUP_CONTACT_ID);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY,
				"PrivateGroupContact/#", PRIVATE_GROUP_TYPE);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_calls/#",
				PRIVATE_CALLLOG_ID);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_calls",
				PRIVATE_CALLLOG);

		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_zone",
				PRIVATE_GROUP_ZONE);
		URI_MATCHER.addURI(PrivateContactContracts.AUTHORITY, "p_type",
				PRIVATE_MIMETYPE);
	}

	/** Contains just the contacts columns */
	private static final ProjectionMap sContactsColumns = ProjectionMap
			.builder().add(PrivateContactContracts.ContactsColumns._ID)
			.add(ContactsColumns.NUMBER).add(ContactsColumns.DISPLAY_NAME)
			.add(ContactsColumns.CONTACTS_PCTOOLS)
			.add(ContactsColumns.CONTACTS_TYPE)
			.add(ContactsColumns.CURRENT_MODE)
			.add(ContactsColumns.SORT_KEY).build();

	/** Contains just the zone columns */
	private static final ProjectionMap sZoneColumns = ProjectionMap.builder()
			.add(PrivateContactContracts.ZoneColumns._ID)
			.add(ZoneColumns.ZONE_NAME).build();

	/** Contains just the group columns */
	private static final ProjectionMap sGroupsColumns = ProjectionMap.builder()
			.add(PrivateContactContracts.ContactsColumns._ID)
			.add(GroupContactColumns.GROUP_ID)
			.add(GroupContactColumns.GROUP_CONTACT_NAME)
			.add(GroupContactColumns.GROUP_CONTACT_NUMBER)
			.add(GroupContactColumns.GROUP_CONTACT_TYPE)
			.add(GroupContactColumns.GROUP_ZONE_NAME)
			.add(GroupContactColumns.GROUP_ZONE_ID)
			.add(GroupContactColumns.GROUP_DEFAULT)
			.add(GroupContactColumns.GROUP_MODE_TYPE).build();

	/** Contains just the data columns */
	private static final ProjectionMap sDataColumns = ProjectionMap.builder()
			.add(DataColumns._ID).add(DataColumns.GROUP_ID)
			.add(DataColumns.MIMETYPE_ID).add(DataColumns.CONTACT_ID)
			.add(DataColumns.DATA1).add(DataColumns.DATA2)
			.add(DataColumns.DATA3).build();

	/** Contains just the call log columns */
	private static final ProjectionMap sCallLogColumns = ProjectionMap
			.builder().add(CallLogColumns._ID).add(CallLogColumns.NUMBER)
			.add(CallLogColumns.DISPLAY_NAME).add(CallLogColumns.DATE)
			.add(CallLogColumns.DURATION).add(CallLogColumns.CALL_LOG_MODE)
			.add(CallLogColumns.IS_READ).add(CallLogColumns.IS_HANDUP)
			.add(CallLogColumns.IS_NEW).build();

	/** Contains just the call the mimetype columns */
	private static final ProjectionMap sMimetypeColumns = ProjectionMap
			.builder().add(MimetypesColumns._ID).add(MimetypesColumns.MIMETYPE)
			.build();

	private volatile PrivateContactsCountDownLatch mReadAccessLatch;
	private volatile PrivateContactsCountDownLatch mWriteAccessLatch;
	private int mMode;

	@Override
	public boolean onCreate() {
		Utils.logProvider("on create");
		mPrivateContactsDatabaseHelper = PrivateContactsDataHelper
				.getInstance(getContext());
		mTransactionContext.set(mContactTransactionContext);
		mMimeTypeCache =mPrivateContactsDatabaseHelper.mMimetypeCache;
		mDbHelper.set(mPrivateContactsDatabaseHelper);
		initialize();
		return true;
	}

	private boolean initialize() {

		mReadAccessLatch = new PrivateContactsCountDownLatch(1);
		mWriteAccessLatch = new PrivateContactsCountDownLatch(1);
		mBackgroundThread = new HandlerThread("ContactsProviderWorker",
				Process.THREAD_PRIORITY_BACKGROUND);
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				performBackgroundTask(msg.what, msg.obj);
			}
		};

		return true;
	}

	protected void performBackgroundTask(int task, Object arg) {
		switch (task) {
		}
	}

	private void waitForAccess(PrivateContactsCountDownLatch latch) {
		if (latch == null) {
			return;
		}

		while (true) {
			try {
				latch.await();
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	protected void scheduleBackgroundTask(int task) {
		mBackgroundHandler.sendEmptyMessage(task);
	}

	protected void scheduleBackgroundTask(int task, Object arg) {
		mBackgroundHandler.sendMessage(mBackgroundHandler.obtainMessage(task,
				arg));
	}

	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		int match = URI_MATCHER.match(uri);
		Utils.logProvider("PrivateContactsProvider   query uri is:" + uri
				+ " selection is:" + selection + "  sortOrder is:" + sortOrder
				+ "match=" + match);

		if (selectionArgs != null) {
			for (int i = 0; i < selectionArgs.length; i++) {
				Utils.logProvider("selectionArgs==" + selectionArgs[i]);
			}
		}
		checkReadableDB();
		// waitForAccess(mWriteAccessLatch);
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (match) {
		case PRIVATE_INDEXSEARCH_ID:

			setTablesAndProjectionMapFroPrivateIndexSearch(qb, uri);
			final String[] PROJECTION = new String[] {
					PrivateContactContracts.ContactsColumns._ID,
					Contacts.DISPLAY_NAME, Contacts.SEARCH_INDEX_NAME,
					Contacts.SEARCH_INDEX_NUMBER };
			String[] selectionArgsIndex = null;
			if (!TextUtils.isEmpty(selection)) {

				String pingyingName = getFullPinYin(selection);// get the
																// pingyin name;
				String firstName = getFirstPinYin(selection); // get the first
																// name;
				selectionArgsIndex = new String[pingyingName.length()];

				if (!isNumeric(pingyingName)) {

					selection = "search_index like ?";
					for (int i = 0; i < pingyingName.length() - 1; i++) {
						selection = selection + "or search_index like ?"
								+ "or number like ?";
					}
					for (int i = 0; i < pingyingName.length(); i++) {
						selectionArgsIndex[i] = "%" + pingyingName + "%";
					}

				} else {
					selection = "number like ?";
					for (int i = 0; i < pingyingName.length() - 1; i++) {
						selection = selection + "or number like ?"
								+ "or number like ?";
					}
					for (int i = 0; i < pingyingName.length(); i++) {
						selectionArgsIndex[i] = "%" + pingyingName + "%";
					}

				}
			} else {
				selection = null;
				selectionArgs = null;

			}
			Cursor cursorIndex = qb.query(mDb.get(), PROJECTION, selection,
					selectionArgsIndex, null, null, sortOrder);
			if (cursorIndex != null) {
				cursorIndex.setNotificationUri(getContext()
						.getContentResolver(),
						PrivateContactContracts.AUTHORITY_URI);
			}
			return cursorIndex;

		case PRIVATE_CONTACTS:
			setTablesAndProjectionMapForPrivateContact(qb, uri);
			Cursor contactscursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);
			Utils.logProvider("PrivateContatProvider after nomal query cursor :"
					+ contactscursor);
			if (contactscursor != null) {
				contactscursor.setNotificationUri(getContext()
						.getContentResolver(),
						PrivateContactContracts.AUTHORITY_URI);
			}
			return contactscursor;
		case PRIVATE_CONTACTS_ID_DATAS:
			List<String> pathSegments = uri.getPathSegments();

			for (String s : pathSegments) {
				Utils.logProvider("PrivateContactsProvider query pathSegments s:"
						+ s);
			}
			long contactId = Long.parseLong(pathSegments.get(1));
			setTablesAndProjectionMapForData(qb, uri);
			selectionArgs = insertSelectionArg(selectionArgs,
					String.valueOf(contactId));
			qb.appendWhere(Data.CONTACT_ID + " = ? ");
			StringBuffer sbTemp = new StringBuffer();
			for (String s : projection) {
				sbTemp.append(s + ",");
			}
			Utils.logProvider("PrivateContactsProvider projection :"
					+ sbTemp.toString() + " selection :" + selection
					+ " selectionArgs :" + sbTemp.toString());
			sbTemp = new StringBuffer();
			for (String s : selectionArgs) {
				sbTemp.append(s + ",");
			}
			break;
		case PRIVATE_CONTACTS_ID:
			long contact_Id = ContentUris.parseId(uri);
			setTablesAndProjectionMapForData(qb, uri);
			selectionArgs = insertSelectionArg(selectionArgs,
					String.valueOf(contact_Id));
			qb.appendWhere(Data.CONTACT_ID + " = ? ");
			break;
		case PRIVATE_GROUP_ZONE:
			setTablesAndProjectionMapFroPrivateZone(qb, uri);
			Cursor groupCursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);
			return groupCursor;

		case PRIVATE_GROUP_CONTACT:
			setTablesAndProjectionMapFroPrivateGroupContact(qb, uri);
			Cursor zoneCursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);
			return zoneCursor;
		case PRIVATE_CALLLOG:
			setTablesAndProjectionMapFroPrivateCall(qb, uri);
			Cursor CallLogCursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);
			return CallLogCursor;
		case PRIVATE_DATA:
			
			setTablesAndProjectionMapForData(qb, uri);
			Cursor cursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);

			return cursor;	
		case PRIVATE_DATA_CONTACTS:
			
			setTablesAndProjectionMapForData(qb, uri);
			Cursor datacursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);

			return datacursor;
			
		case PRIVATE_DATA_GROUP:
			
			setTablesAndProjectionMapForData(qb, uri);
			Cursor groupcursor = qb.query(mDb.get(), projection, selection,
					selectionArgs, null, null, sortOrder);

			return groupcursor;
			default:
				Utils.logProvider(" the url is error");
		}
		return null;
	}

	private String[] insertSelectionArg(String[] selectionArgs, String arg) {
		if (selectionArgs == null) {
			return new String[] { arg };
		} else {
			int newLength = selectionArgs.length + 1;
			String[] newSelectionArgs = new String[newLength];
			newSelectionArgs[0] = arg;
			System.arraycopy(selectionArgs, 0, newSelectionArgs, 1,
					selectionArgs.length);
			return newSelectionArgs;
		}
	}

	private static final class AddressBookIndexQuery {
		public static final String LETTER = "letter";
		public static final String TITLE = "title";
		public static final String COUNT = "count";

		public static final String[] COLUMNS = new String[] { LETTER, TITLE,
				COUNT };

		public static final int COLUMN_LETTER = 0;
		public static final int COLUMN_TITLE = 1;
		public static final int COLUMN_COUNT = 2;

		// The first letter of the sort key column is what is used for the index
		// headings.
		public static final String SECTION_HEADING = "SUBSTR(%1$s,1,1)";

		public static final String ORDER_BY = LETTER + " COLLATE "
				+ "PHONEBOOK";
	}

	private Cursor bundleLetterCountExtras(Cursor cursor,
			final SQLiteDatabase DB, SQLiteQueryBuilder qb, String selection,
			String[] selectionArgs, String sortOrder, String countExpression) {
		if (!(cursor instanceof AbstractCursor)) {
			Log.w(LOG_TAG,
					"Unable to bundle extras.  Cursor is not AbstractCursor.");
			return cursor;
		}
		checkReadableDB();
		String sortKey;
		String sortOrderSuffix = "";
		if (sortOrder != null) {
			int spaceIndex = sortOrder.indexOf(' ');
			if (spaceIndex != -1) {
				sortKey = sortOrder.substring(0, spaceIndex);
				sortOrderSuffix = sortOrder.substring(spaceIndex);
			} else {
				sortKey = sortOrder;
			}
		} else {
			sortKey = Contacts.SORT_KEY;
		}

		String locale = Locale.getDefault().toString();

		HashMap<String, String> projectionMap = Maps.newHashMap();
		String sectionHeading = String.format(Locale.US,
				AddressBookIndexQuery.SECTION_HEADING, sortKey);
		projectionMap.put(AddressBookIndexQuery.LETTER, sectionHeading + " AS "
				+ AddressBookIndexQuery.LETTER);

		// If "what to count" is not specified, we just count all records.
		if (TextUtils.isEmpty(countExpression)) {
			countExpression = "*";
		}
		projectionMap.put(AddressBookIndexQuery.TITLE, "GET_PHONEBOOK_INDEX("
				+ sectionHeading + ",'" + locale + "')" + " AS "
				+ AddressBookIndexQuery.TITLE);
		projectionMap.put(AddressBookIndexQuery.COUNT, "COUNT("
				+ countExpression + ") AS " + AddressBookIndexQuery.COUNT);
		qb.setProjectionMap(projectionMap);

		Cursor indexCursor = qb.query(mDb.get(), AddressBookIndexQuery.COLUMNS,
				selection, selectionArgs, AddressBookIndexQuery.ORDER_BY,
				null /* having */, AddressBookIndexQuery.ORDER_BY
						+ sortOrderSuffix);

		try {
			int groupCount = indexCursor.getCount();
			Utils.logProvider("ContactsProvider2 bundleLetterCountExtra groupCount is:"
					+ groupCount);
			String titles[] = new String[groupCount];
			int counts[] = new int[groupCount];
			int indexCount = 0;
			String currentTitle = null;

			// Since GET_PHONEBOOK_INDEX is a many-to-1 function, we may end up
			// with multiple entries for the same title. The following code
			// collapses those duplicates.
			for (int i = 0; i < groupCount; i++) {
				indexCursor.moveToNext();
				String title = indexCursor
						.getString(AddressBookIndexQuery.COLUMN_TITLE);
				int count = indexCursor
						.getInt(AddressBookIndexQuery.COLUMN_COUNT);
				Utils.logProvider("ContactsProvider2 bundleLetterCountExtra currentTitle is:"
						+ currentTitle
						+ "  title is:"
						+ title
						+ " count is:"
						+ count);
				if (indexCount == 0 || !TextUtils.equals(title, currentTitle)) {
					titles[indexCount] = currentTitle = title;
					counts[indexCount] = count;
					indexCount++;
				} else {
					counts[indexCount - 1] += count;
				}
			}
			Utils.logProvider("ContactsProvider2 bundleLetterCountExtra indexCount is:"
					+ indexCount);
			if (indexCount < groupCount) {
				String[] newTitles = new String[indexCount];
				System.arraycopy(titles, 0, newTitles, 0, indexCount);
				titles = newTitles;

				int[] newCounts = new int[indexCount];
				System.arraycopy(counts, 0, newCounts, 0, indexCount);
				counts = newCounts;
			}

			final Bundle bundle = new Bundle();
			bundle.putStringArray(
					ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES, titles);
			bundle.putIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS,
					counts);
			((AbstractCursor) cursor).setExtras(bundle);
			return cursor;
		} finally {
			indexCursor.close();
		}
	}

	private boolean readBooleanQueryParameter(Uri uri, String parameter,
			boolean defaultValue) {
		String query = uri.getEncodedQuery();
		if (query == null) {
			return defaultValue;
		}
		int index = query.indexOf(parameter);
		if (index == -1) {
			return defaultValue;
		}
		index += parameter.length();
		return !matchQueryParameter(query, index, "=0", false)
				&& !matchQueryParameter(query, index, "=false", true);
	}

	private static boolean matchQueryParameter(String query, int index,
			String value, boolean ignoreCase) {
		Utils.logProvider("COntactsProvider2 matchQueryParameter query is:"
				+ query + " index is:" + index + "value is:" + value
				+ "ingnore is:" + ignoreCase);
		int length = value.length();
		return query.regionMatches(ignoreCase, index, value, 0, length)
				&& (query.length() == index + length || query.charAt(index
						+ length) == '&');
	}
	
	
	

	private void setTablesAndProjectionMapForPrivateContact(
			SQLiteQueryBuilder qb, Uri uri) {
		StringBuilder sb = new StringBuilder();
		sb.append(Views.PRIVATE_CONTACT);
		qb.setProjectionMap(sContactsColumns);
		qb.setTables(sb.toString());
	}

	private void setTablesAndProjectionMapFroPrivateGroupContact(
			SQLiteQueryBuilder qb, Uri uri) {
		qb.setTables(Tables.PRIVATE_GROUP_CONTACT);
		qb.setProjectionMap(sGroupsColumns);
	}

	private void setTablesAndProjectionMapFroPrivateZone(SQLiteQueryBuilder qb,
			Uri uri) {
		qb.setTables(Tables.PRIVATE_ZONE);
		qb.setProjectionMap(sZoneColumns);
	}

	private void setTablesAndProjectionMapFroPrivateIndexSearch(
			SQLiteQueryBuilder qb, Uri uri) {
		qb.setTables(Tables.PRIVATE_CONTACT);
	}

	private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb,
			Uri uri) {
		StringBuilder sb = new StringBuilder();
		sb.append(Tables.PRIVATE_DATA);
		qb.setTables(sb.toString());
		qb.setProjectionMap(sDataColumns);
	}

	private void setTablesAndProjectionMapFroPrivateCall(SQLiteQueryBuilder qb,
			Uri uri) {
		qb.setTables(Tables.PRIVATE_CALL);
		qb.setProjectionMap(sCallLogColumns);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Utils.logProvider("PrivateContactsProvider update uri is:" + uri
				+ " contentValues is:" + values + "selection=" + selection
				+ "URI_MATCHER.match(uri)" + URI_MATCHER.match(uri));

		if (selectionArgs != null) {
			for (int i = 0; i < selectionArgs.length; i++) {
				Utils.logProvider("selectionArgs==" + selectionArgs[i]);
			}
		}
		checkWritableDb();
		// waitForAccess(mWriteAccessLatch);
		return doUpdateActionInTransaction(uri, values, selection,
				selectionArgs);
	}

	private Uri doInsertActionInTransaction(Uri uri, ContentValues values) {
		Uri result = null;
		SQLiteDatabase db = mPrivateContactsDatabaseHelper
				.getWritableDatabase();
		db.beginTransactionWithListener(this);
		try {

			result = insertInTransaction(uri, values);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			contactyChanged(uri);

		}

		return result;
	}

	private void contactyChanged(Uri uri) {
		// Log.d(LOG_TAG, "do***************noticefy>>r" + uri);
		if (uri.equals(PrivateContactContracts.Contacts.CONTENT_URI)) {
			getContext().getContentResolver().notifyChange(
					Contacts.CONTENT_URI, null, false);
		} else if (uri.equals(PrivateContactContracts.Data.CONTENT_URI)) {
			getContext().getContentResolver().notifyChange(Data.CONTENT_URI,
					null, false);
			// } else if
			// (uri.equals(PrivateContactContract.Contacts.CONTENT_LOG_URI)) {
			// // getContext().getContentResolver().notifyChange(
			// // Contacts.CONTENT_LOG_URI, null, false);
			// } else if
			// (uri.getEncodedAuthority().equals("com.neolink.contacts")) {
			getContext().getContentResolver().notifyChange(
					Contacts.CONTENT_URI, null, false);
		} else {
			Log.d(LOG_TAG, "do***************noticefy error" + uri);
		}
	}

	private int doUpdateActionInTransaction(Uri uri, ContentValues values,
			String selection, String[] selectionArgs) {
		int result = 0;
		SQLiteDatabase db = mPrivateContactsDatabaseHelper
				.getWritableDatabase();
		db.beginTransaction();
		try {
			result = updateInTransaction(uri, values, selection, selectionArgs);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			contactyChanged(uri);
		}
		return result;
	}

	private int doDeleteActionInTransaction(Uri uri, String selection,
			String[] selectionArgs) {
		int result = 0;
		SQLiteDatabase db = mPrivateContactsDatabaseHelper
				.getWritableDatabase();
		Utils.logProvider("PrivateContactsProvider doDeleteActionInTransaction uri is:"
				+ uri);
		db.beginTransaction();
		try {
			result = deleteInTransaction(uri, selection, selectionArgs);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			if (uri.equals(PrivateContactContracts.GroupContact.CONTENT_URI)) {
				getContext().getContentResolver().notifyChange(
						GroupContact.CONTENT_URI, null, false);
			} else {
				contactyChanged(uri);
			}
		}
		return result;
	}

	private Uri insertInTransaction(Uri uri, ContentValues values) {
		int match = URI_MATCHER.match(uri);
		Utils.logProvider("PrivateContactsProvider insertInTransaction uri is:"
				+ uri + " match is:" + match);
		switch (match) {
		case PRIVATE_CONTACTS:
			return insertContact(uri, values);
		case PRIVATE_DATA:
			return insertData(uri, values);
		case PRIVATE_GROUP_CONTACT:
			return insertGroupContact(uri, values);
		case PRIVATE_CALLLOG:
			return insertCallLogData(uri, values);
		default:
			return null;
		}

	}

	private int updateInTransaction(Uri uri, ContentValues values,
			String selection, String[] selectionArgs) {
		int count = 0;
		int match = URI_MATCHER.match(uri);
		Log.d(LOG_TAG, "updateInTransaction update uri is:" + uri
				+ " contentValues is:" + values + "match==" + match);
		switch (match) {
		case PRIVATE_CONTACTS:
			count = updateContact(values, selection, selectionArgs);
			return count;
		case PRIVATE_CONTACTS_ID:
			count = updateContact(ContentUris.parseId(uri), values);
			return count;
		case PRIVATE_DATA:
			count = updateData(values, selection, selectionArgs);
			return count;
		case PRIVATE_DATA_ID:
			long dataId = ContentUris.parseId(uri);
			count = updateData(dataId, values, selection, selectionArgs);
			return count;
		case PRIVATE_CALLLOG:
			count = updateDataCallLog(values, selection, selectionArgs);
			return count;
		case PRIVATE_CALLLOG_ID:
			count = updateDataCallLogId(uri,values, selection, selectionArgs);
			return count;
		case PRIVATE_GROUP_CONTACT:
			count = updateDataGroup(values, selection, selectionArgs);
			return count;
		default:
			break;
		}
		return -1;
	}

	private int updateData(long dataId, ContentValues values, String selection,
			String[] selectionArgs) {
		int count = 0;
		count = mDb.get().update(Tables.PRIVATE_DATA, values, selection,
				selectionArgs);
		return count;
	}

	private int updateDataCallLog(ContentValues values, String selection,
			String[] selectionArgs) {
		int count = 0;
		count = mDb.get().update(Tables.PRIVATE_CALL, values, selection,
				selectionArgs);
		return count;
	}
	
	private int updateDataCallLogId(Uri url,ContentValues values, String selection,
			String[] selectionArgs) {
		int count = 0;
        String selectionWithId =
                (PrivateContactContracts.CallLogColumns._ID + "=" + ContentUris.parseId(url) + " ")
                + (selection == null ? "" : " AND (" + selection + ")");
		count = mDb.get().update(Tables.PRIVATE_CALL, values, selectionWithId,
				selectionArgs);
		return count;
	}

	private int updateDataGroup(ContentValues values, String selection,
			String[] selectionArgs) {
		int count = 0;
		count = mDb.get().update(Tables.PRIVATE_GROUP_CONTACT, values,
				selection, selectionArgs);
		return count;
	}

	private int updateData(ContentValues values, String selection,
			String[] selectionArgs) {
		Utils.logProvider("PrivateContactsProvider updateData22 selection "
				+ selection + "values==" + values);
		int count = 0;
		int contacts_id = 0;
		String contacts_type = "";
		Cursor cursor = mDb.get().query(
				Tables.PRIVATE_DATA,
				new String[] { DataColumns._ID, Data.CONTACT_ID,
						Data.MIMETYPE_ID }, selection, selectionArgs, null,
				null, null);

		try {
			while (cursor.moveToNext()) {
				long dataId = cursor.getLong(0);
				contacts_id = cursor.getInt(1);
				contacts_type = cursor.getString(2);
				count += updateData(dataId, values, selection, selectionArgs);
				Utils.logProvider("PrivateContactsProvider updateData22 dataContact_id"
						+ " "
						+ "values=="
						+ values
						+ "contacts_id"
						+ contacts_id
						+ "count=="
						+ count
						+ "dataId=="
						+ dataId
						+ "contacts_type==" + contacts_type);
			}
		} finally {
			cursor.close();
		}
		// Utils.logProvider("PrivateContactsProvider updateData22 selection "+selection+"values=="+values+"dataId"+"count=="+count);

		// if ("1".equals(contacts_type) || "2".equals(contacts_type)) {
		// updateContactDisplayName(values, contacts_id);
		// }

		return count;
	}

	private int updateContact(ContentValues values, String selection,
			String[] selectionArgs) {
		Utils.logProvider("PrivateContactsProvider updateContact22 selection:"
				+ selection + ",values:" + values);
		int count = 0;

		Cursor cursor = mDb.get().query(Views.PRIVATE_CONTACT,
				new String[] { ContactsColumns._ID }, selection, selectionArgs,
				null, null, null);
		try {
			while (cursor.moveToNext()) {
				long contactId = cursor.getLong(0);
				updateContact(contactId, values);
				count++;
			}
		} finally {
			cursor.close();
		}
		return count;
	}

	private int updateContact(long contactId, ContentValues values) {
		Utils.logProvider("PrivateContactProvider updateContact11 contactId :"
				+ contactId + ",contentValues:" + values);
		String[] whereArgs = new String[] { String.valueOf(contactId) };
		int rslt = mDb.get().update(Tables.PRIVATE_CONTACT, values,
				ContactsColumns._ID + " = ?", whereArgs);
		return rslt;
	}
	
	private int deleteInTransaction(Uri uri, String selection,
			String[] selectionArgs) {
		int match = URI_MATCHER.match(uri);
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		switch (match) {
		case PRIVATE_CONTACTS:
			return mDb.get().delete(Tables.PRIVATE_CONTACT, selection,
					selectionArgs);
		case PRIVATE_CONTACTS_ID:
			long contactId = ContentUris.parseId(uri);
			Utils.logProvider("PrivateContactsPRovider deleteInTransaction contactId :"
					+ contactId);
			setTablesAndProjectionMapForPrivateContact(qb, null);
			qb.appendWhere(ContactsColumns._ID + " =? ");
			String[] args = new String[] { String.valueOf(contactId) };
			Cursor c = qb.query(mDb.get(), null, null, args, null, null, null);
			try {
				if (c.getCount() == 1) {
					return deleteContact(contactId);
				} else {
					return 0;
				}
			} finally {
				c.close();
			}
		case PRIVATE_GROUP_CONTACT:
			return deleteGroupData(uri, selection, selectionArgs);
		case PRIVATE_DATA:
			return deleteData(selection, selectionArgs);

		case PRIVATE_CALLLOG:
			return deleteCalllog(uri, selection, selectionArgs);
		case PRIVATE_CALLLOG_ID:
			long callLogId = ContentUris.parseId(uri);
			Utils.logProvider("PrivateContactsPRovider deleteInTransaction Id :"
					+ callLogId);
			setTablesAndProjectionMapFroPrivateCall(qb, null);
			qb.appendWhere(CallLogColumns._ID + " =? ");
			String[] args1 = new String[] { String.valueOf(callLogId) };
			Cursor c1 = qb.query(mDb.get(), null, null, args1, null, null, null);
			try {
				if (c1.getCount() == 1) {
					return deleteCalllogId(callLogId);
				} else {
					return 0;
				}
			} finally {
				c1.close();
			}
		case PRIVATE_DATA_ID:
			long dataId = ContentUris.parseId(uri);
			selectionArgs = new String[] { String.valueOf(dataId) };
			return deleteData(DataColumns._ID + " ?", selectionArgs);

		default:
			break;
		}
		return -1;
	}

	private int deleteGroupData(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase db = mPrivateContactsDatabaseHelper
				.getWritableDatabase();
		int count = db.delete(
				PrivateContactsDataHelper.Tables.PRIVATE_GROUP_CONTACT, where,
				whereArgs);
		return count;
	}

	private int deleteData(String selection, String[] selectionArgs) {
		// Utils.logProvider("PrivateContactsProvider deleteData selection:"+selection+" selection size:"+(selectionArgs==null?0:selectionArgs.length));
		int count = 0;
		count = mDb.get().delete(Tables.PRIVATE_DATA, selection, selectionArgs);

		return count;
	}
	
	private int deleteCalllogId(long id) {
		String whereContact = ContactsColumns._ID + " = " + id;
		int count = mDb.get().delete(Tables.PRIVATE_CALL, whereContact, null);
		return count;
	}

	private int deleteCalllog(Uri uri, String selection, String[] selectionArgs) {
		int count = 0;
		count = mDb.get().delete(Tables.PRIVATE_CALL, selection, selectionArgs);
		return count;
	}

	private int deleteContact(long contactId) {
		String whereContact = ContactsColumns._ID + " = " + contactId;
		String whereData = Data.CONTACT_ID + " = " + contactId;
		int count1 = mDb.get().delete(Tables.PRIVATE_CONTACT, whereContact,
				null);
		int count2 = mDb.get().delete(Tables.PRIVATE_DATA, whereData, null);
		Utils.logProvider("PrivateContactsProvider deleteContact count1  and count2 :"
				+ count1 + "," + count2);
		return count2;
	}

	private Uri insertContact(Uri uri, ContentValues values) {
		checkTransactionContext();

		ContentValues mValues = new ContentValues(values);
		
		if(mValues.containsKey(ContactsColumns.DISPLAY_NAME)){
			mValues.put(ContactsColumns.SEARCH_INDEX_NAME,getFirstPinYin(mValues.getAsString(ContactsColumns.DISPLAY_NAME)));
		}
		if(mValues.containsKey(ContactsColumns.NUMBER))
		mValues.put(ContactsColumns.SEARCH_INDEX_NUMBER, mValues.getAsInteger(ContactsColumns.NUMBER));
		checkTransactionContext();
		checkWritableDb();
		long contactReturnId = mDb.get().insert(Tables.PRIVATE_CONTACT, null,
				mValues);

		Utils.logProvider("contactReturnId=="
				+ contactReturnId
				+ "<<<<<<<<<<<<>>>>>>>>>>>>>>>>insertmContactToDataUpdate"
				+ getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE)
				+mPrivateContactsDatabaseHelper.getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE));
		if (contactReturnId < 0) {
			return null;
		}
		if (values.containsKey(Contacts.DISPLAY_NAME)) {
			insertmContactToDataUpdate(
				contactReturnId,
					values.getAsString(Contacts.DISPLAY_NAME),
					(int) getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE));
		}
		if (values.containsKey(Contacts.NUMBER)) {
			insertmContactToDataUpdate(
					contactReturnId,
					values.getAsString(Contacts.NUMBER),
					(int) getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE));
		}
		mTransactionContext.get().contactInsert(contactReturnId);
		uri = ContentUris.withAppendedId(uri, contactReturnId);
		return uri;
	}

	private Uri insertGroupContact(Uri uri, ContentValues values) {
		Utils.logProvider("PrivateContactsProvider insertGroupContact uri:"
				+ uri + ",values:" + values);
		checkTransactionContext();
		checkWritableDb();
		int zone_id;
		ContentValues mValues = new ContentValues(values);
		long groupReturnId = mDb.get().insert(Tables.PRIVATE_GROUP_CONTACT,
				null, mValues);
		uri = ContentUris.withAppendedId(uri, groupReturnId);
		Log.d(LOG_TAG, "insertGroupContact_groupReturnId=" + groupReturnId);
		if (groupReturnId < 0) {
			Log.d(LOG_TAG, "insertGroupContact_groupReturnId=" + groupReturnId);
		} else {
//			if (values.containsKey(GroupContact.GROUP_ZONE_NAME)) {
//				getZoneInfo(values.getAsString(GroupContact.GROUP_ZONE_NAME),values.getAsInteger(GroupContact.GROUP_ZONE_ID));
//			}else{
//				getZoneInfo("test1111",15);
//			}
			
			if (values.containsKey(GroupContact.GROUP_CONTACT_NAME)) {
				
				Log.d(LOG_TAG, "insertGroupContact_"+mPrivateContactsDatabaseHelper.
						getMimeTypeId(PrivateContactConst.DataKindMimeType.GROUP_NAME_CONTENT_ITEM_TYPE));
			}
			if (values.containsKey(GroupContact.GROUP_CONTACT_NUMBER)) {
				insertDataFromGroupUpdate(
						groupReturnId,
						values.getAsString(GroupContact.GROUP_CONTACT_NUMBER),
						(int) getMimeTypeId(PrivateContactConst.DataKindMimeType.GROUP_NUMBER_CONTENT_ITEM_TYPE));
			}
		}

		return uri;
	}

	private Uri insertCallLogData(Uri uri, ContentValues values) {
		Utils.logProvider("PrivateNormalProvider call log uri:" + uri
				+ ",values:" + values);
		checkTransactionContext();
		ContentValues mValues = new ContentValues(values);
		checkWritableDb();

		int normalid = 0;
		long normalid1 = mDb.get().insert(Tables.PRIVATE_CALL, null, mValues);
		if (normalid1 < 0) {
			Log.d(LOG_TAG, "normalid==" + normalid1);
		}else{
			if (values.containsKey(CallLogColumns.NUMBER)) {
				insertmCallLogToUpdateSelf(values
						.getAsString(CallLogColumns.NUMBER));
			}	
		}
		return uri;
	}
	
	/**
	 * Inserts a record in the {@link Tables#NAME_LOOKUP} table.
	 */
	public void insertmCallLogToUpdateSelf(String number) {
		
		mMode = Settings.Secure.getInt(getContext().getContentResolver(),
				Settings.Secure.PRIVATE_PHONE_MODE, PrivateMode.MODE_UNKNOWN);

		long modeType=0;
		if (mMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
			modeType = (long) PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_PDT;
		} else if (mMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
			modeType = (long) PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_MPT;
		}

		String[] selectionArgs = new String[1];
		mMode =11;
		//selectionArgs[0] = String.valueOf(11);
		selectionArgs[0] = String.valueOf(number);
		checkReadableDB();
		Cursor c = mDb.get().rawQuery(UPDATE_CALL_LOG_FROM_CONTACTS, selectionArgs);
		String name = "";
		long ContactId = 0;
		Utils.logProvider("insertmCallLogToUpdateSelf getCOunt is:"
				+ c.getCount());
		try {
			if (c.getCount() == 0) {
				c.close();
				return;
			}
			while (c.moveToNext()) {
				Utils.logProvider("insertmCallLogToUpdateSelf getCOunt is:"
						+ c.getCount() + " aa:" + c.getLong(0) + "bb:"
						+ c.getString(1));
				name=c.getString(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			c.close();
		}
		checkWritableDb();
		if (mContactToCallLogUpdate == null) {
			mContactToCallLogUpdate = mDb.get().compileStatement(
					"UPDATE " + Tables.PRIVATE_CALL + " SET "
							+ CallLogColumns.CONTACTS_ID + " =? " + " , "
							+ CallLogColumns.DISPLAY_NAME + " =? " + " WHERE "
							+ CallLogColumns.NUMBER + " = ?");
		}
		mContactToCallLogUpdate.bindLong(1, ContactId);
		bindString(mContactToCallLogUpdate, 2, name);
		bindString(mContactToCallLogUpdate, 3, number);
		mContactToCallLogUpdate.execute();
		
	}

	private Uri insertData(Uri uri, ContentValues values) {
		ContentValues mValues = new ContentValues();
		mValues.clear();
		mValues.putAll(values);

		mMode = Settings.Secure.getInt(getContext().getContentResolver(),
				Settings.Secure.PRIVATE_PHONE_MODE, PrivateMode.MODE_UNKNOWN);

		int modeType = 0;
		if (mMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
			modeType = PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_PDT;
		} else if (mMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
			modeType = PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_MPT;
		}

		long contactDataId = mValues.getAsLong(Data.CONTACT_ID);

		String mimeType = mValues.getAsString(MimetypesColumns.MIMETYPE);
		mValues.put(Data.MIMETYPE_ID, getMimeTypeId(mimeType));
		// mValues.put(Data.IMPORT_TYPE, modeType);
		mValues.remove(MimetypesColumns.MIMETYPE);

		long id = mDb.get().insert(Tables.PRIVATE_DATA, null, mValues);
		Utils.logProvider("PrivateContactsProvider insertData id is:" + id
				+ mValues + " mimeType is:" + mimeType + " contactDataId is:"
				+ contactDataId);
		if (id < 0) {
			return null;
		}
		// if (DataKindMimeType.NAME_CONTENT_ITEM_TYPE.equals(mimeType)
		// || DataKindMimeType.PHONE_CONTENT_ITEM_TYPE.equals(mimeType)) {
		// updateContactDisplayName(mValues, contactDataId);
		// }
		return ContentUris.withAppendedId(uri, id);
	}

	public long getMimeTypeId(String mimeType) {
		if (mMimeTypeCache.containsKey(mimeType))
			return mMimeTypeCache.get(mimeType);

		return lookupMimeTypeId(mimeType);
	}

	private long lookupMimeTypeId(String mimeType) {
		checkWritableDb();
		final SQLiteStatement mimeTypeQuery = mDb.get().compileStatement(
				"SELECT " + MimetypesColumns._ID + " FROM "
						+ Tables.PRIVATE_MIMETYPE + " WHERE "
						+ MimetypesColumns.MIMETYPE + " = ?");
		final SQLiteStatement mimetypeInsert = mDb.get().compileStatement(
				"INSERT INTO " + Tables.PRIVATE_MIMETYPE + "("
						+ MimetypesColumns.MIMETYPE + " ) VALUES (?)");
		try {
			return lookupAndCacheId(mimeTypeQuery, mimetypeInsert, mimeType,
					mMimeTypeCache);
		} finally {
			mimetypeInsert.close();
			mimeTypeQuery.close();
		}
	}

	private long lookupAndCacheId(SQLiteStatement query,
			SQLiteStatement insert, String value, HashMap<String, Long> cache) {
		Utils.logProvider("PrivateContactsProvider lookupAndCacheId");
		long id = -1;
		try {
			DatabaseUtils.bindObjectToProgram(query, 1, value);
			id = query.simpleQueryForLong();
			if (id != -1) {
				return id;
			}
		} catch (SQLiteDoneException e) {
			e.printStackTrace();
			DatabaseUtils.bindObjectToProgram(insert, 1, value);
			id = insert.executeInsert();
		Utils.logProvider("PrivateContactsProvider lookupAndCacheId CatchException id is:"
					+ id);
		}
		if (id != -1) {
			cache.put(value, id);
			return id;
		} else {
			throw new IllegalStateException(
					"Couldn't find or create internal value is:" + value);
		}
	}

	public long getZoneInfo(String zoneName, int id) {
		if (mZoneIdCache.containsKey(zoneName))
			return mZoneIdCache.get(zoneName);

		return lookupZoneId(zoneName,id);
	}

	private long lookupZoneId(String zoneName,int zoneid) {
		checkWritableDb();
		final SQLiteStatement mimeTypeQuery = mDb.get().compileStatement(
				"SELECT " + ZoneColumns._ID + " FROM " + Tables.PRIVATE_ZONE
						+ " WHERE " + ZoneColumns.ZONE_NAME + " = ?");
		final SQLiteStatement mimetypeInsert = mDb.get().compileStatement(
				"INSERT INTO " + Tables.PRIVATE_ZONE + "("+ ZoneColumns._ID+" , "
						+ ZoneColumns.ZONE_NAME + " ) VALUES (?,?)");
		try {
			return lookupZoneAndCacheId(mimeTypeQuery, mimetypeInsert,
					zoneName,zoneid, mZoneIdCache);
		} finally {
			mimetypeInsert.close();
			mimeTypeQuery.close();
		}
	}

	private long lookupZoneAndCacheId(SQLiteStatement query,
			SQLiteStatement insert, String value,int zoneid, HashMap<String, Long> cache) {
		Utils.logProvider("PrivateContactsProvider lookupAndCacheId");
		long id = -1;
		try {
			DatabaseUtils.bindObjectToProgram(query, 1, value);
			id = query.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			e.printStackTrace();
			DatabaseUtils.bindObjectToProgram(insert, 1, zoneid);
			DatabaseUtils.bindObjectToProgram(insert, 2, value);
			id = insert.executeInsert();
			Utils.logProvider("PrivateContactsProvider lookupAndCacheId CatchException id is:"
					+ id);
		}
		if (id != -1) {
			cache.put(value, id);
			return id;
		} else {
			throw new IllegalStateException(
					"Couldn't find or create internal value is:" + value);
		}
	}

	private void checkWritableDb() {
		if (mDb.get() == null) {
			mDb.set(mPrivateContactsDatabaseHelper.getWritableDatabase());
		}
	}

	private void checkReadableDB() {
		if (mDb.get() == null) {
			mDb.set(mPrivateContactsDatabaseHelper.getReadableDatabase());
		}
	}

	private void checkTransactionContext() {
		if (mTransactionContext.get() == null) {
			mTransactionContext.set(mContactTransactionContext);
		}
	}

	@Override
	public String getType(Uri uri) {
		final int match = URI_MATCHER.match(uri);
		switch (match) {
		case PRIVATE_CONTACTS:
			return Contacts.CONTENT_TYPE;
		case PRIVATE_CONTACTS_ID:
			return Contacts.CONTENT_ITEM_TYPE;
		case PRIVATE_GROUP_CONTACT:
			return GroupContact.CONTENT_TYPE;
		case PRIVATE_GROUP_CONTACT_ID:
			return GroupContact.CONTENT_ITEM_TYPE;
		}

		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {

		checkWritableDb();
		int match = URI_MATCHER.match(uri);
		Log.d(LOG_TAG, "insert...match==" + match + "values=");
		//waitForAccess(mWriteAccessLatch);
		Uri returnUri = doInsertActionInTransaction(uri, values);
		return returnUri;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Utils.logProvider("PrivateContactsProvider delete uri is:" + uri
				+ "selection=" + selection + "URI_MATCHER.match(uri)"
				+ URI_MATCHER.match(uri));

		if (selectionArgs != null) {
			for (int i = 0; i < selectionArgs.length; i++) {
				Utils.logProvider("selectionArgs==" + selectionArgs[i]);
			}
		}
		checkWritableDb();
		// waitForAccess(mWriteAccessLatch);
		return doDeleteActionInTransaction(uri, selection, selectionArgs);
	}

	@Override
	public void onCommit() {
		checkReadableDB();
		checkTransactionContext();
		Utils.logProvider(" onCommit ----------done");
		try {
			for (long contactId : mTransactionContext.get()
					.getInsertContactIdList()) {
				Utils.logProvider("PrivateContactSaveService onCommit ----------contactId=="
						+ contactId);
				// updateContactDisplayName(null, contactId);
			}
		} finally {
			mTransactionContext.get().clear();
		}
	}

	public void onRollback() {
		Utils.logProvider(" onRollback ----------done");
	}

	@Override
	public void onBegin() {
		checkTransactionContext();

		Log.d(LOG_TAG, "PrivateContactsProvider onBegin");
		mTransactionContext.get().clear();
	}

	public void updateContactDisplayName(ContentValues NumbermValues,
			long contact_id) {

		Long modeType = (long) -1;
		mMode = Settings.Secure.getInt(getContext().getContentResolver(),
				Settings.Secure.PRIVATE_PHONE_MODE, PrivateMode.MODE_UNKNOWN);

		if (mMode == PrivateMode.MODE_MPT1327_ANALOG_TRUNKING) {
			modeType = (long) PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_PDT;
		} else if (mMode == PrivateMode.MODE_PDT_DIGITAL_TRUNKING) {
			modeType = (long) PrivateContactContracts.Contacts.CONTACTS_IMPORT_BY_PCTOOL_MPT;
		}

		String[] selectionArgs = new String[1];
		selectionArgs[0] = String.valueOf(contact_id);
		checkReadableDB();
		Cursor c = mDb.get().rawQuery(UPDATE_CONTACTS, selectionArgs);
		String name = "";
		String phone = "";
		Utils.logProvider("updateContactDisplayName getCOunt is:"
				+ c.getCount() + " contact_id is:" + contact_id + "new  "
				+ NumbermValues);
		try {
			if (c.getCount() == 0) {
				c.close();
				return;
			}
			while (c.moveToNext()) {
				int mimeType = c.getInt(c
						.getColumnIndexOrThrow(DataColumns.MIMETYPE_ID));
				Utils.logProvider("PrivateContactsProvider updateContactDisplayName mimeType is:"
						+ mimeType
						+ "=="
						+ lookupMimeTypeId(DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE));
/*				if (mimeType == lookupMimeTypeId(DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE)) {
					name = c.getString(c
							.getColumnIndexOrThrow(PrivateContactConst.NameEditorDataKind));
				}
				if (mimeType == lookupMimeTypeId(DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE)) {
					phone = c
							.getString(c
									.getColumnIndexOrThrow(PrivateContactConst.NameEditorDataKind));
				}*/
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			c.close();
		}
		checkWritableDb();
		if (mContactDisplayNameUpdate == null) {
			mContactDisplayNameUpdate = mDb.get().compileStatement(
					"UPDATE " + Tables.PRIVATE_CONTACT + " SET "
							+ Contacts.DISPLAY_NAME + " =? " + " , "
							+ Contacts.SORT_KEY + " =? " + " , "
							+ Contacts.SEARCH_INDEX_NAME + " =? " + " , "
							+ Contacts.SEARCH_INDEX_NUMBER + " =? " + " , "
							+ Contacts.NUMBER + " =? " + " WHERE "
							+ ContactsColumns._ID + " = ?");
		}
		Utils.logProvider("PrivateContactsProvider updateContactDisplayName1111 is:"
				+ name + "--contact.id" + contact_id + phone);

		if (TextUtils.isEmpty(name)) {
			 Utils.logProvider("PrivateContactsProvider updateContactDisplayName1111 is:"+name+"--contact.id"+contact_id+phone);

			bindString(mContactDisplayNameUpdate, 1, phone);
			bindString(mContactDisplayNameUpdate, 2, phone);
			bindString(mContactDisplayNameUpdate, 3, phone);
			bindString(mContactDisplayNameUpdate, 4, phone);
			bindInt(mContactDisplayNameUpdate, 5, modeType);

		} else {

			bindString(mContactDisplayNameUpdate, 1, name);// display_name
			bindString(mContactDisplayNameUpdate, 2, getFullPinYin(name));// sort_ley
			bindString(mContactDisplayNameUpdate, 3, getFirstPinYin(name) + "*"
					+ getFullPinYin(name));// search_full and first_name
			bindString(mContactDisplayNameUpdate, 4, phone);// seach_number
			bindInt(mContactDisplayNameUpdate, 5, modeType);

		}
		mContactDisplayNameUpdate.bindLong(6, contact_id);
		mContactDisplayNameUpdate.execute();
	}

	/**
	 * Inserts a record in the {@link Tables#NAME_LOOKUP} table.
	 */
	public void insertmContactToDataUpdate(long ContactId, String values,
			int mimetype) {
		if (mContactToDataUpdate == null) {
			mDbHelper.set(mPrivateContactsDatabaseHelper);
			mContactToDataUpdate = mDbHelper
					.get()
					.getWritableDatabase()
					.compileStatement(
							"INSERT OR IGNORE INTO " + Tables.PRIVATE_DATA
									+ "(" + DataColumns.CONTACT_ID + ","
									+ DataColumns.MIMETYPE_ID + ","
									+ DataColumns.DATA1 + ") VALUES (?,?,?)");
		}
		mContactToDataUpdate.bindLong(1, ContactId);
		mContactToDataUpdate.bindLong(2, mimetype);
		bindString(mContactToDataUpdate, 3, values);
		mContactToDataUpdate.executeInsert();
		
//		if (getMimeTypeId(DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE)== mimetype
//				|| getMimeTypeId(DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE)== mimetype) {
//			updateContactDisplayName(values, ContactId);
//		}
	}

	/**
	 * Inserts a record in the {@link Tables#NAME_LOOKUP} table.
	 */
	public void insertDataFromGroupUpdate(long GroupId, String values,
			int mimetype) {
		if (mGroupToDataUpdate == null) {
			mDbHelper.set(mPrivateContactsDatabaseHelper);
			mGroupToDataUpdate = mDbHelper
					.get()
					.getWritableDatabase()
					.compileStatement(
							"INSERT OR IGNORE INTO " + Tables.PRIVATE_DATA
									+ "(" + DataColumns.GROUP_ID + ","
									+ DataColumns.MIMETYPE_ID + ","
									+ DataColumns.DATA1 + ") VALUES (?,?,?)");
		}
		mGroupToDataUpdate.bindLong(1, GroupId);
		mGroupToDataUpdate.bindLong(2, mimetype);
		bindString(mGroupToDataUpdate, 3, values);
		mGroupToDataUpdate.executeInsert();
	}

	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {

		Utils.logProvider("applyBatch has changed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
				+ operations.size());
		SQLiteDatabase db = mPrivateContactsDatabaseHelper
				.getWritableDatabase();
		// waitForAccess(mWriteAccessLatch);
		db.beginTransaction();// start transaction
		//db.beginTransactionWithListener(this);
		try {
			ContentProviderResult[] results = super.applyBatch(operations);
		db.setTransactionSuccessful();// set successful
			return results;
		} finally {
		    db.endTransaction();// end transaction
			Utils.logProvider("PrivateContactsProvider!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! notifyChange>GroupContact has changed");
			getContext().getContentResolver().notifyChange(
					GroupContact.CONTENT_URI, null, false);
		}

	}

	private void bindString(SQLiteStatement stmt, int index, String value) {
		if (value == null) {
			stmt.bindNull(index);
		} else {
			stmt.bindString(index, value);
		}
	}

	private void bindInt(SQLiteStatement stmt, int index, Long value) {
		if (value == 0) {
			stmt.bindNull(index);
		} else {
			stmt.bindLong(index, value);
		}
	}

	public String getFirstPinYin(String source) {

		if (!Arrays.asList(Collator.getAvailableLocales()).contains(
				Locale.CHINA)) {

			return source;

		}

		ArrayList<Token> tokens = HanziToPinyin.getInstance().get(source);

		if (tokens == null || tokens.size() == 0) {

			return source;

		}

		StringBuffer result = new StringBuffer();

		for (Token token : tokens) {

			if (token.type == Token.PINYIN) {

				result.append(token.target.charAt(0));

			} else {

				result.append("");

			}

		}
		return result.toString().toLowerCase();
	}

	public String getFullPinYin(String source) {
		if (!Arrays.asList(Collator.getAvailableLocales()).contains(
				Locale.CHINA)) {
			return source;
		}
		ArrayList<Token> tokens = HanziToPinyin.getInstance().get(source);
		if (tokens == null || tokens.size() == 0) {
			return source;
		}
		StringBuffer result = new StringBuffer();
		for (Token token : tokens) {
			if (token.type == Token.PINYIN) {
				result.append(token.target);
			} else {
				result.append(token.source);
			}
		}
		return result.toString().toLowerCase();
	}

	public static boolean isNumeric(String str) {
		Pattern pattern = Pattern.compile("[0-9]*");
		return pattern.matcher(str).matches();
	}

	private String[] splitNameByWord(String name) {
		int length = name.length();
		String[] subname = new String[length];
		for (int i = 0; i < name.length(); i++) {

			subname[i] = name.charAt(i) + "";
		}
		return subname;
	}

}