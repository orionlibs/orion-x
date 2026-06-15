package com.orion.quantumcomputing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class QuantumProgram
{
    private final int numberOfQubits;
    private final ArrayList<QuantumStep> steps = new ArrayList<>();
    private QuantumResult result;
    private double[] initAlpha;
    // cache decomposedSteps
    private List<QuantumStep> decomposedSteps = null;


    public QuantumProgram(int numberOfQubits, QuantumStep... steps)
    {
        this.numberOfQubits = numberOfQubits;
        this.initAlpha = new double[numberOfQubits];
        Arrays.fill(initAlpha, 1d);
        addSteps(steps);
    }


    public void initialiseQubit(int idx, double alpha)
    {
        if(idx >= numberOfQubits)
        {
            throw new IllegalArgumentException("Cannot initialise qubit " + idx + " since we have only " + numberOfQubits + " qubits.");
        }
        this.initAlpha[idx] = alpha;
    }


    public double[] getInitialAlphas()
    {
        return this.initAlpha;
    }


    public void addStep(QuantumStep step)
    {
        if(!ensureMeasureSafe(Objects.requireNonNull(step)))
        {
            throw new IllegalArgumentException("Cannot add a superposition step to a measured qubit");
        }
        step.setIndex(steps.size());
        step.setProgram(this);
        steps.add(step);
        this.decomposedSteps = null;
    }


    public void addSteps(QuantumStep... moreSteps)
    {
        for(QuantumStep step : moreSteps)
        {
            addStep(step);
        }
    }


    private boolean ensureMeasureSafe(QuantumStep newStep)
    {
        // determine which qubits might get superpositioned
        List<Integer> mainQubits = new ArrayList<>();
        for(QuantumGate g : newStep.getGates())
        {
            if(g instanceof Hadamard)
            {
                mainQubits.add(g.getMainQubitIndex());
            }
            else if(g instanceof Cnot)
            {
                mainQubits.add(((Cnot)g).getSecondQubitIndex());
            }
        }
        for(QuantumStep step : this.getSteps())
        {
            boolean match = step.getGates()
                                .stream()
                                .filter(g -> g instanceof QuantumMeasurement).map(QuantumGate::getMainQubitIndex)
                                .anyMatch(mainQubits::contains);
            if(match)
            {
                return false;
            }
        }
        ;
        return true;
    }


    public List<QuantumStep> getSteps()
    {
        return this.steps;
    }


    @Deprecated
    public List<QuantumStep> getDecomposedSteps()
    {
        return this.decomposedSteps;
    }


    @Deprecated
    public void setDecomposedSteps(List<QuantumStep> ds)
    {
        this.decomposedSteps = ds;
    }


    public int getNumberQubits()
    {
        return this.numberOfQubits;
    }


    public Result getResult()
    {
        return this.result;
    }


    public void setResult(Result r)
    {
        this.result = r;
    }


    public void printInfo()
    {
        System.out.println("Info about Quantum Program");
        System.out.println("==========================");
        System.out.println("Number of qubits = " + numberOfQubits + ", number of steps = " + steps.size());
        steps.forEach(step -> {
            System.out.println("Step: " + step.getGates());
        });
        System.out.println("==========================");
    }
}
