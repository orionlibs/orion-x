package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Z class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Z extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE.mul(-1d)}};


    /**
     * <p>Constructor for Z.</p>
     *
     * @param idx a int
     */
    public Z(int idx)
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
        return "Z";
    }
}
