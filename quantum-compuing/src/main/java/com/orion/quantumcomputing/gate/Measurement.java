package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Measurement class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Measurement extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    /**
     * <p>Constructor for Measurement.</p>
     */
    public Measurement()
    {
    }


    /**
     * <p>Constructor for Measurement.</p>
     *
     * @param idx a int
     */
    public Measurement(int idx)
    {
        super(idx);
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "M";
    }


    @Override
    public Complex[] applyOptimize(Complex[] v)
    {
        return v;
    }


    @Override
    public boolean hasOptimization()
    {
        return true;
    }
}
