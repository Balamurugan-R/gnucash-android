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

package org.gnucash.android.export.xml;

import org.gnucash.android.db.AccountsDbAdapter;
import org.gnucash.android.db.TransactionsDbAdapter;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

/**
 * Creates a GnuCash XML representation of the accounts and transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class GncXmlExporter extends Exporter{

    private Document mDocument;

    public GncXmlExporter(ExportParams params){
        super(params);
    }

    private void generateGncXml() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = docFactory.newDocumentBuilder();

        mDocument = documentBuilder.newDocument();
        mDocument.setXmlVersion("1.0");
        mDocument.setXmlStandalone(true);

        Element rootElement = mDocument.createElement(GncXmlHelper.TAG_ROOT);
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:gnc",    "http://www.gnucash.org/XML/gnc");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:act",    "http://www.gnucash.org/XML/act");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:book",   "http://www.gnucash.org/XML/book");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cd",     "http://www.gnucash.org/XML/cd");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cmdty",  "http://www.gnucash.org/XML/cmdty");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:price",  "http://www.gnucash.org/XML/price");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:slot",   "http://www.gnucash.org/XML/slot");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:split",  "http://www.gnucash.org/XML/split");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:trn",    "http://www.gnucash.org/XML/trn");
        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:ts",     "http://www.gnucash.org/XML/ts");

        Element bookCountNode = mDocument.createElement(GncXmlHelper.TAG_COUNT_DATA);
        bookCountNode.setAttribute("cd:type", "book");
        bookCountNode.appendChild(mDocument.createTextNode("1"));
        rootElement.appendChild(bookCountNode);

        Element bookNode = mDocument.createElement(GncXmlHelper.TAG_BOOK);
        bookNode.setAttribute("version", GncXmlHelper.BOOK_VERSION);
        rootElement.appendChild(bookNode);

        Element bookIdNode = mDocument.createElement(GncXmlHelper.TAG_BOOK_ID);
        bookIdNode.setAttribute("type", "guid");
        bookIdNode.appendChild(mDocument.createTextNode(UUID.randomUUID().toString().replaceAll("-", "")));
        bookNode.appendChild(bookIdNode);

        Element cmdtyCountData = mDocument.createElement(GncXmlHelper.TAG_COUNT_DATA);
        cmdtyCountData.setAttribute("cd:type", "commodity");
        cmdtyCountData.appendChild(mDocument.createTextNode("1")); //TODO: put actual number of currencies
        bookNode.appendChild(cmdtyCountData);

        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mContext);
        List<Account> accountList = accountsDbAdapter.getAllAccounts();
        accountsDbAdapter.close();

        Element accountCountNode = mDocument.createElement(GncXmlHelper.TAG_COUNT_DATA);
        accountCountNode.setAttribute("cd:type", "account");
        accountCountNode.appendChild(mDocument.createTextNode(String.valueOf(accountList.size())));
        bookNode.appendChild(accountCountNode);

        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(mContext);
        List<Transaction> transactionsList = transactionsDbAdapter.getAllTransactions();
        transactionsDbAdapter.close();
        Element transactionCountNode = mDocument.createElement(GncXmlHelper.TAG_COUNT_DATA);
        transactionCountNode.setAttribute("cd:type", "transaction");
        transactionCountNode.appendChild(mDocument.createTextNode(String.valueOf(transactionsList.size())));
        bookNode.appendChild(transactionCountNode);

        for (Account account : accountList) {
            account.toGncXml(mDocument, bookNode);
        }

        for (Transaction transaction : transactionsList) {
            transaction.toGncXml(mDocument, bookNode);
        }


    }

    @Override
    public String generateExport() throws ExporterException{
        StringWriter stringWriter = new StringWriter();

        try {
            generateGncXml();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            Transformer transformer = transformerFactory.newTransformer();

            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(mDocument);
            StreamResult result = new StreamResult(stringWriter);

            transformer.transform(source, result);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ExporterException(mParameters, e);
        }
        return stringWriter.toString();
    }
}
