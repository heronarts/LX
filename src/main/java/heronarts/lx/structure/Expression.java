/**
 * Copyright 2019- Mark C. Slee, Heron Arts LLC
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
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.structure;

public class Expression {

  public static abstract class Result {

    public static class Numeric extends Result {
      private final float number;

      private Numeric(float number) {
        this.number = number;
      }

      public float getNumber() {
        return this.number;
      }

      @Override
      public String toString() {
        return String.valueOf(this.number);
      }
    }

    public static class Boolean extends Result {

      public static final Boolean TRUE = new Boolean(true);
      public static final Boolean FALSE = new Boolean(false);

      private static Boolean get(boolean value) {
        return value ? TRUE : FALSE;
      }

      private final boolean bool;

      private Boolean(boolean bool) {
        this.bool = bool;
      }

      public boolean getBoolean() {
        return this.bool;
      }

      @Override
      public String toString() {
        return String.valueOf(this.bool);
      }
    }
  }

  private static final String[][] EXPRESSION_OPERATORS = {
    { "||", "|" }, // Both forms are logical, not bitwise
    { "&&", "&" }, // Both forms are logical, not bitwise
    { "<=", ">=", "<", ">" },
    { "==", "!=" },
    { "+", "-" },
    { "*", "/", "%" },
    { "^" }
  };

  private static int _getOperatorIndex(String expression, char[] chars, String operator) {
    if ("-".equals(operator)) {
      for (int index = chars.length - 1; index > 0; --index) {
        // Skip over the tricky unary minus operator! If preceded by another operator,
        // then it's actually just a negative sign which will be handled later. Do not
        // process it as a subtraction.
        if ((chars[index] == '-') && !isUnaryMinus(chars, index)) {
          return index;
        }
      }
      return -1;
    }
    int lastIndex = expression.lastIndexOf(operator);
    if ("&".equals(operator) || "|".equals(operator)) {
      while ((lastIndex > 0) && operator.equals(expression.substring(lastIndex-1, lastIndex))) {
        // If & or | were part of && or ||, skip over them
        lastIndex = expression.lastIndexOf(operator, lastIndex-2);
      }
    }
    return lastIndex;
  }

  public static float evaluateNumeric(String expression) {
    if (evaluate(expression) instanceof Result.Numeric numeric) {
      return numeric.number;
    }
    throw new IllegalArgumentException("Expected expression to be numeric: " + expression);
  }

  public static boolean evaluateBoolean(String expression) {
    if (evaluate(expression) instanceof Result.Boolean bool) {
      return bool.bool;
    }
    throw new IllegalArgumentException("Expected expression to be boolean: " + expression);
  }

  /**
   * Expressions can have ambiguous types when nested with parentheses! This is getting
   * out of control and I really should have just used a proper expression parsing library
   * of some sort (-mcslee, June 2025, and yet bound to continue bolting onto this...)
   *
   * @param expression Portion of expression to evaluate
   * @return ExpressionResult, which may be boolean or numeric
   */
  public static Result evaluate(String expression) {
    final char[] chars = expression.toCharArray();

    // Parentheses pass
    int openParen = -1;
    for (int i = 0; i < chars.length; ++i) {
      if (chars[i] == '(') {
        openParen = i;
      } else if (chars[i] == ')') {
        if (openParen < 0) {
          throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
        }

        // Whenever we find a closed paren, evaluate just this one parenthetical.
        // This will naturally work from in->out on nesting, since every closed-paren
        // catches the open-paren that was closest to it.
        Result result = evaluate(expression.substring(openParen+1, i));
        if ((openParen == 0) && (i == chars.length-1)) {
          // Whole thing in parentheses? Just return!
          return result;
        }

        // Evaluate expression recursively with this parenthetical removed
        return evaluate(
          expression.substring(0, openParen) +
          result.toString() +
          expression.substring(i + 1)
        );
      }
    }

    // Ternary conditional, lowest precedence, right->left associative
    final int condition = expression.indexOf('?');
    if (condition > 0) {
      final int end = expression.lastIndexOf(':');
      if (end <= condition) {
        throw new IllegalArgumentException("Mismatched ternary conditional ?: in expression: " + expression);
      }
      return evaluateBoolean(expression.substring(0, condition)) ?
        evaluate(expression.substring(condition+1, end)) :
        evaluate(expression.substring(end+1));
    }

    // Left->right associative operators, working up the precedence ladder
    for (String[] operators : EXPRESSION_OPERATORS) {
      int lastIndex = -1;
      String operator = null;
      for (String candidate : operators) {
        int candidateIndex = _getOperatorIndex(expression, chars, candidate);
        if (candidateIndex > lastIndex) {
          operator = candidate;
          lastIndex = candidateIndex;
        }
      }
      if (operator != null) {
        final String left = expression.substring(0, lastIndex);
        final String right = expression.substring(lastIndex + operator.length());

        return switch (operator) {
          case "&&", "&" -> Result.Boolean.get(
            evaluateBoolean(left) && evaluateBoolean(right)
          );
          case "||", "|" -> Result.Boolean.get(
            evaluateBoolean(left) || evaluateBoolean(right)
          );
          case "<=" -> Result.Boolean.get(
            evaluateNumeric(left) <= evaluateNumeric(right)
          );
          case "<" -> Result.Boolean.get(
            evaluateNumeric(left) < evaluateNumeric(right)
          );
          case ">=" -> Result.Boolean.get(
            evaluateNumeric(left) >= evaluateNumeric(right)
          );
          case ">" -> Result.Boolean.get(
            evaluateNumeric(left) > evaluateNumeric(right)
          );
          case "==" -> Result.Boolean.get(
            evaluateNumeric(left) == evaluateNumeric(right)
          );
          case "!=" -> Result.Boolean.get(
            evaluateNumeric(left) != evaluateNumeric(right)
          );
          case "+" -> new Result.Numeric(
            evaluateNumeric(left) + evaluateNumeric(right)
          );
          case "-" -> new Result.Numeric(
            evaluateNumeric(left) - evaluateNumeric(right)
          );
          case "*" -> new Result.Numeric(
            evaluateNumeric(left) * evaluateNumeric(right)
          );
          case "/" -> new Result.Numeric(
            evaluateNumeric(left) / evaluateNumeric(right)
          );
          case "%" -> new Result.Numeric(
            evaluateNumeric(left) % evaluateNumeric(right)
          );
          case "^" -> new Result.Numeric(
            (float) Math.pow(evaluateNumeric(left), evaluateNumeric(right))
          );
          default -> throw new IllegalStateException("Unrecognized operator: " + operator);
        };
      }
    }

    // Dreaded nasty unary operators!
    final String trimmed = expression.trim();
    if (!trimmed.isEmpty()) {
      final char unary = trimmed.charAt(0);
      if (unary == '-') {
        // Float.parseFloat() would handle one of these fine, but it won't handle
        // them potentially stacking up at the front, e.g. if multiple expression
        // resolutions have resulted in something like ---4, so do the negations
        // manually one by one
        return new Result.Numeric(-evaluateNumeric(expression.substring(1)));
      } else if (unary == '!') {
        return Result.Boolean.get(!evaluateBoolean(expression.substring(1)));
      }

      // Check for simple function operators
      for (SimpleFunction function : SimpleFunction.values()) {
        final String name = function.name();
        if (trimmed.startsWith(name)) {
          final float argument = evaluateNumeric(expression.substring(name.length()));
          return new Result.Numeric(function.compute.compute(argument));
        }
      }
    }

    // Sort out what we got here
    return switch (trimmed.toLowerCase()) {
      case "" -> throw new IllegalArgumentException("Cannot evaluate empty expression: " + expression);
      case "true" -> Result.Boolean.TRUE;
      case "false" -> Result.Boolean.FALSE;
      default -> new Result.Numeric(Float.parseFloat(trimmed));
    };
  }

  private enum SimpleFunction {
    sin(f -> { return (float) Math.sin(Math.toRadians(f)); }),
    cos(f -> { return (float) Math.cos(Math.toRadians(f)); }),
    tan(f -> { return (float) Math.tan(Math.toRadians(f)); }),
    asin(f -> { return (float) Math.toDegrees(Math.asin(f)); }),
    acos(f -> { return (float) Math.toDegrees(Math.acos(f)); }),
    atan(f -> { return (float) Math.toDegrees(Math.atan(f)); }),
    deg(f -> { return (float) Math.toDegrees(f); }),
    rad(f -> { return (float) Math.toRadians(f); }),
    abs(f -> { return Math.abs(f); }),
    sqrt(f -> { return (float) Math.sqrt(f); }),
    floor(f -> { return (float) Math.floor(f); }),
    ceil(f -> { return (float) Math.ceil(f); }),
    round(f -> { return Math.round(f); });

    private interface Compute {
      public float compute(float f);
    }

    private final Compute compute;

    private SimpleFunction(Compute compute) {
      this.compute = compute;
    }

  }

  private static final String OPERATOR_CHARS = "^*/+-%<>=!&|";

  private static boolean isUnaryMinus(char[] chars, int index) {
    // Check it's actually a minus
    if (chars[index] != '-') {
      return false;
    }

    // If at the very front of the thing, it's unary!
    if (index == 0) {
      return true;
    }

    // Check if preceded by another simple operator, e.g. 4+-4
    if (OPERATOR_CHARS.indexOf(chars[index-1]) >= 0) {
      return true;
    }
    // Check if preceded by a simple function token, which will no longer have
    // parentheses, e.g. sin(-4) will have become sin-4 after parenthetical resolution
    for (SimpleFunction function : SimpleFunction.values()) {
      final String name = function.name();
      final int len = name.length();
      if ((index >= len) && new String(chars, index-len, len).equals(name)) {
        return true;
      }
    }
    return false;
  }

}
