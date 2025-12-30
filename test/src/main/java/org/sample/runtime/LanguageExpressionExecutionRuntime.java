package org.sample.runtime;

import lombok.NonNull;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public final class LanguageExpressionExecutionRuntime implements ExecutionRuntime {
    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public @NonNull Result execute(@NonNull final Input input) {
        try {
            String statements = input.command().trim();
            if (statements.isEmpty()) return new Result(1, "No statements provided");

            Expression expression = parser.parseExpression(statements);
            String result = (String) expression.getValue();

            return new Result(0, result != null ? result : "null");
        } catch (Exception e) {
            return new Result(1, e.getMessage());
        }
    }

    @Override
    public @NonNull String name() {
        return "LanguageExpression";
    }
}
