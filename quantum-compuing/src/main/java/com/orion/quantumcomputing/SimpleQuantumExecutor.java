package com.orion.quantumcomputing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SimpleQuantumExecutor implements QuantumExecutor
{
    static Logger LOG = Logger.getLogger(SimpleQuantumExecutor.class.getName());


    public SimpleQuantumExecutor()
    {
    }


    @Override
    public QuantumResult runProgram(QuantumProgram p)
    {
        LOG.info("runProgram ");
        int nQubits = p.getNumberQubits();
        Qubit[] qubit = new Qubit[nQubits];
        for(int i = 0; i < nQubits; i++)
        {
            qubit[i] = new Qubit();
        }
        int dim = 1 << nQubits;
        double[] initalpha = p.getInitialAlphas();
        Complex[] probs = new Complex[dim];
        for(int i = 0; i < dim; i++)
        {
            probs[i] = Complex.ONE;
            for(int j = 0; j < nQubits; j++)
            {
                int pw = nQubits - j - 1;
                int pt = 1 << pw;
                int div = i / pt;
                int md = div % 2;
                if(md == 0)
                {
                    probs[i] = probs[i].mul(initalpha[j]);
                }
                else
                {
                    probs[i] = probs[i].mul(Math.sqrt(1 - initalpha[j] * initalpha[j]));
                }
            }
        }
        List<QuantumStep> steps = p.getSteps();
        List<QuantumStep> simpleSteps = p.getDecomposedSteps();
        if(simpleSteps == null)
        {
            simpleSteps = new ArrayList<>();
            for(QuantumStep step : steps)
            {
                simpleSteps.addAll(QuantumComputations.decomposeStep(step, nQubits));
            }
            p.setDecomposedSteps(simpleSteps);
        }
        QuantumResult result = new QuantumResult(nQubits, steps.size());
        int cnt = 0;
        result.setIntermediateProbability(0, probs);
        LOG.fine("START RUN, number of steps = " + simpleSteps.size());
        for(QuantumStep step : simpleSteps)
        {
            if(!step.getGates().isEmpty())
            {
                LOG.finer("RUN STEP " + step + ", cnt = " + cnt);
                cnt++;
                LOG.finest("before this step, probs = ");
                //      printProbs(probs);
                probs = applyStep(step, probs, qubit);
                LOG.info("after this step, probs = " + probs);
                //    printProbs(probs);
                int idx = step.getComplexStep();
                // System.err.println("complex? "+idx);
                if(idx > -1)
                {
                    result.setIntermediateProbability(idx, probs);
                }
            }
        }
        LOG.info("DONE RUN, probability vector = " + probs);
        printProbs(probs);
        double[] qp = calculateQubitStatesFromVector(probs);
        for(int i = 0; i < nQubits; i++)
        {
            qubit[i].setProbability(qp[i]);
        }
        result.measureSystem();
        p.setResult(result);
        return result;
    }


    @Override
    public void runProgram(QuantumProgram p, Consumer<QuantumResult> result)
    {
        Thread t = new Thread(() -> result.accept(runProgram(p)));
        t.start();
    }


    private void printProbs(Complex[] p)
    {
        Complex.printArray(p);
    }


    private List<QuantumStep> decomposeSteps(List<QuantumStep> steps)
    {
        return steps;
    }


    private Complex[] applyStep(QuantumStep step, Complex[] vector, Qubit[] qubits)
    {
        LOG.finer("start applystep, vectorsize = " + vector.length + ", ql = " + qubits.length);
        long s0 = System.currentTimeMillis();
        List<QuantumGate> gates = step.getGates();
        if(!gates.isEmpty() && gates.get(0) instanceof ProbabilitiesGate)
        {
            ProbabilitiesGate probGate = (ProbabilitiesGate)gates.get(0);
            probGate.setProbabilites(vector);
            return vector;
        }
        if(gates.size() == 1 && gates.get(0) instanceof PermutationGate)
        {
            PermutationGate pg = (PermutationGate)gates.get(0);
            return QuantumComputations.permutateVector(vector, pg.getIndex1(), pg.getIndex2());
        }
        Complex[] result = new Complex[vector.length];
        boolean vdd = true;
        result = QuantumComputations.calculateNewState(gates, vector, qubits.length);
        long s1 = System.currentTimeMillis();
        LOG.finer("done applystep took " + (s1 - s0));
        return result;
    }


    @Deprecated
    public Complex[][] tensor(Complex[][] a, Complex[][] b)
    {
        int d1 = a.length;
        int d2 = b.length;
        Complex[][] result = new Complex[d1 * d2][d1 * d2];
        for(int rowa = 0; rowa < d1; rowa++)
        {
            for(int cola = 0; cola < d1; cola++)
            {
                for(int rowb = 0; rowb < d2; rowb++)
                {
                    for(int colb = 0; colb < d2; colb++)
                    {
                        result[d2 * rowa + rowb][d2 * cola + colb] = a[rowa][cola].mul(b[rowb][colb]);
                    }
                }
            }
        }
        return result;
    }


    private double[] calculateQubitStatesFromVector(Complex[] vectorresult)
    {
        int nq = (int)Math.round(Math.log(vectorresult.length) / Math.log(2));
        double[] answer = new double[nq];
        int ressize = 1 << nq;
        for(int i = 0; i < nq; i++)
        {
            int pw = i;
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


    public Complex[][] createPermutationMatrix(int first, int second, int n)
    {
        Complex[][] swapMatrix = new Swap().getMatrix();
        Complex[][] iMatrix = new Identity().getMatrix();
        Complex[][] answer = iMatrix;
        int i = 1;
        if(first == 0)
        {
            answer = swapMatrix;
            i++;
        }
        while(i < n)
        {
            if(i == first)
            {
                i++;
                answer = tensor(answer, swapMatrix);
            }
            else
            {
                answer = tensor(answer, iMatrix);
            }
            i++;
        }
        return answer;
    }
}
