package com.orion.quantumcomputing.gate;

public class Z extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE.mul(-1d)}};


    public Z(int idx)
    {
        super(idx);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public String getCaption()
    {
        return "Z";
    }
}
