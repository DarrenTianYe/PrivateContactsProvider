package com.neolink.providers.contacts;

import java.util.ArrayList;

import com.google.android.collect.Lists;

public class PrivateContactTransactionContext {
	private ArrayList<Long> mInsertContactId = Lists.newArrayList();
	
	public void contactInsert(long contactId){
		mInsertContactId.add(contactId);
	}
	
	public ArrayList<Long> getInsertContactIdList(){
		return mInsertContactId;
	}
	
	public void clear(){
		mInsertContactId.clear();
	}
}
