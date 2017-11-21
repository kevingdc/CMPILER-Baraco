package baraco.execution.commands.simple;

import baraco.builder.errorcheckers.UndeclaredChecker;
import baraco.execution.commands.EvaluationCommand;
import baraco.execution.commands.ICommand;
import baraco.antlr.parser.BaracoParser.*;
import baraco.ide.View;
import baraco.representations.BaracoArray;
import baraco.representations.BaracoValue;
import baraco.representations.BaracoValueSearcher;
import baraco.semantics.utils.Expression;
import baraco.semantics.utils.StringUtils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrintCommand implements ICommand, ParseTreeListener {

    private final static String TAG = "PrintCommand";
    private final Pattern functionPattern = Pattern.compile("([a-zA-Z0-9]+)\\(([ ,.a-zA-Z0-9]+)\\)");

    private ExpressionContext expressionCtx;

    private String statementToPrint = "";
    private boolean complexExpr = false;
    private boolean arrayAccess = false;
    private boolean isLN = false;

    private List<Object> printExpr = new ArrayList<>();

    private boolean evaluatedExp = false;

    public PrintCommand(StatementContext sCtx) {

        isLN = sCtx.PRINTLN() != null;

        this.expressionCtx = sCtx.expression(0);

        UndeclaredChecker undeclaredChecker = new UndeclaredChecker(expressionCtx);
        undeclaredChecker.verify();

        statementToPrint = "";
    }

    @Override
    public void execute() {
        ParseTreeWalker treeWalker = new ParseTreeWalker();
        treeWalker.walk(this, this.expressionCtx);

        if (isLN)
            statementToPrint += "\n";

        View.printInConsole(this.statementToPrint);

        statementToPrint = "";
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visitErrorNode(ErrorNode node) {

    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {

        if(ctx instanceof LiteralContext) {

            LiteralContext literalCtx = (LiteralContext) ctx;

            if (literalCtx.StringLiteral() != null) {
                String quotedString = literalCtx.StringLiteral().getText();

                this.statementToPrint += StringUtils.removeQuotes(quotedString);

            } else if (literalCtx.IntegerLiteral() != null) {

                ParserRuleContext prCtx = literalCtx;

                while(!(prCtx instanceof StatementContext)){
                    prCtx = prCtx.getParent();
                        if ( (prCtx.getText().startsWith("(") && prCtx.getText().endsWith(")") ) ||
                                 functionPattern.matcher(prCtx.getText()).matches())
                            break; // if it belongs to complex or function
                }

                if (prCtx instanceof StatementContext) { // if not in complex
                    int value = Integer.parseInt(literalCtx.IntegerLiteral().getText());
                    this.statementToPrint += value;
                }
            } else if (literalCtx.FloatingPointLiteral() != null) {
                float value = Float.parseFloat(literalCtx.FloatingPointLiteral().getText());
                this.statementToPrint += value;
            } else if (literalCtx.BooleanLiteral() != null) {
                this.statementToPrint += literalCtx.BooleanLiteral().getText();
            } else if (literalCtx.CharacterLiteral() != null) {
                this.statementToPrint += literalCtx.CharacterLiteral().getText();
            }

        } else if (ctx instanceof ExpressionContext) {

            System.out.println("EXPRESSION CONT " + ctx.getText());

            try {
                int some = Integer.parseInt(ctx.getText());
                return;
            }catch (NumberFormatException ex) {

            }

            ExpressionContext expCtx = (ExpressionContext) ctx;

            ParserRuleContext prCtx = ctx;

            while(!(prCtx instanceof StatementContext)) { // if it belongs to complex
                prCtx = prCtx.getParent();
                if (prCtx.getText().endsWith("]") ||
                        functionPattern.matcher(prCtx.getText()).matches() )
                        break;
            }

            if (prCtx instanceof StatementContext &&
                    !ctx.getText().contains("\"") &&
                    functionPattern.matcher(ctx.getText()).matches()) {

                try {

                    EvaluationCommand evComm = new EvaluationCommand(expCtx);
                    evComm.execute();

                    statementToPrint += evComm.getStringResult();

                } catch (ClassCastException ex) {

                } catch (Expression.ExpressionException ex) {

                }

            }

        } else if(ctx instanceof PrimaryContext) {

            PrimaryContext primaryCtx = (PrimaryContext) ctx;

            System.out.println("PRIMARY CONTEXT " + ctx.getText());

            if(primaryCtx.expression() != null && !primaryCtx.getText().contains("\"")) {

                ParserRuleContext prCtx = primaryCtx;

                while(!(prCtx instanceof StatementContext) || prCtx.getText().equals(ctx.getText()) ){ // if it belongs to complex

                    if ( (prCtx.getText().startsWith("(") && prCtx.getText().endsWith(")")) && !(prCtx.getText().equals(ctx.getText()) ) )
                        break;

                    prCtx = prCtx.getParent();

                }

                if (prCtx instanceof StatementContext || prCtx.getParent() instanceof StatementContext) {

                    ExpressionContext exprCtx = primaryCtx.expression();

                    this.complexExpr = true;
                    System.out.println("Complex expression detected: " + exprCtx.getText());

                    EvaluationCommand evaluationCommand = new EvaluationCommand(exprCtx);
                    evaluationCommand.execute();

                    this.statementToPrint += evaluationCommand.getStringResult();

                }
            }

            else if(primaryCtx.Identifier() != null && !this.complexExpr) {
                String identifier = primaryCtx.getText();

                BaracoValue value = BaracoValueSearcher.searchBaracoValue(identifier);

                System.out.println(identifier + " is identifier");

                if(value != null) {
                    if (value.getPrimitiveType() == BaracoValue.PrimitiveType.ARRAY) {
                        this.arrayAccess = true;
                        this.evaluateArrayPrint(value, primaryCtx);
                    } else if (!this.arrayAccess) {
                        this.statementToPrint += value.getValue();
                        printExpr.add(value.getValue());
                    }
                }


            } else {
                complexExpr = false;
            }
        }
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {

    }

    public String getStatementToPrint() {
        return this.statementToPrint;
    }

    private void evaluateArrayPrint(BaracoValue baracoValue, PrimaryContext primaryCtx) {

        //move up and determine expression contexts
        ExpressionContext parentExprCtx = (ExpressionContext) primaryCtx.getParent().getParent();
        ExpressionContext arrayIndexExprCtx = parentExprCtx.expression(1);

        EvaluationCommand evaluationCommand = new EvaluationCommand(arrayIndexExprCtx);
        evaluationCommand.execute();

        BaracoArray baracoArray = (BaracoArray) baracoValue.getValue();
        BaracoValue arrayMobiValue = baracoArray.getValueAt(evaluationCommand.getResult().intValue());

        this.statementToPrint += arrayMobiValue.getValue().toString();
    }
}
