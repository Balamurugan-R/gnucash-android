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

package org.gnucash.android.ui.util;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ToggleButton;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.TransactionType;

/**
 * A special type of {@link android.widget.ToggleButton} which displays the appropriate CREDIT/DEBIT labels for the
 * different account types.
 * //TODO: Localize the label strings
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionTypeToggleButton extends ToggleButton {
    private AccountType mAccountType = AccountType.EXPENSE;

    public TransactionTypeToggleButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TransactionTypeToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TransactionTypeToggleButton(Context context) {
        super(context);
    }

    public void setAccountType(AccountType accountType){
        this.mAccountType = accountType;
        switch (mAccountType) {
            case CASH:
                setTextOn("Spend");
                setTextOff("Receive");
                break;
            case BANK:
                setTextOn("Deposit");
                setTextOff("Withdrawal");
                break;
            case CREDIT:
                setTextOn("Charge");
                setTextOff("Payment");
                break;
            case ASSET:
            case LIABILITY:
                setTextOn("Decrease");
                setTextOff("Increase");
                break;
            case INCOME:
                setTextOn("Charge");
                setTextOff("Income");
                break;
            case EXPENSE:
                setTextOn("Rebate");
                setTextOff("Expense");
                break;
            case PAYABLE:
                setTextOn("Payment");
                setTextOff("Bill");
                break;
            case RECEIVABLE:
                setTextOn("Payment");
                setTextOff("Invoice");
                break;
            case EQUITY:
                setTextOn("Decrease");
                setTextOff("Increase");
                break;
            case STOCK:
            case MUTUAL:
                setTextOn("Buy");
                setTextOff("Sell");
                break;
            case CURRENCY:
            case ROOT:
                setTextOn("Debit");
                setTextOff("Credit");
                break;
        }
        setText(isChecked() ? getTextOn() : getTextOff());
        invalidate();
    }

    public AccountType getAccountType(){
        return mAccountType;
    }

    public TransactionType getTransactionType(){
        if (mAccountType.hasDebitNormalBalance()){
            return isChecked() ? TransactionType.CREDIT : TransactionType.DEBIT;
        } else {
            return isChecked() ? TransactionType.DEBIT : TransactionType.CREDIT;
        }
    }
}
