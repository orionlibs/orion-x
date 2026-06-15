package com.orion.quantumcomputing.gate;

public class QuantumMeasurement extends SingleQubitGate
{
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    public QuantumMeasurement()
    {
    }


    public QuantumMeasurement(int idx)
    {
        super(idx);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


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
