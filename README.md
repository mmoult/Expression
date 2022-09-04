![](https://github.com/mmoult/Expression/workflows/"Java CI with Gradle"/badge.svg)

# Expression
A small library to evaluate String expressions or solve for a variable (future) in Java.

## Table of contents
* [Technologies](#technologies)
* [Expression syntax](#expression-syntax)
* [Use](#use)

## Technologies
The project requires a Java version of at least 1.8 to compile. The project also uses Gradle, but a Gradle wrapper has been provided. See [here](https://docs.gradle.org/current/userguide/gradle_wrapper.html) to learn more.

## Expression Syntax
Solves algebraic expressions in double precision, ignoring all whitespace. There are eight
recognized operators which can be used in the expression:

<pre>( ) ^ r * / + -</pre>

The 'r' is root. For example, "2r 4" is the square root of 4. All other operators perform as expected. There are also ten supported functions:

<pre>cos sin tan log ln round ceil floor max min</pre>

The single-argument functions, cos, sin, tan, ln, round, ceil, and floor, take an argument
on their right, ie, "cos1" or "cos(1)". The two-argument functions, max, min, and log,
receive one argument on the left and the other on the right. Note that log's left exponent
is the base, and the right is the argument. For example, "10 log 100" = 2. For all functions,
parentheses are not necessary except to specify precedence.
<p>
Note the list of operator precedence, in order from top to bottom:
<ol>
<li>( )</li>
<li>-<i>(unary negation)</i> cos sin tan ln round ceil floor</li>
<li>^ r log</li>
<li>* /</li>
<li>+ -</li>
<li>max min</li>
</ol>
If an operation is missing between two values, multiplication is assumed. For example,
"(3)4" = 12 and "7x" = -14, if x = -2.

## Use
The class, `expression.ExpressionSolver` is the public interface to use. Construct the solver with the names of the variables that will be used (no variables are provided by default, not even pi or e) and the values which correspond to those variable names.

There are two ways to evaluate a string expression. If the same expression (with different variable values) will be used multiple times, use `parseString(String expression)`, which will construct an `Expression` object to represent the expression. The same expression object can be evaluated multiple times, even if the values of the variables are changed by `setVariables(String[] variables, double[] values)` or `setValues(double[] values)`. Evaluate the expression by calling `eval(Expression e)`.

The second way to evaluate a string expression is by using `evalString(String expression)`. Under the covers, it follows the same process as described above. The only disadvantage to using it is that the Expression is not returned, and thus not available for reuse. This is recommended if the expression will only be evaluated once.
