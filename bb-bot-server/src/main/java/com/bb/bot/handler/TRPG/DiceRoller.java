package com.bb.bot.handler.TRPG;

import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.*;

/**
 * 跑团骰子工具
 * @author ren
 */
public class DiceRoller {
    private static final Random random = new Random();

    /**
     * 骰子命令正则
     */
    public static final String COMMAND_REG = "\\.(rb|rp|ww|ra|rc|r?)\\s*(.*)";
    public static final Pattern COMMAND_PATTERN = Pattern.compile(COMMAND_REG);

    /**
     * 骰子表达式正则
     */
    public static final Pattern DICE_PATTERN = Pattern.compile("(\\d*)d(\\d+|%|F(?:\\.1|\\.2)?)");

    public static void main(String[] args) {
        String input = ".ww5a11";
        String result = parseAndRoll(input);
        System.out.println(result);
    }

    /**
     * 进行骰子投掷
     *
     * 一个基本，但要素齐全的骰子指令格式如下：
     * .[r][d100]
     * 让我们分解一下：
     * . 起始符号
     * r 指令名*，支持r-标准骰子（可省略不写），rb-奖励骰，rp-惩罚骰，ww-无限骰，ra或rc（等价为 d%）
     * d100 骰子表达式*
     * 详情参考https://www.paotuan.io/dice/
     * 在该网站格式的基础上，去掉了h指令以及骰子表达式后面的那些属性检定指令
     */
    public static String parseAndRoll(String input) {
        try {
            Matcher matcher = COMMAND_PATTERN.matcher(input);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("无效的指令格式");
            }

            String command = matcher.group(1);
            String expression = matcher.group(2).trim();

            return switch (command) {
                case "", "r" -> rollStandardDice(expression);
                case "rb" -> rollBonusDice(expression);
                case "rp" -> rollPenaltyDice(expression);
                case "ww" -> rollWildDice(expression);
                case "ra", "rc" -> rollStandardDice("d%");
                default -> throw new IllegalArgumentException("未知的指令: " + command);
            };
        } catch (IllegalArgumentException e) {
           return "输入指令有误: " + e.getMessage();
        }
    }

    /**
     * 标准投掷
     */
    private static String rollStandardDice(String expression) {
        int total = 0;
        List<Integer> results = rollDiceWithExpression(expression);

        for (Integer result : results) {
            total += result;
        }

        // Handle arithmetic in the expression
        String arithmeticExpression = expression.replaceAll("(\\d*)d(\\d+|%|F(?:\\.1|\\.2)?)", Integer.toString(total));
        total = evaluateArithmeticExpression(arithmeticExpression);

        return "进行骰子投掷：" + results.toString() + "，总点数：" + total;
    }

    /**
     * 奖励骰
     */
    private static String rollBonusDice(String expression) {
        int bestResult = Integer.MAX_VALUE;

        //没有表达式默认为1d100
        if (StringUtils.isBlank(expression)) {
            expression = "d%";
        }
        //表达式为.rb[数字]时，自动补全为.rb[数字]d%
        if (!expression.contains("d")) {
            expression += "d%";
        }
        List<Integer> resultList = rollDiceWithExpression(expression);
        for (Integer result : resultList) {
            bestResult = Math.min(bestResult, result);
        }

        return "进行奖励骰投掷：" + resultList.toString() + "，取最低结果: " + bestResult;
    }

    /**
     * 惩罚骰
     */
    private static String rollPenaltyDice(String expression) {
        int worstResult = Integer.MIN_VALUE;

        //没有表达式默认为1d100
        if (StringUtils.isBlank(expression)) {
            expression = "d%";
        }
        //表达式为.rb[数字]时，自动补全为.rb[数字]d%
        if (!expression.contains("d")) {
            expression += "d%";
        }
        List<Integer> resultList = rollDiceWithExpression(expression);
        for (Integer result : resultList) {
            worstResult = Math.max(worstResult, result);
        }

        return "进行惩罚骰投掷：" + resultList.toString() + "，取最高结果: " + worstResult;
    }

    /**
     * 无限骰
     */
    private static String rollWildDice(String expression) {
        String[] parts = expression.split("a");
        int x = Integer.parseInt(parts[0]);
        int y = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;

        if (y < 2 || y > 10) {
            throw new IllegalArgumentException("无效的无限骰重掷点数: " + y);
        }

        List<Integer> resultList = new ArrayList<>();
        int total = 0;
        int count = x;

        while (count > 0) {
            int result = rollDice("10");
            resultList.add(result);

            if (result >= y) {
                count++;
            }
            if (result >= 8) {
                total++;
            }
            count--;
        }

        return "进行无限骰投掷：" + resultList + "，对结果≥8的骰子计数: " + total;
    }

    /**
     * 按骰子表达式进行投掷
     */
    private static List<Integer> rollDiceWithExpression(String expression) {
        List<Integer> results = new ArrayList<>();
        Matcher matcher = DICE_PATTERN.matcher(expression);

        while (matcher.find()) {
            String countStr = matcher.group(1);
            String type = matcher.group(2);
            int count = countStr.isEmpty() ? 1 : Integer.parseInt(countStr);

            if (count < 1 || count > 100) {
                throw new IllegalArgumentException("无效的骰子数量: " + count);
            }

            int[] rolls = new int[count];
            for (int i = 0; i < count; i++) {
                //按骰子类型投掷一次
                rolls[i] = rollDice(type);
                //添加该次投掷结果
                results.add(rolls[i]);
            }
        }

        return results;
    }

    /**
     * 按骰子类型投掷一次
     */
    private static int rollDice(String type) {
        switch (type) {
            case "%":
            case "100":
                return random.nextInt(100) + 1;
            case "F":
                return random.nextInt(3) - 1;
            case "F.1":
                return random.nextInt(6) == 5 ? 1 : (random.nextInt(6) == 0 ? -1 : 0);
            case "F.2":
                return random.nextInt(3) - 1;
            default:
                try {
                    return random.nextInt(Integer.parseInt(type)) + 1;
                }catch (Exception e) {
                    throw new IllegalArgumentException("无效的骰子类型: " + type);
                }
        }
    }

    private static int evaluateArithmeticExpression(String expression) {
        try {
            return (int) eval(expression);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的数学表达式: " + expression);
        }
    }

    // Simple expression evaluator (supports basic arithmetic operations)
    // For more complex expressions, consider using an existing library like exp4j or a scripting engine like Java's built-in Nashorn
    public static double eval(final String str) {
        class Parser {
            int pos = -1, c;

            void eatChar() {
                c = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            void eatSpace() {
                while (Character.isWhitespace(c)) eatChar();
            }

            double parse() {
                eatChar();
                double v = parseExpression();
                if (c != -1) throw new RuntimeException("Unexpected: " + (char)c);
                return v;
            }

            double parseExpression() {
                double v = parseTerm();
                for (;;) {
                    eatSpace();
                    if (c == '+') { // addition
                        eatChar();
                        v += parseTerm();
                    } else if (c == '-') { // subtraction
                        eatChar();
                        v -= parseTerm();
                    } else {
                        return v;
                    }
                }
            }

            double parseTerm() {
                double v = parseFactor();
                for (;;) {
                    eatSpace();
                    if (c == '/') { // division
                        eatChar();
                        v /= parseFactor();
                    } else if (c == '*' || c == '(') { // multiplication
                        if (c == '*') eatChar();
                        v *= parseFactor();
                    } else {
                        return v;
                    }
                }
            }

            double parseFactor() {
                double v;
                boolean negate = false;
                eatSpace();
                if (c == '+' || c == '-') { // unary plus & minus
                    negate = c == '-';
                    eatChar();
                    eatSpace();
                }
                if (c == '(') { // parentheses
                    eatChar();
                    v = parseExpression();
                    if (c == ')') eatChar();
                } else { // numbers
                    StringBuilder sb = new StringBuilder();
                    while ((c >= '0' && c <= '9') || c == '.') {
                        sb.append((char)c);
                        eatChar();
                    }
                    if (sb.length() == 0) throw new RuntimeException("Unexpected: " + (char)c);
                    v = Double.parseDouble(sb.toString());
                }
                eatSpace();
                if (c == '^') { // exponentiation
                    eatChar();
                    v = Math.pow(v, parseFactor());
                }
                if (negate) v = -v; // unary minus
                return v;
            }
        }
        return new Parser().parse();
    }
}

