package org.redfx.javaqc.ch10.groveroracle;

import Complex;
import org.redfx.strange.Program;
import org.redfx.strange.QuantumExecutionEnvironment;
import org.redfx.strange.Qubit;
import org.redfx.strange.Result;
import org.redfx.strange.Step;
import org.redfx.strange.gate.Oracle;
import org.redfx.strange.gate.ProbabilitiesGate;
import org.redfx.strange.gate.X;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;
import org.redfx.strangefx.render.Renderer;

public class Main {

    public static void main(String[] args) {
        System.err.println("Use 00 as input");
        try00();
        System.err.println("\nUse 01 as input");
        try01();
        System.err.println("\nUse 10 as input");
        try10();
        System.err.println("\nUse 11 as input");
        try11();
    }

    static void try00() {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(2);

        QuantumStep oracleStep = new QuantumStep();
        Oracle oracle = createOracle();
        oracleStep.addGate(oracle);

        program.addStep(oracleStep);
        QuantumStep probStep = new QuantumStep();
        probStep.addGate(new ProbabilitiesGate(0));
        program.addStep(probStep);

        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();

        System.err.println("measured = "+ qubits[0].measure() + ", " + qubits[1].measure());
        Renderer.renderProgram(program);

    }

    static void try01() {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = null;
        program = new Program(2);

        QuantumStep prepareStep = new QuantumStep();
        prepareStep.addGate(new X(0));
        program.addStep(prepareStep);

        QuantumStep oracleStep = new QuantumStep();
        Oracle oracle = createOracle();
        oracleStep.addGate(oracle);
        program.addStep(oracleStep);

        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();

        System.err.println(", measured = "
                + qubits[0].measure() + ", " + qubits[1].measure());

    }

    static void try10() {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = null;
        program = new Program(2);

        QuantumStep prepareStep = new QuantumStep();
        prepareStep.addGate(new X(1));
        program.addStep(prepareStep);

        QuantumStep oracleStep = new QuantumStep();
        Oracle oracle = createOracle();
        oracleStep.addGate(oracle);
        program.addStep(oracleStep);

        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();

        System.err.println(", measured = "
                + qubits[0].measure() + ", " + qubits[1].measure());
    }

    static void try11() {
        QuantumExecutionEnvironment simulator = new SimpleQuantumExecutionEnvironment();
        Program program = new Program(2);

        QuantumStep prepareStep = new QuantumStep();
        prepareStep.addGate(new X(0));
        prepareStep.addGate(new X(1));
        program.addStep(prepareStep);

        QuantumStep oracleStep = new QuantumStep();
        Oracle oracle = createOracle();
        oracleStep.addGate(oracle);
        program.addStep(oracleStep);

        Result result = simulator.runProgram(program);
        Qubit[] qubits = result.getQubits();

        System.err.println(", measured = "
                + qubits[0].measure() + ", " + qubits[1].measure());

    }

    static Oracle createOracle() {
        Complex[][] matrix = new Complex[4][4];

        matrix[0][0] = Complex.ONE;
        matrix[1][1] = Complex.ONE;
        matrix[2][2] = Complex.ONE.mul(-1);
        matrix[3][3] = Complex.ONE;

        return new Oracle(matrix);
    }

}
