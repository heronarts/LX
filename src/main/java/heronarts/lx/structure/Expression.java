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

import heronarts.lx.utils.LXUtils;

/**
 * Expressions can have ambiguous types when nested with parentheses! This is getting
 * out of control and I really should have just used a proper expression parsing library
 * of some sort.
 *
 * Did you actually read that? I kept leaving comments like that for years, suggesting this
 * should all be replaced, but at this point (July 2025) I suppose I've changed my tune.
 * This is self-contained enough in a reasonably manageable small-ish amount of code, with a
 * few particular bits-and-bobs like rounding float precision errors that are of direct
 * relevance to the LXF use case. Thanks to Andrew Look for adding unit test support.
 */
public class Expression {

  public static abstract class Result<T> {

    public abstract T getValue();

    public static class Numeric extends Result<Float> {

      private final float number;

      private Numeric(float number) {
        this.number = number;
      }

      @Override
      public Float getValue() {
        return this.number;
      }

      @Override
      public String toString() {
        // Keep scientific notation out of here!! Will break recursive parsing
        // if we end up with "E-7" hanging about in there.
        //
        // If you need more than 10 digits of precision from LXF files, probably
        // doing something wrong (and in fact this rounding is often desirable
        // to stave off miniscule fixture mis-alignment that stems from
        // precision errors, e.g. Math.sin(0) != Math.sin(Math.PI)
        return String.format("%.10f", this.number);
      }
    }

    public static class Boolean extends Result<java.lang.Boolean> {

      public static final Boolean TRUE = new Boolean(true);
      public static final Boolean FALSE = new Boolean(false);

      private static Boolean get(boolean value) {
        return value ? TRUE : FALSE;
      }

      private final boolean bool;

      private Boolean(boolean bool) {
        this.bool = bool;
      }

      @Override
      public java.lang.Boolean getValue() {
        return this.bool;
      }

      @Override
      public String toString() {
        return String.valueOf(this.bool);
      }
    }

    public static class List extends Result<Result<?>[]> {

      private final Result<?>[] results;

      private List(Result<?>[] results) {
        this.results = results;
      }

      @Override
      public Result<?>[] getValue() {
        return this.results;
      }

      @Override
      public String toString() {
        final StringBuilder str = new StringBuilder();
        boolean first = true;
        for (Result<?> result : this.results) {
          if (first) {
            first = false;
          } else {
            str.append(',');
          }
          str.append(result.toString());
        }
        return str.toString();
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
   * Evaluate a mathematical expression containing a mix of operators, function calls
   * and parentheses. The result type is ambiguous, depending upon the content. Use
   * evaluateNumeric() or evaluateBoolean() when a known result type is desired.
   *
   * @param expression Portion of expression to evaluate
   * @return Result, which may be Boolean, Numeric or List
   */
  public static Result<?> evaluate(String expression) {
    expression = expression.trim();
    if (expression.isEmpty()) {
      throw new IllegalArgumentException("Cannot evaluate empty expression");
    }

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
        final String inner = expression.substring(openParen+1, i);
        Result<?> result = evaluate(inner);
        if ((openParen == 0) && (i == chars.length-1)) {
          // Whole thing was in parentheses? Just return!
          return result;
        }

        // Check for a function call against this parenthetical
        String left = expression.substring(0, openParen).trim();
        for (SimpleFunction function : SimpleFunction.values()) {
          final String name = function.name();
          if (left.endsWith(name)) {
            result = function.evaluate(result);
            left = left.substring(0, left.length() - name.length()).trim();
            break;
          }
        }

        // Nothing around us after possible function call?
        final String right = expression.substring(i + 1).trim();
        if (left.isEmpty() && right.isEmpty()) {
          return result;
        }

        // Evaluate expression recursively with this parenthetical removed
        return evaluate(left + result.toString() + right);
      }
    }

    // We're now clear of parentheses, check for a comma-delimited list
    if (expression.indexOf(',') >= 0) {
      final String[] parts = expression.split(",");
      final Result<?>[] results = new Result<?>[parts.length];
      int i = 0;
      for (String part : parts) {
        results[i++] = evaluate(part);
      }
      return new Result.List(results);
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
    final char unary = expression.charAt(0);
    if (unary == '-') {
      // Float.parseFloat() would handle one of these fine, but it won't handle
      // them potentially stacking up at the front, e.g. if multiple expression
      // resolutions have resulted in something like ---4, so do the negations
      // manually one by one
      return new Result.Numeric(-evaluateNumeric(expression.substring(1)));
    } else if (unary == '!') {
      return Result.Boolean.get(!evaluateBoolean(expression.substring(1)));
    }

    // Sort out what we got here
    return switch (expression.toLowerCase()) {
      case "true" -> Result.Boolean.TRUE;
      case "false" -> Result.Boolean.FALSE;
      default -> new Result.Numeric(Float.parseFloat(expression));
    };
  }

  private enum SimpleFunction {
    acos(1, f -> { return (float) Math.toDegrees(Math.acos(f[0])); }),
    asin(1, f -> { return (float) Math.toDegrees(Math.asin(f[0])); }),
    atan(1, f -> { return (float) Math.toDegrees(Math.atan(f[0])); }),
    atan2(2, f -> { return (float) Math.toDegrees(Math.atan2(f[0], f[1])); }),
    atan2p(2, f -> { return (float) Math.toDegrees(LXUtils.atan2pf(f[0], f[1])); }),

    // NB: it's critical that cos/sin/tan come *after* asin/acos/atan so that
    // they are not mistaken for the above when checking for functions, since
    // str.endsWidth("cos") is true when str.endsWidth("acos") is true
    cos(1, f -> { return LXUtils.cosf(Math.toRadians(f[0])); }),
    sin(1, f -> { return LXUtils.sinf(Math.toRadians(f[0])); }),
    tan(1, f -> { return LXUtils.tanf(Math.toRadians(f[0])); }),

    abs(1, f -> { return Math.abs(f[0]); }),
    avg(2, f -> { return 0.5f * (f[0] + f[1]); }),
    cbrt(1, f -> { return (float) Math.cbrt(f[0]); }),
    ceil(1, f -> { return (float) Math.ceil(f[0]); }),
    clamp(3, f -> { return LXUtils.clampf(f[0], f[1], f[2]); }),
    deg(1, f -> { return (float) Math.toDegrees(f[0]); }),
    exp(1, f -> { return (float) Math.exp(f[0]); }),
    floor(1, f -> { return (float) Math.floor(f[0]); }),
    ilerp(3, f -> { return LXUtils.ilerpf(f[0], f[1], f[2]); }),
    lerp(3, f -> { return LXUtils.lerpf(f[0], f[1], f[2]); }),
    log(1, f -> { return (float) Math.log(f[0]); }),
    log10(1, f -> { return (float) Math.log10(f[0]); }),
    max(2, f -> { return Math.max(f[0], f[1]); }),
    min(2, f -> { return Math.min(f[0], f[1]); }),
    pow(2, f -> { return (float) Math.pow(f[0], f[1]); }),
    rad(1, f -> { return (float) Math.toRadians(f[0]); }),
    round(1, f -> { return Math.round(f[0]); }),
    sqrt(1, f -> { return (float) Math.sqrt(f[0]); });

    private interface Compute {
      public float compute(float ... f);
    }

    private final int numArgs;
    private final Compute compute;

    private SimpleFunction(int numArgs, Compute compute) {
      this.numArgs = numArgs;
      this.compute = compute;
    }

    private Result.Numeric evaluate(Result<?> result) {
      switch (result) {
      case Result.List list -> {
        if (this.numArgs != list.results.length) {
          throw new IllegalArgumentException("Function " + name() + " expects " + this.numArgs + " arguments, was given " + list.results.length);
        }
        final float[] args = new float[list.results.length];
        int a = 0;
        for (Result<?> arg : list.results) {
          if (arg instanceof Result.Numeric numeric) {
            args[a++] = numeric.number;
          } else {
            throw new IllegalArgumentException("Function " + name() + " requires numeric arguments, was passed " + arg);
          }
        }
        return new Result.Numeric(this.compute.compute(args));
      }

      case Result.Numeric numeric -> {
        if (this.numArgs != 1) {
          throw new IllegalArgumentException("Function " + name() + " expects " + this.numArgs + " arguments, was given 1");
        }
        return new Result.Numeric(this.compute.compute(numeric.number));
      }

      default -> throw new IllegalArgumentException("Function " + name() + " expects numeric arguments, was passed " + result);
      }
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

    return false;
  }

}
