package com.orion.quantumcomputing;

public class Qubit
{
    private Complex alpha;
    private Complex beta;
    private double theta = 0;
    private double phi = 0;
    private boolean measured = false;
    private boolean measuredValue;
    private double probability;


    public Qubit()
    {
        this.alpha = Complex.ONE;
        this.beta = Complex.ZERO;
    }


    public Qubit(double alphaRealPart)
    {
        this.alpha = new Complex(alphaRealPart, 0);
        this.beta = new Complex(Math.sqrt(1 - alphaRealPart * alphaRealPart), 0);
    }


    private double calculate0()
    {
        return Math.cos(theta / 2);
    }


    private Complex calculate1()
    {
        double s = Math.sin(theta / 2);
        return new Complex(Math.cos(phi) * s, Math.sin(phi) * s);
    }


    public void setProbability(double probability)
    {
        this.probability = probability;
    }


    public int measure()
    {
        return measuredValue ? 1 : 0;
    }


    public void setMeasuredValue(boolean v)
    {
        this.measuredValue = v;
    }


    public double getProbability()
    {
        return this.probability;
    }
}
