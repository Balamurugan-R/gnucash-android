package org.gnucash.android.model;

import java.util.UUID;

/**
 * A split amount in a transaction.
 * Every transaction is made up of at least two splits (representing a double entry transaction)
 * <p>The split amount is always stored in the database as the absolute value alongside its transaction type of CREDIT/DEBIT<br/>
 * This is independent of the negative values which are shown in the UI (for user convenience).
 * The actual movement of the balance in the account depends on the type of normal balance of the account and the
 * transaction type of the split.</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class Split {
    /**
     * Amount value of this split
     */
    private Money mAmount;

    /**
     * Unique ID of this split
     */
    private String mUID;

    /**
     * Transaction UID which this split belongs to
     */
    private String mTransactionUID;

    /**
     * Account UID which this split belongs to
     */
    private String mAccountUID;

    /**
     * The type of this transaction, credit or debit
     */
    private TransactionType mSplitType;

    /**
     * Memo associated with this split
     */
    private String mMemo;

    /**
     * Initialize split with an amount and account
     * @param amount Money amount of this split
     * @param accountUID String UID of transfer account
     */
    public Split(Money amount, String accountUID){
        setAmount(amount);
        setAccountUID(accountUID);
        mUID = UUID.randomUUID().toString();
    }


    public Money getAmount() {
        return mAmount;
    }

    public void setAmount(Money amount) {
        this.mAmount = amount;
    }

    public String getUID() {
        return mUID;
    }

    public void setUID(String uid) {
        this.mUID = uid;
    }

    public String getTransactionUID() {
        return mTransactionUID;
    }

    public void setTransactionUID(String transactionUID) {
        this.mTransactionUID = transactionUID;
    }

    public String getAccountUID() {
        return mAccountUID;
    }

    public void setAccountUID(String accountUID) {
        this.mAccountUID = accountUID;
    }

    public TransactionType getType() {
        return mSplitType;
    }

    public void setType(TransactionType transactionType) {
        this.mSplitType = transactionType;
    }

    public String getMemo() {
        return mMemo;
    }

    public void setMemo(String memo) {
        this.mMemo = memo;
    }

    public Split createPair(String accountUID){
        Split pair = new Split(mAmount.absolute(), accountUID);
        pair.setType(mSplitType.invert());
        pair.setMemo(mMemo);

        return pair;
    }
}
