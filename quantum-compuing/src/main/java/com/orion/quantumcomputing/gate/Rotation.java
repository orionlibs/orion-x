package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Rotation class.</p>
 *
 * @author alain
 * @version $Id: $Id
 */
public class Rotation extends SingleQubitGate
{
    protected final double thetav;
    private final Axes axis;
    protected Complex[][] matrix;


    /**
     * <p>Constructor for Rotation.</p>
     *
     * @param theta a double
     * @param axis a {@link org.redfx.strange.gate.Rotation.Axes} object
     * @param idx a int
     */
    public Rotation(double theta, Axes axis, int idx)
    {
        super(idx);
        this.thetav = theta;
        this.axis = axis;
        switch(axis)
        {
            case XAxis:
                matrix = new Complex[][] {{new Complex(Math.cos(theta / 2), 0), new Complex(0, -Math.sin(theta / 2))}, {new Complex(0, -Math.sin(theta / 2)), new Complex(Math.cos(theta / 2), 0)}};
                break;
            case YAxis:
                matrix = new Complex[][] {{new Complex(Math.cos(theta / 2), 0), new Complex(-Math.sin(theta / 2), 0)}, {new Complex(Math.sin(theta / 2), 0), new Complex(Math.cos(theta / 2))}};
                break;
            case ZAxis:
                matrix = new Complex[][] {
                                {new Complex(Math.cos(theta / 2), -1 * Math.sin(theta / 2)), Complex.ZERO},
                                {Complex.ZERO, new Complex(Math.cos(theta / 2), -1 * Math.sin(theta / 2))}};
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        super.setInverse(v);
        matrix = Complex.conjugateTranspose(matrix);
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "Rotation of " + axis.name() + " with angle " + thetav;
    }


    public enum Axes
    {
        XAxis,
        YAxis,
        ZAxis
    }
}
