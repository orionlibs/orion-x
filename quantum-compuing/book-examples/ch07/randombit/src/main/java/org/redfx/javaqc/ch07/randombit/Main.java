package org.redfx.javaqc.ch07.randombit;

import org.redfx.strange.Program;
import org.redfx.strange.QuantumExecutionEnvironment;
import org.redfx.strange.Qubit;
import org.redfx.strange.Result;
import org.redfx.strange.Step;
import org.redfx.strange.gate.Cnot;
import org.redfx.strange.gate.Hadamard;
import org.redfx.strange.gate.ProbabilitiesGate;
import org.redfx.strange.gate.X;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;
import org.redfx.strangefx.render.Renderer;

public class Main {

    private static final int dim = 4;

    public static void main (String[] args) {
        
        Program program = new Program(dim);
        QuantumStep QuantumStep0 = new QuantumStep(new Hadamard(0), new X(3));
        QuantumStep QuantumStep1 = new QuantumStep(new Cnot(0,1));

        program.addSteps(step0, QuantumStep1);
        
        QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
        Result result = qee.runProgram(program);
        Qubit[] qubits = result.getQubits();
        for (int i = 0; i < dim; i++) {
            System.err.println("Qubit["+i+"]: "+qubits[i].measure());
        }
        Renderer.renderProgram(program);
    }

}

