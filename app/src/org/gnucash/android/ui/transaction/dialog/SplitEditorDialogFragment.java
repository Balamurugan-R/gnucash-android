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
import org.gnucash.android.model.*;
import org.gnucash.android.ui.UxArgument;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.ui.util.AmountInputFormatter;
import org.gnucash.android.ui.util.TransactionTypeToggleButton;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

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

    private AccountsDbAdapter mAccountsDbAdapter;
    private SplitsDbAdapter mSplitsDbAdapter;
    private Cursor mCursor;
    private SimpleCursorAdapter mCursorAdapter;
    private List<View> mSplitItemViewList;
    private long mAccountId;
    private String mAccountUID;

    private BalanceTextWatcher mBalanceUpdater = new BalanceTextWatcher();
    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    private List<String> mRemovedSplitUIDs = new ArrayList<String>();
    /**
     * Create and return a new instance of the fragment with the appropriate paramenters
     * @param baseAmountString String with base amount which is being split
     * @return New instance of SplitEditorDialogFragment
     */
    public static SplitEditorDialogFragment newInstance(String baseAmountString){
        SplitEditorDialogFragment fragment = new SplitEditorDialogFragment();
        Bundle args = new Bundle();
        args.putString(UxArgument.AMOUNT_STRING, baseAmountString);
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

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check
        List<Split> splitList = ((TransactionFormFragment) getTargetFragment()).getSplitList();
        if (!splitList.isEmpty()) {
            //aha! there are some splits. Let's load those instead
            loadSplitViews(splitList);
        } else {
            final Currency currency = Currency.getInstance(mAccountsDbAdapter.getCurrencyCode(mAccountUID));
            Split split = new Split(new Money(mBaseAmount, currency), mAccountUID);
            AccountType accountType = mAccountsDbAdapter.getAccountType(mAccountUID);
            TransactionType transactionType;
            if (accountType.hasDebitNormalBalance()) {
                transactionType = mBaseAmount.signum() < 0 ? TransactionType.CREDIT : TransactionType.DEBIT;
            } else {
                transactionType = mBaseAmount.signum() < 0 ? TransactionType.CREDIT : TransactionType.DEBIT;
            }
            split.setType(transactionType);
            View view = addSplitView(split);
            view.findViewById(R.id.input_accounts_spinner).setEnabled(false);
            view.findViewById(R.id.btn_remove_split).setVisibility(View.GONE);
        }

        setListeners();
        updateTotal();
    }

    private void loadSplitViews(List<Split> splitList) {
        for (Split split : splitList) {
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
        ImageButton removeSplitButton           = (ImageButton) splitView.findViewById(R.id.btn_remove_split);
        Spinner accountsSpinner                 = (Spinner)     splitView.findViewById(R.id.input_accounts_spinner);
        final TextView splitCurrencyTextView    = (TextView)    splitView.findViewById(R.id.split_currency_symbol);
        final TextView splitUidTextView         = (TextView)    splitView.findViewById(R.id.split_uid);
        final TransactionTypeToggleButton splitTypeButton = (TransactionTypeToggleButton) splitView.findViewById(R.id.btn_split_type);

        splitAmountEditText.addTextChangedListener(new AmountInputFormatter(splitAmountEditText,splitTypeButton));
        splitAmountEditText.addTextChangedListener(mBalanceUpdater);

        removeSplitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRemovedSplitUIDs.add(splitUidTextView.getText().toString());
                mSplitsLinearLayout.removeView(splitView);
                mSplitItemViewList.remove(splitView);
                updateTotal();
            }
        });

        accountsSpinner.setOnItemSelectedListener(new TypeButtonLabelUpdater(splitTypeButton));

        splitTypeButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            //TODO: look at the type of account and movement in account
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    int red = getResources().getColor(R.color.debit_red);
                    splitTypeButton.setTextColor(red);
                    splitAmountEditText.setTextColor(red);
                    splitCurrencyTextView.setTextColor(red);
                } else {
                    int green = getResources().getColor(R.color.credit_green);
                    splitTypeButton.setTextColor(green);
                    splitAmountEditText.setTextColor(green);
                    splitCurrencyTextView.setTextColor(green);
                }
                String amountText = splitAmountEditText.getText().toString();
                if (amountText.length() > 0) {
                    String changedSignText = TransactionFormFragment.parseInputToDecimal(amountText).negate().toPlainString();
                    splitAmountEditText.setText(changedSignText);
                }
                updateTotal();
            }
        });

        splitTypeButton.setChecked(mBaseAmount.signum() < 0);
        splitUidTextView.setText(UUID.randomUUID().toString());

        updateTransferAccountsList(accountsSpinner);
        AccountType accountType = mAccountsDbAdapter.getAccountType(accountsSpinner.getSelectedItemId());

        if (split != null) {
            splitTypeButton.setChecked(split.getAmount().isNegative());
            setSelectedTransferAccount(mAccountsDbAdapter.getAccountID(split.getAccountUID()), accountsSpinner);
            splitAmountEditText.setText(split.getAmount().toPlainString());
            splitMemoEditText.setText(split.getMemo());
            splitUidTextView.setText(split.getUID());
            accountType = mAccountsDbAdapter.getAccountType(split.getAccountUID());
            if (accountType.hasDebitNormalBalance()){
                splitTypeButton.setChecked(split.getType() == TransactionType.CREDIT);
            } else {
                splitTypeButton.setChecked(split.getType() == TransactionType.DEBIT);
            }
        }
        splitTypeButton.setAccountType(accountType);
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
                ((TransactionFormFragment) getTargetFragment()).setSplitList(splitList, mRemovedSplitUIDs);

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
            TextView splitUidTextView               = (TextView)    splitView.findViewById(R.id.split_uid);

            BigDecimal amountBigDecimal = TransactionFormFragment.parseInputToDecimal(splitAmountEditText.getText().toString());
            String currencyCode = mAccountsDbAdapter.getCurrencyCode(accountsSpinner.getSelectedItemId());
            String accountUID = mAccountsDbAdapter.getAccountUID(accountsSpinner.getSelectedItemId());
            Money amount = new Money(amountBigDecimal, Currency.getInstance(currencyCode));
            Split split = new Split(amount, accountUID);
            split.setMemo(splitMemoEditText.getText().toString());
            AccountType accountType = mAccountsDbAdapter.getAccountType(accountUID);
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
            split.setUID(splitUidTextView.getText().toString().trim());
            splitList.add(split);
        }
        return splitList;
    }

    /**
     * Updates the displayed total for the transaction.
     * Computes the total of the splits, the unassigned balance and the split sum
     */
    private void updateTotal(){
        List<Split> splitList = extractSplitsFromView();
        String currencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountId);
        Money zeroInstance  = Money.createZeroInstance(currencyCode);

        Money transactionAmount = zeroInstance; //new Money(mBaseAmount, Currency.getInstance(currencyCode));
        Money splitSum          = zeroInstance;
        for (Split split : splitList) {
            Money amount = split.getAmount().absolute();
            transactionAmount = amount.compareTo(transactionAmount) > 0 ? amount : transactionAmount;
            splitSum = splitSum.add(amount);
        }

        Money unassigned = zeroInstance;
        Money assigned = zeroInstance;

        if (splitList.size() > 1){
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
//        int fontColor = balance.isNegative() ? getActivity().getResources().getColor(R.color.debit_red) :
//                getActivity().getResources().getColor(R.color.credit_green);
//        balanceTextView.setTextColor(fontColor);
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

    private class TypeButtonLabelUpdater implements AdapterView.OnItemSelectedListener {
        TransactionTypeToggleButton mTypeToggleButton;

        public TypeButtonLabelUpdater(TransactionTypeToggleButton typeToggleButton){
            this.mTypeToggleButton = typeToggleButton;
        }

        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            AccountType accountType = mAccountsDbAdapter.getAccountType(id);
            mTypeToggleButton.setAccountType(accountType);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    }
}
