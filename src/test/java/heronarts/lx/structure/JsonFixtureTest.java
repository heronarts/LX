package heronarts.lx.structure;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class JsonFixtureTest {
    @Test
    void testSimpleEvaluateBooleanExpression() {
        assertEquals(true, JsonFixture._evaluateBooleanExpression("true"));
        // Using ".toString()" as least invasive way to access the contents of ExpressionResult
        assertEquals("true", JsonFixture._evaluateExpression("true").toString());
    }

    @Test
    void testEvaluateBooleanExpressionWithAndOperator() {
        assertEquals(true, JsonFixture._evaluateBooleanExpression("true&&true"));
        assertEquals("true", JsonFixture._evaluateExpression("true&&true").toString());
    }
}
