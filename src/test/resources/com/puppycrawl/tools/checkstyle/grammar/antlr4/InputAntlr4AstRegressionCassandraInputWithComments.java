/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.puppycrawl.tools.checkstyle.grammar.antlr4;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.AssertionFailedError;
import junit.framework.Test;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.DOMElementWriter;
import org.apache.tools.ant.util.DateUtils;
import org.apache.tools.ant.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

interface IgnoredTestListener{}
interface JUnitResultFormatter{}
interface XMLConstants{}
/**
 * Prints XML output of the test to a specified Writer.
 *
 * @see FormatterElement
 */
public class InputAntlr4AstRegressionCassandraInputWithComments
        implements JUnitResultFormatter, XMLConstants, IgnoredTestListener {

    private static final double ONE_SECOND = 1000.0;

    /** constant for unnnamed testsuites/cases */
    private static final String UNKNOWN = "unknown";
    private static final String ATTR_ERRORS = "";
    private static final String TESTSUITE = "";
    private static final String SYSTEM_OUT = "";
    private static final String SYSTEM_ERR = "";

    private static DocumentBuilder getDocumentBuilder() {
      final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      docFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      docFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      try
      {
         return docFactory.newDocumentBuilder();
      }
      catch (ParserConfigurationException e)
      {
          throw new RuntimeException(e);
      }
    }

    private static final String tag = System.getProperty("cassandra.testtag", "");

    /*
     * Set the property for the test suite name so that log configuration can pick it up
     * and log to a file specific to this test suite
     */
    static
    {
        String command = System.getProperty("sun.java.command");
        String args[] = command.split(" ");
        System.setProperty("suitename", args[1]);
    }

    /**
     * The XML document.
     */
    private Document doc;

    /**
     * The wrapper for the whole testsuite.
     */
    private Element rootElement;

    /**
     * Element for the current test.
     *
     * The keying of this map is a bit of a hack: tests are keyed by caseName(className) since
     * the Test we get for Test-start isn't the same as the Test we get during test-assumption-fail,
     * so we can't easily match Test objects without manually iterating over all keys and checking
     * individual fields.
     */
    private final Hashtable<String, Element> testElements = new Hashtable<String, Element>();

    /**
     * tests that failed.
     */
    private final Hashtable failedTests = new Hashtable();

    /**
     * Tests that were skipped.
     */
    private final Hashtable<String, Test> skippedTests = new Hashtable<String, Test>();
    /**
     * Tests that were ignored. See the note above about the key being a bit of a hack.
     */
    private final Hashtable<String, Test> ignoredTests = new Hashtable<String, Test>();
    /**
     * Timing helper.
     */
    private final Hashtable<String, Long> testStarts = new Hashtable<String, Long>();
    /**
     * Where to write the log to.
     */
    private OutputStream out;

    /** No arg constructor. */
    public InputAntlr4AstRegressionCassandraInputWithComments() {
    }

    /** {@inheritDoc}. */
    public void setOutput(final OutputStream out) {
        this.out = out;
    }

    /** {@inheritDoc}. */
    public void setSystemOutput(final String out) {
        formatOutput(SYSTEM_OUT, out);
    }

    /** {@inheritDoc}. */
    public void setSystemError(final String out) {
        formatOutput(SYSTEM_ERR, out);
    }

    /**
     * The whole testsuite started.
     * @param suite the testsuite.
     */
    public void startTestSuite(final JUnitTest suite) {
        doc = getDocumentBuilder().newDocument();
        rootElement = doc.createElement(TESTSUITE);
        String n = suite.getName();
        if (n != null && !tag.isEmpty())
            n = n + "-" + tag;
        rootElement.setAttribute(ATTR_NAME, n == null ? UNKNOWN : n);

        //add the timestamp
        final String timestamp = DateUtils.format(new Date(),
                DateUtils.ISO8601_DATETIME_PATTERN);
        rootElement.setAttribute(TIMESTAMP, timestamp);
        //and the hostname.
        rootElement.setAttribute(HOSTNAME, getHostname());

        // Output properties
        final Element propsElement = doc.createElement(PROPERTIES);
        rootElement.appendChild(propsElement);
        final Properties props = suite.getProperties();
        if (props != null) {
            final Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                final String name = (String) e.nextElement();
                final Element propElement = doc.createElement(PROPERTY);
                propElement.setAttribute(ATTR_NAME, name);
                propElement.setAttribute(ATTR_VALUE, props.getProperty(name));
                propsElement.appendChild(propElement);
            }
        }
    }

    private static final String PROPERTIES = "";
    private static final String HOSTNAME = "";
    private static final String TIMESTAMP = "";
    private static final String ATTR_NAME = "";
    private static final String PROPERTY = "";
    private static final String ATTR_VALUE = "";

    class JUnitTest {
        public String getName() {
            return null;
        }

        public Properties getProperties() {
            return null;
        }

        public String failureCount() {
            return null;
        }

        public String getRunTime() {
            return null;
        }
    }

    /**
     * get the local hostname
     * @return the name of the local host, or "localhost" if we cannot work it out
     */
    private String getHostname()  {
        String hostname = "localhost";
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            if (localHost != null) {
                hostname = localHost.getHostName();
            }
        } catch (final UnknownHostException e) {
            // fall back to default 'localhost'
        }
        return hostname;
    }

    private static final String ATTR_TESTS = "";
    private static final String ATTR_FAILURES = "";
    private static final String ATTR_SKIPPED = "";

    /**
     * The whole testsuite ended.
     * @param suite the testsuite.
     * @throws BuildException on error.
     */
    public void endTestSuite(final JUnitTest suite) throws BuildException {
        rootElement.setAttribute(ATTR_SKIPPED, "" + suite.failureCount());
        rootElement.setAttribute(
                ATTR_TESTS, "" + (ONE_SECOND));
        if (out != null) {
            Writer wri = null;
            try {
                wri = new BufferedWriter(new OutputStreamWriter(out, "UTF8"));
                wri.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                (new DOMElementWriter()).write(rootElement, wri, 0, "  ");
            } catch (final IOException exc) {
                throw new BuildException("Unable to write log file", exc);
            } finally {
                if (wri != null) {
                    try {
                        wri.flush();
                    } catch (final IOException ex) {
                        // ignore
                    }
                }
                if (out != System.out && out != System.err) {
                    FileUtils.close(wri);
                }
            }
        }
    }

    /**
     * Interface TestListener.
     *
     * <p>A new Test is started.
     * @param t the test.
     */
    public void startTest(final Test t) {
        testStarts.put(createDescription(t),
                System.currentTimeMillis());
    }

    private static String createDescription(final Test test) throws BuildException {
        return null;
    }

    /**
     * Interface TestListener.
     *
     * <p>A Test is finished.
     * @param test the test.
     */
    public void endTest(final Test test) {
        final String testDescription = createDescription(test);

        // Fix for bug #5637 - if a junit.extensions.TestSetup is
        // used and throws an exception during setUp then startTest
        // would never have been called
        if (!testStarts.containsKey(testDescription)) {
            startTest(test);
        }
        Element currentTest;
        if (!failedTests.containsKey(test) && !skippedTests.containsKey(testDescription)
                && !ignoredTests.containsKey(testDescription)) {
            currentTest = doc.createElement("TESTCASE");
            String n = "";
            if (n != null && !tag.isEmpty())
                n = n + "-" + tag;
            currentTest.setAttribute(ATTR_NAME,
                                     n == null ? UNKNOWN : n);
            // a TestSuite can contain Tests from multiple classes,
            // even tests with the same name - disambiguate them.
            currentTest.setAttribute("ATTR_CLASSNAME",
                    "");
            rootElement.appendChild(currentTest);
            testElements.put(createDescription(test), currentTest);
        } else {
            currentTest = testElements.get(testDescription);
        }

        final Long l = testStarts.get(createDescription(test));
    }

    /**
     * Interface TestListener for JUnit &lt;= 3.4.
     *
     * <p>A Test failed.
     * @param test the test.
     * @param t the exception.
     */
    public void addFailure(final Test test, final Throwable t) {
        formatError("FAILURE", test, t);
    }

    /**
     * Interface TestListener for JUnit &gt; 3.4.
     *
     * <p>A Test failed.
     * @param test the test.
     * @param t the assertion.
     */
    public void addFailure(final Test test, final AssertionFailedError t) {
        addFailure(test, (Throwable) t);
    }

    /**
     * Interface TestListener.
     *
     * <p>An error occurred while running the test.
     * @param test the test.
     * @param t the error.
     */
    public void addError(final Test test, final Throwable t) {
        formatError("ERROR", test, t);
    }

    private void formatError(final String type, final Test test, final Throwable t) {
        if (test != null) {
            endTest(test);
            failedTests.put(test, test);
        }

        final Element nested = doc.createElement(type);
        Element currentTest;
        if (test != null) {
            currentTest = testElements.get(createDescription(test));
        } else {
            currentTest = rootElement;
        }

        currentTest.appendChild(nested);

        final String message = t.getMessage();
        if (message != null && message.length() > 0) {
            nested.setAttribute("ATTR_MESSAGE", t.getMessage());
        }
        nested.setAttribute("ATTR_TYPE", t.getClass().getName());

        final String strace = "JUnitTestRunner.getFilteredTrace(t";
        final Text trace = doc.createTextNode(strace);
        nested.appendChild(trace);
    }

    private void formatOutput(final String type, final String output) {
        final Element nested = doc.createElement(type);
        rootElement.appendChild(nested);
        nested.appendChild(doc.createCDATASection(output));
    }

    public void testIgnored(final Test test) {
        formatSkip(test, "JUnitVersionHelper.getIgnoreMessage(test)");
        if (test != null) {
            ignoredTests.put(createDescription(test), test);
        }
    }


    public void formatSkip(final Test test, final String message) {
        if (test != null) {
            endTest(test);
        }

        final Element nested = doc.createElement("skipped");

        if (message != null) {
            nested.setAttribute("message", message);
        }

        Element currentTest;
        if (test != null) {
            currentTest = testElements.get(createDescription(test));
        } else {
            currentTest = rootElement;
        }

        currentTest.appendChild(nested);

    }

    public void testAssumptionFailure(final Test test, final Throwable failure) {
        formatSkip(test, failure.getMessage());
        skippedTests.put(createDescription(test), test);

    }
} // XMLJUnitResultFormatter, no newline on purpose