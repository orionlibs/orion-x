package org.redfx.javaqc.ch08.haha;

import org.redfx.strange.*;
import org.redfx.strange.gate.*;
import org.redfx.strange.local.*;
import org.redfx.strangefx.render.*;

import java.util.Random;

public class Main {

    public static void main(String[] args) {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();

        Program program = new Program(2);
        QuantumStep QuantumStep0 = new QuantumStep();
        QuantumStep0.addGate(new X(0));

        QuantumStep QuantumStep1 = new QuantumStep();
        QuantumStep1.addGate(new Hadamard(0));
        QuantumStep1.addGate(new Hadamard(1));

        QuantumStep QuantumStep2 = new QuantumStep();
        QuantumStep2.addGate(new Hadamard(0));
        QuantumStep2.addGate(new Hadamard(1));

        program.addStep(step0);
        program.addStep(step1);
        program.addStep(step2);

        Result result = simulator.runProgram(program);
        Qubit[] qubit = result.getQubits();

        Renderer.renderProgram(program);
    }

}
