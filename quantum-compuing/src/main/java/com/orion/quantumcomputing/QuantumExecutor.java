package com.orion.quantumcomputing;

import java.util.function.Consumer;

public interface QuantumExecutor
{
    QuantumResult runProgram(QuantumProgram p);


    void runProgram(QuantumProgram p, Consumer<QuantumResult> result);


    default Complex[][] mmul(Complex[][] a, Complex[][] b)
    {
        return Complex.mmul(a, b);
    }
    // Future<Result> runProgram(Program p);
}
