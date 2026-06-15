package com.orion.quantumcomputing.gate;

import java.util.function.Consumer;
import java.util.logging.Logger;

public class ImmediateQuantumMeasurement extends SingleQubitGate
{
    static Logger LOG = Logger.getLogger(ImmediateQuantumMeasurement.class.getName());
    private final Consumer<Boolean> consumer;
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    public ImmediateQuantumMeasurement(Consumer<Boolean> consumer)
    {
        this.consumer = consumer;
    }


    public ImmediateQuantumMeasurement(int idx, Consumer<Boolean> consumer)
    {
        super(idx);
        this.consumer = consumer;
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override public String getCaption()
    {
        return "IM";
    }


    public Consumer<Boolean> getConsumer()
    {
        return this.consumer;
    }
}
