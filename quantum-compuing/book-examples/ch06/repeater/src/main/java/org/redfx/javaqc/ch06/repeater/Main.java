package org.redfx.javaqc.ch06.repeater;

import org.redfx.strange.*;
import org.redfx.strange.gate.*;
import org.redfx.strange.local.*;
import org.redfx.strangefx.render.*;

public class Main {

    public static void main(String[] args) {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(5);
        // QuantumStep QuantumStep0 = new QuantumStep();
        // QuantumStep0.addGate(new X(0));
        // program.addStep(step0);
        QuantumStep QuantumStep1 = new QuantumStep();
        QuantumStep1.addGate(new Hadamard(1));
        QuantumStep1.addGate(new Hadamard(3));
        QuantumStep QuantumStep2 = new QuantumStep();
        QuantumStep2.addGate(new Cnot(1,2));
        QuantumStep2.addGate(new Cnot(3,4));
        QuantumStep QuantumStep3 = new QuantumStep();
        QuantumStep3.addGate(new Cnot(0,1));
        QuantumStep QuantumStep4 = new QuantumStep();
        QuantumStep4.addGate(new Hadamard(0));
        QuantumStep QuantumStep5 = new QuantumStep();
        QuantumStep5.addGate(new Measurement(0));
        QuantumStep5.addGate(new Measurement(1));
        QuantumStep QuantumStep6 = new QuantumStep();
        QuantumStep6.addGate(new Cnot(1,2));
        QuantumStep QuantumStep7 = new QuantumStep();
        QuantumStep7.addGate(new Cz(0,2));

        QuantumStep QuantumStep8 = new QuantumStep();
        QuantumStep8.addGate(new Cnot(2,3));
        QuantumStep QuantumStep9 = new QuantumStep();
        QuantumStep9.addGate(new Hadamard(2));
        QuantumStep QuantumStep10 = new QuantumStep();
        QuantumStep10.addGate(new Measurement(2));
        QuantumStep10.addGate(new Measurement(3));
        QuantumStep QuantumStep11 = new QuantumStep();
        QuantumStep11.addGate(new Cnot(3,4));
        QuantumStep QuantumStep12 = new QuantumStep();
        QuantumStep12.addGate(new Cz(2,4));
        program.addStep(step1);
        program.addStep(step2);
        program.addStep(step3);
        program.addStep(step4);
        program.addStep(step5);
        program.addStep(step6);
        program.addStep(step7);
        program.addStep(step8);
        program.addStep(step9);
        program.addStep(step10);
        program.addStep(step11);
        program.addStep(step12);
        program.initializeQubit(0, .4);
        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();
        Qubit q0 = qubits[0];
        Qubit q1 = qubits[1];
        Qubit q2 = qubits[2];
        int v0 = q0.measure();
        int v1 = q1.measure();
        int v2 = q2.measure();
        System.err.println("v = "+v2);
        Renderer.renderProgram(program);
        Renderer.showProbabilities(program, 1000);
    }  

}
