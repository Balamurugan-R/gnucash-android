/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction.dialog;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import org.gnucash.android.R;
import org.gnucash.android.db.AccountsDbAdapter;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.SplitsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.UxArgument;
import org.gnucash.android.ui.util.AmountInputFormatter;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class SplitEditorDialogFragment extends DialogFragment {

    private LinearLayout mSplitsLinearLayout;
    private TextView mAssignedSplitsTextView;
    private TextView mUnassignedAmountTextView;
    private TextView mTransactionAmount;
    private Button mAddSplit;
    private Button mSaveButton;
    private Button mCancelButton;

    private long mTransactionId;
    private AccountsDbAdapter mAccountsDbAdapter;
    private SplitsDbAdapter mSplitsDbAdapter;
    private Cursor mCursor;
    private SimpleCursorAdapter mCursorAdapter;
    private List<View> mSplitItemViewList;
    private long mAccountId;
    private String mAccountUID;

    private BalanceTextWatcher mBalanceUpdater = new BalanceTextWatcher();
    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    /**
     * Create and return a new instance of the fragment with the appropriate paramenters
     * @param transactionId DB record ID of the transaction whose splits are to be edited
     * @param accountId DB record ID of the account to which the transaction belongs
     * @return New instance of SplitEditorDialogFragment
     */
    public static SplitEditorDialogFragment newInstance(long transactionId, long accountId){
        SplitEditorDialogFragment fragment = new SplitEditorDialogFragment();
        Bundle args = new Bundle();
        args.putLong(UxArgument.SELECTED_TRANSACTION_ID, transactionId);
        args.putLong(UxArgument.SELECTED_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_split_editor, container, false);
        mSplitsLinearLayout = (LinearLayout) view.findViewById(R.id.split_list_layout);

        mAssignedSplitsTextView     = (TextView) view.findViewById(R.id.splits_sum);
        mUnassignedAmountTextView   = (TextView) view.findViewById(R.id.unassigned_balance);
        mTransactionAmount          = (TextView) view.findViewById(R.id.transaction_total);

        mAddSplit   = (Button) view.findViewById(R.id.btn_add_split);
        mSaveButton = (Button) view.findViewById(R.id.btn_save);
        mCancelButton       = (Button) view.findViewById(R.id.btn_cancel);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getDialog().getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);

        getDialog().setTitle("Transaction splits");

        initArgs();
        mSplitItemViewList = new ArrayList<View>();

        mSplitsDbAdapter = new SplitsDbAdapter(getActivity());

        if (mTransactionId <= 0) {
            //we are editing splits for a new transaction.
            // But the user may have already created some splits before. Let's check
            List<Split> splitList = ((TransactionFormFragment)getTargetFragment()).getSplitList();
            if (!splitList.isEmpty()) {
                //aha! there are some splits. Let's load those instead
                loadSplitViews(splitList);
            } else {
                final Currency currency = Currency.getInstance(mAccountsDbAdapter.getCurrencyCode(mAccountUID));
                Split split = new Split(new Money(mBaseAmount, currency), mAccountUID);
                split.setPrimary(true);
                addSplitView(split);
            }
        } else {
            List<Split> splitList = mSplitsDbAdapter.getSplitsForTransaction(mTransactionId);
            loadSplitViews(splitList);
        }

        setListeners();
        updateTotal();
    }

    private void loadSplitViews(List<Split> splitList) {
        for (Split split : splitList) {
            if (split.getAccountUID().equals(mAccountUID))
                split.setPrimary(true);
            addSplitView(split);
        }
    }

    /**
     * Add a split view and initialize it with <code>split</code>
     * @param split Split to initialize the contents to
     * @return Returns the split view which was added
     */
    private View addSplitView(Split split){
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View splitView = layoutInflater.inflate(R.layout.item_split_entry, mSplitsLinearLayout, false);
        mSplitsLinearLayout.addView(splitView,0);
        bindSplitView(splitView, split);
        mSplitItemViewList.add(splitView);
        return splitView;
    }

    /**
     * Extracts arguments passed to the view and initializes necessary adapters and cursors
     */
    private void initArgs() {
        mAccountsDbAdapter = new AccountsDbAdapter(getActivity());

        Bundle args     = getArguments();
        mTransactionId  = args.getLong(UxArgument.SELECTED_TRANSACTION_ID);
        mAccountId      = ((TransactionsActivity)getActivity()).getCurrentAccountID();
        mAccountUID     = mAccountsDbAdapter.getAccountUID(mAccountId);
        mBaseAmount     = new BigDecimal(args.getString(UxArgument.AMOUNT_STRING));

        String conditions = "(" //+ DatabaseHelper.KEY_ROW_ID + " != " + mAccountId + " AND "
                + DatabaseHelper.KEY_CURRENCY_CODE + " = '" + mAccountsDbAdapter.getCurrencyCode(mAccountId)
                + "' AND " + DatabaseHelper.KEY_UID + " != '" + mAccountsDbAdapter.getGnuCashRootAccountUID()
                + "' AND " + DatabaseHelper.KEY_PLACEHOLDER + " = 0"
                + ")";
        mCursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(conditions);
    }

    /**
     * Binds the different UI elements of an inflated list view to corresponding actions
     * @param splitView Split item view
     * @param split {@link org.gnucash.android.model.Split} to use to populate the view
     */
    private void bindSplitView(final View splitView, Split split){
        EditText splitMemoEditText              = (EditText)    splitView.findViewById(R.id.input_split_memo);
        final EditText splitAmountEditText      = (EditText)    splitView.findViewById(R.id.input_split_amount);
        final ToggleButton splitTypeButton      = (ToggleButton) splitView.findViewById(R.id.btn_split_type);
        ImageButton removeSplitButton           = (ImageButton) splitView.findViewById(R.id.btn_remove_split);
        Spinner accountsSpinner                 = (Spinner)     splitView.findViewById(R.id.input_accounts_spinner);
        final TextView splitCurrencyTextView    = (TextView)    splitView.findViewById(R.id.split_currency_symbol);

        splitAmountEditText.addTextChangedListener(new AmountInputFormatter(splitAmountEditText,splitTypeButton));
        splitAmountEditText.addTextChangedListener(mBalanceUpdater);

        removeSplitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSplitsLinearLayout.removeView(splitView);
                mSplitItemViewList.remove(splitView);
                updateTotal();
            }
        });

        //TODO: use account specific labels for the type
        splitTypeButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            //TODO: look at the type of account and movement in account
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    int red = getResources().getColor(R.color.debit_red);
                    splitTypeButton.setTextColor(red);
                    splitAmountEditText.setTextColor(red);
                    splitCurrencyTextView.setTextColor(red);
                }
                else {
                    int green = getResources().getColor(R.color.credit_green);
                    splitTypeButton.setTextColor(green);
                    splitAmountEditText.setTextColor(green);
                    splitCurrencyTextView.setTextColor(green);
                }
                String amountText = splitAmountEditText.getText().toString();
                if (amountText.length() > 0){
                    String changedSignText = TransactionFormFragment.parseInputToDecimal(amountText).negate().toPlainString();
                    splitAmountEditText.setText(changedSignText);
                }
                updateTotal();
            }
        });

        splitTypeButton.setChecked(mBaseAmount.signum() < 0);

        updateTransferAccountsList(accountsSpinner);
        if (split != null) {
            splitTypeButton.setChecked(split.getAmount().isNegative());
            setSelectedTransferAccount(mAccountsDbAdapter.getAccountID(split.getAccountUID()), accountsSpinner);
            splitAmountEditText.setText(split.getAmount().toPlainString());
            splitMemoEditText.setText(split.getMemo());

            if (split.isPrimary()){
                accountsSpinner.setEnabled(false);
                removeSplitButton.setVisibility(View.GONE);
                splitAmountEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        mBaseAmount = TransactionFormFragment.parseInputToDecimal(editable.toString());
                    }
                });
            }
        }
    }

    /**
     * Updates the spinner to the selected transfer account
     * @param accountId Database ID of the transfer account
     */
    private void setSelectedTransferAccount(long accountId, final Spinner accountsSpinner){
        for (int pos = 0; pos < mCursorAdapter.getCount(); pos++) {
            if (mCursorAdapter.getItemId(pos) == accountId){
                final int position = pos;
                accountsSpinner.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        accountsSpinner.setSelection(position);
                    }
                }, 500);
                break;
            }
        }
    }
    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private void updateTransferAccountsList(Spinner transferAccountSpinner){

        mCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(),
                android.R.layout.simple_spinner_item, mCursor);
        mCursorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        transferAccountSpinner.setAdapter(mCursorAdapter);
    }

    /**
     * Attaches listeners for the buttons of the dialog
     */
    protected void setListeners(){
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Split> splitList = extractSplitsFromView();
                ((TransactionFormFragment)getTargetFragment()).setSplitList(splitList);

                dismiss();
            }
        });

        mAddSplit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addSplitView(null);
            }
        });
    }

    /**
     * Extracts the input from the views and builds {@link org.gnucash.android.model.Split}s to correspond to the input.
     * @return List of {@link org.gnucash.android.model.Split}s represented in the view
     */
    private List<Split> extractSplitsFromView(){
        List<Split> splitList = new ArrayList<Split>();
        for (View splitView : mSplitItemViewList) {
            EditText splitMemoEditText              = (EditText)    splitView.findViewById(R.id.input_split_memo);
            final EditText splitAmountEditText      = (EditText)    splitView.findViewById(R.id.input_split_amount);
            Spinner accountsSpinner                 = (Spinner)     splitView.findViewById(R.id.input_accounts_spinner);
            final ToggleButton splitTypeButton      = (ToggleButton) splitView.findViewById(R.id.btn_split_type);


            BigDecimal amountBigDecimal = TransactionFormFragment.parseInputToDecimal(splitAmountEditText.getText().toString());
            String currencyCode = mAccountsDbAdapter.getCurrencyCode(accountsSpinner.getSelectedItemId());
            String accountUID = mAccountsDbAdapter.getAccountUID(accountsSpinner.getSelectedItemId());
            Money amount = new Money(amountBigDecimal, Currency.getInstance(currencyCode));
            Split split = new Split(amount, accountUID);
            split.setMemo(splitMemoEditText.getText().toString());
            Account.AccountType accountType = mAccountsDbAdapter.getAccountType(accountUID);
            if (accountType.hasDebitNormalBalance()){
                //if we show negative value to a user, then that is a credit for "debit balance" accounts
                if (splitTypeButton.isChecked())
                    split.setType(TransactionType.CREDIT);
                else
                    split.setType(TransactionType.DEBIT);
            } else {
                //if we show negative value to a user, then that is a debit for "debit balance" accounts
                if (splitTypeButton.isChecked())
                    split.setType(TransactionType.DEBIT);
                else
                    split.setType(TransactionType.CREDIT);
            }

            splitList.add(split);
        }
        return splitList;
    }

    /**
     * Updates the displayed total for the transaction
     */
    private void updateTotal(){
        List<Split> splitList = extractSplitsFromView();
        String currencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountId);
        Money zeroInstance  = Money.createZeroInstance(currencyCode);

        Money transactionAmount = new Money(mBaseAmount, Currency.getInstance(currencyCode));
        Money splitSum          = zeroInstance;
        for (Split split : splitList) {
            splitSum = splitSum.add(split.getAmount());
        }

        Money unassigned = zeroInstance;
        Money assigned = zeroInstance;

        if (!splitSum.equals(transactionAmount)){
            assigned = splitSum.subtract(transactionAmount);
            unassigned = transactionAmount.subtract(assigned);
        }

        displayBalance(mAssignedSplitsTextView, assigned);
        displayBalance(mUnassignedAmountTextView, unassigned);
        displayBalance(mTransactionAmount, transactionAmount);
    }

    private void displayBalance(TextView balanceTextView, Money balance){
        balanceTextView.setText(balance.formattedString());
        //TODO: Consider the type of account and movement caused
        int fontColor = balance.isNegative() ? getActivity().getResources().getColor(R.color.debit_red) :
                getActivity().getResources().getColor(R.color.credit_green);
        balanceTextView.setTextColor(fontColor);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAccountsDbAdapter.close();
        mSplitsDbAdapter.close();
    }

    /**
     * Updates the displayed balance of the accounts when the amount of a split is changed
     */
    private class BalanceTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            updateTotal();
        }
    }
}
