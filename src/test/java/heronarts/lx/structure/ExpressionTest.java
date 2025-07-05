/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * <p>This file is part of the LX Studio software library. By using LX, you agree to the terms of
 * the LX Studio Software License and Distribution Agreement, available at: http://lx.studio/license
 *
 * <p>Please note that the LX license is not open-source. The license allows for free,
 * non-commercial use.
 *
 * <p>HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, AND SPECIFICALLY
 * DISCLAIMS ANY WARRANTY OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE,
 * WITH RESPECT TO THE SOFTWARE.
 *
 * @author Andrew M. Look <andrew.m.look@gmail.com>
 * @author Mark C. Slee <mark@heronarts.com>
 */
package heronarts.lx.structure;

import org.junit.jupiter.api.Test;

import static heronarts.lx.structure.Expression.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionTest {

    /**
     * Testing more complex expressions, similar to an expression that might show up in an actual LXF
     * file, for example: ($offset+(($row-1)/2))*$pointSpacing
     */
    @Test
    void testLXFLikeExpressions() {
        //
        // We'll simulate with actual numbers since variables aren't available in this context

        // Simulate: offset=0, row=2, pointSpacing=1.9685039
        // (0+((2-1)/2))*1.9685039 = (0+(1/2))*1.9685039 = 0.5*1.9685039 = 0.98425195
        assertEquals(0.98425195f, evaluateNumeric("(0+((2-1)/2))*1.9685039"), 0.0001f);

        // Simulate: offset=1, row=3, pointSpacing=1.9685039
        // (1+((3-1)/2))*1.9685039 = (1+1)*1.9685039 = 2*1.9685039 = 3.9370078
        assertEquals(3.9370078f, evaluateNumeric("(1+((3-1)/2))*1.9685039"), 0.0001f);

        // Test expression like: ($row-1)*$rowSpacing
        // Simulate: row=2, rowSpacing=1.7047743848
        // (2-1)*1.7047743848 = 1*1.7047743848 = 1.7047743848
        assertEquals(1.7047743848f, evaluateNumeric("(2-1)*1.7047743848"), 0.0001f);

        // Test expression like: ($offset+(($row-1)/2)-0.5)*$pointSpacing
        // Simulate: offset=0, row=2, pointSpacing=1.9685039
        // (0+((2-1)/2)-0.5)*1.9685039 = (0+0.5-0.5)*1.9685039 = 0*1.9685039 = 0
        assertEquals(0.0f, evaluateNumeric("(0+((2-1)/2)-0.5)*1.9685039"), 0.0001f);

        // Test ternary expression like: $flipBacking ? -2 : 2
        assertEquals(-2.0f, evaluateNumeric("true?-2:2"));
        assertEquals(2.0f, evaluateNumeric("false?-2:2"));
    }

    @Test
    void testLiterals() {
        assertEquals(42.0f, evaluateNumeric("42"));
        assertEquals(3.14f, evaluateNumeric("3.14"));
        assertEquals(-5.0f, evaluateNumeric("-5"));
        assertEquals(0.0f, evaluateNumeric("0"));
        assertEquals(Float.valueOf(0f), evaluate("0").getValue());
        assertTrue(evaluateBoolean("true"));
        assertFalse(evaluateBoolean("false"));
        assertEquals(java.lang.Boolean.TRUE, evaluate("true").getValue());
    }

    @Test
    void testWhitespaceHandling() {
        assertEquals(7.0f, evaluateNumeric("3 + 4"));
        assertEquals(12.0f, evaluateNumeric("  3  *  4  "));
    }

    @Test
    void testBasicArithmetic() {
        assertEquals(7.0f, evaluateNumeric("3+4"));
        assertEquals(-1.0f, evaluateNumeric("3-4"));
        assertEquals(12.0f, evaluateNumeric("3*4"));
        assertEquals(0.75f, evaluateNumeric("3/4"));
        assertEquals(1.0f, evaluateNumeric("3%2"));
        assertEquals(9.0f, evaluateNumeric("3^2"));
    }

    @Test
    void testOperatorPrecedence() {
        assertEquals(14.0f, evaluateNumeric("2+3*4"));
        assertEquals(20.0f, evaluateNumeric("(2+3)*4"));
        assertEquals(11.0f, evaluateNumeric("3+2^3"));
        assertEquals(125.0f, evaluateNumeric("(3+2)^3"));
    }

    @Test
    void testNestedParentheses() {
        assertEquals(42.0f, evaluateNumeric("((((42))))"));
        assertEquals(22.0f, evaluateNumeric("2*((3+4)*2-3)"));
        assertEquals(46.0f, evaluateNumeric("2*(3+(4*5))"));
    }

    @Test
    void testComparisonOperations() {
        assertTrue(evaluateBoolean("5>3"));
        assertFalse(evaluateBoolean("3>5"));
        assertTrue(evaluateBoolean("5>=5"));
        assertTrue(evaluateBoolean("3<5"));
        assertFalse(evaluateBoolean("5<3"));
        assertTrue(evaluateBoolean("3<=3"));
        assertTrue(evaluateBoolean("5==5"));
        assertFalse(evaluateBoolean("5==3"));
        assertTrue(evaluateBoolean("5!=3"));
        assertFalse(evaluateBoolean("5!=5"));
    }

    @Test
    void testLogicalOperations() {
        assertTrue(evaluateBoolean("true&&true"));
        assertFalse(evaluateBoolean("true&&false"));
        assertTrue(evaluateBoolean("true||false"));
        assertFalse(evaluateBoolean("false||false"));

        // Test both forms of logical operators
        assertTrue(evaluateBoolean("true&true"));
        assertTrue(evaluateBoolean("true|false"));
    }

    @Test
    void testUnaryOperators() {
        assertEquals(-5.0f, evaluateNumeric("-5"));
        assertEquals(5.0f, evaluateNumeric("--5"));
        assertEquals(-5.0f, evaluateNumeric("---5"));

        assertFalse(evaluateBoolean("!true"));
        assertTrue(evaluateBoolean("!false"));
        assertTrue(evaluateBoolean("!!true"));
    }

    @Test
    void testEvaluateUnaryMinus() {
        assertEquals(-1f, evaluateNumeric("-(-(-1))"));
    }

    @Test
    void testArithmeticWithUnary() {
        assertEquals(0.0f, evaluateNumeric("4+-4"));
    }

    @Test
    void testEvaluateFunctionCalls() {
        assertEquals(0f, evaluateNumeric("sin(180)"));
        assertEquals(1f, evaluateNumeric("sin(90)"));
        assertEquals(0f, evaluateNumeric("cos(90)"));
        assertEquals(-1f, evaluateNumeric("cos(180)"));
        assertEquals(1f, evaluateNumeric("tan(45)"));
        assertEquals(-1f, evaluateNumeric("tan(-45)"));
        assertEquals(1f, evaluateNumeric("pow(2, 0)"));
        assertEquals(32f, evaluateNumeric("pow(2, 5)"));
        assertEquals(90f, evaluateNumeric("atan2(1, 0)"));
        assertEquals(135f, evaluateNumeric("atan2(1, -1)"));
        assertEquals(180f, evaluateNumeric("atan2(0, -1)"));
        assertEquals(-45f, evaluateNumeric("atan2(-1, 1)"));
        assertEquals(315f, evaluateNumeric("atan2p( ((2-3)) , (sin(90)))"));
        assertEquals(0f, evaluateNumeric("sin(000.0)"), 0.0001f);
        assertEquals(1f, evaluateNumeric("cos(0.000)"), 0.0001f);
        assertEquals(5f, evaluateNumeric("abs(-5)"));
        assertEquals(3f, evaluateNumeric("sqrt(9.0)"));
        assertEquals(2f, evaluateNumeric("floor(2.7)"));
        assertEquals(3f, evaluateNumeric("round(2.7)"));
        // Note(look): this doesn't work yet, unclear if it should even be supported
        //    assertEquals(3f, evaluateNumeric("ceil(+2.3)"));
    }

    @Test
    void testTernaryConditional() {
        assertEquals(10.0f, evaluateNumeric("true?10:20"));
        assertEquals(20.0f, evaluateNumeric("false?10:20"));
        assertEquals(5.0f, evaluateNumeric("3>2?5:7"));
        assertEquals(7.0f, evaluateNumeric("2>3?5:7"));

        // Nested ternary
        assertEquals(1.0f, evaluateNumeric("true?true?1:2:3"));
        assertEquals(2.0f, evaluateNumeric("true?false?1:2:3"));
        assertEquals(3.0f, evaluateNumeric("false?true?1:2:3"));

        // Nested ternary (with parentheses)
        assertEquals(3.0f, evaluateNumeric("false?(true?1:2):3"));
        assertEquals(3.0f, evaluateNumeric(" false ?  ( true ?  1 : 2  ) :  3 "));
    }

    @Test
    void testEvaluateErrorCases() {
        // Test empty expression
        assertThrows(IllegalArgumentException.class, () -> evaluate(""));

        // Test mismatched parentheses
        assertThrows(IllegalArgumentException.class, () -> evaluate("(2+3"));
        assertThrows(IllegalArgumentException.class, () -> evaluate("2+3)"));

        // Test mismatched ternary conditional
        assertThrows(IllegalArgumentException.class, () -> evaluate("true?5"));
        assertThrows(IllegalArgumentException.class, () -> evaluate("5:3"));

        // Test invalid number format
        assertThrows(NumberFormatException.class, () -> evaluate("abc"));
    }
}
