package com.orion.quantumcomputing.gate;

import org.redfx.strange.Block;
import org.redfx.strange.BlockGate;
import org.redfx.strange.Step;
import org.redfx.strange.gate.Add;
import org.redfx.strange.gate.Swap;
import org.redfx.strange.local.Computations;

/**
 * <p>Mul class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Mul extends BlockGate<Mul>
{
    final Block block;


    /**
     * Multiply the qubit in the x register with an integer mul
     *
     * @param x0 start idx x register
     * @param x1 end idx x register
     * @param mul
     * x_0 ----- y_0 + x_0
     * x_1 ----- y+1 + x_1
     * y_0 ----- 0
     * y_1 ----- 0
     */
    public Mul(int x0, int x1, int mul)
    {
        super();
        setIndex(x0);
        this.idx = x0;
        this.block = createBlock(x0, x1, mul);
        setBlock(block);
    }


    /**
     * <p>createBlock.</p>
     *
     * @param y0 a int
     * @param y1 a int
     * @param mul a int
     * @return a {@link Block} object
     */
    public Block createBlock(int y0, int y1, int mul)
    {
        int x0 = 0;
        int x1 = y1 - y0;
        int size = 1 + x1 - x0;
        int dim = 1 << size;
        Block answer = new Block("Mul", 2 * size);
        for(int i = 0; i < mul; i++)
        {
            org.redfx.strange.gate.Add add = new org.redfx.strange.gate.Add(x0, x1, x1 + 1, x1 + size);
            answer.addStep(new Step(add));
        }
        for(int i = x0; i < x1 + 1; i++)
        {
            answer.addStep(new Step(new Swap(i, i + size)));
        }
        int invsteps = Computations.getInverseModulus(mul, dim);
        for(int i = 0; i < invsteps; i++)
        {
            org.redfx.strange.gate.Add add = new Add(x0, x1, x1 + 1, x1 + size).inverse();
            answer.addStep(new Step(add));
        }
        return answer;
    }
}
