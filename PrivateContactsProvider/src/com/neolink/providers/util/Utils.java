package com.neolink.providers.util;

import com.neolink.providers.contacts.PrivateContactsProvider;

import android.util.Log;

public class Utils {

	private static final String TAG_PROVIDER = PrivateContactsProvider.LOG_TAG;
	private static boolean isProvider = true;

	public static void logProvider(String message) {
		if (isProvider)
			Log.d(TAG_PROVIDER, message);
	}
}
