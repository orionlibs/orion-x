package com.orion.quantumcomputing.gate;

public class Hadamard extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.HC, Complex.HC}, {Complex.HC, Complex.HCN}};


    public Hadamard(int idx)
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
        return "H";
    }
}
