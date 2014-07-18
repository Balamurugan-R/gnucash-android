package org.gnucash.android.export.xml;

import org.gnucash.android.model.Money;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date: 17.07.2014
 *
 * @author Ngewi
 */
public abstract class GncXmlHelper {
    public static final String TAG_PREFIX           = "gnc:";
    /*
    Qualified GnuCash XML tag names
     */
    public static final String TAG_ROOT             = "gnc-v2";
    public static final String TAG_BOOK             = "gnc:book";
    public static final String TAG_BOOK_ID          = "book:id";
    public static final String TAG_COUNT_DATA       = "gnc:count-data";
    public static final String ATTRIBUTE_CD_TYPE    = "cd:type";

    public static final String TAG_COMMODITY        = "gnc:commodity";
    public static final String TAG_NAME             = "act:name";
    public static final String TAG_ACCT_ID          = "act:id";
    public static final String TAG_TYPE             = "act:type";
    public static final String TAG_COMMODITY_ID     = "cmdty:id";
    public static final String TAG_COMMODITY_SPACE  = "cmdty:space";
    public static final String TAG_COMMODITY_SCU    = "act:commodity-scu";
    public static final String TAG_PARENT_UID       = "act:parent";
    public static final String TAG_ACCOUNT          = "gnc:account";
    public static final String TAG_SLOT_KEY         = "slot:key";
    public static final String TAG_SLOT_VALUE       = "slot:value";
    public static final String TAG_ACT_SLOTS        = "act:slots";
    public static final String TAG_SLOT             = "slot";
    public static final String TAG_ACCT_DESCRIPTION = "act:description";

    public static final String TAG_TRANSACTION      = "transcation";
    public static final String TAG_TRX_ID           = "trn:id";
    public static final String TAG_TRX_CURRENCY     = "trn:currency";
    public static final String TAG_DATE_POSTED      = "trn:date-posted";
    public static final String TAG_DATE             = "ts:date";
    public static final String TAG_DATE_ENTERED     = "trn:date-entered";
    public static final String TAG_TRX_DESCRIPTION  = "trn:description";
    public static final String TAG_TRX_SPLITS       = "trn:splits";
    public static final String TAG_TRX_SPLIT        = "trn:split";

    public static final String TAG_SPLIT_ID         = "split:id";
    public static final String TAG_SPLIT_MEMO       = "split:memo";
    public static final String TAG_RECONCILED_STATE = "split:reconciled_state";
    public static final String TAG_QUANTITY         = "split:quantity";
    public static final String TAG_SPLIT_ACCOUNT    = "split:account";
    public static final String TAG_SPLIT_VALUE      = "split:value";
    public static final String TAG_SPLIT_QUANTITY   = "split_quantity";

    public static final String BOOK_VERSION         = "2.0.0";
    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    /**
     * Formats dates for the GnuCash XML format
     * @param milliseconds Milliseconds since epoch
     */
    public static String formatDate(long milliseconds){
        return DATE_FORMATTER.format(new Date(milliseconds));
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd HH:mm:ss Z"
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    public static long parseDate(String dateString) throws ParseException {
        Date date = DATE_FORMATTER.parse(dateString);
        return date.getTime();
    }

    /**
     * Formats the money amounts into the GnuCash XML format
     * @param amount {@link org.gnucash.android.model.Money} amount
     * @return GnuCash XML representation of amount
     */
    public static String formatMoney(Money amount){
        BigDecimal decimal = amount.asBigDecimal().multiply(new BigDecimal(100));
        return decimal.toPlainString() + "/100";
    }
}
