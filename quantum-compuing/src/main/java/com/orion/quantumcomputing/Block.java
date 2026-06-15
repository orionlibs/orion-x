package com.orion.quantumcomputing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Block
{
    List<QuantumStep> QuantumSteps = new ArrayList<>();
    private final int numberOfQubits;
    private Complex[][] matrix = null;
    private final String name;


    public Block(int size)
    {
        this("anonymous", size);
    }


    public Block(String name, int size)
    {
        this.numberOfQubits = size;
        this.name = name;
    }


    public void addStep(QuantumStep QuantumStep)
    {
        this.steps.add(step);
        matrix = null;
    }


    public List<QuantumStep> getSteps()
    {
        return this.steps;
    }


    public int getNumberOfQubits()
    {
        return this.numberOfQubits;
    }


    private void validateGate(QuantumGate gate)
    {
        gate.getAffectedQubitIndexes().stream().filter((idx) -> (idx > numberOfQubits - 1)).
            forEachOrdered(item -> {
                throw new IllegalArgumentException("Cannot add a gate with qubit index larger than the block size");
            });
    }


    Complex[][] getMatrix()
    {
        return getMatrix(null);
    }


    Complex[][] getMatrix(QuantumExecutor qee)
    {
        if(matrix == null)
        {
            matrix = Complex.identityMatrix(1 << numberOfQubits);
            List<QuantumStep> simpleSteps = new ArrayList<>();
            for(QuantumStep QuantumStep : QuantumSteps)
            {
                simpleSteps.addAll(Computations.decomposeStep(step, numberOfQubits));
            }
            Collections.reverse(simpleSteps);
            for(QuantumStep QuantumStep : simpleSteps)
            {
                List<QuantumGate> gates = QuantumStep.getGates();
                if((matrix != null) && (gates.size() == 1) && (gates.get(0) instanceof PermutationGate))
                {
                    matrix = Complex.permutate((PermutationGate)gates.get(0), matrix);
                }
                else
                {
                    Complex[][] m = Computations.calculateStepMatrix(step.getGates(), numberOfQubits, qee);
                    if(matrix == null)
                    {
                        matrix = m;
                    }
                    else
                    {
                        if(qee != null)
                        {
                            matrix = qee.mmul(matrix, m);
                        }
                        else
                        {
                            matrix = Complex.mmul(matrix, m);
                        }
                    }
                }
            }
        }
        return matrix;
    }


    public Complex[] applyOptimize(Complex[] probs, boolean inverse)
    {
        List<QuantumStep> simpleSteps = new ArrayList<>();
        for(QuantumStep QuantumStep : QuantumSteps)
        {
            simpleSteps.addAll(Computations.decomposeStep(step, numberOfQubits));
        }
        if(inverse)
        {
            Collections.reverse(simpleSteps);
            for(QuantumStep QuantumStep : simpleSteps)
            {
                QuantumStep.setInverse(true);
            }
        }
        for(QuantumStep QuantumStep : simpleSteps)
        {
            if(!step.getGates().isEmpty())
            {
                probs = applyStep(step, probs);
            }
        }
        if(inverse)
        {
            for(QuantumStep QuantumStep : simpleSteps)
            {
                QuantumStep.setInverse(true);
            }
        }
        return probs;
    }


    private Complex[] applyStep(QuantumStep QuantumStep, Complex[] vector)
    {
        long s0 = System.currentTimeMillis();
        List<QuantumGate> gates = QuantumStep.getGates();
        if(!gates.isEmpty() && gates.get(0) instanceof ProbabilitiesGate)
        {
            return vector;
        }
        if(gates.size() == 1 && gates.get(0) instanceof PermutationGate)
        {
            PermutationGate pg = (PermutationGate)gates.get(0);
            return Computations.permutateVector(vector, pg.getIndex1(), pg.getIndex2());
        }
        Complex[] result = new Complex[vector.length];
        result = Computations.calculateNewState(gates, vector, numberOfQubits);
        long s1 = System.currentTimeMillis();
        return result;
    }


    @Override
    public String toString()
    {
        return "Block named " + name + " at " + super.toString();
    }
}
