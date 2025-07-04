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
}
