package org.gnucash.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Split;
import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;

/**
 * Date: 23.03.2014
 *
 * @author Ngewi
 */
public class MigrationHelper {
    public static final String LOG_TAG = "MigrationHelper";

    public static boolean addSplit(SQLiteDatabase db, Split split){
        ContentValues contentValues = new ContentValues();
        contentValues.put(SplitEntry.COLUMN_UID, split.getUID());
        contentValues.put(SplitEntry.COLUMN_TRANSACTION_UID, split.getTransactionUID());
        contentValues.put(SplitEntry.COLUMN_AMOUNT, split.getAmount().absolute().toPlainString());
        contentValues.put(SplitEntry.COLUMN_TYPE, split.getType().name());

        Log.d(LOG_TAG, "Adding new transaction split to db");
        long rowId = db.insert(DatabaseSchema.SplitEntry.TABLE_NAME, null, contentValues);
        return rowId > 0;
    }

    /**
     * Returns the currency code (according to the ISO 4217 standard) of the account
     * with unique Identifier <code>accountUID</code>
     * @param accountUID Unique Identifier of the account
     * @return Currency code of the account
     */
    public static String getCurrencyCode(SQLiteDatabase db, String accountUID) {
        Cursor cursor = db.query(AccountEntry.TABLE_NAME,
                new String[] {AccountEntry.COLUMN_CURRENCY},
                AccountEntry.COLUMN_UID + "= ?",
                new String[]{accountUID}, null, null, null);

        if (cursor == null || cursor.getCount() <= 0)
            return null;

        cursor.moveToFirst();
        String currencyCode = cursor.getString(0);
        cursor.close();
        return currencyCode;
    }

    /**
     * Updates a specific entry of an transaction
     * @param db SQLite database
     * @param transactionUID Unique ID of the transaction
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public static int updateTransaction(SQLiteDatabase db, String transactionUID, String columnKey, String newValue){
        ContentValues contentValues = new ContentValues();
        contentValues.put(columnKey, newValue);

        return db.update(TransactionEntry.TABLE_NAME, contentValues,
                TransactionEntry.COLUMN_UID + "= ?", new String[]{transactionUID});
    }

    /**
     * Performs same functtion as {@link AccountsDbAdapter#getFullyQualifiedAccountName(String)}
     * <p>This method is only necessary because we cannot open the database again (by instantiating {@link org.gnucash.android.db.AccountsDbAdapter}
     * while it is locked for upgrades. So we reimplement the method here.</p>
     * @param db SQLite database
     * @param accountUID Unique ID of account whose fully qualified name is to be determined
     * @return Fully qualified (colon-sepaated) account name
     * @see AccountsDbAdapter#getFullyQualifiedAccountName(String)
     */
    static String getFullyQualifiedAccountName(SQLiteDatabase db, String accountUID){
        //get the parent account UID of the account
        Cursor cursor = db.query(AccountEntry.TABLE_NAME,
                new String[] {AccountEntry.COLUMN_PARENT_ACCOUNT_UID},
                AccountEntry.COLUMN_UID + " = ?",
                new String[]{accountUID},
                null, null, null, null);

        String parentAccountUID = null;
        if (cursor != null && cursor.moveToFirst()){
            parentAccountUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID));
            cursor.close();
        }

        //get the name of the account
        cursor = db.query(AccountEntry.TABLE_NAME,
                new String[]{AccountEntry.COLUMN_NAME},
                AccountEntry.COLUMN_UID + " = ?",
                new String[]{accountUID}, null, null, null);

        String accountName = null;
        if (cursor != null && cursor.moveToFirst()){
            accountName = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_NAME));
            cursor.close();
        }

        String gnucashRootAccountUID = getGnuCashRootAccountUID(db);
        if (parentAccountUID == null || accountName == null
            || parentAccountUID.equalsIgnoreCase(gnucashRootAccountUID)){
            return accountName;
        }

        String parentAccountName = getFullyQualifiedAccountName(db, parentAccountUID);

        return parentAccountName + AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + accountName;
    }

    /**
     * Returns the GnuCash ROOT account UID.
     * <p>In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android does not have this ROOT account by default unless the account
     * structure was imported from GnuCash for desktop. Hence this method also returns <code>null</code> as an
     * acceptable result.</p>
     * <p><b>Note:</b> NULL is an acceptable response, be sure to check for it</p>
     * @return Unique ID of the GnuCash root account.
     */
    private static String getGnuCashRootAccountUID(SQLiteDatabase db){
        String condition = AccountEntry.COLUMN_TYPE + "= '" + AccountType.ROOT.name() + "'";
        Cursor cursor =  db.query(DatabaseSchema.AccountEntry.TABLE_NAME,
                null, condition, null, null, null,
                AccountEntry.COLUMN_NAME + " ASC");
        String rootUID = null;
        if (cursor != null && cursor.moveToFirst()){
            rootUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID));
            cursor.close();
        }
        return rootUID;
    }
}
