package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 *
 * This class describe a Gate that operates on a single qubit only and that is
 * predefined with a given matrix.
 *
 * @author johan
 * @version $Id: $Id
 */
public class SingleQubitMatrixGate extends SingleQubitGate
{
    private Complex[][] matrix;


    /**
     * <p>Constructor for SingleQubitMatrixGate.</p>
     *
     * @param idx a int
     * @param m an array of {@link Complex} objects
     */
    public SingleQubitMatrixGate(int idx, Complex[][] m)
    {
        super(idx);
        this.matrix = m;
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return this.matrix;
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        super.setInverse(v);
        matrix = Complex.conjugateTranspose(matrix);
    }


    /** {@inheritDoc} */
    @Override public String toString()
    {
        return "SingleQubitMatrixGate with index " + idx;
    }
}
