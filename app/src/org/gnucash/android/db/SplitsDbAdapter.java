package org.gnucash.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class SplitsDbAdapter extends DatabaseAdapter {

    private static final String LOG_TAG = "SplitsDbAdapter";

    public SplitsDbAdapter(Context context){
        super(context);
    }

    /**
     * Adds a split to the database.
     * If the split (with same unique ID) already exists, then it is simply updated
     * @param split {@link org.gnucash.android.model.Split} to be recorded in DB
     * @return Record ID of the newly saved split
     */
    public long addSplit(Split split){
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseHelper.KEY_UID, split.getUID());
        contentValues.put(DatabaseHelper.KEY_TRANSACTION_UID, split.getTransactionUID());
        contentValues.put(DatabaseHelper.KEY_AMOUNT, split.getAmount().absolute().toPlainString());
        contentValues.put(DatabaseHelper.KEY_TYPE, split.getType().name());

        Log.d(LOG_TAG, "Adding new transaction to db");
        long rowId = -1;
        if ((rowId = getID(split.getUID())) > 0){
            //if transaction already exists, then just update
            Log.d(TAG, "Updating existing transaction split");
            mDb.update(DatabaseHelper.SPLITS_TABLE_NAME, contentValues,
                    DatabaseHelper.KEY_ROW_ID + " = " + rowId, null);
        } else {
            Log.d(TAG, "Adding new transaction split to db");
            rowId = mDb.insert(DatabaseHelper.SPLITS_TABLE_NAME, null, contentValues);
        }

        return rowId;
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     * <p>This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry</p>
     * @param cursor Cursor pointing to transaction record in database
     * @return {@link org.gnucash.android.model.Split} instance
     */
    public Split buildSplitInstance(Cursor cursor){
        String uid          = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_UID));
        String amountString = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_AMOUNT));
        String typeName     = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_TYPE));
        String accountUID   = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_ACCOUNT_UID));
        String transxUID    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_TRANSACTION_UID));

        String currencyCode = getCurrencyCode(accountUID);
        Money amount = new Money(amountString, currencyCode);

        Split split = new Split(amount, accountUID);
        split.setUID(uid);
        split.setTransactionUID(transxUID);
        split.setType(TransactionType.valueOf(typeName));

        return split;
    }


    /**
     * Returns the currency code of the transaction
     * @param accountUID String unique ID of account
     * @return ISO 4217 currency code string
     */
    public String getCurrencyCode(String accountUID){
        Cursor cursor = mDb.query(DatabaseHelper.ACCOUNTS_TABLE_NAME,
                new String[] {DatabaseHelper.KEY_CURRENCY_CODE},
                DatabaseHelper.KEY_UID + "= ?",
                new String[]{accountUID}, null, null, null);

        String currencyCode = Money.DEFAULT_CURRENCY_CODE;

        if (cursor != null && cursor.moveToFirst()){
            currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_CURRENCY_CODE));
            cursor.close();
        }

        return currencyCode;
    }

    /**
     * Retrieves a split from the database
     * @param uid Unique Identifier String of the split transaction
     * @return {@link org.gnucash.android.model.Split} instance
     */
    public Split getSplit(String uid){
        long id = getID(uid);
        Cursor cursor = fetchRecord(id);

        Split split = null;
        if (cursor != null && cursor.moveToFirst()){
            split = buildSplitInstance(cursor);
            cursor.close();
        }

        return split;
    }

    /**
     * Returns the list of splits for a transaction
     * @param transactionUID String unique ID of transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     */
    public List<Split> getSplitsForTransaction(String transactionUID){
        Cursor cursor = fetchSplitsforTransaction(transactionUID);
        List<Split> splitList = new ArrayList<Split>();
        while (cursor != null && cursor.moveToNext()){
            splitList.add(buildSplitInstance(cursor));
        }
        if (cursor != null)
            cursor.close();

        return splitList;
    }

    /**
     * Returns the list of splits for a transaction
     * @param transactionID DB record ID of the transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     * @see #getSplitsForTransaction(String)
     * @see #getTransactionUID(long)
     */
    public List<Split> getSplitsForTransaction(long transactionID){
        return getSplitsForTransaction(getTransactionUID(transactionID));
    }

    /**
     * Fetch splits for a given transaction within a specific account
     * @param transactionUID String unique ID of transaction
     * @param accountUID String unique ID of account
     * @return List of splits
     */
    public List<Split> getSplitsForTransactionInAccount(String transactionUID, String accountUID){
        Cursor cursor = fetchSplits(transactionUID, accountUID);
        List<Split> splitList = new ArrayList<Split>();
        while (cursor != null && cursor.moveToNext()){
            splitList.add(buildSplitInstance(cursor));
        }
        if (cursor != null)
            cursor.close();

        return splitList;
    }

    /**
     * Fetches a collection of splits for a given condition and sorted by <code>sortOrder</code>
     * @param condition String condition, formatted as SQL WHERE clause
     * @param sortOrder Sort order for the returned records
     * @return Cursor to split records
     */
    public Cursor fetchSplits(String condition, String sortOrder){
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, condition, null, null, null, sortOrder);
    }

    /**
     * Returns the database record ID of the split with unique IDentifier <code>uid</code>
     * @param uid Unique Identifier String of the split transaction
     * @return Database record ID of split
     */
    public long getID(String uid){
        Cursor cursor = mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                new String[] {DatabaseHelper.KEY_ROW_ID},
                DatabaseHelper.KEY_UID + " = ?", new String[]{uid}, null, null, null);
        long result = -1;
        if (cursor != null && cursor.moveToFirst()){
            Log.d(TAG, "Transaction already exists. Returning existing id");
            result = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_ROW_ID));

            cursor.close();
        }
        return result;
    }

    public Cursor fetchSplitsforTransaction(String transactionUID){
        Log.v(TAG, "Fetching all splits for transaction UID " + transactionUID);
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, DatabaseHelper.KEY_TRANSACTION_UID + " = ?",
                new String[]{transactionUID},
                null, null, DatabaseHelper.KEY_NAME + " ASC");
    }

    public Cursor fetchSplitsForTransactionAndAccount(String transactionUID, String accountUID){
        Log.v(TAG, "Fetching all splits for transaction ID " + transactionUID
                + "and account ID " + accountUID);
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, DatabaseHelper.KEY_TRANSACTION_UID + " = ? AND "
                + DatabaseHelper.KEY_ACCOUNT_UID + " = ?",
                new String[]{transactionUID, accountUID},
                null, null, DatabaseHelper.KEY_NAME + " ASC");
    }

    /**
     * Returns the unique ID of a transaction given the database record ID of same
     * @param transactionId Database record ID of the transaction
     * @return String unique ID of the transaction or null if transaction with the ID cannot be found.
     */
    public String getTransactionUID(long transactionId){
        Cursor cursor = mDb.query(DatabaseHelper.TRANSACTIONS_TABLE_NAME,
                new String[]{DatabaseHelper.KEY_ROW_ID, DatabaseHelper.KEY_UID},
                DatabaseHelper.KEY_ROW_ID + " = " + transactionId,
                null, null, null, null);

        String trxUID = null;
        if (cursor != null && cursor.moveToFirst()){
            trxUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_UID));
        }

        return trxUID;
    }

    @Override
    public Cursor fetchRecord(long rowId) {
        return fetchRecord(DatabaseHelper.SPLITS_TABLE_NAME, rowId);
    }

    @Override
    public Cursor fetchAllRecords() {
        return fetchAllRecords(DatabaseHelper.SPLITS_TABLE_NAME);
    }

    @Override
    public boolean deleteRecord(long rowId) {
        return deleteRecord(DatabaseHelper.SPLITS_TABLE_NAME, rowId);
    }

    @Override
    public int deleteAllRecords() {
        return deleteAllRecords(DatabaseHelper.SPLITS_TABLE_NAME);
    }
}
