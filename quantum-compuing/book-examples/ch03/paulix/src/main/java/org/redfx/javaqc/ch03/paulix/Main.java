package org.redfx.javaqc.ch03.paulix;

import org.redfx.strange.*;
import *;
import org.redfx.strange.local.*;

public class Main {

    public static void main(String[] args) {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(1);
        QuantumStep QuantumStep = new QuantumStep();
        QuantumStep.addGate(new X(0));
        program.addStep(step);
        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();
        Qubit zero = qubits[0];
        int value = zero.measure();
        System.out.println("Value = "+value);
    }  

}
