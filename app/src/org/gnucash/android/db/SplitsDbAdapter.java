package org.gnucash.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import org.gnucash.android.model.AccountType;
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
        contentValues.put(DatabaseHelper.KEY_ACCOUNT_UID, split.getAccountUID());

        long rowId = -1;
        if ((rowId = getID(split.getUID())) > 0){
            //if split already exists, then just update
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
     * Returns the sum of the splits for a given account.
     * This takes into account the kind of movement caused by the split in the account (which also depends on account type)
     * @param accountUID String unique ID of account
     * @return Balance of the splits for this account
     */
    public Money computeSplitBalance(String accountUID){
        Cursor cursor = fetchSplitsForAccount(accountUID);
        String currencyCode = getCurrencyCode(accountUID);
        Money splitSum = new Money("0", currencyCode);
        AccountType accountType = getAccountType(accountUID);

        if (cursor != null){
            while(cursor.moveToNext()){
                String amountString = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_AMOUNT));
                String typeString = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_TYPE));

                TransactionType transactionType = TransactionType.valueOf(typeString);
                Money amount = new Money(amountString, currencyCode);

                if (accountType.hasDebitNormalBalance()){
                    switch (transactionType) {
                        case DEBIT:
                            splitSum = splitSum.add(amount);
                            break;
                        case CREDIT:
                            splitSum = splitSum.subtract(amount);
                            break;
                    }
                } else {
                    switch (transactionType) {
                        case DEBIT:
                            splitSum = splitSum.subtract(amount);
                            break;
                        case CREDIT:
                            splitSum = splitSum.add(amount);
                            break;
                    }
                }
            }
            cursor.close();
        }
        return splitSum;
    }

    /**
     * Returns the list of splits for a transaction
     * @param transactionUID String unique ID of transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     */
    public List<Split> getSplitsForTransaction(String transactionUID){
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
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
        Cursor cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID);
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

    /**
     * Returns the unique identifier string of the split
     * @param id Database record ID of the split
     * @return String unique identifier of the split
     */
    public String getUID(long id){
        Cursor cursor = mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                new String[]{DatabaseHelper.KEY_UID},
                DatabaseHelper.KEY_ROW_ID + " = " + id, null, null, null, null);
        String uid = null;
        if (cursor != null && cursor.moveToFirst()){
            Log.d(TAG, "Transaction already exists. Returning existing id");
            uid = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.KEY_UID));
            cursor.close();
        }
        return uid;
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     * @param transactionUID Unique idendtifier of the transaction
     * @return Cursor to splits
     */
    public Cursor fetchSplitsForTransaction(String transactionUID){
        Log.v(TAG, "Fetching all splits for transaction UID " + transactionUID);
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, DatabaseHelper.KEY_TRANSACTION_UID + " = ?",
                new String[]{transactionUID},
                null, null, null);
    }

    /**
     * Fetches splits for a given account
     * @param accountUID String unique ID of account
     * @return Cursor containing splits dataset
     */
    public Cursor fetchSplitsForAccount(String accountUID){
        Log.d(TAG, "Fetching all splits for account UID " + accountUID);
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, DatabaseHelper.KEY_ACCOUNT_UID + " = ?",
                new String[]{accountUID},
                null, null, null);
    }

    /**
     * Returns a cursor to splits for a given transaction and account
     * @param transactionUID Unique idendtifier of the transaction
     * @param accountUID String unique ID of account
     * @return Cursor to splits data set
     */
    public Cursor fetchSplitsForTransactionAndAccount(String transactionUID, String accountUID){
        Log.v(TAG, "Fetching all splits for transaction ID " + transactionUID
                + "and account ID " + accountUID);
        return mDb.query(DatabaseHelper.SPLITS_TABLE_NAME,
                null, DatabaseHelper.KEY_TRANSACTION_UID + " = ? AND "
                + DatabaseHelper.KEY_ACCOUNT_UID + " = ?",
                new String[]{transactionUID, accountUID},
                null, null, DatabaseHelper.KEY_AMOUNT + " ASC");
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
        String transactionUID = getSplit(getUID(rowId)).getTransactionUID();
        boolean result = deleteRecord(DatabaseHelper.SPLITS_TABLE_NAME, rowId);

        //if we just deleted the last split, then remove the transaction from db
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
        if (cursor != null){
            if (cursor.getCount() > 0){
                result &= deleteTransaction(getTransactionID(transactionUID));
            }
            cursor.close();
        }
        return result;
    }

    /**
     * Returns the database record ID for the specified transaction UID
     * @param transactionUID Unique idendtifier of the transaction
     * @return Database record ID for the transaction
     */
    public long getTransactionID(String transactionUID){
        long id = -1;
        Cursor c = mDb.query(DatabaseHelper.TRANSACTIONS_TABLE_NAME,
                new String[]{DatabaseHelper.KEY_ROW_ID},
                DatabaseHelper.KEY_UID + "='" + transactionUID + "'",
                null, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
            c.close();
        }
        return id;
    }

    /**
     * Deletes all splits for a particular transaction and the transaction itself
     * @param transactionId Database record ID of the transaction
     * @return <code>true</code> if at least one split was deleted, <code>false</code> otherwise.
     */
    public boolean deleteSplitsForTransaction(long transactionId){
        String trxUID = getTransactionUID(transactionId);
        boolean result = mDb.delete(DatabaseHelper.SPLITS_TABLE_NAME,
                DatabaseHelper.KEY_TRANSACTION_UID + "=?",
                new String[]{trxUID}) > 0;
        result &= deleteTransaction(transactionId);
        return result;
    }

    /**
     * Deletes splits for a specific transaction and account and the transaction itself
     * @param transactionId Database record ID of the transaction
     * @param accountId Database ID of the account
     * @return Number of records deleted
     */
    public int deleteSplitsForTransactionAndAccount(long transactionId, long accountId){
        String transactionUID  = getTransactionUID(transactionId);
        String accountUID      = getAccountUID(accountId);
        int deletedCount = mDb.delete(DatabaseHelper.SPLITS_TABLE_NAME,
                DatabaseHelper.KEY_TRANSACTION_UID + "= ? AND "
                + DatabaseHelper.KEY_ACCOUNT_UID + "= ?",
                new String[]{transactionUID, accountUID});
        deleteTransaction(transactionId);
        return deletedCount;
    }

    /**
     * Deletes the transaction from the the database
     * @param transactionId Database record ID of the transaction
     */
    private boolean deleteTransaction(long transactionId) {
        return deleteRecord(DatabaseHelper.TRANSACTIONS_TABLE_NAME, transactionId);
    }

    @Override
    public int deleteAllRecords() {
        return deleteAllRecords(DatabaseHelper.SPLITS_TABLE_NAME);
    }
}
