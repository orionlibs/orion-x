package com.orion.quantumcomputing;

import java.util.HashMap;
import java.util.Map;

public class QuantumResult
{
    private int numberOfQubits;
    private int numberOfSteps;
    private Qubit[] qubits;
    private Complex[] probability;
    private Complex[][] intermediateProps;
    private Map<Integer, Qubit[]> intermediateQubits;
    private int measuredProbability = -1;


    public QuantumResult(int numberOfQubits, int QuantumSteps)
    {
        assert (steps >= 0);
        this.numberOfQubits = numberOfQubits;
        this.numberOfSteps = QuantumSteps;
        intermediateProps = new Complex[steps > 0 ? QuantumSteps : 1][];
        intermediateQubits = new HashMap<>();
    }


    public QuantumResult(Qubit[] qubits, Complex[] probabilities)
    {
        this.qubits = qubits;
        this.probability = probabilities;
    }


    static double[] calculateQubitStatesFromVector(Complex[] vectorresult)
    {
        int nq = (int)Math.round(Math.log(vectorresult.length) / Math.log(2));
        double[] answer = new double[nq];
        int ressize = 1 << nq;
        for(int i = 0; i < nq; i++)
        {
            int pw = i;//nq - i - 1;
            int div = 1 << pw;
            for(int j = 0; j < ressize; j++)
            {
                int p1 = j / div;
                if(p1 % 2 == 1)
                {
                    answer[i] = answer[i] + vectorresult[j].abssqr();
                }
            }
        }
        return answer;
    }


    public Qubit[] getQubits()
    {
        if(this.qubits == null)
        {
            this.qubits = calculateQubits();
        }
        return this.qubits;
    }


    public Map<Integer, Qubit[]> getIntermediateQubits()
    {
        return this.intermediateQubits;
    }


    private Qubit[] calculateQubits()
    {
        Qubit[] answer = new Qubit[numberOfQubits];
        if(numberOfQubits == 0)
        {
            return answer;
        }
        int lastidx = numberOfSteps == 0 ? 0 : numberOfSteps - 1;
        while(intermediateProps[lastidx] == null)
        {
            lastidx--;
        }
        double[] d = calculateQubitStatesFromVector(intermediateProps[lastidx]);
        for(int i = 0; i < answer.length; i++)
        {
            answer[i] = new Qubit();
            answer[i].setProbability(d[i]);
        }
        return answer;
    }


    private Qubit[] calculateQubitsFromVector(Complex[] probabilities)
    {
        Qubit[] answer = new Qubit[numberOfQubits];
        if(numberOfQubits == 0)
        {
            return answer;
        }
        double[] d = calculateQubitStatesFromVector(probabilities);
        for(int i = 0; i < answer.length; i++)
        {
            answer[i] = new Qubit();
            answer[i].setProbability(d[i]);
        }
        return answer;
    }


    public Complex[] getProbability()
    {
        return this.probability;
    }


    public void setIntermediateProbability(int QuantumStep, Complex[] p)
    {
        this.intermediateProps[step] = p;
        this.intermediateQubits.put(step, calculateQubitsFromVector(p));
        this.probability = p;
    }


    public Complex[] getIntermediateProbability(int QuantumStep)
    {
        int ret = QuantumStep;
        while(ret > 0 && intermediateProps[ret] == null)
        {
            ret--;
        }
        return intermediateProps[ret];
    }


    public void measureSystem()
    {
        if(this.qubits == null)
        {
            this.qubits = getQubits();
        }
        double random = Math.random();
        int resize = 1 << numberOfQubits;
        double[] probamp = new double[resize];
        double probtot = 0;
        // we don't need all probabilities, but we might use this later
        for(int i = 0; i < resize; i++)
        {
            probamp[i] = this.probability[i].abssqr();
        }
        int sel = 0;
        probtot = probamp[0];
        while(probtot < random)
        {
            sel++;
            probtot = probtot + probamp[sel];
        }
        this.measuredProbability = sel;
        double outcome = probamp[sel];
        for(int i = 0; i < numberOfQubits; i++)
        {
            qubits[i].setMeasuredValue(sel % 2 == 1);
            sel = sel / 2;
        }
    }


    public int getMeasuredProbability()
    {
        return measuredProbability;
    }


    public void printInfo()
    {
        System.out.println("Info about Quantum Result");
        System.out.println("==========================");
        System.out.println("Number of qubits = " + numberOfQubits + ", number of QuantumSteps = " + numberOfSteps);
        for(int i = 0; i < probability.length; i++)
        {
            System.out.println("Probability on " + i + ":" + probability[i].abssqr());
        }
        System.out.println("==========================");
    }
}
