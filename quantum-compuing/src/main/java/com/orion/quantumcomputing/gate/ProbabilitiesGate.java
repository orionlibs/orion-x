package com.orion.quantumcomputing.gate;

public class ProbabilitiesGate extends InformalGate
{
    private Complex[] probabilities;


    public ProbabilitiesGate(int idx)
    {
        super(idx);
    }


    @Override
    public String getCaption()
    {
        return "P";
    }


    @Override
    public int getSize()
    {
        return -1;
    }


    @Override
    public String getName()
    {
        return "Probabilities";
    }


    @Override
    public void setInverse(boolean v)
    {
        // NOP
    }


    public void setProbabilites(Complex[] vector)
    {
        this.probabilities = vector;
    }


    public Complex[] getProbabilities()
    {
        return this.probabilities;
    }
}
