package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Y class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Y extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ZERO, Complex.I.mul(-1)}, {Complex.I, Complex.ZERO}};


    /**
     * <p>Constructor for Y.</p>
     *
     * @param idx a int
     */
    public Y(int idx)
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
        return "Y";
    }
}
