package com.neolink.providers.contacts;

import java.util.HashMap;

import neolink.telephony.PrivateContactConst;
import neolink.telephony.PrivateContactContracts.CallLogColumns;
import neolink.telephony.PrivateContactContracts.Contacts;
import neolink.telephony.PrivateContactContracts.ContactsColumns;
import neolink.telephony.PrivateContactContracts.Data;
import neolink.telephony.PrivateContactContracts.DataColumns;
import neolink.telephony.PrivateContactContracts.GroupContact;
import neolink.telephony.PrivateContactContracts.GroupContactColumns;
import neolink.telephony.PrivateContactContracts.MimetypesColumns;
import neolink.telephony.PrivateContactContracts.ZoneColumns;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.provider.ContactsContract.RawContacts;
import com.neolink.providers.util.Utils;

public class PrivateContactsDataHelper extends SQLiteOpenHelper {
	private static final String LOG_TAG = "PrivateContactsDataHelper";

	private static final String DATABASE_NAME = "privatecontacts2.db";
	private static final int VERSION = 47;
	private static PrivateContactsDataHelper sSingleton;
	private SQLiteDatabase mDefaultWritableDatabase = null;
	private Context mContext;
	/** In-memory cache of previously found MIME-type mappings */
	public final HashMap<String, Long> mMimetypeCache = new HashMap<String, Long>();

	public interface PrivateContact {
		public static final String CONCRETE_CONTACT_ID = Tables.PRIVATE_CONTACT
				+ "." + ContactsColumns._ID;
	}

	public interface PrivateData {
		public static final String CONCRETE_DATA_ID = Tables.PRIVATE_DATA + "."
				+ DataColumns._ID;
		public static final String CONCRETE_DATA_CONTACT_ID = Tables.PRIVATE_DATA
				+ "." + Data.CONTACT_ID;
		public static final String CONCRETE_DATA_GROUP_ID = Tables.PRIVATE_DATA
				+ "." + Data.GROUP_ID;
		public static final String CONCRETE_DATA_MIMETYPE_ID = Tables.PRIVATE_DATA
				+ "." + Data.MIMETYPE_ID;
	}

	public interface PrivateMimeType {
		public static final String CONCRETE_MIMETYPE_ID = Tables.PRIVATE_MIMETYPE
				+ "." + MimetypesColumns._ID;
		public static final String CONCRETE_MIMETYPE_MIMETYPE = Tables.PRIVATE_MIMETYPE
				+ "." + MimetypesColumns.MIMETYPE;
	}
	
	public interface PrivateGroup {
		public static final String CONCRETE_GROUP_ID = Tables.PRIVATE_GROUP_CONTACT
				+ "." + GroupContactColumns._ID;
	}

	public interface Tables {
		public static final String PRIVATE_CONTACT = "p_contacts";
		public static final String PRIVATE_DATA = "p_data";
		public static final String PRIVATE_MIMETYPE = "p_mimetypes";
		public static final String PRIVATE_CALL = "p_calls";
		public static final String PRIVATE_GROUP_CONTACT = "p_group";
		public static final String PRIVATE_ZONE = "p_zone";
	}

	public interface Views {
		public static final String PRIVATE_CONTACT = "view_pcontact";
		public static final String PRIVATE_DATA = "view_pdata";
	}

	public static final String CREATE_DATABASE_PCONTACTS = "CREATE TABLE "
			+ Tables.PRIVATE_CONTACT + " (" + BaseColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + Contacts.DISPLAY_NAME
			+ " TEXT," + Contacts.NUMBER + " INTEGER ,"
			+ Contacts.CONTACTS_TYPE + " INTEGER DEFAULT -1 ,"
			+ Contacts.CONTACTS_PCTOOLS + " INTEGER DEFAULT -1 ,"
			+ Contacts.SEARCH_INDEX_NAME + " TEXT,"
			+ Contacts.SEARCH_INDEX_NUMBER + " TEXT," + Contacts.CURRENT_MODE
			+ " INTEGER DEFAULT -1 ," + Contacts.SORT_KEY + " );";

	public static final String CREATE_DATABASE_PDATA = "CREATE TABLE "
			+ Tables.PRIVATE_DATA + " (" + BaseColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + Data.CONTACT_ID
			+ " INTEGER ," + Data.GROUP_ID + " INTEGER ," + Data.MIMETYPE_ID
			+ " INTEGER ," + Data.DATA1 + " TEXT," + Data.DATA2 + " TEXT,"
			+ Data.DATA3 + " TEXT" + " )";

	public static final String CREATE_DATABASE_PGCONTACT = "CREATE TABLE "
			+ Tables.PRIVATE_GROUP_CONTACT + " (" + BaseColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ GroupContactColumns.GROUP_ID + " INTEGER DEFAULT -1 ,"
			+ GroupContactColumns.GROUP_CONTACT_NAME + " TEXT ,"
			+ GroupContactColumns.GROUP_CONTACT_NUMBER + " TEXT ,"
			+ GroupContactColumns.GROUP_MODE_TYPE + " INTEGER DEFAULT -1 ,"
			+ GroupContactColumns.GROUP_CONTACT_TYPE + " INTEGER DEFAULT -1 ,"
			+ GroupContactColumns.GROUP_ZONE_ID + " INTEGER DEFAULT -1 ,"
			+ GroupContactColumns.GROUP_ZONE_NAME + " INTEGER DEFAULT -1 ,"
			+ GroupContactColumns.GROUP_DEFAULT + " INTEGER DEFAULT -1 ,"
			+ GroupContact.GROUP_CONTACT_SORT_KEY + " TEXT " + " )";

	public static final String CREATE_DATABASE_PMIMETYPE = "CREATE TABLE "
			+ Tables.PRIVATE_MIMETYPE + " (" + MimetypesColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + MimetypesColumns.MIMETYPE
			+ " TEXT " + " )";

	public static final String CREATE_DATABASE_PZONE = "CREATE TABLE "
			+ Tables.PRIVATE_ZONE + " (" + ZoneColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + ZoneColumns.ZONE_NAME
			+ " TEXT  "
			+ " )";

	public static final String CREATE_DATABASE_PCALLS = "CREATE TABLE "
			+ Tables.PRIVATE_CALL + " (" + CallLogColumns._ID
			+ " INTEGER PRIMARY KEY AUTOINCREMENT," + CallLogColumns.CONTACTS_ID
			+ " INTEGER," + CallLogColumns.GROUP_ID
			+ " INTEGER,"+ CallLogColumns.NUMBER + " TEXT,"
			+ CallLogColumns.DISPLAY_NAME + " TEXT," + CallLogColumns.DATE
			+ " INGETER," + CallLogColumns.DURATION + " INTEGER,"
			+ CallLogColumns.TYPE + " INTEGER, " + CallLogColumns.IS_READ
			+ " INTEGER ," + CallLogColumns.IS_HANDUP + " INTEGER ,"
			+ CallLogColumns.CALL_LOG_MODE + " INTEGER ,"
			+ CallLogColumns.IS_NEW + " INTEGER NOT NULL DEFAULT 0" + " )";

	public static final String CREATE_INDEX_ON_PMIMETYPE = "CREATE UNIQUE INDEX p_mimetype ON "
			+ Tables.PRIVATE_MIMETYPE + " (" + MimetypesColumns.MIMETYPE + " )";

	public PrivateContactsDataHelper(Context context) {
		super(context, DATABASE_NAME, null, VERSION);
		Utils.logProvider("PrivateContactsDataHelper constructor");
		mContext = context;
	}

	public static synchronized PrivateContactsDataHelper getInstance(
			Context context) {
		Utils.logProvider("PrivateContactsDataHelper getInstance");
		if (sSingleton == null) {
			sSingleton = new PrivateContactsDataHelper(context);
		}
		return sSingleton;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Utils.logProvider("PrivateContactsDataHelper database version is:"
				+ VERSION);
		this.mDefaultWritableDatabase = db;
		createDatabaseContacts(db);
		createDatabaseData(db);
		createDatabaseMimetype(db);
		createDatabasePrivateCalls(db);
		createDatabasePrivateGroupContacts(db);
		createDatabasePrivateGroupZone(db);
		createdefaultMiMetype(db);
		createContactsTriggers(db);
		createGroupTriggers(db);
		createContactsIndexes(db, false);
		createGroupIndexes(db, false);
		createViews(db);
		// boolean isPreLoadGroupContact =
		// mContext.getResources().getBoolean(R.bool.group_pre_load);
		// if(isPreLoadGroupContact){
		// loadTestGroupContact(db);
		// }
	}

	// private void loadTestGroupContact(SQLiteDatabase db){
	// String[] nameArray =
	// mContext.getResources().getStringArray(R.array.group_contact_name);
	// String[] numberArray =
	// mContext.getResources().getStringArray(R.array.group_contact_number);
	// if(nameArray.length != numberArray.length)throw new
	// IllegalStateException("preLoadGroupContact not formated");
	// for(int i=0;i<nameArray.length;i++){
	// String name = nameArray[i];
	// String number = numberArray[i];
	//
	// ContentValues values = new ContentValues();
	// values.put(GroupContact.GROUP_CONTACT_NAME, name);
	// values.put(GroupContact.GROUP_CONTACT_NUMBER, number);
	// db.insert(Tables.PRIVATE_GROUP_CONTACT, null, values);
	// }
	// }

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Utils.logProvider("PrivateContactsDatabaseHelper oldVersion is:"
				+ oldVersion + "  newVersion is:" + newVersion);
		this.mDefaultWritableDatabase = db;
		if (oldVersion != newVersion) {
			dropAll(db);
			onCreate(db);
		}
	}

	private void createDatabaseData(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PDATA);
	}

	private void createDatabaseContacts(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PCONTACTS);
	}

	private void createDatabaseMimetype(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PMIMETYPE);
		db.execSQL(CREATE_INDEX_ON_PMIMETYPE);
	}

	private void createDatabasePrivateCalls(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PCALLS);
	}

	private void createDatabasePrivateGroupContacts(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PGCONTACT);
	}

	private void createDatabasePrivateGroupZone(SQLiteDatabase db) {
		db.execSQL(CREATE_DATABASE_PZONE);
	}

	private void createdefaultMiMetype(SQLiteDatabase db) {
		/**
		 * contacats number 9
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 9
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE
				+ " ' " + ")");

		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE, (long) 9);
		/**
		 * contacats name 10
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 10
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE
				+ " ' " + ")");

		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE, (long) 10);
		/**
		 * group number 11
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 11
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.GROUP_NUMBER_CONTENT_ITEM_TYPE
				+ " ' " + ")");

		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.GROUP_NUMBER_CONTENT_ITEM_TYPE, (long) 11);
		/**
		 * group name 12
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 12
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.GROUP_NAME_CONTENT_ITEM_TYPE
				+ " ' " + ")");
		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.GROUP_NAME_CONTENT_ITEM_TYPE, (long) 12);
		/**
		 * group memeber ship 13
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 13
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.GROUP_MEMBER_ITEM_TYPE
				+ " ' " + ")");
		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.GROUP_MEMBER_ITEM_TYPE, (long) 13);
		
		/**
		 * group memeber ship 13
		 */
		db.execSQL("INSERT INTO "
				+ Tables.PRIVATE_MIMETYPE
				+ " ("
				+ MimetypesColumns._ID
				+ ","
				+ MimetypesColumns.MIMETYPE
				+ " )"
				+ "VALUES"
				+ "("
				+ 14
				+ ",  '"
				+ PrivateContactConst.DataKindMimeType.GROUP_ZONE_ITEM_TYPE
				+ " ' " + ")");
		mMimetypeCache.put(PrivateContactConst.DataKindMimeType.GROUP_ZONE_ITEM_TYPE, (long) 14);
	}

	/**
	 * Convert a mimetype into an integer, using {@link Tables#MIMETYPES} for
	 * lookups and possible allocation of new IDs as needed.
	 */
	public long getMimeTypeId(String mimetype) {
		// Try an in-memory cache lookup
		if (mMimetypeCache.containsKey(mimetype))
			return mMimetypeCache.get(mimetype);

		return lookupMimeTypeId(mimetype, getWritableDatabase());
	}

	private long lookupMimeTypeId(String mimetype, SQLiteDatabase db) {
		final SQLiteStatement mimetypeQuery = db.compileStatement("SELECT "
				+ MimetypesColumns._ID + " FROM " + Tables.PRIVATE_MIMETYPE
				+ " WHERE " + MimetypesColumns.MIMETYPE + "=?");

		final SQLiteStatement mimetypeInsert = db
				.compileStatement("INSERT INTO " + Tables.PRIVATE_MIMETYPE
						+ "(" + MimetypesColumns.MIMETYPE + ") VALUES (?)");

		try {
			return lookupAndCacheId(mimetypeQuery, mimetypeInsert, mimetype,
					mMimetypeCache);
		} finally {
			mimetypeQuery.close();
			mimetypeInsert.close();
		}
	}

	/**
	 * Perform an internal string-to-integer lookup using the compiled
	 * {@link SQLiteStatement} provided. If a mapping isn't found in database,
	 * it will be created. All new, uncached answers are added to the cache
	 * automatically.
	 * 
	 * @param query
	 *            Compiled statement used to query for the mapping.
	 * @param insert
	 *            Compiled statement used to insert a new mapping when no
	 *            existing one is found in cache or from query.
	 * @param value
	 *            Value to find mapping for.
	 * @param cache
	 *            In-memory cache of previous answers.
	 * @return An unique integer mapping for the given value.
	 */
	private long lookupAndCacheId(SQLiteStatement query,
			SQLiteStatement insert, String value, HashMap<String, Long> cache) {
		long id = -1;
		try {
			// Try searching database for mapping
			DatabaseUtils.bindObjectToProgram(query, 1, value);
			id = query.simpleQueryForLong();
		} catch (SQLiteDoneException e) {
			// Nothing found, so try inserting new mapping
			DatabaseUtils.bindObjectToProgram(insert, 1, value);
			id = insert.executeInsert();
		}
		if (id != -1) {
			// Cache and return the new answer
			cache.put(value, id);
			return id;
		} else {
			// Otherwise throw if no mapping found or created
			throw new IllegalStateException("Couldn't find or create internal "
					+ "lookup table entry for value " + value);
		}
	}

	private void createContactsTriggers(SQLiteDatabase db) {
		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
				+ "_deleted;");
		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT + "_deleted "
				+ "   BEFORE DELETE ON " + Tables.PRIVATE_CONTACT + " BEGIN "
				+ "   DELETE FROM " + Tables.PRIVATE_DATA + "     WHERE "
				+ DataColumns.CONTACT_ID + "=OLD." + ContactsColumns._ID + ";"
				+ " END");

		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
				+ "_updated1;");
		db.execSQL("CREATE TRIGGER "
				+ Tables.PRIVATE_CONTACT
				+ "_updated1 "
				+ "   AFTER UPDATE ON "
				+ Tables.PRIVATE_CONTACT
				+ " BEGIN "
				+ "   UPDATE "
				+ Tables.PRIVATE_DATA
				+ "     SET "
				+ DataColumns.DATA1
				+ "=NEW."
				+ ContactsColumns.DISPLAY_NAME
				+ "     WHERE "
				+ DataColumns.CONTACT_ID
				+ "=OLD."
				+ ContactsColumns._ID
				+ " AND "
				+ DataColumns.MIMETYPE_ID
				+ "= "
				+ getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE)
				+ " ;" + " END");
		
		
		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
				+ "_updated2;");
		db.execSQL("CREATE TRIGGER "
				+ Tables.PRIVATE_CONTACT
				+ "_updated2 "
				+ "   AFTER UPDATE ON "
				+ Tables.PRIVATE_CONTACT
				+ " BEGIN "
				+ "   UPDATE "
				+ Tables.PRIVATE_DATA
				+ "     SET "
				+ DataColumns.DATA1
				+ "=NEW."
				+ ContactsColumns.NUMBER
				+ "     WHERE "
				+ DataColumns.CONTACT_ID
				+ "=OLD."
				+ ContactsColumns._ID
				+ " AND "
				+ DataColumns.MIMETYPE_ID
				+ "= "
				+ getMimeTypeId(PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE)
				+ " ;" + " END");
		
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "_inserted;");
//		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT + "_inserted"
//				+ " AFTER INSERT ON " + Tables.PRIVATE_CONTACT + " BEGIN "
//				+ " INSERT INTO " + Tables.PRIVATE_DATA + " ( "
//				+ DataColumns.CONTACT_ID + "," + DataColumns.MIMETYPE_ID + ","
//				+ DataColumns.DATA1 + " ) " + " VALUES " + "(" + "NEW."
//				+ ContactsColumns._ID + " , " + "NEW." + MimetypesColumns._ID
//				+ " , " + "NEW." + ContactsColumns.DISPLAY_NAME + ")" + ";"
//				+ " END");
//
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "_contacts__inserted;");
//		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT + "_contacts"
//				+ "_inserted" + " AFTER INSERT ON " + Tables.PRIVATE_CONTACT
//				+ " BEGIN " + " INSERT INTO " + Tables.PRIVATE_DATA + " ( "
//				+ DataColumns.CONTACT_ID + "," + DataColumns.MIMETYPE_ID + ","
//				+ DataColumns.DATA1 + " ) " + " VALUES " + "(" + "NEW."
//				+ ContactsColumns._ID + " , " + "NEW." + MimetypesColumns._ID
//				+ " , " + "NEW." + ContactsColumns.NUMBER + ")" + ";" + " END");
//		

//		
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "_contacts__inserted;");
//		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT + "_contacts"
//				+ "_inserted" + " AFTER INSERT ON " + Tables.PRIVATE_CONTACT
//				+ " BEGIN " + " INSERT INTO " + Tables.PRIVATE_DATA + " ( "
//				+ DataColumns.CONTACT_ID + "," + DataColumns.MIMETYPE_ID + ","
//				+ DataColumns.DATA1 + " ) " + " VALUES " + "(" + "NEW."
//				+ ContactsColumns._ID + " , " + "NEW." + MimetypesColumns._ID
//				+ " , " + "NEW." + ContactsColumns.NUMBER + ")" + ";" + " END");
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "contacats_inserted;");
//		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT+"_contacts" + "contacats_inserted"
//				+ " AFTER INSERT ON " + Tables.PRIVATE_CONTACT + " BEGIN "
//				+ " INSERT INTO " + Tables.PRIVATE_DATA + " ( "
//				+ DataColumns.CONTACT_ID + " , "
//				+ DataColumns.DATA1 + " ) "+"SELECT "+ContactsColumns._ID+" , "
//				+ContactsColumns.DISPLAY_NAME+" FROM "+Tables.PRIVATE_CONTACT+" WHERE "
//				+"NEW."
//				+ ContactsColumns._ID +"=NEW."
//						+ DataColumns.GROUP_ID+";"
//				+ " END");
//		
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "_contacts__inserted;");
//		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_CONTACT + "_contacts"
//				+ "_inserted" + " AFTER INSERT ON " + Tables.PRIVATE_CONTACT
//				+ " BEGIN " + " INSERT INTO " + Tables.PRIVATE_DATA + " ( "
//				+ DataColumns.CONTACT_ID + "," + DataColumns.MIMETYPE_ID + ","
//				+ DataColumns.DATA1 + " ) " + " VALUES " + "(" + "NEW."
//				+ ContactsColumns._ID + " , " + "NEW." + MimetypesColumns._ID
//				+ " , " + "NEW." + ContactsColumns.NUMBER + ")" +" WHERE "+ PrivateMimeType.CONCRETE_MIMETYPE_MIMETYPE
//				+ "='"
//				+ PrivateContactConst.DataKindMimeType.CONTACTS_NUMBER_CONTENT_ITEM_TYPE
//				+ "' ;" + " END");	
		
//		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_CONTACT
//				+ "_updated1;");
//		db.execSQL("CREATE TRIGGER "
//				+ Tables.PRIVATE_CONTACT
//				+ "_updated1 "
//				+ "   AFTER UPDATE ON "
//				+ Tables.PRIVATE_CONTACT
//				+ " BEGIN "
//				+ "   UPDATE "
//				+ Tables.PRIVATE_DATA
//				+ "     SET "
//				+ DataColumns.DATA1
//				+ "=NEW."
//				+ ContactsColumns.DISPLAY_NAME
//				+ "     WHERE "
//				+  "NEW."+Data.CONTACT_ID
//				+ "=NEW."
//				+ ContactsColumns._ID
//				+ " AND "
//				+ PrivateMimeType.CONCRETE_MIMETYPE_MIMETYPE
//				+ "='"
//				+ PrivateContactConst.DataKindMimeType.CONTACTS_NAME_CONTENT_ITEM_TYPE
//				+ "' ;" + " END");
		
		
	/*	
		
        db.execSQL("DROP TRIGGER IF EXISTS " + Tables.RAW_CONTACTS + "_marked_deleted;");
        db.execSQL("CREATE TRIGGER " + Tables.RAW_CONTACTS + "_marked_deleted "
                + "   AFTER UPDATE ON " + Tables.RAW_CONTACTS
                + " BEGIN "
                + "   UPDATE " + Tables.RAW_CONTACTS
                + "     SET "
                +         RawContacts.VERSION + "=OLD." + RawContacts.VERSION + "+1 "
                + "     WHERE " + RawContacts._ID + "=OLD." + RawContacts._ID
                + "       AND NEW." + RawContacts.DELETED + "!= OLD." + RawContacts.DELETED + ";"
                + " END");
                */
			
//		INSERT INTO first_table_name [(column1, column2, ... columnN)] 
//				   SELECT column1, column2, ...columnN 
//				   FROM second_table_name
//				   [WHERE condition];
//		
//        final String insertContactsWithoutAccount = (
//                " INSERT OR IGNORE INTO " + Tables.DEFAULT_DIRECTORY +
//                "     SELECT " + RawContacts.CONTACT_ID +
//                "     FROM " + Tables.RAW_CONTACTS +
//                "     WHERE " + RawContactsColumns.CONCRETE_ACCOUNT_ID +
//                            "=" + Clauses.LOCAL_ACCOUNT_ID + ";");
		

		// CREATE TRIGGER audit_log AFTER INSERT
		// ON COMPANY
		// BEGIN
		// INSERT INTO AUDIT(EMP_ID, ENTRY_DATE) VALUES (new.ID,
		// datetime('now'));
		// END;

	}

	private void createGroupTriggers(SQLiteDatabase db) {
		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_GROUP_CONTACT
				+ "_deleted;");
		db.execSQL("CREATE TRIGGER " + Tables.PRIVATE_GROUP_CONTACT
				+ "_deleted " + "   BEFORE DELETE ON "
				+ Tables.PRIVATE_GROUP_CONTACT + " BEGIN " + "   DELETE FROM "
				+ Tables.PRIVATE_DATA + "     WHERE " + DataColumns.GROUP_ID
				+ "=OLD." + ContactsColumns._ID + ";" + " END");
		
		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_GROUP_CONTACT
				+ "_updated1;");
		db.execSQL("CREATE TRIGGER "
				+ Tables.PRIVATE_GROUP_CONTACT
				+ "_updated1 "
				+ "   AFTER UPDATE ON "
				+ Tables.PRIVATE_GROUP_CONTACT
				+ " BEGIN "
				+ "   UPDATE "
				+ Tables.PRIVATE_DATA
				+ "     SET "
				+ DataColumns.DATA1
				+ "=NEW."
				+ GroupContactColumns.GROUP_CONTACT_NAME
				+ "     WHERE "
				+ DataColumns.GROUP_ID
				+ "=OLD."
				+ GroupContactColumns._ID
				+ " AND "
				+ DataColumns.MIMETYPE_ID
				+ "= "
				+ getMimeTypeId(PrivateContactConst.DataKindMimeType.GROUP_NAME_CONTENT_ITEM_TYPE)
				+ " ;" + " END");

		db.execSQL("DROP TRIGGER IF EXISTS " + Tables.PRIVATE_GROUP_CONTACT
				+ "_updated2;");
		db.execSQL("CREATE TRIGGER "
				+ Tables.PRIVATE_GROUP_CONTACT
				+ "_updated2 "
				+ "   AFTER UPDATE ON "
				+ Tables.PRIVATE_GROUP_CONTACT
				+ " BEGIN "
				+ "   UPDATE "
				+ Tables.PRIVATE_DATA
				+ "     SET "
				+ DataColumns.DATA1
				+ "=NEW."
				+ GroupContactColumns.GROUP_CONTACT_NUMBER
				+ "     WHERE "
				+ DataColumns.GROUP_ID
				+ "=OLD."
				+ GroupContactColumns._ID
				+ " AND "
				+ DataColumns.MIMETYPE_ID
				+ "= "
				+ getMimeTypeId(PrivateContactConst.DataKindMimeType.GROUP_NUMBER_CONTENT_ITEM_TYPE)
				+ " ;" + " END");

	}

	private void createContactsIndexes(SQLiteDatabase db,
			boolean rebuildSqliteStats) {
		db.execSQL("DROP INDEX IF EXISTS contacts_lookup_index");
		db.execSQL("CREATE INDEX contacts_lookup_index ON "
				+ Tables.PRIVATE_CONTACT + " (" + ContactsColumns.NUMBER + ","
				+ ContactsColumns.CURRENT_MODE + ", "
				+ ContactsColumns.DISPLAY_NAME + ", "
				+ ContactsColumns.CONTACTS_TYPE + ");");

		db.execSQL("DROP INDEX IF EXISTS name_number_lookup_index");
		db.execSQL("CREATE INDEX name_number_lookup_index ON "
				+ Tables.PRIVATE_CONTACT + " ("
				+ ContactsColumns.SEARCH_INDEX_NAME + ","
				+ ContactsColumns.SEARCH_INDEX_NUMBER + ", "
				+ ContactsColumns.DISPLAY_NAME + ", " + ContactsColumns._ID
				+ ");");
	}

	private void createGroupIndexes(SQLiteDatabase db,
			boolean rebuildSqliteStats) {
		db.execSQL("DROP INDEX IF EXISTS group_lookup_index");
		db.execSQL("CREATE INDEX group_lookup_index ON "
				+ Tables.PRIVATE_GROUP_CONTACT + " (" + GroupContactColumns._ID
				+ "," + GroupContactColumns.GROUP_ID + ", "
				+ GroupContactColumns.GROUP_CONTACT_NAME + ", "
				+ GroupContactColumns.GROUP_CONTACT_NUMBER + ", "
				+ GroupContactColumns.GROUP_CONTACT_TYPE + ", "
				+ GroupContactColumns.GROUP_MODE_TYPE + ", "
				+ GroupContactColumns.GROUP_ZONE_ID + ", "
				+ GroupContactColumns.GROUP_ZONE_NAME + ", "
				+ GroupContactColumns.GROUP_MODE_TYPE + ", "
				+ GroupContactColumns.GROUP_DEFAULT + ");");

	}

	/*
	 * private void createIndexdatabase(SQLiteDatabase db){
	 * db.execSQL(CREATE_INDEX_TABLE); }
	 */

	private void createViews(SQLiteDatabase db) {
		db.execSQL("DROP VIEW IF EXISTS " + Views.PRIVATE_CONTACT + " ;");
		db.execSQL("DROP VIEW IF EXISTS " + Views.PRIVATE_DATA + " ;");

		String data_view_string = "SELECT " + PrivateData.CONCRETE_DATA_ID
				+ ", " + Contacts.DISPLAY_NAME + "," + Data.CONTACT_ID + " ,"
				+ Data.MIMETYPE_ID + "," + MimetypesColumns.MIMETYPE + ","
				+ Data.DATA1 + "," + Data.DATA2 + "," + Data.DATA3 + " FROM "
				+ Tables.PRIVATE_DATA + " JOIN " + Tables.PRIVATE_MIMETYPE
				+ " ON (" + PrivateData.CONCRETE_DATA_MIMETYPE_ID + " = "
				+ PrivateMimeType.CONCRETE_MIMETYPE_ID + " )" + " JOIN "
				+ Tables.PRIVATE_CONTACT + " ON ("
				+ PrivateData.CONCRETE_DATA_CONTACT_ID + " = "
				+ PrivateContact.CONCRETE_CONTACT_ID + " )";
		Utils.logProvider(data_view_string);
		db.execSQL("CREATE VIEW " + Views.PRIVATE_DATA + " AS "
				+ data_view_string);

		String contact_view_string = "SELECT " + ContactsColumns._ID + ","
				+ Contacts.DISPLAY_NAME + "," + Contacts.SORT_KEY + ","
				+ Contacts.CURRENT_MODE + "," + Contacts.CONTACTS_PCTOOLS + ","+ Contacts.CONTACTS_TYPE + ","
				+ Contacts.SEARCH_INDEX_NAME + "," + Contacts.NUMBER + " FROM "
				+ Tables.PRIVATE_CONTACT;
		Utils.logProvider(contact_view_string);
		db.execSQL("CREATE VIEW " + Views.PRIVATE_CONTACT + " AS "
				+ contact_view_string);
	}

	private void dropAll(SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_CONTACT);
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_DATA);
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_MIMETYPE);
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_CALL);
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_GROUP_CONTACT);
		db.execSQL("DROP TABLE IF EXISTS " + Tables.PRIVATE_ZONE);
	}

	@Override
	public SQLiteDatabase getWritableDatabase() {
		final SQLiteDatabase db;
		if (mDefaultWritableDatabase != null) {
			db = mDefaultWritableDatabase;
		} else {
			db = super.getWritableDatabase();
		}
		return db;
	}

}