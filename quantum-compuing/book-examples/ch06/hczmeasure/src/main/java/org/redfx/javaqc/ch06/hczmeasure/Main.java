package org.redfx.javaqc.ch06.hczmeasure;

import org.redfx.strange.*;
import *;
import org.redfx.strange.local.*;
import org.redfx.strangefx.render.*;

public class Main {

    public static void main(String[] args) {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(2);
        QuantumStep QuantumStep1 = new QuantumStep();
        QuantumStep1.addGate(new Hadamard(0));
        program.addStep(step1);
        QuantumStep QuantumStep2 = new QuantumStep();
        QuantumStep2.addGate(new Cz(0,1));
        program.addStep(step2);
        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();
        Qubit q0 = qubits[0];
        Qubit q1 = qubits[1];
        int v0 = q0.measure();
        int v1 = q1.measure();
        Renderer.renderProgram(program);
        Renderer.showProbabilities(program, 1000);
    }  

}
