/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.datetime;

import com.espertech.esper.client.*;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.client.soda.EPStatementObjectModel;
import com.espertech.esper.client.util.DateTime;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportDateTime;
import com.espertech.esper.support.bean.SupportTimeStartEndA;
import com.espertech.esper.support.bean.SupportTimeStartEndB;
import com.espertech.esper.support.bean.lambda.LambdaAssertionUtil;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.timer.SupportDateTimeFieldType;
import com.espertech.esper.util.EventRepresentationEnum;
import junit.framework.TestCase;

import javax.xml.xpath.XPathConstants;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestDTIntervalOps extends TestCase {

    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp() {

        Configuration config = SupportConfigFactory.getConfiguration();
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
        listener = new SupportUpdateListener();

        // register types for testing
        for (SupportDateTimeFieldType fieldType : SupportDateTimeFieldType.values()) {
            registerType(fieldType.name(), fieldType.getType());   // registers types "A_MSEC" and "B_MSEC" as object-array types
        }
    }

    public void tearDown() {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        listener = null;
    }

    public void testCreateSchema() {
        runAssertionCreateSchema(EventRepresentationEnum.DEFAULT);
        runAssertionCreateSchema(EventRepresentationEnum.OBJECTARRAY);
        runAssertionCreateSchema(EventRepresentationEnum.MAP);
    }

    private void runAssertionCreateSchema(EventRepresentationEnum eventRepresentationEnum) {

        String startA = "2002-05-30T09:00:00.000";
        String endA = "2002-05-30T09:00:01.000";
        String startB = "2002-05-30T09:00:00.500";
        String endB = "2002-05-30T09:00:00.700";

        // test Map type Long-type timestamps
        runAssertionCreateSchemaWTypes(eventRepresentationEnum, "long",
                DateTime.parseDefaultMSec(startA), DateTime.parseDefaultMSec(endA),
                DateTime.parseDefaultMSec(startB), DateTime.parseDefaultMSec(endB));

        // test Map type Calendar-type timestamps
        runAssertionCreateSchemaWTypes(eventRepresentationEnum, "java.util.Calendar",
                DateTime.parseDefaultCal(startA), DateTime.parseDefaultCal(endA),
                DateTime.parseDefaultCal(startB), DateTime.parseDefaultCal(endB));

        // test Map type Date-type timestamps
        runAssertionCreateSchemaWTypes(eventRepresentationEnum, "java.util.Date",
                DateTime.parseDefaultDate(startA), DateTime.parseDefaultDate(endA),
                DateTime.parseDefaultDate(startB), DateTime.parseDefaultDate(endB));

        // test Map type LocalDateTime-type timestamps
        runAssertionCreateSchemaWTypes(eventRepresentationEnum, "java.time.LocalDateTime",
                DateTime.parseDefaultLocalDateTime(startA), DateTime.parseDefaultLocalDateTime(endA),
                DateTime.parseDefaultLocalDateTime(startB), DateTime.parseDefaultLocalDateTime(endB));

        // test Map type ZonedDateTime-type timestamps
        runAssertionCreateSchemaWTypes(eventRepresentationEnum, "java.time.ZonedDateTime",
                DateTime.parseDefaultZonedDateTime(startA), DateTime.parseDefaultZonedDateTime(endA),
                DateTime.parseDefaultZonedDateTime(startB), DateTime.parseDefaultZonedDateTime(endB));

        // test Bean-type Date-type timestamps
        String epl = eventRepresentationEnum.getAnnotationText() + " create schema SupportBean as " + SupportBean.class.getName() + " starttimestamp longPrimitive endtimestamp longBoxed";
        epService.getEPAdministrator().createEPL(epl);

        EPStatement stmt = epService.getEPAdministrator().createEPL("select a.get('month') as val0 from SupportBean a");
        stmt.addListener(listener);

        SupportBean theEvent = new SupportBean();
        theEvent.setLongPrimitive(DateTime.parseDefaultMSec(startA));
        epService.getEPRuntime().sendEvent(theEvent);
        assertEquals(4, listener.assertOneGetNewAndReset().get("val0"));

        EPStatementObjectModel model = epService.getEPAdministrator().compileEPL(epl);
        assertEquals(epl.trim(), model.toEPL());
        stmt = epService.getEPAdministrator().create(model);
        assertEquals(epl.trim(), stmt.getText());

        // try XML
        ConfigurationEventTypeXMLDOM desc = new ConfigurationEventTypeXMLDOM();
        desc.setRootElementName("ABC");
        desc.setStartTimestampPropertyName("mystarttimestamp");
        desc.setEndTimestampPropertyName("myendtimestamp");
        desc.addXPathProperty("mystarttimestamp", "/test/prop", XPathConstants.NUMBER);
        try {
            epService.getEPAdministrator().getConfiguration().addEventType("TypeXML", desc);
            fail();
        }
        catch (ConfigurationException ex) {
            assertEquals("Declared start timestamp property 'mystarttimestamp' is expected to return a Date, Calendar or long-typed value but returns 'java.lang.Double'", ex.getMessage());
        }

        epService.initialize();
    }

    public void testCalendarOps() {
        String seedTime = "2002-05-30T09:00:00.000"; // seed is time for B

        Object[][] expected = {
                {"2999-01-01T09:00:00.001", 0, true},       // sending in A
        };
        assertExpression(seedTime, 0, "a.withDate(2001, 1, 1).before(b)", expected, null);

        expected = new Object[][] {
                {"2999-01-01T10:00:00.001", 0, false},
                {"2999-01-01T08:00:00.001", 0, true},
        };
        assertExpression(seedTime, 0, "a.withDate(2001, 1, 1).before(b.withDate(2001, 1, 1))", expected, null);

        // Test end-timestamp preserved when using calendar ops
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 2000, false},
        };
        assertExpression(seedTime, 0, "a.before(b)", expected, null);
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 2000, false},
        };
        assertExpression(seedTime, 0, "a.withTime(8, 59, 59, 0).before(b)", expected, null);

        // Test end-timestamp preserved when using calendar ops
        expected = new Object[][] {
                {"2002-05-30T09:00:01.000", 0, false},
                {"2002-05-30T09:00:01.001", 0, true},
        };
        assertExpression(seedTime, 1000, "a.after(b)", expected, null);

        // NOT YET SUPPORTED (a documented limitation of datetime methods)
        // assertExpression(seedTime, 0, "a.after(b.withTime(9, 0, 0, 0))", expected, null);   // the "b.withTime(...) must retain the end-timestamp correctness (a documented limitation)
    }

    public void testInvalid() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean", SupportBean.class.getName());
        registerBeanType();

        // wrong 1st parameter - string
        tryInvalid("select a.before('x') from A as a",
                   "Error starting statement: Failed to validate select-clause expression 'a.before('x')': Failed to resolve enumeration method, date-time method or mapped property 'a.before('x')': For date-time method 'before' the first parameter expression returns 'class java.lang.String', however requires a Date, Calendar, Long-type return value or event (with timestamp) [select a.before('x') from A as a]");

        // wrong 1st parameter - event not defined with timestamp expression
        tryInvalid("select a.before(b) from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before(b)': For date-time method 'before' the first parameter is event type 'SupportBean', however no timestamp property has been defined for this event type [select a.before(b) from A#lastevent as a, SupportBean#lastevent as b]");

        // wrong 1st parameter - boolean
        tryInvalid("select a.before(true) from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before(true)': For date-time method 'before' the first parameter expression returns 'class java.lang.Boolean', however requires a Date, Calendar, Long-type return value or event (with timestamp) [select a.before(true) from A#lastevent as a, SupportBean#lastevent as b]");

        // wrong zero parameters
        tryInvalid("select a.before() from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before()': Parameters mismatch for date-time method 'before', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing interval start value, or an expression providing timestamp or timestamped-event and an expression providing interval start value and an expression providing interval finishes value, but receives no parameters [select a.before() from A#lastevent as a, SupportBean#lastevent as b]");

        // wrong target
        tryInvalid("select theString.before(a) from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'theString.before(a)': Date-time enumeration method 'before' requires either a Calendar, Date, long, LocalDateTime or ZonedDateTime value as input or events of an event type that declares a timestamp property but received java.lang.String [select theString.before(a) from A#lastevent as a, SupportBean#lastevent as b]");
        tryInvalid("select b.before(a) from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'b.before(a)': Date-time enumeration method 'before' requires either a Calendar, Date, long, LocalDateTime or ZonedDateTime value as input or events of an event type that declares a timestamp property [select b.before(a) from A#lastevent as a, SupportBean#lastevent as b]");
        tryInvalid("select a.get('month').before(a) from A#lastevent as a, SupportBean#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.get(\"month\").before(a)': Invalid input for date-time method 'before' [select a.get('month').before(a) from A#lastevent as a, SupportBean#lastevent as b]");

        // test before/after
        tryInvalid("select a.before(b, 'abc') from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before(b,\"abc\")': Error validating date-time method 'before', expected a time-period expression or a numeric-type result for expression parameter 1 but received java.lang.String [select a.before(b, 'abc') from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.before(b, 1, 'def') from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before(b,1,\"def\")': Error validating date-time method 'before', expected a time-period expression or a numeric-type result for expression parameter 2 but received java.lang.String [select a.before(b, 1, 'def') from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.before(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.before(b,1,2,3)': Parameters mismatch for date-time method 'before', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing interval start value, or an expression providing timestamp or timestamped-event and an expression providing interval start value and an expression providing interval finishes value, but receives 4 expressions [select a.before(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b]");

        // test coincides
        tryInvalid("select a.coincides(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.coincides(b,1,2,3)': Parameters mismatch for date-time method 'coincides', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing threshold for start and end value, or an expression providing timestamp or timestamped-event and an expression providing threshold for start value and an expression providing threshold for end value, but receives 4 expressions [select a.coincides(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.coincides(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.coincides(b,-1)': The coincides date-time method does not allow negative start and end values [select a.coincides(b, -1) from A#lastevent as a, B#lastevent as b]");

        // test during+interval
        tryInvalid("select a.during(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.during(b,1,2,3)': Parameters mismatch for date-time method 'during', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing maximum distance interval both start and end, or an expression providing timestamp or timestamped-event and an expression providing minimum distance interval both start and end and an expression providing maximum distance interval both start and end, or an expression providing timestamp or timestamped-event and an expression providing minimum distance start and an expression providing maximum distance start and an expression providing minimum distance end and an expression providing maximum distance end, but receives 4 expressions [select a.during(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b]");

        // test finishes+finished-by
        tryInvalid("select a.finishes(b, 1, 2) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.finishes(b,1,2)': Parameters mismatch for date-time method 'finishes', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing maximum distance between end timestamps, but receives 3 expressions [select a.finishes(b, 1, 2) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.finishes(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.finishes(b,-1)': The finishes date-time method does not allow negative threshold value [select a.finishes(b, -1) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.finishedby(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.finishedby(b,-1)': The finishedby date-time method does not allow negative threshold value [select a.finishedby(b, -1) from A#lastevent as a, B#lastevent as b]");

        // test meets+met-by
        tryInvalid("select a.meets(b, 1, 2) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.meets(b,1,2)': Parameters mismatch for date-time method 'meets', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing maximum distance between start and end timestamps, but receives 3 expressions [select a.meets(b, 1, 2) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.meets(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.meets(b,-1)': The meets date-time method does not allow negative threshold value [select a.meets(b, -1) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.metBy(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.metBy(b,-1)': The metBy date-time method does not allow negative threshold value [select a.metBy(b, -1) from A#lastevent as a, B#lastevent as b]");

        // test overlaps+overlapped-by
        tryInvalid("select a.overlaps(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.overlaps(b,1,2,3)': Parameters mismatch for date-time method 'overlaps', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing maximum distance interval both start and end, or an expression providing timestamp or timestamped-event and an expression providing minimum distance interval both start and end and an expression providing maximum distance interval both start and end, but receives 4 expressions [select a.overlaps(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b]");

        // test start/startedby
        tryInvalid("select a.starts(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.starts(b,1,2,3)': Parameters mismatch for date-time method 'starts', the method has multiple footprints accepting an expression providing timestamp or timestamped-event, or an expression providing timestamp or timestamped-event and an expression providing maximum distance between start timestamps, but receives 4 expressions [select a.starts(b, 1, 2, 3) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.starts(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.starts(b,-1)': The starts date-time method does not allow negative threshold value [select a.starts(b, -1) from A#lastevent as a, B#lastevent as b]");
        tryInvalid("select a.startedBy(b, -1) from A#lastevent as a, B#lastevent as b",
                   "Error starting statement: Failed to validate select-clause expression 'a.startedBy(b,-1)': The startedBy date-time method does not allow negative threshold value [select a.startedBy(b, -1) from A#lastevent as a, B#lastevent as b]");
    }

    public void testInvalidConfig() {
        ConfigurationEventTypeLegacy configBean = new ConfigurationEventTypeLegacy();

        configBean.setStartTimestampPropertyName(null);
        configBean.setEndTimestampPropertyName("caldate");
        tryInvalidConfig(SupportDateTime.class, configBean, "Declared end timestamp property requires that a start timestamp property is also declared");

        configBean.setStartTimestampPropertyName("xyz");
        configBean.setEndTimestampPropertyName(null);
        tryInvalidConfig(SupportBean.class, configBean, "Declared start timestamp property name 'xyz' was not found");

        configBean.setStartTimestampPropertyName("longPrimitive");
        configBean.setEndTimestampPropertyName("xyz");
        tryInvalidConfig(SupportBean.class, configBean, "Declared end timestamp property name 'xyz' was not found");

        configBean.setEndTimestampPropertyName(null);
        configBean.setStartTimestampPropertyName("theString");
        tryInvalidConfig(SupportBean.class, configBean, "Declared start timestamp property 'theString' is expected to return a Date, Calendar or long-typed value but returns 'java.lang.String'");

        configBean.setStartTimestampPropertyName("longPrimitive");
        configBean.setEndTimestampPropertyName("theString");
        tryInvalidConfig(SupportBean.class, configBean, "Declared end timestamp property 'theString' is expected to return a Date, Calendar or long-typed value but returns 'java.lang.String'");

        configBean.setStartTimestampPropertyName("msecdate");
        configBean.setEndTimestampPropertyName("caldate");
        tryInvalidConfig(SupportDateTime.class, configBean, "Declared end timestamp property 'caldate' is expected to have the same property type as the start-timestamp property 'msecdate'");
    }

    public void testBeforeInSelectClause() {

        registerBeanType();

        String[] fields = "c0,c1".split(",");
        String epl =
                "select " +
                "a.msecdateStart.before(b.msecdateStart) as c0," +
                "a.before(b) as c1 " +
                " from A#lastevent as a, " +
                "      B#lastevent as b";
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);
        LambdaAssertionUtil.assertTypesAllSame(stmt.getEventType(), fields, Boolean.class);

        epService.getEPRuntime().sendEvent(SupportTimeStartEndB.make("B1", "2002-05-30T09:00:00.000", 0));

        epService.getEPRuntime().sendEvent(SupportTimeStartEndA.make("A1", "2002-05-30T08:59:59.000", 0));
        EPAssertionUtil.assertPropsAllValuesSame(listener.assertOneGetNewAndReset(), fields, true);

        epService.getEPRuntime().sendEvent(SupportTimeStartEndA.make("A2", "2002-05-30T08:59:59.950", 0));
        EPAssertionUtil.assertPropsAllValuesSame(listener.assertOneGetNewAndReset(), fields, true);
    }

    public void testBeforeWhereClauseWithBean() {

        registerBeanType();

        Validator expectedValidator = new BeforeValidator(1L, Long.MAX_VALUE);
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, true},
                {"2002-05-30T08:59:59.999", 0, true},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };

        String[] expressions = new String[]{
                "a.before(b)",
                "a.before(b, 1 millisecond)",
                "a.before(b, 1 millisecond, 1000000000L)",
                "a.msecdateStart.before(b)",
                "a.utildateStart.before(b)",
                "a.caldateStart.before(b)",
                "a.before(b.msecdateStart)",
                "a.before(b.utildateStart)",
                "a.before(b.caldateStart)",
                "a.msecdateStart.before(b.msecdateStart)",
                "a.msecdateStart.before(b.msecdateStart)",
                "a.utildateStart.before(b.utildateStart)",
                "a.caldateStart.before(b.caldateStart)",
                "a.utildateStart.before(b.caldateStart)",
                "a.utildateStart.before(b.msecdateStart)",
                "a.caldateStart.before(b.utildateStart)",
                "a.caldateStart.before(b.msecdateStart)",
                "a.ldtStart.before(b.ldtStart)",
                "a.zdtStart.before(b.zdtStart)"
        };
        String seedTime = "2002-05-30T09:00:00.000";
        for (String expression : expressions) {
            assertExpressionBean(seedTime, 0, expression, expected, expectedValidator);
        }
    }

    public void testBeforeWhereClause() {

        String seedTime = "2002-05-30T09:00:00.000";
        BeforeValidator expectedValidator = new BeforeValidator(1L, Long.MAX_VALUE);
        Object[][] expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, true},
                {"2002-05-30T08:59:59.000", 999, true},
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T08:59:59.000", 1001, false},
                {"2002-05-30T08:59:59.999", 0, true},
                {"2002-05-30T08:59:59.999", 1, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        assertExpression(seedTime, 0, "a.before(b)", expected, expectedValidator);
        assertExpression(seedTime, 100000, "a.before(b)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, true},
                {"2002-05-30T08:59:59.899", 0, true},
                {"2002-05-30T08:59:59.900", 0, true},
                {"2002-05-30T08:59:59.901", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        expectedValidator = new BeforeValidator(100L, Long.MAX_VALUE);
        assertExpression(seedTime, 0, "a.before(b, 100 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 100000, "a.before(b, 100 milliseconds)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.499", 0, false},
                {"2002-05-30T08:59:59.499", 1, true},
                {"2002-05-30T08:59:59.500", 0, true},
                {"2002-05-30T08:59:59.500", 1, true},
                {"2002-05-30T08:59:59.500", 400, true},
                {"2002-05-30T08:59:59.500", 401, false},
                {"2002-05-30T08:59:59.899", 0, true},
                {"2002-05-30T08:59:59.899", 2, false},
                {"2002-05-30T08:59:59.900", 0, true},
                {"2002-05-30T08:59:59.900", 1, false},
                {"2002-05-30T08:59:59.901", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        expectedValidator = new BeforeValidator(100L, 500L);
        assertExpression(seedTime, 0, "a.before(b, 100 milliseconds, 500 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 100000, "a.before(b, 100 milliseconds, 500 milliseconds)", expected, expectedValidator);

        // test expression params
        epService.getEPAdministrator().createEPL("create variable long V_START = 100");
        epService.getEPAdministrator().createEPL("create variable long V_END = 500");
        assertExpression(seedTime, 0, "a.before(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        epService.getEPRuntime().setVariableValue("V_START", 200);
        epService.getEPRuntime().setVariableValue("V_END", 800);
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.199", 0, false},
                {"2002-05-30T08:59:59.199", 1, true},
                {"2002-05-30T08:59:59.200", 0, true},
                {"2002-05-30T08:59:59.800", 0, true},
                {"2002-05-30T08:59:59.801", 0, false},
        };
        expectedValidator = new BeforeValidator(200L, 800L);
        assertExpression(seedTime, 0, "a.before(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        // test negative and reversed max and min
        expected = new Object[][] {
                {"2002-05-30T08:59:59.500", 0, false},
                {"2002-05-30T09:00:00.990", 0, false},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.500", 0, true},
                {"2002-05-30T09:00:00.501", 0, false},
        };
        expectedValidator = new BeforeValidator(-500L, -100L);
        assertExpression(seedTime, 0, "a.before(b, -100 milliseconds, -500 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.before(b, -500 milliseconds, -100 milliseconds)", expected, expectedValidator);

        // test month logic
        seedTime = "2002-03-01T09:00:00.000";
        expected = new Object[][] {
                {"2002-02-01T09:00:00.000", 0, true},
                {"2002-02-01T09:00:00.001", 0, false}
        };
        expectedValidator = new BeforeValidator(getMillisecForDays(28), Long.MAX_VALUE);
        assertExpression(seedTime, 100, "a.before(b, 1 month)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-01-01T08:59:59.999", 0, false},
                {"2002-01-01T09:00:00.000", 0, true},
                {"2002-01-11T09:00:00.000", 0, true},
                {"2002-02-01T09:00:00.000", 0, true},
                {"2002-02-01T09:00:00.001", 0, false}
        };
        expectedValidator = new BeforeValidator(getMillisecForDays(28), getMillisecForDays(28+31));
        assertExpression(seedTime, 100, "a.before(b, 1 month, 2 month)", expected, expectedValidator);
    }

    public void testAfterWhereClause() {

        Validator expectedValidator = new AfterValidator(1L, Long.MAX_VALUE);
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, true},
        };
        assertExpression(seedTime, 0, "a.after(b)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b, 1 millisecond)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b, 1 millisecond, 1000000000L)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b, 1000000000L, 1 millisecond)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.startTS.after(b)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b.startTS)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.002", 0, true},
        };
        assertExpression(seedTime, 1, "a.after(b)", expected, expectedValidator);
        assertExpression(seedTime, 1, "a.after(b, 1 millisecond, 1000000000L)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.099", 0, false},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.101", 0, true},
        };
        expectedValidator = new AfterValidator(100L, Long.MAX_VALUE);
        assertExpression(seedTime, 0, "a.after(b, 100 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b, 100 milliseconds, 1000000000L)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.099", 0, false},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.500", 0, true},
                {"2002-05-30T09:00:00.501", 0, false},
        };
        expectedValidator = new AfterValidator(100L, 500L);
        assertExpression(seedTime, 0, "a.after(b, 100 milliseconds, 500 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.after(b, 100 milliseconds, 500 milliseconds)", expected, expectedValidator);

        // test expression params
        epService.getEPAdministrator().createEPL("create variable long V_START = 100");
        epService.getEPAdministrator().createEPL("create variable long V_END = 500");
        assertExpression(seedTime, 0, "a.after(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        epService.getEPRuntime().setVariableValue("V_START", 200);
        epService.getEPRuntime().setVariableValue("V_END", 800);
        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.199", 0, false},
                {"2002-05-30T09:00:00.200", 0, true},
                {"2002-05-30T09:00:00.800", 0, true},
                {"2002-05-30T09:00:00.801", 0, false},
        };
        expectedValidator = new AfterValidator(200L, 800L);
        assertExpression(seedTime, 0, "a.after(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        // test negative distances
        expected = new Object[][] {
                {"2002-05-30T08:59:59.599", 0, false},
                {"2002-05-30T08:59:59.600", 0, true},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        expectedValidator = new AfterValidator(-500L, -100L);
        assertExpression(seedTime, 100, "a.after(b, -100 milliseconds, -500 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 100, "a.after(b, -500 milliseconds, -100 milliseconds)", expected, expectedValidator);

        // test month logic
        seedTime = "2002-02-01T09:00:00.000";
        expected = new Object[][] {
                {"2002-03-01T09:00:00.099", 0, false},
                {"2002-03-01T09:00:00.100", 0, true}
        };
        expectedValidator = new AfterValidator(getMillisecForDays(28), Long.MAX_VALUE);
        assertExpression(seedTime, 100, "a.after(b, 1 month)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-03-01T09:00:00.099", 0, false},
                {"2002-03-01T09:00:00.100", 0, true},
                {"2002-04-01T09:00:00.100", 0, true},
                {"2002-04-01T09:00:00.101", 0, false}
        };
        assertExpression(seedTime, 100, "a.after(b, 1 month, 2 month)", expected, null);
    }

    public void testCoincidesWhereClause() {

        Validator expectedValidator = new CoincidesValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        assertExpression(seedTime, 0, "a.coincides(b)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.coincides(b, 0 millisecond)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.coincides(b, 0, 0)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.startTS.coincides(b)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.coincides(b.startTS)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 1, true},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 1, false},
        };
        assertExpression(seedTime, 1, "a.coincides(b)", expected, expectedValidator);
        assertExpression(seedTime, 1, "a.coincides(b, 0, 0)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.899", 0, false},
                {"2002-05-30T08:59:59.900", 0, true},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 50, true},
                {"2002-05-30T09:00:00.000", 100, true},
                {"2002-05-30T09:00:00.000", 101, false},
                {"2002-05-30T09:00:00.099", 0, true},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.101", 0, false},
        };
        expectedValidator = new CoincidesValidator(100L);
        assertExpression(seedTime, 0, "a.coincides(b, 100 milliseconds)", expected, expectedValidator);
        assertExpression(seedTime, 0, "a.coincides(b, 100 milliseconds, 0.1 sec)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.799", 0, false},
                {"2002-05-30T08:59:59.800", 0, true},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.099", 0, true},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.200", 0, true},
                {"2002-05-30T09:00:00.201", 0, false},
        };
        expectedValidator = new CoincidesValidator(200L, 500L);
        assertExpression(seedTime, 0, "a.coincides(b, 200 milliseconds, 500 milliseconds)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.799", 0, false},
                {"2002-05-30T08:59:59.799", 200, false},
                {"2002-05-30T08:59:59.799", 201, false},
                {"2002-05-30T08:59:59.800", 0, false},
                {"2002-05-30T08:59:59.800", 199, false},
                {"2002-05-30T08:59:59.800", 200, true},
                {"2002-05-30T08:59:59.800", 300, true},
                {"2002-05-30T08:59:59.800", 301, false},
                {"2002-05-30T09:00:00.050", 0, true},
                {"2002-05-30T09:00:00.099", 0, true},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.101", 0, false},
        };
        expectedValidator = new CoincidesValidator(200L, 50L);
        assertExpression(seedTime, 50, "a.coincides(b, 200 milliseconds, 50 milliseconds)", expected, expectedValidator);

        // test expression params
        epService.getEPAdministrator().createEPL("create variable long V_START = 200");
        epService.getEPAdministrator().createEPL("create variable long V_END = 50");
        assertExpression(seedTime, 50, "a.coincides(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        epService.getEPRuntime().setVariableValue("V_START", 200);
        epService.getEPRuntime().setVariableValue("V_END", 70);
        expected = new Object[][] {
                {"2002-05-30T08:59:59.800", 0, false},
                {"2002-05-30T08:59:59.800", 179, false},
                {"2002-05-30T08:59:59.800", 180, true},
                {"2002-05-30T08:59:59.800", 200, true},
                {"2002-05-30T08:59:59.800", 320, true},
                {"2002-05-30T08:59:59.800", 321, false},
        };
        expectedValidator = new CoincidesValidator(200L, 70L);
        assertExpression(seedTime, 50, "a.coincides(b, V_START milliseconds, V_END milliseconds)", expected, expectedValidator);

        // test month logic
        seedTime = "2002-02-01T09:00:00.000";    // lasts to "2002-04-01T09:00:00.000" (28+31 days)
        expected = new Object[][] {
                {"2002-02-15T09:00:00.099", getMillisecForDays(28+14), true},
                {"2002-01-01T08:00:00.000", getMillisecForDays(28+30), false}
        };
        expectedValidator = new CoincidesValidator(getMillisecForDays(28));
        assertExpression(seedTime, getMillisecForDays(28+31), "a.coincides(b, 1 month)", expected, expectedValidator);
    }

    public void testDuringWhereClause() {

        Validator expectedValidator = new DuringValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, true},
                {"2002-05-30T09:00:00.001", 98, true},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.099", 0, true},
                {"2002-05-30T09:00:00.099", 1, false},
                {"2002-05-30T09:00:00.100", 0, false},
        };
        assertExpression(seedTime, 100, "a.during(b)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 1, false},
        };
        assertExpression(seedTime, 0, "a.during(b)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.001", 0, true},
                {"2002-05-30T09:00:00.001", 2000000, true},
        };
        assertExpression(seedTime, 100, "a.startTS.during(b)", expected, null);    // want to use null-validator here

        // test 1-parameter footprint
        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 83, false},
                {"2002-05-30T09:00:00.001", 84, true},
                {"2002-05-30T09:00:00.001", 98, true},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.015", 69, false},
                {"2002-05-30T09:00:00.015", 70, true},
                {"2002-05-30T09:00:00.015", 84, true},
                {"2002-05-30T09:00:00.015", 85, false},
                {"2002-05-30T09:00:00.016", 80, false},
                {"2002-05-30T09:00:00.099", 0, false},
        };
        expectedValidator = new DuringValidator(15L);
        assertExpression(seedTime, 100, "a.during(b, 15 milliseconds)", expected, expectedValidator);

        // test 2-parameter footprint
        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 78, false},
                {"2002-05-30T09:00:00.001", 79, false},
                {"2002-05-30T09:00:00.004", 85, false},
                {"2002-05-30T09:00:00.005", 74, false},
                {"2002-05-30T09:00:00.005", 75, true},
                {"2002-05-30T09:00:00.005", 90, true},
                {"2002-05-30T09:00:00.005", 91, false},
                {"2002-05-30T09:00:00.006", 83, true},
                {"2002-05-30T09:00:00.020", 76, false},
                {"2002-05-30T09:00:00.020", 75, true},
                {"2002-05-30T09:00:00.020", 60, true},
                {"2002-05-30T09:00:00.020", 59, false},
                {"2002-05-30T09:00:00.021", 68, false},
                {"2002-05-30T09:00:00.099", 0, false},
        };
        expectedValidator = new DuringValidator(5L, 20L);
        assertExpression(seedTime, 100, "a.during(b, 5 milliseconds, 20 milliseconds)", expected, expectedValidator);

        // test 4-parameter footprint
        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.004", 85, false},
                {"2002-05-30T09:00:00.005", 64, false},
                {"2002-05-30T09:00:00.005", 65, true},
                {"2002-05-30T09:00:00.005", 85, true},
                {"2002-05-30T09:00:00.005", 86, false},
                {"2002-05-30T09:00:00.020", 49, false},
                {"2002-05-30T09:00:00.020", 50, true},
                {"2002-05-30T09:00:00.020", 70, true},
                {"2002-05-30T09:00:00.020", 71, false},
                {"2002-05-30T09:00:00.021", 55, false},
        };
        expectedValidator = new DuringValidator(5L, 20L, 10L, 30L);
        assertExpression(seedTime, 100, "a.during(b, 5 milliseconds, 20 milliseconds, 10 milliseconds, 30 milliseconds)", expected, expectedValidator);
    }

    public void testFinishesWhereClause() {

        Validator expectedValidator = new FinishesValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 98, false},
                {"2002-05-30T09:00:00.001", 99, true},
                {"2002-05-30T09:00:00.001", 100, false},
                {"2002-05-30T09:00:00.050", 50, true},
                {"2002-05-30T09:00:00.099", 0, false},
                {"2002-05-30T09:00:00.099", 1, true},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.101", 0, false},
        };
        assertExpression(seedTime, 100, "a.finishes(b)", expected, expectedValidator);
        assertExpression(seedTime, 100, "a.finishes(b, 0)", expected, expectedValidator);
        assertExpression(seedTime, 100, "a.finishes(b, 0 milliseconds)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 99, false},
                {"2002-05-30T09:00:00.001", 93, false},
                {"2002-05-30T09:00:00.001", 94, true},
                {"2002-05-30T09:00:00.001", 100, true},
                {"2002-05-30T09:00:00.001", 104, true},
                {"2002-05-30T09:00:00.001", 105, false},
                {"2002-05-30T09:00:00.050", 50, true},
                {"2002-05-30T09:00:00.104", 0, true},
                {"2002-05-30T09:00:00.104", 1, true},
                {"2002-05-30T09:00:00.105", 0, true},
                {"2002-05-30T09:00:00.105", 1, false},
        };
        expectedValidator = new FinishesValidator(5L);
        assertExpression(seedTime, 100, "a.finishes(b, 5 milliseconds)", expected, expectedValidator);
    }

    public void testFinishedByWhereClause() {

        Validator expectedValidator = new FinishedByValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 1099, false},
                {"2002-05-30T08:59:59.000", 1100, true},
                {"2002-05-30T08:59:59.000", 1101, false},
                {"2002-05-30T08:59:59.999", 100, false},
                {"2002-05-30T08:59:59.999", 101, true},
                {"2002-05-30T08:59:59.999", 102, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 50, false},
                {"2002-05-30T09:00:00.000", 100, false},
        };
        assertExpression(seedTime, 100, "a.finishedBy(b)", expected, expectedValidator);
        assertExpression(seedTime, 100, "a.finishedBy(b, 0)", expected, expectedValidator);
        assertExpression(seedTime, 100, "a.finishedBy(b, 0 milliseconds)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 1094, false},
                {"2002-05-30T08:59:59.000", 1095, true},
                {"2002-05-30T08:59:59.000", 1105, true},
                {"2002-05-30T08:59:59.000", 1106, false},
                {"2002-05-30T08:59:59.999", 95, false},
                {"2002-05-30T08:59:59.999", 96, true},
                {"2002-05-30T08:59:59.999", 106, true},
                {"2002-05-30T08:59:59.999", 107, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 95, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.000", 105, false},
        };
        expectedValidator = new FinishedByValidator(5L);
        assertExpression(seedTime, 100, "a.finishedBy(b, 5 milliseconds)", expected, expectedValidator);
    }

    public void testIncludesByWhereClause() {

        Validator expectedValidator = new IncludesValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.000", 1101, true},
                {"2002-05-30T08:59:59.000", 3000, true},
                {"2002-05-30T08:59:59.999", 101, false},
                {"2002-05-30T08:59:59.999", 102, true},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 50, false},
                {"2002-05-30T09:00:00.000", 102, false},
        };
        assertExpression(seedTime, 100, "a.includes(b)", expected, expectedValidator);

        // test 1-parameter form
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.000", 1105, false},
                {"2002-05-30T08:59:59.994", 106, false},
                {"2002-05-30T08:59:59.994", 110, false},
                {"2002-05-30T08:59:59.995", 105, false},
                {"2002-05-30T08:59:59.995", 106, true},
                {"2002-05-30T08:59:59.995", 110, true},
                {"2002-05-30T08:59:59.995", 111, false},
                {"2002-05-30T08:59:59.999", 101, false},
                {"2002-05-30T08:59:59.999", 102, true},
                {"2002-05-30T08:59:59.999", 106, true},
                {"2002-05-30T08:59:59.999", 107, false},
                {"2002-05-30T09:00:00.000", 105, false},
                {"2002-05-30T09:00:00.000", 106, false},
        };
        expectedValidator = new IncludesValidator(5L);
        assertExpression(seedTime, 100, "a.includes(b, 5 milliseconds)", expected, expectedValidator);

        // test 2-parameter form
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.000", 1105, false},
                {"2002-05-30T08:59:59.979", 130, false},
                {"2002-05-30T08:59:59.980", 124, false},
                {"2002-05-30T08:59:59.980", 125, true},
                {"2002-05-30T08:59:59.980", 140, true},
                {"2002-05-30T08:59:59.980", 141, false},
                {"2002-05-30T08:59:59.995", 109, false},
                {"2002-05-30T08:59:59.995", 110, true},
                {"2002-05-30T08:59:59.995", 125, true},
                {"2002-05-30T08:59:59.995", 126, false},
                {"2002-05-30T08:59:59.996", 112, false},
        };
        expectedValidator = new IncludesValidator(5L, 20L);
        assertExpression(seedTime, 100, "a.includes(b, 5 milliseconds, 20 milliseconds)", expected, expectedValidator);

        // test 4-parameter form
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.000", 1105, false},
                {"2002-05-30T08:59:59.979", 150, false},
                {"2002-05-30T08:59:59.980", 129, false},
                {"2002-05-30T08:59:59.980", 130, true},
                {"2002-05-30T08:59:59.980", 150, true},
                {"2002-05-30T08:59:59.980", 151, false},
                {"2002-05-30T08:59:59.995", 114, false},
                {"2002-05-30T08:59:59.995", 115, true},
                {"2002-05-30T08:59:59.995", 135, true},
                {"2002-05-30T08:59:59.995", 136, false},
                {"2002-05-30T08:59:59.996", 124, false},
        };
        expectedValidator = new IncludesValidator(5L, 20L, 10L, 30L);
        assertExpression(seedTime, 100, "a.includes(b, 5 milliseconds, 20 milliseconds, 10 milliseconds, 30 milliseconds)", expected, expectedValidator);
    }

    public void testMeetsWhereClause() {

        Validator expectedValidator = new MeetsValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 1000, true},
                {"2002-05-30T08:59:59.000", 1001, false},
                {"2002-05-30T08:59:59.998", 1, false},
                {"2002-05-30T08:59:59.999", 1, true},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 1, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        assertExpression(seedTime, 0, "a.meets(b)", expected, expectedValidator);

        // test 1-parameter form
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 0, false},
                {"2002-05-30T08:59:59.000", 994, false},
                {"2002-05-30T08:59:59.000", 995, true},
                {"2002-05-30T08:59:59.000", 1005, true},
                {"2002-05-30T08:59:59.000", 1006, false},
                {"2002-05-30T08:59:59.994", 0, false},
                {"2002-05-30T08:59:59.994", 1, true},
                {"2002-05-30T08:59:59.995", 0, true},
                {"2002-05-30T08:59:59.999", 0, true},
                {"2002-05-30T08:59:59.999", 1, true},
                {"2002-05-30T08:59:59.999", 6, true},
                {"2002-05-30T08:59:59.999", 7, false},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 1, true},
                {"2002-05-30T09:00:00.000", 5, true},
                {"2002-05-30T09:00:00.005", 0, true},
                {"2002-05-30T09:00:00.005", 1, false},
        };
        expectedValidator = new MeetsValidator(5L);
        assertExpression(seedTime, 0, "a.meets(b, 5 milliseconds)", expected, expectedValidator);
    }

    public void testMetByWhereClause() {

        Validator expectedValidator = new MetByValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T09:00:00.990", 0, false},
                {"2002-05-30T09:00:00.100", 0, true},
                {"2002-05-30T09:00:00.100", 500, true},
                {"2002-05-30T09:00:00.101", 0, false},
        };
        assertExpression(seedTime, 100, "a.metBy(b)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.999", 1, false},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 1, true},
        };
        assertExpression(seedTime, 0, "a.metBy(b)", expected, expectedValidator);

        // test 1-parameter form
        expected = new Object[][] {
                {"2002-05-30T08:59:59.994", 0, false},
                {"2002-05-30T08:59:59.994", 5, false},
                {"2002-05-30T08:59:59.995", 0, true},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 20, true},
                {"2002-05-30T09:00:00.005", 0, true},
                {"2002-05-30T09:00:00.005", 1000, true},
                {"2002-05-30T09:00:00.006", 0, false},
        };
        expectedValidator = new MetByValidator(5L);
        assertExpression(seedTime, 0, "a.metBy(b, 5 milliseconds)", expected, expectedValidator);

        expected = new Object[][] {
                {"2002-05-30T08:59:59.994", 0, false},
                {"2002-05-30T08:59:59.994", 5, false},
                {"2002-05-30T08:59:59.995", 0, false},
                {"2002-05-30T09:00:00.094", 0, false},
                {"2002-05-30T09:00:00.095", 0, true},
                {"2002-05-30T09:00:00.105", 0, true},
                {"2002-05-30T09:00:00.105", 5000, true},
                {"2002-05-30T09:00:00.106", 0, false},
        };
        expectedValidator = new MetByValidator(5L);
        assertExpression(seedTime, 100, "a.metBy(b, 5 milliseconds)", expected, expectedValidator);
    }

    public void testOverlapsWhereClause() {

        Validator expectedValidator = new OverlapsValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T08:59:59.000", 1001, true},
                {"2002-05-30T08:59:59.000", 1050, true},
                {"2002-05-30T08:59:59.000", 1099, true},
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.999", 1, false},
                {"2002-05-30T08:59:59.999", 2, true},
                {"2002-05-30T08:59:59.999", 100, true},
                {"2002-05-30T08:59:59.999", 101, false},
                {"2002-05-30T09:00:00.000", 0, false},
        };
        assertExpression(seedTime, 100, "a.overlaps(b)", expected, expectedValidator);

        // test 1-parameter form (overlap by not more then X msec)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T08:59:59.000", 1001, true},
                {"2002-05-30T08:59:59.000", 1005, true},
                {"2002-05-30T08:59:59.000", 1006, false},
                {"2002-05-30T08:59:59.000", 1100, false},
                {"2002-05-30T08:59:59.999", 1, false},
                {"2002-05-30T08:59:59.999", 2, true},
                {"2002-05-30T08:59:59.999", 6, true},
                {"2002-05-30T08:59:59.999", 7, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 5, false},
        };
        expectedValidator = new OverlapsValidator(5L);
        assertExpression(seedTime, 100, "a.overlaps(b, 5 milliseconds)", expected, expectedValidator);

        // test 2-parameter form (overlap by min X and not more then Y msec)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 1004, false},
                {"2002-05-30T08:59:59.000", 1005, true},
                {"2002-05-30T08:59:59.000", 1010, true},
                {"2002-05-30T08:59:59.000", 1011, false},
                {"2002-05-30T08:59:59.999", 5, false},
                {"2002-05-30T08:59:59.999", 6, true},
                {"2002-05-30T08:59:59.999", 11, true},
                {"2002-05-30T08:59:59.999", 12, false},
                {"2002-05-30T08:59:59.999", 12, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 5, false},
        };
        expectedValidator = new OverlapsValidator(5L, 10L);
        assertExpression(seedTime, 100, "a.overlaps(b, 5 milliseconds, 10 milliseconds)", expected, expectedValidator);
    }

    public void testOverlappedByWhereClause() {

        Validator expectedValidator = new OverlappedByValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 1, false},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.001", 100, true},
                {"2002-05-30T09:00:00.099", 1, false},
                {"2002-05-30T09:00:00.099", 2, true},
                {"2002-05-30T09:00:00.100", 0, false},
                {"2002-05-30T09:00:00.100", 1, false},
        };
        assertExpression(seedTime, 100, "a.overlappedBy(b)", expected, expectedValidator);

        // test 1-parameter form (overlap by not more then X msec)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 1, false},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.094", 7, false},
                {"2002-05-30T09:00:00.094", 100, false},
                {"2002-05-30T09:00:00.095", 5, false},
                {"2002-05-30T09:00:00.095", 6, true},
                {"2002-05-30T09:00:00.095", 100, true},
                {"2002-05-30T09:00:00.099", 1, false},
                {"2002-05-30T09:00:00.099", 2, true},
                {"2002-05-30T09:00:00.099", 100, true},
                {"2002-05-30T09:00:00.100", 100, false},
        };
        expectedValidator = new OverlappedByValidator(5L);
        assertExpression(seedTime, 100, "a.overlappedBy(b, 5 milliseconds)", expected, expectedValidator);

        // test 2-parameter form (overlap by min X and not more then Y msec)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.000", 1000, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 1, false},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.089", 14, false},
                {"2002-05-30T09:00:00.090", 10, false},
                {"2002-05-30T09:00:00.090", 11, true},
                {"2002-05-30T09:00:00.090", 1000, true},
                {"2002-05-30T09:00:00.095", 5, false},
                {"2002-05-30T09:00:00.095", 6, true},
                {"2002-05-30T09:00:00.096", 5, false},
                {"2002-05-30T09:00:00.096", 100, false},
                {"2002-05-30T09:00:00.100", 100, false},
        };
        expectedValidator = new OverlappedByValidator(5L, 10L);
        assertExpression(seedTime, 100, "a.overlappedBy(b, 5 milliseconds, 10 milliseconds)", expected, expectedValidator);
    }

    public void testStartsWhereClause() {

        Validator expectedValidator = new StartsValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.999", 100, false},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 1, true},
                {"2002-05-30T09:00:00.000", 99, true},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.001", 0, false},
        };
        assertExpression(seedTime, 100, "a.starts(b)", expected, expectedValidator);

        // test 1-parameter form (max distance between start times)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.994", 6, false},
                {"2002-05-30T08:59:59.995", 0, true},
                {"2002-05-30T08:59:59.995", 104, true},
                {"2002-05-30T08:59:59.995", 105, false},
                {"2002-05-30T09:00:00.000", 0, true},
                {"2002-05-30T09:00:00.000", 1, true},
                {"2002-05-30T09:00:00.000", 99, true},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.001", 0, true},
                {"2002-05-30T09:00:00.005", 94, true},
                {"2002-05-30T09:00:00.005", 95, false},
                {"2002-05-30T09:00:00.005", 100, false},
        };
        expectedValidator = new StartsValidator(5L);
        assertExpression(seedTime, 100, "a.starts(b, 5 milliseconds)", expected, expectedValidator);
    }

    public void testStartedByWhereClause() {

        Validator expectedValidator = new StartedByValidator();
        String seedTime = "2002-05-30T09:00:00.000";
        Object[][] expected = {
                {"2002-05-30T08:59:59.999", 100, false},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.000", 101, true},
                {"2002-05-30T09:00:00.001", 0, false},
                {"2002-05-30T09:00:00.001", 101, false},
        };
        assertExpression(seedTime, 100, "a.startedBy(b)", expected, expectedValidator);

        // test 1-parameter form (max distance between start times)
        expected = new Object[][] {
                {"2002-05-30T08:59:59.994", 6, false},
                {"2002-05-30T08:59:59.995", 0, false},
                {"2002-05-30T08:59:59.995", 105, false},
                {"2002-05-30T08:59:59.995", 106, true},
                {"2002-05-30T09:00:00.000", 0, false},
                {"2002-05-30T09:00:00.000", 100, false},
                {"2002-05-30T09:00:00.000", 101, true},
                {"2002-05-30T09:00:00.001", 99, false},
                {"2002-05-30T09:00:00.001", 100, true},
                {"2002-05-30T09:00:00.005", 94, false},
                {"2002-05-30T09:00:00.005", 95, false},
                {"2002-05-30T09:00:00.005", 96, true},
        };
        expectedValidator = new StartedByValidator(5L);
        assertExpression(seedTime, 100, "a.startedBy(b, 5 milliseconds)", expected, expectedValidator);
    }

    private void tryInvalidConfig(Class clazz, ConfigurationEventTypeLegacy config, String message) {
        try {
            epService.getEPAdministrator().getConfiguration().addEventType(clazz.getName(), clazz.getName(), config);
            fail();
        }
        catch (ConfigurationException ex) {
            assertEquals(message, ex.getMessage());
        }
    }

    private void tryInvalid(String epl, String message) {
        try {
            epService.getEPAdministrator().createEPL(epl);
            fail();
        }
        catch (EPStatementException ex) {
            assertEquals(message, ex.getMessage());
        }
    }

    private void registerType(String suffix, String timestampType) {
        String props = "(startTS " + timestampType + ", endTS " + timestampType + ") starttimestamp startTS endtimestamp endTS";
        epService.getEPAdministrator().createEPL("create objectarray schema A_" + suffix + " as " + props);
        epService.getEPAdministrator().createEPL("create objectarray schema B_" + suffix + " as " + props);
    }

    private void registerBeanType() {
        ConfigurationEventTypeLegacy configBean = new ConfigurationEventTypeLegacy();
        configBean.setStartTimestampPropertyName("msecdateStart");
        configBean.setEndTimestampPropertyName("msecdateEnd");
        epService.getEPAdministrator().getConfiguration().addEventType("A", SupportTimeStartEndA.class.getName(), configBean);
        epService.getEPAdministrator().getConfiguration().addEventType("B", SupportTimeStartEndB.class.getName(), configBean);
    }

    private void runAssertionCreateSchemaWTypes(EventRepresentationEnum eventRepresentationEnum, String typeOfDatetimeProp, Object startA, Object endA, Object startB, Object endB) {
        epService.getEPAdministrator().createEPL(eventRepresentationEnum.getAnnotationText() + " create schema TypeA as (startts " + typeOfDatetimeProp + ", endts " + typeOfDatetimeProp + ") starttimestamp startts endtimestamp endts");
        epService.getEPAdministrator().createEPL(eventRepresentationEnum.getAnnotationText() + " create schema TypeB as (startts " + typeOfDatetimeProp + ", endts " + typeOfDatetimeProp + ") starttimestamp startts endtimestamp endts");

        EPStatement stmt = epService.getEPAdministrator().createEPL("select a.includes(b) as val0 from TypeA#lastevent as a, TypeB#lastevent as b");
        stmt.addListener(listener);

        makeSendEvent("TypeA", eventRepresentationEnum, startA, endA);
        makeSendEvent("TypeB", eventRepresentationEnum, startB, endB);
        assertEquals(true, listener.assertOneGetNewAndReset().get("val0"));

        epService.getEPAdministrator().destroyAllStatements();
        epService.getEPAdministrator().getConfiguration().removeEventType("TypeA", true);
        epService.getEPAdministrator().getConfiguration().removeEventType("TypeB", true);
    }

    private void makeSendEvent(String typeName, EventRepresentationEnum eventRepresentationEnum, Object startTs, Object endTs) {
        Map<String, Object> theEvent = new LinkedHashMap<String, Object>();
        theEvent.put("startts", startTs);
        theEvent.put("endts", endTs);
        if (eventRepresentationEnum.isObjectArrayEvent()) {
            epService.getEPRuntime().sendEvent(theEvent.values().toArray(), typeName);
        }
        else {
            epService.getEPRuntime().sendEvent(theEvent, typeName);
        }
    }

    private void assertExpression(String seedTime, long seedDuration, String whereClause, Object[][] timestampsAndResult, Validator validator) {
        for (SupportDateTimeFieldType fieldType : SupportDateTimeFieldType.values()) {
            assertExpressionForType(seedTime, seedDuration, whereClause, timestampsAndResult, validator, fieldType);
        }
    }

    private void assertExpressionForType(String seedTime, long seedDuration, String whereClause, Object[][] timestampsAndResult, Validator validator, SupportDateTimeFieldType fieldType) {

        String epl = "select * from A_" + fieldType.name() + "#lastevent as a, B_" + fieldType.name() + "#lastevent as b " +
                "where " + whereClause;
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        epService.getEPRuntime().sendEvent(new Object[] {fieldType.makeStart(seedTime), fieldType.makeEnd(seedTime, seedDuration)}, "B_" + fieldType.name());

        for (Object[] test : timestampsAndResult) {
            String testtime = (String) test[0];
            Long testduration = ((Number) test[1]).longValue();
            boolean expected = (Boolean) test[2];

            long rightStart = DateTime.parseDefaultMSec(seedTime);
            long rightEnd = rightStart + seedDuration;
            long leftStart = DateTime.parseDefaultMSec(testtime);
            long leftEnd = leftStart + testduration;
            String message = "time " + testtime + " duration " + testduration + " for '" + whereClause + "'";

            if (validator != null) {
                assertEquals("Validation of expected result failed for " + message, expected, validator.validate(leftStart, leftEnd, rightStart, rightEnd));
            }

            epService.getEPRuntime().sendEvent(new Object[] {fieldType.makeStart(testtime), fieldType.makeEnd(testtime, testduration)}, "A_" + fieldType.name());

            if (!listener.isInvoked() && expected) {
                fail("Expected but not received for " + message);
            }
            if (listener.isInvoked() && !expected) {
                fail("Not expected but received for " + message);
            }
            listener.reset();
        }

        stmt.destroy();
    }

    private static long getMillisecForDays(int days) {
        return days*24*60*60*1000L;
    }

    private void assertExpressionBean(String seedTime, long seedDuration, String whereClause, Object[][] timestampsAndResult, Validator validator) {

        String epl = "select * from A#lastevent as a, B#lastevent as b " +
                "where " + whereClause;
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        epService.getEPRuntime().sendEvent(SupportTimeStartEndB.make("B", seedTime, seedDuration));

        for (Object[] test : timestampsAndResult) {
            String testtime = (String) test[0];
            Long testduration = ((Number) test[1]).longValue();
            boolean expected = (Boolean) test[2];

            long rightStart = DateTime.parseDefaultMSec(seedTime);
            long rightEnd = rightStart + seedDuration;
            long leftStart = DateTime.parseDefaultMSec(testtime);
            long leftEnd = leftStart + testduration;
            String message = "time " + testtime + " duration " + testduration + " for '" + whereClause + "'";

            if (validator != null) {
                assertEquals("Validation of expected result failed for " + message, expected, validator.validate(leftStart, leftEnd, rightStart, rightEnd));
            }

            epService.getEPRuntime().sendEvent(SupportTimeStartEndA.make("A", testtime, testduration));

            if (!listener.isInvoked() && expected) {
                fail("Expected but not received for " + message);
            }
            if (listener.isInvoked() && !expected) {
                fail("Not expected but received for " + message);
            }
            listener.reset();
        }

        stmt.destroy();
    }

    private interface Validator {
        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd);
    }

    private class BeforeValidator implements Validator {
        private Long start;
        private Long end;

        private BeforeValidator(Long start, Long end) {
            this.start = start;
            this.end = end;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            long delta = rightStart - leftEnd;
            return start <= delta && delta <= end;
        }
    }

    private class AfterValidator implements Validator {
        private Long start;
        private Long end;

        private AfterValidator(Long start, Long end) {
            this.start = start;
            this.end = end;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            long delta = leftStart - rightEnd;
            return start <= delta && delta <= end;
        }
    }

    private class CoincidesValidator implements Validator {
        private final Long startThreshold;
        private final Long endThreshold;

        private CoincidesValidator() {
            startThreshold = 0L;
            endThreshold = 0L;
        }

        private CoincidesValidator(Long startThreshold) {
            this.startThreshold = startThreshold;
            this.endThreshold = startThreshold;
        }

        private CoincidesValidator(Long startThreshold, Long endThreshold) {
            this.startThreshold = startThreshold;
            this.endThreshold = endThreshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            long startDelta = Math.abs(leftStart - rightStart);
            long endDelta = Math.abs(leftEnd - rightEnd);
            return ((startDelta <= startThreshold) &&
                   (endDelta <= endThreshold));
        }
    }

    private class DuringValidator implements Validator {

        private int form;
        private Long threshold;
        private Long minThreshold;
        private Long maxThreshold;
        private Long minStartThreshold;
        private Long maxStartThreshold;
        private Long minEndThreshold;
        private Long maxEndThreshold;

        private DuringValidator() {
            form = 1;
        }

        private DuringValidator(Long threshold) {
            form = 2;
            this.threshold = threshold;
        }

        private DuringValidator(Long minThreshold, Long maxThreshold) {
            form = 3;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        private DuringValidator(Long minStartThreshold, Long maxStartThreshold, Long minEndThreshold, Long maxEndThreshold) {
            form = 4;
            this.minStartThreshold = minStartThreshold;
            this.maxStartThreshold = maxStartThreshold;
            this.minEndThreshold = minEndThreshold;
            this.maxEndThreshold = maxEndThreshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            if (form == 1) {
                return rightStart < leftStart &&
                       leftEnd < rightEnd;
            }
            else if (form == 2) {
                long distanceStart = leftStart - rightStart;
                if (distanceStart <= 0 || distanceStart > threshold) {
                    return false;
                }
                long distanceEnd = rightEnd - leftEnd;
                return !(distanceEnd <= 0 || distanceEnd > threshold);
            }
            else if (form == 3) {
                long distanceStart = leftStart - rightStart;
                if (distanceStart < minThreshold || distanceStart > maxThreshold) {
                    return false;
                }
                long distanceEnd = rightEnd - leftEnd;
                return !(distanceEnd < minThreshold || distanceEnd > maxThreshold);
            }
            else if (form == 4) {
                long distanceStart = leftStart - rightStart;
                if (distanceStart < minStartThreshold || distanceStart > maxStartThreshold) {
                    return false;
                }
                long distanceEnd = rightEnd - leftEnd;
                return !(distanceEnd < minEndThreshold || distanceEnd > maxEndThreshold);
            }
            throw new IllegalStateException("Invalid form: " + form);
        }
    }

    private class FinishesValidator implements Validator {
        private Long threshold;

        private FinishesValidator() {
        }

        private FinishesValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            if (threshold == null) {
                return ((rightStart < leftStart) && (leftEnd == rightEnd));
            }
            else {
                if (rightStart >= leftStart) {
                    return false;
                }
                long delta = Math.abs(leftEnd - rightEnd);
                return delta <= threshold;
            }
        }
    }

    private class FinishedByValidator implements Validator {
        private Long threshold;

        private FinishedByValidator() {
        }

        private FinishedByValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {

            if (threshold == null) {
                return ((leftStart < rightStart) && (leftEnd == rightEnd));
            }
            else {
                if (leftStart >= rightStart) {
                    return false;
                }
                long delta = Math.abs(leftEnd - rightEnd);
                return delta <= threshold;
            }
        }
    }

    private class IncludesValidator implements Validator {

        private int form;
        private Long threshold;
        private Long minThreshold;
        private Long maxThreshold;
        private Long minStartThreshold;
        private Long maxStartThreshold;
        private Long minEndThreshold;
        private Long maxEndThreshold;

        private IncludesValidator() {
            form = 1;
        }

        private IncludesValidator(Long threshold) {
            form = 2;
            this.threshold = threshold;
        }

        private IncludesValidator(Long minThreshold, Long maxThreshold) {
            form = 3;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        private IncludesValidator(Long minStartThreshold, Long maxStartThreshold, Long minEndThreshold, Long maxEndThreshold) {
            form = 4;
            this.minStartThreshold = minStartThreshold;
            this.maxStartThreshold = maxStartThreshold;
            this.minEndThreshold = minEndThreshold;
            this.maxEndThreshold = maxEndThreshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
                                
            if (form == 1) {
                return leftStart < rightStart &&
                       rightEnd < leftEnd;
            }
            else if (form == 2) {
                long distanceStart = rightStart - leftStart;
                if (distanceStart <= 0 || distanceStart > threshold) {
                    return false;
                }
                long distanceEnd = leftEnd - rightEnd;
                return !(distanceEnd <= 0 || distanceEnd > threshold);
            }
            else if (form == 3) {
                long distanceStart = rightStart - leftStart;
                if (distanceStart < minThreshold || distanceStart > maxThreshold) {
                    return false;
                }
                long distanceEnd = leftEnd - rightEnd;
                return !(distanceEnd < minThreshold || distanceEnd > maxThreshold);
            }
            else if (form == 4) {
                long distanceStart = rightStart - leftStart;
                if (distanceStart < minStartThreshold || distanceStart > maxStartThreshold) {
                    return false;
                }
                long distanceEnd = leftEnd - rightEnd;
                return !(distanceEnd < minEndThreshold || distanceEnd > maxEndThreshold);
            }
            throw new IllegalStateException("Invalid form: " + form);
        }
    }

    private class MeetsValidator implements Validator {
        private Long threshold;

        private MeetsValidator() {
        }

        private MeetsValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {

            if (threshold == null) {
                return rightStart == leftEnd;
            }
            else {
                long delta = Math.abs(rightStart - leftEnd);
                return delta <= threshold;
            }
        }
    }

    private class MetByValidator implements Validator {
        private Long threshold;

        private MetByValidator() {
        }

        private MetByValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {

            if (threshold == null) {
                return leftStart == rightEnd;
            }
            else {
                long delta = Math.abs(leftStart - rightEnd);
                return delta <= threshold;
            }
        }
    }

    private class OverlapsValidator implements Validator {
        private int form;
        private Long threshold;
        private Long minThreshold;
        private Long maxThreshold;

        private OverlapsValidator() {
            form = 1;
        }

        private OverlapsValidator(Long threshold) {
            form = 2;
            this.threshold = threshold;
        }

        private OverlapsValidator(Long minThreshold, Long maxThreshold) {
            form = 3;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {

            boolean match = (leftStart < rightStart) &&
                       (rightStart < leftEnd) &&
                       (leftEnd < rightEnd);

            if (form == 1) {
                return match;
            }
            else if (form == 2) {
                if (!match) {
                    return false;
                }
                long delta = leftEnd - rightStart;
                return 0 <= delta && delta <= threshold;
            }
            else if (form == 3) {
                if (!match) {
                    return false;
                }
                long delta = leftEnd - rightStart;
                return minThreshold <= delta && delta <= maxThreshold;
            }
            throw new IllegalArgumentException("Invalid form " + form);
        }
    }

    private class OverlappedByValidator implements Validator {
        private int form;
        private Long threshold;
        private Long minThreshold;
        private Long maxThreshold;

        private OverlappedByValidator() {
            form = 1;
        }

        private OverlappedByValidator(Long threshold) {
            form = 2;
            this.threshold = threshold;
        }

        private OverlappedByValidator(Long minThreshold, Long maxThreshold) {
            form = 3;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {

            boolean match = (rightStart < leftStart) &&
                            (leftStart < rightEnd) &&
                            (rightEnd < leftEnd);

            if (form == 1) {
                return match;
            }
            else if (form == 2) {
                if (!match) {
                    return false;
                }
                long delta = rightEnd - leftStart;
                return 0 <= delta && delta <= threshold;
            }
            else if (form == 3) {
                if (!match) {
                    return false;
                }
                long delta = rightEnd - leftStart;
                return minThreshold <= delta && delta <= maxThreshold;
            }
            throw new IllegalArgumentException("Invalid form " + form);
        }
    }

    private class StartsValidator implements Validator {
        private Long threshold;

        private StartsValidator() {
        }

        private StartsValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            if (threshold == null) {
                return (leftStart == rightStart) && (leftEnd < rightEnd);
            }
            else {
                long delta = Math.abs(leftStart - rightStart);
                return (delta <= threshold) && (leftEnd < rightEnd);
            }
        }
    }

    private class StartedByValidator implements Validator {
        private Long threshold;

        private StartedByValidator() {
        }

        private StartedByValidator(Long threshold) {
            this.threshold = threshold;
        }

        public boolean validate(long leftStart, long leftEnd, long rightStart, long rightEnd) {
            if (threshold == null) {
                return (leftStart == rightStart) && (leftEnd > rightEnd);
            }
            else {
                long delta = Math.abs(leftStart - rightStart);
                return (delta <= threshold) && (leftEnd > rightEnd);
            }
        }
    }
}
