/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.gnucash.android.model.*;

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Helper class for managing the SQLite database.
 * Creates the database and handles upgrades
 * @author Ngewi Fet <ngewif@gmail.com>
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {
	
	/**
	 * Tag for logging
	 */
	private static final String LOG_TAG = DatabaseHelper.class.getName();
	
	/**
	 * Name of the database
	 */
	private static final String DATABASE_NAME = "gnucash_db";

	/**
	 * Account which the origin account this transaction in double entry mode.
     * This is no longer used since the introduction of splits
	 */
    @Deprecated
	public static final String KEY_DOUBLE_ENTRY_ACCOUNT_UID 	= "double_account_uid";

	/**
	 * SQL statement to create the accounts table in the database
	 */
	private static final String ACCOUNTS_TABLE_CREATE = "create table " + AccountEntry.TABLE_NAME + " ("
			+ AccountEntry._ID                      + " integer primary key autoincrement, "
			+ AccountEntry.COLUMN_UID 	            + " varchar(255) not null, "
			+ AccountEntry.COLUMN_NAME 	            + " varchar(255) not null, "
			+ AccountEntry.COLUMN_TYPE              + " varchar(255) not null, "
			+ AccountEntry.COLUMN_CURRENCY          + " varchar(255) not null, "
            + AccountEntry.COLUMN_COLOR_CODE        + " varchar(255), "
            + AccountEntry.COLUMN_FAVORITE 		    + " tinyint default 0, "
            + AccountEntry.COLUMN_FULL_NAME 	    + " varchar(255), "
            + AccountEntry.COLUMN_PLACEHOLDER            + " tinyint default 0, "
            + AccountEntry.COLUMN_PARENT_ACCOUNT_UID     + " varchar(255), "
            + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID   + " varchar(255), "
            + "UNIQUE (" + AccountEntry.COLUMN_UID       + ")"
			+ ");";
	
	/**
	 * SQL statement to create the transactions table in the database
	 */
	private static final String TRANSACTIONS_TABLE_CREATE = "create table " + TransactionEntry.TABLE_NAME + " ("
			+ TransactionEntry._ID 		            + " integer primary key autoincrement, "
			+ TransactionEntry.COLUMN_UID 		    + " varchar(255) not null, "
			+ TransactionEntry.COLUMN_NAME		    + " varchar(255), "
			+ TransactionEntry.COLUMN_DESCRIPTION 	+ " text, "
			+ TransactionEntry.COLUMN_TIMESTAMP     + " integer not null, "
			+ TransactionEntry.COLUMN_EXPORTED      + " tinyint default 0, "
            + TransactionEntry.COLUMN_CURRENCY      + " varchar(255) not null, "
            + TransactionEntry.COLUMN_RECURRENCE_PERIOD + " integer default 0, "
			+ "UNIQUE (" 		+ TransactionEntry.COLUMN_UID + ") "
			+ ");";

    /**
     * SQL statement to create the transaction splits table
     */
    private static final String SPLITS_TABLE_CREATE = "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
            + SplitEntry._ID                    + " integer primary key autoincrement, "
            + SplitEntry.COLUMN_UID             + " varchar(255) not null, "
            + SplitEntry.COLUMN_MEMO 	        + " text, "
            + SplitEntry.COLUMN_TYPE            + " varchar(255) not null, "
            + SplitEntry.COLUMN_AMOUNT          + " varchar(255) not null, "
            + SplitEntry.COLUMN_ACCOUNT_UID 	+ " varchar(255) not null, "
            + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
            + "FOREIGN KEY (" 	+ SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + "), "
            + "FOREIGN KEY (" 	+ SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + "), "
            + "UNIQUE (" 		+ SplitEntry.COLUMN_UID + ") "
            + ");";

    /**
	 * Constructor
	 * @param context Application context
	 */
	public DatabaseHelper(Context context){
		super(context, DATABASE_NAME, null, DatabaseSchema.DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i(LOG_TAG, "Creating gnucash database tables");
		db.execSQL(ACCOUNTS_TABLE_CREATE);
		db.execSQL(TRANSACTIONS_TABLE_CREATE);
        db.execSQL(SPLITS_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i(LOG_TAG, "Upgrading database from version "
				+ oldVersion + " to " + newVersion);
		
		if (oldVersion < newVersion){
			//introducing double entry accounting
			Log.i(LOG_TAG, "Upgrading database to version " + newVersion);
			if (oldVersion == 1 && newVersion >= 2){
				Log.i(LOG_TAG, "Adding column for double-entry transactions");
				String addColumnSql = "ALTER TABLE " + TransactionEntry.TABLE_NAME +
									" ADD COLUMN " + KEY_DOUBLE_ENTRY_ACCOUNT_UID + " varchar(255)";
				
				//introducing sub accounts
				Log.i(LOG_TAG, "Adding column for parent accounts");
				String addParentAccountSql = "ALTER TABLE " + AccountEntry.TABLE_NAME +
						" ADD COLUMN " + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " varchar(255)";
	
				db.execSQL(addColumnSql);
				db.execSQL(addParentAccountSql);

                //update account types to GnuCash account types
                //since all were previously CHECKING, now all will be CASH
                Log.i(LOG_TAG, "Converting account types to GnuCash compatible types");
                ContentValues cv = new ContentValues();
                cv.put(SplitEntry.COLUMN_TYPE, AccountType.CASH.toString());
                db.update(AccountEntry.TABLE_NAME, cv, null, null);

                oldVersion = 2;
            }
			

            if (oldVersion == 2 && newVersion >= 3){
                Log.i(LOG_TAG, "Adding flag for placeholder accounts");
                String addPlaceHolderAccountFlagSql = "ALTER TABLE " + AccountEntry.TABLE_NAME +
                        " ADD COLUMN " + AccountEntry.COLUMN_PLACEHOLDER + " tinyint default 0";

                db.execSQL(addPlaceHolderAccountFlagSql);
                oldVersion = 3;
            }

            if (oldVersion == 3 && newVersion >= 4){
                Log.i(LOG_TAG, "Updating database to version 4");
                String addRecurrencePeriod = "ALTER TABLE " + TransactionEntry.TABLE_NAME +
                        " ADD COLUMN " + TransactionEntry.COLUMN_RECURRENCE_PERIOD + " integer default 0";

                String addDefaultTransferAccount = "ALTER TABLE " + AccountEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " varchar(255)";

                String addAccountColor = " ALTER TABLE " + AccountEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_COLOR_CODE + " varchar(255)";

                db.execSQL(addRecurrencePeriod);
                db.execSQL(addDefaultTransferAccount);
                db.execSQL(addAccountColor);

                oldVersion = 4;
            }

            if (oldVersion == 4 && newVersion >= 5){
                Log.i(LOG_TAG, "Upgrading database to version 5");
                String addAccountFavorite = " ALTER TABLE " + AccountEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_FAVORITE + " tinyint default 0";
                db.execSQL(addAccountFavorite);

                oldVersion = 5;
            }

            if (oldVersion == 5 && newVersion >= 6){
                Log.i(LOG_TAG, "Upgrading database to version 6");
                String addFullAccountNameQuery = " ALTER TABLE " + AccountEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_FULL_NAME + " varchar(255) ";
                db.execSQL(addFullAccountNameQuery);

                //update all existing accounts with their fully qualified name
                Cursor cursor = db.query(AccountEntry.TABLE_NAME,
                        new String[]{AccountEntry._ID, AccountEntry.COLUMN_UID},
                        null, null, null, null, null);
                while(cursor != null && cursor.moveToNext()){
                    String uid = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID));
                    String fullName = MigrationHelper.getFullyQualifiedAccountName(db, uid);

                    if (fullName == null)
                        continue;

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(AccountEntry.COLUMN_FULL_NAME, fullName);

                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(AccountEntry._ID));
                    db.update(AccountEntry.TABLE_NAME, contentValues, AccountEntry._ID + " = " + id, null);
                }

                if (cursor != null) {
                    cursor.close();
                }

                oldVersion = 6;
            }

            if (oldVersion == 6 && newVersion == 7){
                Log.i(LOG_TAG, "Upgrading database to version 6");

                //TODO: Backup database first!!
                /* - Backup in GnuCash XML format
                 * - Read in file from GnuCash XML format
                 */
                db.execSQL(SPLITS_TABLE_CREATE);
                String addCurrencyToTransactions = " ALTER TABLE " + TransactionEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_CURRENCY + " varchar(255) ";
                db.execSQL(addCurrencyToTransactions);

                Cursor trxCursor = db.query(TransactionEntry.TABLE_NAME,
                        null, null, null, null, null, null);
                while(trxCursor != null && trxCursor.moveToNext()){
                    String accountUID = trxCursor.getString(trxCursor.getColumnIndexOrThrow(SplitEntry.COLUMN_ACCOUNT_UID));
                    String doubleAccountUID = trxCursor.getString(trxCursor.getColumnIndexOrThrow(KEY_DOUBLE_ENTRY_ACCOUNT_UID));
                    Currency currency = Currency.getInstance(MigrationHelper.getCurrencyCode(db, accountUID));
                    String amountString = trxCursor.getString(trxCursor.getColumnIndexOrThrow(SplitEntry.COLUMN_AMOUNT));
                    Money amount = new Money(new BigDecimal(amountString), currency);

                    String transactionUID = trxCursor.getString(trxCursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID));
                    MigrationHelper.updateTransaction(db, transactionUID, AccountEntry.COLUMN_CURRENCY, currency.getCurrencyCode());
                    //TODO: Migrate recurring transactions. (special flag for their splits?)
                    TransactionType transactionType = TransactionType.valueOf(trxCursor.getString(trxCursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TYPE)));
                    String transactionMemo = trxCursor.getString(trxCursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_DESCRIPTION));

                    Split split = new Split(amount, accountUID);
                    split.setTransactionUID(transactionUID);
                    split.setType(transactionType);
                    split.setMemo(transactionMemo);

                    if (doubleAccountUID != null && doubleAccountUID.length() > 0) {
                        MigrationHelper.addSplit(db, split.createPair(doubleAccountUID));
                    }

                    MigrationHelper.addSplit(db, split);
                }

                //unused rows cannot be dropped easily in sqlite, so we leave them there and just ignore them
                //String[] transxColumnsToDrop = new String[]{COLUMN_AMOUNT, COLUMN_TYPE, KEY_ACCOUNT_UID, KEY_DOUBLE_ENTRY_ACCOUNT_UID};

//                String addCurrencyToTransactions = "ALTER TABLE " + TABLE_NAME
//                        + " ADD COLUMN " + KEY_CURRENCY_CODE + " varchar(255) not null";
//                db.execSQL(addCurrencyToTransactions);

            }
		}

        if (oldVersion != newVersion) {
            Log.w(LOG_TAG, "Upgrade for the database failed. The Database is currently at version " + oldVersion);
        }
	}


}
