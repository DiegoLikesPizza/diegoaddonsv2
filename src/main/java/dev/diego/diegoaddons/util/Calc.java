package dev.diego.diegoaddons.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * A small arithmetic evaluator, for the search box that doubles as a calculator.
 *
 * <p>Shunting-yard rather than a recursive parser: the whole grammar is four infix operators and a
 * bracket, and shunting-yard expresses exactly that in one pass with no grammar to maintain.
 *
 * <p><b>{@code x} multiplies.</b> That is the point of having this at all - the thing people type in
 * SkyBlock is {@code 2x2} or {@code 32x64}, because that is how the game writes stack sizes and
 * recipe amounts. Supporting only {@code *} would mean a calculator you have to translate into
 * before using.
 *
 * <p>Numbers may carry SkyBlock's own shorthand: {@code 1.4m} is 1,400,000. Same reasoning - the
 * amounts being multiplied are usually read off a lore line that already writes them that way.
 *
 * <p>Anything that is not a well-formed expression returns null rather than throwing or guessing.
 * The caller is a search box, and most of what is typed into it is a word, not a sum.
 */
public final class Calc {
    private Calc() {
    }

    /**
     * Evaluates {@code expr}, or null when it is not arithmetic.
     *
     * <p>Returns null for anything with no digit in it at all before doing any work, which is the
     * common case: a search for "dragon" should not walk a parser.
     */
    public static Double eval(String expr) {
        if (expr == null || expr.isBlank() || expr.chars().noneMatch(Character::isDigit)) {
            return null;
        }
        List<String> tokens = tokenise(expr);
        if (tokens == null) {
            return null;
        }
        List<String> rpn = toRpn(tokens);
        return rpn == null ? null : evaluate(rpn);
    }

    /** Splits into numbers, operators and brackets, or null on any character that is neither. */
    private static List<String> tokenise(String expr) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < expr.length()
                        && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    i++;
                }
                // One shorthand suffix, if it is there: 1.4m, 60k, 2b.
                if (i < expr.length() && "kmbt".indexOf(Character.toLowerCase(expr.charAt(i))) >= 0) {
                    i++;
                }
                out.add(expr.substring(start, i));
            } else if ("+-*x/()".indexOf(Character.toLowerCase(c)) >= 0) {
                out.add(String.valueOf(Character.toLowerCase(c)));
                i++;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else {
                return null;   // a letter that is not "x" or a suffix: this is a word, not a sum
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static int precedence(String token) {
        return switch (token) {
            case "+", "-" -> 1;
            case "*", "x", "/" -> 2;
            default -> -1;
        };
    }

    /** Shunting-yard: infix to postfix. Null when the brackets do not balance. */
    private static List<String> toRpn(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        for (String token : tokens) {
            if (number(token) != null) {
                output.add(token);
            } else if (token.equals("(")) {
                ops.push(token);
            } else if (token.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) {
                    output.add(ops.pop());
                }
                if (ops.isEmpty()) {
                    return null;
                }
                ops.pop();
            } else if (precedence(token) > 0) {
                // Left-associative, so an operator of equal precedence pops first: 8/4/2 is 1.
                while (!ops.isEmpty() && precedence(ops.peek()) >= precedence(token)) {
                    output.add(ops.pop());
                }
                ops.push(token);
            } else {
                return null;
            }
        }
        while (!ops.isEmpty()) {
            String op = ops.pop();
            if (op.equals("(")) {
                return null;   // an unclosed bracket
            }
            output.add(op);
        }
        return output;
    }

    /** Evaluates postfix. Null when the expression is malformed or divides by zero. */
    private static Double evaluate(List<String> rpn) {
        Deque<Double> stack = new ArrayDeque<>();
        for (String token : rpn) {
            Double num = number(token);
            if (num != null) {
                stack.push(num);
                continue;
            }
            if (stack.size() < 2) {
                return null;
            }
            double b = stack.pop();
            double a = stack.pop();
            switch (token) {
                case "+" -> stack.push(a + b);
                case "-" -> stack.push(a - b);
                case "*", "x" -> stack.push(a * b);
                case "/" -> {
                    if (b == 0) {
                        // Not infinity: "= Infinity" beside a search box is noise, and a division by
                        // zero here is a half-typed expression far more often than a real question.
                        return null;
                    }
                    stack.push(a / b);
                }
                default -> {
                    return null;
                }
            }
        }
        if (stack.size() != 1) {
            return null;
        }
        double result = stack.pop();
        return Double.isFinite(result) ? result : null;
    }

    /** A number with an optional k/m/b/t suffix, or null when the token is not one. */
    private static Double number(String token) {
        if (token.isEmpty()) {
            return null;
        }
        char last = Character.toLowerCase(token.charAt(token.length() - 1));
        int multiplier = switch (last) {
            case 'k' -> 1_000;
            case 'm' -> 1_000_000;
            case 'b' -> 1_000_000_000;
            case 't' -> 1_000_000_000;   // times a thousand again below
            default -> 1;
        };
        String digits = multiplier == 1 ? token : token.substring(0, token.length() - 1);
        if (digits.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(digits) * multiplier;
            return last == 't' ? value * 1_000 : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The result, written the way the rest of the mod writes numbers.
     *
     * <p>Whole results lose the decimal point - {@code 2x2} answering "= 4.0" looks like a bug -
     * and everything else is grouped and cut to two places.
     */
    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format(Locale.ROOT, "%,d", (long) value);
        }
        return String.format(Locale.ROOT, "%,.2f", value);
    }
}
