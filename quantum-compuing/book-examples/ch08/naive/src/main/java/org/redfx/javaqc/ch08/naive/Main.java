package org.redfx.javaqc.ch08.naive;

import org.redfx.strange.*;
import *;
import org.redfx.strange.local.*;
import org.redfx.strangefx.render.*;

import java.util.Random;

public class Main {

    public static void main(String[] args) {
        final int SIZE = 4;
        Random random = new Random();

        boolean[] aliceBits = new boolean[SIZE];
        for (int i = 0 ; i < SIZE; i++) {
            aliceBits[i] = random.nextBoolean();
        }

        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(SIZE);
        QuantumStep QuantumStep1 = new QuantumStep();
        QuantumStep QuantumStep2 = new QuantumStep();
        for (int i = 0; i < SIZE; i++) {
            if (aliceBits[i]) QuantumStep1.addGate(new X(i));
            QuantumStep2.addGate(new Measurement(i));
        }

        program.addStep(step1);
        program.addStep(step2);

        Result result = simulator.runProgram(program);
        Qubit[] qubit = result.getQubits();

        int[] measurement = new int[SIZE];
        boolean[] bobBits = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            measurement[i] = qubit[i].measure();
            bobBits[i] = measurement[i] == 1;
            System.err.println("Alice sent "+(aliceBits[i] ? "1" : "0") + " and Bob received "+(bobBits[i] ? "1" : "0"));
        }

        Renderer.renderProgram(program);
    }

}
