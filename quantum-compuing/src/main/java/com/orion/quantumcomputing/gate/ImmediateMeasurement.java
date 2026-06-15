package com.orion.quantumcomputing.gate;

import Complex;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>Measurement class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class ImmediateMeasurement extends SingleQubitGate
{
    static Logger LOG = Logger.getLogger(ImmediateMeasurement.class.getName());
    private final Consumer<Boolean> consumer;
    Complex[][] matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, Complex.ONE}};


    /**
     * <p>Constructor for ImmediateMeasurement.</p>
     * @param consumer this callback will be invoked when the measurement is done.
     * The creator of this gate will be notified on whether 0 was measured (false) or 1 (true)
     */
    public ImmediateMeasurement(Consumer<Boolean> consumer)
    {
        this.consumer = consumer;
    }


    /**
     * <p>Constructor for ImmediateMeasurement.</p>
     *
     * @param idx index of the qubit that this gate is applied to.
     * @param consumer this callback will be invoked when the measurement is done.
     * The creator of this gate will be notified on whether 0 was measured (false) or 1 (true)
     */
    public ImmediateMeasurement(int idx, Consumer<Boolean> consumer)
    {
        super(idx);
        this.consumer = consumer;
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
        return "IM";
    }


    public Consumer<Boolean> getConsumer()
    {
        return this.consumer;
    }
}
