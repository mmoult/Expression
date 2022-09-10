package expression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import expression.ExpressionParser.*;

class SolverTest {
	static ExpressionSolver solve;
	static double maxErr = 0.0001;
	
	@BeforeAll
	static void setup() {
		String[] vars = {"pi", "e"};
		double[] vals = {3.14159265, 2.71828};
		solve = new ExpressionSolver(vars, vals);
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
		assertTrue(ExpressionParser.equals(38.31370849898476, solve.eval(add), maxErr));
	}

	@Test
	void integrationSimple() {
		// Verify that it works for both opt and nonopt
		String expression = "(sin(pi/2) + ln e) / 2";
		double opt = solve.evalString(expression);
		solve.parse.optimize = false;
		double nonopt = solve.evalString(expression);
		solve.parse.optimize = true;
		assertTrue(ExpressionParser.equals(opt, nonopt, maxErr));
		assertTrue(ExpressionParser.equals(opt, 1, maxErr));
	}
}
