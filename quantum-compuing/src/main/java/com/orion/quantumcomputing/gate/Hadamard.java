package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Hadamard class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Hadamard extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.HC, Complex.HC}, {Complex.HC, Complex.HCN}};


    /**
     * <p>Constructor for Hadamard.</p>
     *
     * @param idx a int
     */
    public Hadamard(int idx)
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
        return "H";
    }
}
