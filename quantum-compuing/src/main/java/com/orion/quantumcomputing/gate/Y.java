package com.orion.quantumcomputing.gate;

public class Y extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ZERO, Complex.I.mul(-1)}, {Complex.I, Complex.ZERO}};


    public Y(int idx)
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
        return "Y";
    }
}
