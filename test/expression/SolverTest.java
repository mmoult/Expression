package expression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import expression.ExpressionParser.*;

class SolverTest {
	static ExpressionSolver solve;
	
	@BeforeAll
	static void setup() {
		String[] vars = {"pi", "e"};
		double[] vals = {3.14159265, 2.71828};
		solve = new ExpressionSolver(vars);
		solve.setValues(vals);
		solve.parse.maxErr = 0.0001;
	}
	
	@Test
	void evalNormalCase() {
		// This is more of a formality than anything, since it is very unlikely
		// that I made a mistake in evaluation. Operations correspond directly
		// with how they evaluate.
		// (sqrt(2) * 8) + (3^2 * 3) = 38.31370849898476
		Addition add = new Addition();
		Multiplication left = new Multiplication();
		Root sqrt = new Root();
		sqrt.setLhs(new Constant(2));
		sqrt.setRhs(new Constant(2));
		left.setLhs(sqrt);
		left.setRhs(new Constant(8));
		add.setLhs(left);
		Multiplication right = new Multiplication();
		Exponentiation square = new Exponentiation();
		square.setLhs(new Constant(3));
		square.setRhs(new Constant(2));
		right.setLhs(square);
		right.setRhs(new Constant(3));
		add.setRhs(right);
		assertTrue(solve.parse.equals(38.31370849898476, solve.eval(add)));
	}

	@Test
	void integrationSimple() {
		// Verify that it works for both opt and nonopt
		String expression = "(sin(pi/2) + ln e) / 2";
		Expression exp = solve.parseString(expression);
		double opt = solve.eval(exp);
		double nonopt = solve.evalString(expression);
		assertTrue(solve.parse.equals(opt, nonopt));
		assertTrue(solve.parse.equals(opt, 1));
	}
	
	@Test
	void integrationPrecedence() {
		String expression = "6 / 2(4 - 1)";
		Expression exp = solve.parseString(expression);
		double opt = solve.eval(exp);
		double nonopt = solve.evalString(expression);
		assertTrue(solve.parse.equals(opt, nonopt));
		assertTrue(solve.parse.equals(opt, 9));
	}
}
