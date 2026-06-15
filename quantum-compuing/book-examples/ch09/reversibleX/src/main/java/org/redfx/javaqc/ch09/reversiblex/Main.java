package org.redfx.javaqc.ch09.reversiblex;

import org.redfx.strange.Program;
import org.redfx.strange.QuantumExecutionEnvironment;
import org.redfx.strange.Qubit;
import org.redfx.strange.Result;
import org.redfx.strange.Step;
import org.redfx.strange.gate.X;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;
import org.redfx.strangefx.render.Renderer;

import java.util.Random;

public class Main {

    public static void main(String[] args) {

        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(1);
        QuantumStep QuantumStep0 = new QuantumStep();
        QuantumStep0.addGate(new X(0));

        QuantumStep QuantumStep1 = new QuantumStep();
        QuantumStep1.addGate(new X(0));
        program.addStep(step0);
        program.addStep(step1);
        program.initializeQubit(0,.5);

        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();
        Renderer.renderProgram(program);
        Renderer.showProbabilities(program,1000);
    }

}
