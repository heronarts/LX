/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Andrew M. Look <andrew.m.look@gmail.com>
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.structure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class JsonFixtureTest {
  @Test
  void testSimpleEvaluateBooleanExpression() {
    assertEquals(true, Expression.evaluateBoolean("true"));
    assertEquals(Expression.Result.Boolean.TRUE, Expression.evaluate("true"));
  }

  @Test
  void testEvaluateBooleanExpressionWithAndOperator() {
    assertEquals(true, Expression.evaluateBoolean("true&&true"));
    assertEquals(Expression.Result.Boolean.TRUE, Expression.evaluate("true&&true"));
  }

  @Test
  void testEvaluateUnaryMinus() {
    assertEquals(-1f, Expression.evaluateNumeric("-(-(-1))"));
  }

  @Test
  void testEvaluateFunctionCalls() {
    assertEquals(0f, Expression.evaluateNumeric("sin(180)"));
    assertEquals(1f, Expression.evaluateNumeric("sin(90)"));
    assertEquals(0f, Expression.evaluateNumeric("cos(90)"));
    assertEquals(-1f, Expression.evaluateNumeric("cos(180)"));
    assertEquals(1f, Expression.evaluateNumeric("tan(45)"));
    assertEquals(-1f, Expression.evaluateNumeric("tan(-45)"));
    assertEquals(1f, Expression.evaluateNumeric("pow(2, 0)"));
    assertEquals(32f, Expression.evaluateNumeric("pow(2, 5)"));
    assertEquals(90f, Expression.evaluateNumeric("atan2(1, 0)"));
    assertEquals(135f, Expression.evaluateNumeric("atan2(1, -1)"));
    assertEquals(180f, Expression.evaluateNumeric("atan2(0, -1)"));
    assertEquals(-45f, Expression.evaluateNumeric("atan2(-1, 1)"));
    assertEquals(315f, Expression.evaluateNumeric("atan2p( ((2-3)) , (sin(90)))"));
  }
}
