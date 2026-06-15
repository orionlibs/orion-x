package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>X class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class X extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ZERO, Complex.ONE}, {Complex.ONE, Complex.ZERO}};


    /**
     * <p>Constructor for X.</p>
     *
     * @param idx a int
     */
    public X(int idx)
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
        return "X";
    }
}
