import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import expression.ExpressionSolver;

public class Main {
	
	public static void main(String args[]) {
		new Main();
	}
	
	public Main() {
		String[] vars = {"pi", "e", "t"};
		double[] values = {3.141592653589, 2.718281828459, 0};
		ExpressionSolver solve = new ExpressionSolver(vars, values);
		Scanner scan = new Scanner(System.in);
		
		System.out.println("Function Tracer:");
		System.out.println("-------------------");
		System.out.println("Enter your function(s) in terms of t. Empty to stop.");
		List<ExpressionSolver.Expression> functions = new ArrayList<>();
		String[] varNames = {"x", "y", "z", "w"};
		for(int i=0; ; i++) {
			String name;
			if(i < varNames.length)
				name = varNames[i];
			else
				name = ("a" + (i - varNames.length));
			System.out.print(name + "(t) = ");
			String func = scan.nextLine();
			if(func.isEmpty() || func.equals("quit"))
				break;
			functions.add(solve.parseString(func));
		}
		if(functions.isEmpty()) {
			scan.close();
			return; //no functions were specified!
		}
		
		System.out.println("Pinpoint at a given t. \"quit\" to exit.");
		while(true) {
			System.out.print("t = ");
			String tt = scan.nextLine();
			// arrays are passed by reference (changes in "values" should update "solve")
			if(tt.isEmpty())
				values[2] += .1;
			else if(tt.equals("quit"))
				break;
			else
				values[2] = solve.evalString(tt);
			
			System.out.print("(");
			for(int i=0; i<functions.size(); i++) {
				if(i != 0)
					System.out.print(", ");
				System.out.print(solve.eval(functions.get(i)));
			}
			System.out.println(")");
		}
		scan.close();
	}
}
