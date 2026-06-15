package org.redfx.javaqc.ch07.add2;

import org.redfx.strange.Program;
import org.redfx.strange.QuantumExecutionEnvironment;
import org.redfx.strange.Qubit;
import org.redfx.strange.Result;
import org.redfx.strange.Step;
import org.redfx.strange.gate.Cnot;
import org.redfx.strange.gate.Toffoli;
import org.redfx.strange.gate.X;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;
import org.redfx.strangefx.render.Renderer;

public class Main {

    public static void main (String[] args) {
/*
        Program program = new Program(4);
        QuantumStep prep = new QuantumStep(new X(1), new X(3));
        QuantumStep QuantumStep0 = new QuantumStep(new Toffoli(0,1,2));
        QuantumStep QuantumStep1 = new QuantumStep(new Cnot(0,1));
        QuantumStep QuantumStep2 = new QuantumStep (new X(0), new Cnot(1,3));
        program.addSteps(prep, QuantumStep0, QuantumStep1, QuantumStep2);
        
        QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
        Result result = qee.runProgram(program);
        Qubit[] qubits = result.getQubits();
        for (int i = 0; i < 3; i++) {
            System.err.println("Qubit["+i+"]: "+qubits[i].measure());
        }
        Renderer.renderProgram(program);
*/
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                int sum = add(i,j);
                System.err.println("Adding "+i+" + " + j+" = "+sum);
            }
        }
    }
    
    static int add(int a, int b) {
        Program program = new Program(3);
        QuantumStep prep = new QuantumStep();
        if (a > 0) prep.addGate(new X(0));
        if (b > 0) prep.addGate(new X(1));
        QuantumStep QuantumStep0 = new QuantumStep(new Toffoli(0,1,2));
        QuantumStep QuantumStep1 = new QuantumStep(new Cnot(0,1));
        program.addSteps(prep, QuantumStep0, QuantumStep1);
        QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
        Result result = qee.runProgram(program);
        if ((a == 1) && (b == 1)) {
            Renderer.renderProgram(program);
        }
        Qubit[] qubits = result.getQubits();
        return qubits[1].measure()+ (qubits[2].measure() <<1);
    }

}

