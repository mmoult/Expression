package expression;

import java.util.List;

import expression.ExpressionLexer.Token;

/**Solves algebraic expressions in double precision, ignoring all whitespace. There are eight
 * recognized operators which can be used in the expression:
 * 
 * <pre>( ) ^ r * / + -</pre>
 * 
 * The 'r' is root. For example, "2r 4" is the square root of 4. All other
 * operators perform as expected. There are also ten supported functions:
 * 
 * <pre>cos sin tan log ln round ceil floor max min</pre>
 * 
 * The single-argument functions, cos, sin, tan, ln, round, ceil, and floor, take an argument
 * on their right, ie, "cos 1" or "cos(1)". The two-argument functions, max, min, and log,
 * receive one argument on the left and the other on the right. Note that log's left exponent
 * is the base, and the right is the argument. For example, "10 log 100" = 2. For all functions,
 * parentheses are not necessary except to specify precedence.
 * <p>
 * Note the list of operator precedence, in order from top to bottom:
 * <ol>
 * <li>( )</li>
 * <li>-<i>(unary negation)</i></li>
 * <li>cos sin tan ln round ceil floor</li>
 * <li>^ r log</li>
 * <li>* /</li>
 * <li>+ -</li>
 * <li>max min</li>
 * </ol>
 * If an operation is missing between two values, multiplication is assumed. For example,
 * "(3)4" = 12 and "7x" = -14, if x = -2.
 * <p>
 * Any named values, such as pi or e, should be provided through the constructor or may be
 * changed through {@link #setVariables(String[], double[])}. If the values change, but the
 * variable names remain constant, use {@link #setValues(double[])}.
 * @author Matthew Moulton
 */
public class ExpressionSolver {
	protected String[] variables;
	protected double[] values = null;
	
	protected ExpressionLexer lex;
	protected ExpressionParser parse;
	
	/**
	 * Some operations, such as multiplication by 0 simplification, are only possible if we limit
	 * values to be rational.
	 */
	public boolean rational = true;
	
	/**
	 * @param variables A list of the variable names each beginning with an alphabetic character
	 * and containing only alphanumeric characters.
	 */
	public ExpressionSolver(String[] variables) {
		this.variables = variables;
		lex = new ExpressionLexer();
		parse = new ExpressionParser();
	}
	
	/**
	 * Sets the variables and values usable in expressions.
	 * @param variables a list of variables to use
	 * @param values a list of values to use for the given variables
	 */
	public void setVariables(String[] variables) {
		this.variables = variables;
	}
	
	/**
	 * Sets the values usable in expressions, which will correspond to previously set
	 * variable names.
	 * @param values a list of values to use
	 */
	public void setValues(double[] values) {
		this.values = values;
		if (values.length != variables.length)
			throw new RuntimeException("Lengths of variables and values must match!");
	}
	
	/**
	 * All implementers of the interface must be able to provide a double value with a given
	 * ExpressionSolver (which may contain variables to resolve).
	 */
	public static interface Expression {
		double getValue(ExpressionSolver s);
	}
	
	/**
	 * Calls {@link #parseString(String, boolean)} with optimizations on.
	 * @param expression the string expression to parse
	 * @return the parsed and optimized expression
	 */
	public Expression parseString(String expression) {
		return parseString(expression, true);
	}
	
	/**
	 * Produces an abstract expression tree to represent the string. The expression
	 * structure can be evaluated once or multiple times with different variables
	 * using {@link #eval(Expression)}.
	 * @param expression the string expression to parse
	 * @param whether the expression should be optimized
	 * @return an expression tree that represents the given string
	 */
	public Expression parseString(String expression, boolean optimize) {
		List<Token> tokens = lex.lexExpression(expression);
		
		// Now that we have a token list, we want to parse it
		return parse.parse(tokens, variables, optimize);
	}
	
	/**
	 * Computes a value for the expression, resolving variables as necessary.
	 * @param e the expression to evaluate
	 * @return the double-precision result
	 */
	public double eval(Expression e) {
		if (values == null)
			throw new RuntimeException("Unintialized values! Try calling \"setValues\" first!");
		return e.getValue(this);
	}
	
	/**
	 * Computes a value for the string expression, resolving variables as necessary.
	 * @param expression the expression to evaluate
	 * @return the double-precision result
	 */
	public double evalString(String expression) {
		// If we are immediately evaluating the string, don't perform optimizations
		return eval(parseString(expression, false));
	}
	
	public static class UndefinedVarException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public final String identifier;
		public UndefinedVarException(String identifier) {
			super("Encountered undefined variable \"" + identifier + "\" in expression!");
			this.identifier = identifier;
		}
	}
	
	/**
	 * Returns the corresponding value for the given identifier, if any. If none can be found,
	 * a runtime exception is thrown.
	 * @param identifier the identifier to find the value for
	 * @return the value of the given identifier
	 */
	public double get(String identifier) {
		for (int i = 0; i < variables.length; i++) {
			if (variables[i].equals(identifier))
				return values[i];
		}
		throw new UndefinedVarException(identifier);
	}
	
}