package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.InformalGate;

/**
 * <p>ProbabilitiesGate class.</p>
 *
 * @author alain
 * @version $Id: $Id
 */
public class ProbabilitiesGate extends InformalGate
{
    private Complex[] probabilities;


    /**
     * <p>Constructor for ProbabilitiesGate.</p>
     *
     * @param idx a int
     */
    public ProbabilitiesGate(int idx)
    {
        super(idx);
    }


    /** {@inheritDoc} */
    @Override
    public String getCaption()
    {
        return "P";
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return -1;
    }


    /** {@inheritDoc} */
    @Override
    public String getName()
    {
        return "Probabilities";
    }


    /** {@inheritDoc} */
    @Override public void setInverse(boolean v)
    {
        // NOP
    }


    /**
     * Set the probability vector for the system at the point where this gate is located.
     * @param vector
     */
    public void setProbabilites(Complex[] vector)
    {
        this.probabilities = vector;
    }


    /**
     * Return the probability vector at the location of this gate.
     * @return an array containing probabilities (as complex numbers).
     */
    public Complex[] getProbabilities()
    {
        return this.probabilities;
    }
}
