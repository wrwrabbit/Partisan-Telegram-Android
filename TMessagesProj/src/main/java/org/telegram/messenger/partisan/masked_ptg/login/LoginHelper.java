package org.telegram.messenger.partisan.masked_ptg.login;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Stack;

public class LoginHelper {
    private String remainingExpression;
    private char decimalSeparator;
    private final Stack<BigDecimal> valueStack = new Stack<>();
    private final Stack<Character> operationStack = new Stack<>();

    private LoginHelper(String expression, char decimalSeparator) {
        this.remainingExpression = expression;
        this.decimalSeparator = decimalSeparator;
    }

    public static BigDecimal calculateExpression(String expression, char decimalSeparator) throws Exception {
        return new LoginHelper(expression, decimalSeparator).calculateExpressionInternal();
    }

    private BigDecimal calculateExpressionInternal() throws Exception {
        while (!remainingExpression.isEmpty()) {
            char firstChar = remainingExpression.charAt(0);
            if (("0123456789" + decimalSeparator).contains(String.valueOf(firstChar))) {
                processValue();
            } else {
                processOperation(firstChar);
            }
        }
        while (!operationStack.isEmpty()) {
            performLastOperation();
        }
        return !valueStack.isEmpty() ? valueStack.pop() : null;
    }

    private void processValue() throws Exception {
        int numberEndPos = 0;
        BigDecimal currentValue = null;
        try {
            while (numberEndPos < remainingExpression.length()) {
                String currentSubstring = remainingExpression.substring(0, numberEndPos + 1)
                        .replace(decimalSeparator, '.');
                currentValue = new BigDecimal(currentSubstring);
                numberEndPos++;
            }
        } catch (Exception ignore) {
        }
        if (currentValue == null) {
            throw new Exception();
        }
        remainingExpression = remainingExpression.substring(numberEndPos);
        valueStack.push(currentValue);
    }

    private void processOperation(char firstChar) throws Exception {
        char operation = firstChar;
        remainingExpression = remainingExpression.substring(1);
        if (operation == '%') {
            if (!valueStack.isEmpty()) {
                BigDecimal newValue = valueStack.pop().divide(new BigDecimal(100));
                if (!valueStack.isEmpty()) {
                    BigDecimal oldValue = valueStack.peek();
                    newValue = newValue.multiply(oldValue);
                }
                valueStack.push(newValue);
            }
        } else {
            Character prevOperation = !operationStack.isEmpty() ? operationStack.peek() : null;
            if (prevOperation != null && getOperationPriority(operation) <= getOperationPriority(prevOperation)) {
                performLastOperation();
            }
            if (!remainingExpression.isEmpty()) {
                operationStack.push(operation);
            }
        }
    }

    private static int getOperationPriority(char operation) throws Exception {
        if ("+-".contains(String.valueOf(operation))) {
            return 1;
        } else if ("×/".contains(String.valueOf(operation))) {
            return 2;
        } else {
            throw new Exception();
        }
    }

    private void performLastOperation() {
        BigDecimal val2 = valueStack.pop();
        BigDecimal val1 = !valueStack.isEmpty() ? valueStack.pop() : BigDecimal.ZERO;
        char lastOperation = operationStack.pop();
        BigDecimal result = calculateOperationResult(val1, val2, lastOperation);
        valueStack.push(result);
    }

    private static BigDecimal calculateOperationResult(BigDecimal val1, BigDecimal val2, char action) {
        switch (action) {
            case '+': return val1.add(val2);
            case '-': return val1.subtract(val2);
            case '×': return val1.multiply(val2);
            case '/': return val1.divide(val2, 32, RoundingMode.HALF_UP);
            default: throw new RuntimeException("Invalid action");
        }
    }
}
